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

🚧 **Phase 0 — Architecture & engineering rules.** No application code has been
written yet. See [docs/build-plan.md](docs/build-plan.md) for the phased plan
and current phase gate.

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

Not yet available — Phase 1 will add `compose.yaml` (PostgreSQL + LocalStack for
S3/SQS) and the first runnable API skeleton. This README will be updated with
run instructions at that point.

## Repository layout

See [docs/architecture.md § Repository Structure](docs/architecture.md#repository-structure).
