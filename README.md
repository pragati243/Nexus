# Nexus — Enterprise Data Integration & Reconciliation Platform

Nexus imports records from external systems (CSV files and APIs), normalizes and
persists them asynchronously, reconciles the same business entity across two or
more source systems, surfaces discrepancies with an explainable reason, and gives
authorized operators an auditable workflow to resolve them.

This is a portfolio project built to demonstrate production-grade backend
engineering practices — not a toy CRUD app. The interesting parts are
**correctness under failure**: idempotency, exactly-once *effects* on top of
at-least-once *delivery*, transactional outbox, bounded retries with DLQ replay,
tenant isolation, and an explainable reconciliation engine with full audit
history.

## Status

🚧 **Phase 1 — Project skeleton & local developer loop.** A running API
skeleton exists (health/readiness/liveness probes, service metadata endpoint,
global error handling, security foundation). No business modules yet. See
[docs/build-plan.md](docs/build-plan.md) for the phased plan and current
phase gate.

## Start here

| Doc | Purpose |
|---|---|
| [docs/architecture.md](docs/architecture.md) | System context, containers, components, data flow, deployment topology, trust boundaries, failure paths |
| [docs/data-model.md](docs/data-model.md) | Entities, relationships, invariants, indexes, retention |
| [docs/api/initial-contract.md](docs/api/initial-contract.md) | REST contract: endpoints, schemas, status codes, pagination, idempotency |
| [docs/threat-model.md](docs/threat-model.md) | STRIDE-style threat model: auth, tenant isolation, secrets, uploads, queues |
| [docs/decisions.md](docs/decisions.md) + [docs/adr/](docs/adr/) | Architecture decision records with trade-offs |
| [docs/build-plan.md](docs/build-plan.md) | Phased implementation plan, acceptance criteria, test commands |
| [CLAUDE.md](CLAUDE.md) | Engineering rules for anyone (human or AI) implementing this codebase |

## Stack (verified against official sources on 2026-10-17 — see [ADR-0006](docs/adr/0006-spring-boot-4-vs-3.md))

- **Language/runtime:** Java 21 LTS
- **Framework:** Spring Boot 4.1.x (Spring Framework 7, Jakarta EE 11 / Servlet 6.1)
- **Build:** Maven
- **Database:** PostgreSQL + Flyway
- **Messaging:** Amazon SQS (standard queue) + DLQ
- **Storage:** Amazon S3
- **Compute:** AWS ECS Fargate (API service + worker service, same image, different entry point)
- **Observability:** Spring Boot Actuator, Micrometer, OpenTelemetry → CloudWatch
- **IaC:** Terraform
- **Testing:** JUnit 5, Mockito, Testcontainers, WireMock
- **CI:** GitHub Actions

## Local development

### Prerequisites

- JDK 21 (any distribution — Temurin, Microsoft Build of OpenJDK, etc.). You
  do **not** need Maven installed; this repo uses the Maven Wrapper, which
  only needs a JDK on `JAVA_HOME`/`PATH` to bootstrap itself.
- Docker (for the local PostgreSQL container and for Testcontainers-backed
  tests).

### Run the API locally

```bash
docker compose up -d                 # starts PostgreSQL on localhost:5432
cp .env.example .env                 # edit if you changed compose defaults
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Then:

- `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- `curl http://localhost:8080/actuator/health/liveness` /
  `.../readiness` → liveness/readiness probes
- `curl http://localhost:8080/api/v1/system/info` → service metadata
- Swagger UI: http://localhost:8080/swagger-ui.html

### Run the worker locally

The worker has no HTTP endpoints and no real behavior yet (messaging/SQS
consumption lands in Phase 3) — it exists to prove the two-entry-point shape
boots cleanly:

```bash
./mvnw spring-boot:run -Dspring-boot.run.main-class=com.nexus.NexusWorkerApplication -Dspring-boot.run.profiles=local
```

### Build and test

```bash
./mvnw verify
```

Runs unit tests plus Testcontainers-backed integration tests (spins up a real
ephemeral PostgreSQL per test class — no shared/mocked database). Requires
Docker to be running.

### Build the container image

```bash
docker build -t nexus-api:local .
docker run --rm -p 8080:8080 --env-file .env nexus-api:local
```

Only the API is containerized so far — see
[docs/build-plan.md](docs/build-plan.md) Phase 1 notes for why the worker
image is deferred.

## Repository layout

See [docs/architecture.md § Repository Structure](docs/architecture.md#repository-structure).
