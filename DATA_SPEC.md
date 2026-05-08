# Data Specification

## Edge Device Payload (Aggregation Service Ingestion)

The Edge Device Simulator sends `POST` requests to your aggregation service.

### Endpoint

```
POST http://localhost:8080/data
Content-Type: application/json
```

### Request Body

```json
{
  "device_id": "d-abc123",
  "timestamp": "2026-04-26T10:00:00Z",
  "payload": {
    "sensor_a": 42.1,
    "sensor_b": true,
    "sensor_c": "nominal"
  }
}
```

Fields:

| Field       | Type             | Description                                    |
|-------------|------------------|------------------------------------------------|
| `device_id` | string           | Unique identifier for the device.              |
| `timestamp` | string (RFC3339) | Time the reading was taken on the device.      |
| `payload`   | object           | Sensor readings. Schema varies by device type. |

### Notes

- The Edge Device Simulator may occasionally send malformed payloads. Decide how your service handles invalid records and document that behavior in `DESIGN.md`.
- The Edge Device Simulator dispatches requests concurrently and does not pace itself based on response time.
- The Edge Device Simulator retries transient failures with bounded attempts: `5xx` responses are retried with backoff, `429` responses are retried respecting the `Retry-After` header. After the retry budget is exhausted, or on connection errors and other `4xx` responses, the record is dropped at the edge and will not be re-sent.

---

## Transform Service API

Your aggregation service calls the Transform Service to process device data.

### Endpoint

```
POST {TRANSFORM_SVC_URL}/transform
Content-Type: application/json
```

The transform endpoint is configured via the `TRANSFORM_SVC_URL` environment variable.

### Request Body

```json
{
  "device_id": "d-abc123",
  "timestamp": "2026-04-26T10:00:00Z",
  "payload": {
    "sensor_a": 42.1,
    "sensor_b": true,
    "sensor_c": "nominal"
  }
}
```

### Response

```json
{
  "transformed": {
    "value_a": 42.1,
    "status": "nominal"
  }
}
```

### Notes

- The Transform Service has variable response latency. A fraction of requests will experience latency significantly above the baseline.
- The Transform Service returns `5xx` errors at a baseline rate under normal conditions. On `5xx` responses, retry with bounded backoff.

---

## Storage Service API

Your aggregation service writes the transformed data to the Storage Service.

### Write Endpoint

```
POST {STORAGE_SVC_URL}/write
Content-Type: application/json
```

Use the `STORAGE_SVC_URL` environment variable as the base URL (default: `http://storage-svc:9001`).

### Request Body

An array of records. Maximum **100 items** per request.

```json
[
  {
    "device_id": "d-abc123",
    "timestamp": "2026-04-26T10:00:00Z",
    "data": {
      "value_a": 42.1,
      "status": "nominal"
    }
  }
]
```

### Response Body

```json
{ "stored": 1 }
```

### Responses

- `200 OK` — all records accepted; body contains `{"stored": N}`
- `400 Bad Request` — malformed request or item validation failure; no records stored
- `429 Too Many Requests` — rate limit exceeded; respect the `Retry-After` header
- `5xx` — transient error; retry with bounded backoff

### Notes

- The Storage Service returns `5xx` errors at a baseline rate under normal conditions. On error, retry the entire batch — do not silently drop records.
- Sending an empty array or a batch exceeding 100 items returns `400`.
