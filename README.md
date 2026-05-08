# Data Aggregation Service — Take-Home Assignment

## Overview

```
Edge Device Simulator ──HTTP POST──► Aggregation Service ──► 2) Storage Service
(provided by MODE)                   (you implement)            (provided by MODE)
                                         │
                                         │
                                         ▼
                                  1) Transform Service
                                     (provided by MODE)
```

The **Edge Device Simulator** sends sensor data payloads from multiple IoT devices to your **Aggregation Service**.

Your service must:

1. Receive device data
2. Use the **Transform Service** to process valid readings
3. Write transformed results to the **Storage Service**

## Timebox

Target: **3–5 hours**.

## What You Implement

Implement only the aggregation service. All other services are provided by MODE and run as Docker containers.

Go is recommended, but any language is fine as long as it runs in a Docker container.

You can use AI tools or coding agents to help with the implementation.

## What MODE Provides

- `compose.yml` — Docker Compose file that starts all provided services
- `DATA_SPEC.md` — Full API specification for all service interfaces

The provided services should not be modified.

## Service Contracts

All API contracts are documented in `DATA_SPEC.md`. Key points:

- `POST /data` — your ingestion endpoint for device payloads
- `POST /transform` — transform service; may be slow; retry on 5xx
- `POST /write` — storage service; accepts an array of records

## Design Expectations

Your aggregation service should stay responsive and available even when downstream services are slow or failing. The provided Transform and Storage services behave like real backends — read `DATA_SPEC.md` for their contracts and decide how your service should handle what you find there.

Aim to deliver every valid record the service has accepted. Dropping accepted valid records is a failure mode, not a design choice. Invalid payloads are a separate matter — see `DATA_SPEC.md`.

The exact success response for an accepted payload is part of your API design. Document your choice and trade-offs in `DESIGN.md`.

## How to Run

```bash
docker compose up --build
```

Add your aggregation service to `compose.yml` before running.

## Execution Requirements

| Requirement            | Detail                                                                         |
|------------------------|--------------------------------------------------------------------------------|
| **Ingestion endpoint** | `POST http://localhost:8080/data` accepts edge device payloads                 |
| **Health check**       | `GET http://localhost:8080/healthz` returns `200 OK` when the service is ready |

> **Health check tip:** Declare a `healthcheck` in your `compose.yml` so the edge simulator waits for your service to be ready before sending data.

Environment variables your aggregation service should read:

| Variable            | Default                     | Description                       |
|---------------------|-----------------------------|-----------------------------------|
| `PORT`              | `8080`                      | Port to listen on                 |
| `TRANSFORM_SVC_URL` | `http://transform-svc:9000` | Base URL of the Transform Service |
| `STORAGE_SVC_URL`   | `http://storage-svc:9001`   | Base URL of the Storage Service   |

## Deliverables

| File          | Required | Notes                                                                                                 |
|---------------|----------|-------------------------------------------------------------------------------------------------------|
| `Dockerfile`  | **Yes**  | Must build and run your aggregation service                                                           |
| `DESIGN.md`   | **Yes**  | Completed design document (see template)                                                              |
| `compose.yml` | **Yes**  | Extend the provided file to add your service and any additional services your implementation requires |

## DESIGN.md

The `DESIGN.md` template has sections for:

- Architecture overview (what components you built and how data flows)
- Key design decisions (how you handled the characteristics of the provided services)
- Trade-offs and limitations
- Optional middleware or observability tooling, if you added any, and the trade-offs behind it
- How you worked (optional — any notes on tools, approaches, or AI usage)

## How to Submit

1. Clone or download this repository to your local environment.
2. Implement the aggregation service.
3. Push your work to your GitHub account. Make sure the repository is **private**.
4. Invite the `tinkermode` GitHub account as a collaborator.

**Do not fork this repository directly, as it is public.**


## Assignment

See [DESIGN.md](./DESIGN.md) for architecture overview, design decisions, and trade-offs.