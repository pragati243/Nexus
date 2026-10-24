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

🚧 **Phase 3 — Identity, tenant membership & authorization.** Real OIDC/JWT
validation (signature, issuer, audience, expiry) in every profile except
`local`/`test`, which use an isolated, per-JVM-ephemeral-key auth path that
cannot activate alongside `prod`. `TenantContext` is resolved server-side
from `tenant_memberships` on every request — never from a client-supplied
field — and enforced through a real vertical slice
(`POST`/`GET /api/v1/integrations`). No ingestion/reconciliation business
logic yet (that's Phase 3b/4). See [docs/authorization-model.md](docs/authorization-model.md)
for the permission model and [docs/build-plan.md](docs/build-plan.md) for
the phased plan and current phase gate.

## Start here

| Doc | Purpose |
|---|---|
| [docs/architecture.md](docs/architecture.md) | System context, containers, components, data flow, deployment topology, trust boundaries, failure paths |
| [docs/data-model.md](docs/data-model.md) | Entities, relationships, invariants, indexes, retention |
| [docs/api/initial-contract.md](docs/api/initial-contract.md) | REST contract: endpoints, schemas, status codes, pagination, idempotency |
| [docs/threat-model.md](docs/threat-model.md) | STRIDE-style threat model: auth, tenant isolation, secrets, uploads, queues |
| [docs/authorization-model.md](docs/authorization-model.md) | Role/permission matrix, tenant resolution algorithm, prod vs. local/test auth |
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

### Calling a protected endpoint locally

Every other endpoint needs a bearer token. With `local` or `test` active,
mint one for any subject — no credential check, no real identity provider
involved (see [docs/authorization-model.md § 3](docs/authorization-model.md#3-authentication-production-vs-localtest)):

```bash
curl -X POST http://localhost:8080/api/v1/dev/tokens \
  -H "Content-Type: application/json" \
  -d '{"subject":"local-dev-user"}'
# => {"accessToken":"...", "tokenType":"Bearer", "expiresInSeconds":3600}
```

That subject has no tenant membership yet, so a protected call will 403
until you insert one (there's no membership-management endpoint yet —
Phase 3's explicit non-goals). For now, insert a row directly:

```sql
INSERT INTO tenants (id, name, slug, status) VALUES (gen_random_uuid(), 'Acme', 'acme', 'ACTIVE');
INSERT INTO tenant_memberships (id, tenant_id, principal_id, role)
  SELECT gen_random_uuid(), id, 'local-dev-user', 'TENANT_ADMIN' FROM tenants WHERE slug = 'acme';
```

Then:

```bash
curl http://localhost:8080/api/v1/integrations \
  -H "Authorization: Bearer <accessToken from above>"
```
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
