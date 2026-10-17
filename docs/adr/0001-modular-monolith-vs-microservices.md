# ADR-0001: Modular monolith with separately deployable API/worker, not microservices

Status: Accepted

## Context

Nexus has clearly separable concerns — ingestion, normalization,
reconciliation, discrepancy management, audit — that could each be built as
an independent service with its own datastore and deployment pipeline. For a
portfolio project demonstrating senior-level backend engineering, there's a
temptation to maximize the number of services to look more "enterprise."

## Decision

Build one codebase with clear module boundaries
(`identity, tenant, integration, ingestion, normalization, processing,
reconciliation, discrepancy, audit, messaging, storage, security,
observability, shared`), compiled into one Maven artifact, deployed as two
ECS Fargate services (`nexus-api`, `nexus-worker`) that share the same
PostgreSQL database and communicate only through the database and SQS — never
through direct service-to-service HTTP calls.

## Alternatives considered

- **Full microservices** (separate service + datastore per domain module):
  rejected for v1. The engineering depth this project is meant to demonstrate
  — idempotency, outbox, reconciliation correctness, tenant isolation — is
  largely orthogonal to service count, and a microservices split would add
  distributed-transaction complexity (sagas, eventual consistency between
  services) without a concrete scaling or team-ownership reason to justify
  it. Splitting prematurely also makes local development and testing
  (Testcontainers, end-to-end flows) significantly harder for no payoff at
  this stage.
- **Single deployable (API and worker in one process)**: rejected. The API
  and worker have different scaling drivers (request rate vs. queue depth)
  and different failure blast radii (an API memory leak shouldn't stop
  message processing, and vice versa). Splitting the *deployment* while
  keeping the *codebase* unified gets the operational benefit without the
  distributed-system cost.

## Trade-offs accepted

- A future genuine need to scale or own a module independently (e.g.
  reconciliation becomes CPU-heavy enough to need its own fleet with
  different hardware) will require an extraction effort later. This is
  accepted as cheaper than paying the distributed-systems tax up front for a
  need that doesn't yet exist.
- All modules currently share one Postgres instance; a noisy-neighbor query
  from one module can affect another's latency. Mitigated by per-module query
  review and indexing discipline (see [data-model.md](../data-model.md)), not
  by splitting the database.

## Revisit when

A specific module has a measured scaling or team-ownership need that the
modular monolith can't satisfy with its current levers (more Fargate tasks,
better indexes, read replicas). Document the concrete trigger in a new ADR
before splitting — "it would look better as microservices" is explicitly not
a valid trigger.
