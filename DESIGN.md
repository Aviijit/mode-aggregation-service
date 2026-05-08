# Design Document

## Architecture Overview

```
POST /data (DataController)
     │
     │  validate → 400 Bad Request  (malformed / missing fields)
     │  queue full → 503 + Retry-After: 1  (edge will retry)
     │  valid → 202 Accepted  (immediately, before any downstream call)
     ▼
IngestChannel  (Channel<TransformRequest>, capacity 10 000)
     │
     ▼
TransformWorkerPool  (30 coroutines, kotlinx.coroutines)
     │  POST /transform — bounded retry, exponential backoff
     │  permanent failure → logged drop (device_id + timestamp preserved in log)
     ▼
StorageChannel  (Channel<StorageRecord>, capacity 10 000)
     │
     ▼
StorageBatcher  (single coroutine)
     │  flush when: batch == 100 items  OR  500 ms elapsed
     │  POST /write — bounded retry, exponential backoff, Retry-After respected
     │  permanent failure → logged with full record list (not silently dropped)
     ▼
Storage Service
```

All components run inside a single `CoroutineScope(SupervisorJob())`. A failure
in one child coroutine does not cancel the others.

---

## Design Decisions

### 1. 202 Accepted immediately on valid payload

The aggregation service returns `202 Accepted` as soon as a payload passes
validation and is enqueued — before any call to the Transform or Storage
services.

**Why:** The edge simulator dispatches concurrently and does not pace itself on
response latency. The Transform Service has variable latency with occasional
long-tail slowdowns. If the aggregation service waited for the full
transform + store pipeline before responding, slow transforms would hold
HTTP connections open, eventually exhausting the edge simulator's connection
pool and causing it to drop records. Decoupling ingestion from processing keeps
the ingestion endpoint fast regardless of downstream health.

**Trade-off:** The service guarantees delivery of records it has *accepted and
queued*, not records that have been *stored*. If the process crashes after a
202 but before the record is written to storage, that record is lost. This is
documented as a known limitation.

### 2. 503 when the ingest queue is full

If `IngestChannel` is at capacity, the controller returns `503 Service
Unavailable` with `Retry-After: 1`. The edge simulator's retry contract
(respects 5xx with backoff) means the record will be re-sent.

**Why:** A fixed-capacity channel is a deliberate back-pressure mechanism. It
prevents unbounded memory growth when the transform workers are slower than the
ingest rate (e.g. during a latency spike). 503 is the correct signal: the
service is healthy but temporarily saturated.

### 3. In-memory channels instead of an external queue

Both `IngestChannel` and `StorageChannel` are Kotlin `Channel` instances backed
by an in-process buffer.

**Why:** Keeps the implementation self-contained (no additional infrastructure
containers), is fast, and is sufficient for the stated scope. The durability
window (records in-memory but not yet stored) is bounded by the channel
capacity and the transform latency.

**Trade-off:** A process crash loses everything in the channels. For a
production system, a durable queue (e.g. Redis Streams, Kafka) would eliminate
this gap. This is a known, accepted limitation for the take-home scope.

### 4. Single batcher coroutine owns the batch buffer

`StorageBatcher` is a single coroutine that exclusively owns a
`mutableListOf<StorageRecord>`. No mutex, no concurrent access.

**Why:** A single owner eliminates shared-state bugs entirely. Kotlin
coroutines make this natural: the coroutine `select`s between a new record
arriving on `StorageChannel` and a time-based ticker, so it can flush on either
size or time without any thread synchronisation.

### 5. Flush on 100 items OR 500 ms

The batcher accumulates records until either the Storage Service's maximum
batch size (100) is reached, or 500 ms elapses since the last flush.

**Why:** Batching reduces the number of HTTP calls to the Storage Service and
improves throughput. The 500 ms window caps the latency added to any individual
record. Both thresholds are configurable via environment variables (`BATCH_SIZE`,
`BATCH_FLUSH_MS`).

**Observation:** At the simulator's default `send_rate=2`, batches are typically
size 1 under normal load. Batching provides measurable benefit during burst
events — observed batch sizes of 13–18 records during 20-device concurrent bursts.

### 6. Retry strategy

| Downstream | Max attempts | Initial delay | Max delay | Notes                              |
|------------|--------------|---------------|-----------|------------------------------------|
| Transform  | 5            | 200 ms        | 8 s       | Exponential backoff + ±25% jitter  |
| Storage    | 6            | 100 ms        | 10 s      | Same; 429 → wait exact Retry-After |

Jitter prevents retry storms when multiple workers hit a failing backend
simultaneously. 4xx responses (except 429) are not retried — they indicate a
logic error, not a transient failure.

### 7. Graceful shutdown (full drain)

On `SIGTERM`:
1. `IngestChannel` is closed — no new records enter the pipeline.
2. Worker coroutines finish their current `POST /transform` call, enqueue the
   result to `StorageChannel`, then exit their `for` loop cleanly.
3. `StorageBatcher` detects `StorageChannel` closed, flushes the remaining
   batch, and exits.
4. The coroutine scope is cancelled.

This minimises the number of accepted-but-undelivered records on a clean
shutdown. A 30-second timeout bounds the wait.

### 8. Invalid payload handling

Payloads failing validation (missing `device_id`, blank `device_id`, missing
`timestamp`, invalid RFC3339 timestamp format, missing `payload` object) are
rejected immediately with `400 Bad Request` and never enqueued.

**Why:** Invalid records cannot be transformed or stored correctly. Returning
400 is the correct signal — the edge simulator's contract treats 4xx as
non-retryable and drops the record at the edge, which is the right outcome
for structurally invalid data.

---

## Trade-offs and Limitations

| Limitation                    | Impact                                              | Production fix                         |
|-------------------------------|-----------------------------------------------------|----------------------------------------|
| In-memory queue               | Records lost on crash                               | Durable queue (Kafka / Redis Streams)  |
| 202 before storage confirms   | Accepted ≠ stored                                   | Durable queue + at-least-once delivery |
| Transform failure = drop      | Record lost after retries exhausted                 | Dead-letter queue or alerting          |
| Storage failure after retries | Batch logged with device IDs, not requeued          | Dead-letter storage, replay mechanism  |
| No deduplication              | Edge retries can produce duplicate records          | Idempotency key on storage records     |
| Single batcher coroutine      | Throughput bottleneck at very high volume           | Multiple batcher partitions            |
| No MDC correlation IDs        | Logs correlated by device_id only                   | MDC via kotlinx-coroutines-slf4j       |

---

## Additional Components

No additional infrastructure services were added. The implementation is
intentionally self-contained (`docker compose up --build` only).

Redis Streams and a lightweight message broker were considered for durable
queuing but excluded as over-engineering for the stated scope — the trade-off
is documented in the limitations table above.

---

## How You Worked

- Analysed the full spec (`DATA_SPEC.md`, `README.md`) before writing any code
- Designed the architecture and agreed on all key decisions before implementation began
- Used Claude & Ollama as a pair-programming assistant for design
  discussion, code generation, and debugging. All generated code was reviewed and
  verified against the spec contracts at each step.
- Implemented files in dependency order: models → utils → clients → services
  → controller → entry point → Dockerfile → compose