# Engineering rules for Nexus

These rules override default behavior for anyone — human or AI — implementing
this codebase. If a request conflicts with these rules, flag the conflict
instead of silently picking a side.

## Non-negotiables

1. **At-least-once delivery, idempotent effects.** Never claim or design for
   exactly-once message delivery. SQS standard queues redeliver. Every consumer
   must be safe to run twice on the same message (see
   [docs/architecture.md § Failure Paths](docs/architecture.md#failure-paths-and-recovery)).
2. **Never trust a client-supplied `tenantId`.** Tenant scope is derived from
   the authenticated principal's memberships, never taken as a request
   parameter at face value. Every repository query and every service method
   that touches tenant-scoped data must be provably scoped — prefer a
   `TenantContext` resolved once per request and threaded through, not
   re-derived ad hoc.
3. **Never commit secrets or invent credentials.** `.env.example` holds
   placeholder names only. Real secrets live in AWS Secrets Manager (cloud) or
   a local `.env` that is gitignored (dev). If a task needs a credential that
   doesn't exist, stop and ask — do not fabricate one.
4. **Flyway only. No Hibernate `ddl-auto: update/create` outside of unit
   tests.** Every schema change is a versioned, forward-only migration file
   checked into `src/main/resources/db/migration`. Migrations must be
   backward-compatible with the previous application version whenever an
   expand/contract pattern is feasible (add nullable column → backfill →
   enforce constraint in a later migration, not all three in one step during a
   live rollout).
5. **A business state change and its outbox event are written in the same
   database transaction.** Never call the SQS client directly from inside a
   request-handling transaction. See [ADR-0003](docs/adr/0003-transactional-outbox.md).
6. **Database constraints are the last line of defense, not the first.**
   Application-level validation is for good error messages; `UNIQUE`,
   `CHECK`, `FOREIGN KEY` and `NOT NULL` constraints are for correctness even
   if application code has a bug.
7. **Every discrepancy resolution is an audited, authorized action.** No
   endpoint mutates a `discrepancies` row without writing a
   `discrepancy_events` row and an `audit_events` row in the same transaction.
8. **Don't introduce microservices, Redis, or Kafka without a documented
   reason.** Start from the modular monolith + separately deployable worker.
   If a concrete requirement later demands one of these, write an ADR before
   adding the dependency.
9. **State dependency versions explicitly and verify them, don't guess.**
   When adding or upgrading a dependency, check it against the official docs
   or Maven Central and note how/when it was checked in the relevant ADR or
   PR description. Don't carry forward a pin that's silently gone EOL.
10. **No silent scope-skipping.** If a task's acceptance criteria can't be
    fully met (missing credential, ambiguous requirement, out-of-scope
    dependency), say so explicitly and list what's deferred — don't ship a
    partial implementation that looks complete.

## Style and architecture rules

- Prefer the simplest solution that satisfies the invariant. No speculative
  abstraction layers, no "just in case" configuration knobs.
- Modules (`identity`, `tenant`, `integration`, `ingestion`, `normalization`,
  `processing`, `reconciliation`, `discrepancy`, `audit`, `messaging`,
  `storage`, `security`, `observability`, `shared`) talk to each other through
  explicit application-service interfaces or domain events — never by
  reaching into another module's JPA repository directly.
- `shared` holds genuinely cross-cutting primitives (e.g. `TenantId`,
  `Money`, `Result` types, error codes) — not business logic. If something in
  `shared` starts making domain decisions, it's misplaced.
- Every module that writes state owns its own migrations, its own tests, and
  its own repository interfaces.
- New code ships with tests. Unit tests for business rules (reconciliation
  comparison logic, normalization, idempotency key handling) use JUnit 5 +
  Mockito. Anything that touches PostgreSQL, S3, or SQS semantics is tested
  against Testcontainers / LocalStack, not mocked at the SDK boundary, because
  SDK-level mocks don't catch real serialization, constraint, or visibility
  bugs.
- Every design decision that isn't obvious from the code gets an ADR in
  `docs/adr/` — reason plus trade-off, not just the choice.

## Working through phases

Follow [docs/build-plan.md](docs/build-plan.md) in order. Each phase has
explicit acceptance criteria and a test command. Do not start a phase whose
dependencies aren't met. Do not silently expand a phase's scope to include
later-phase work — flag it as a proposed scope change instead.

## Non-functional targets (to validate, not assumed achieved)

See [docs/architecture.md § Non-Functional Targets](docs/architecture.md#non-functional-targets).
These are targets the implementation should be measured against with real
load tests and failure injection, not claims to repeat unverified.
