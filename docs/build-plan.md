# Nexus — Phased Build Plan

Each phase has explicit acceptance criteria, its dependency on prior phases,
and the command(s) used to verify it. Do not start a phase whose
dependencies aren't met; do not silently fold a later phase's scope into an
earlier one. Phases are committed and reviewed one at a time — do not begin
the next phase automatically (see [CLAUDE.md](../CLAUDE.md)).

## Phase 0 — Architecture & engineering rules ✅ this phase

**Depends on:** nothing.
**Produces:** this document, [architecture.md](architecture.md),
[data-model.md](data-model.md), [api/initial-contract.md](api/initial-contract.md),
[threat-model.md](threat-model.md), [decisions.md](decisions.md) + ADRs,
[CLAUDE.md](../CLAUDE.md), repo skeleton (no application code).
**Acceptance criteria:**
- [x] All invariants from the original brief are represented in
      `data-model.md` with explicit constraints.
- [x] Every workflow (ingestion, reconciliation, failure recovery) has a
      diagram and a written explanation of the idempotency/correctness
      guarantee.
- [x] Every architecture decision with a real trade-off has an ADR.
- [x] Dependency versions are verified against official sources, not
      assumed (see [ADR-0006](adr/0006-spring-boot-4-vs-3.md)).
**Test command:** none — documentation only, no application code exists.
**Gate to Phase 1:** project owner reviews this doc set and the open
assumptions below.

### Open assumptions carried out of Phase 0

1. `source_records.external_record_id` derivation strategy is
   source-type-dependent and will be finalized against the first two real
   normalization rule sets in Phase 3, not guessed now (see
   [data-model.md](data-model.md#source_records)).
2. Exact Spring Boot 4.1.x **patch** version, and the compatible
   Testcontainers/Flyway/Micrometer/OpenTelemetry/AWS-SDK-BOM versions pulled
   in by its dependency management, are pinned and re-verified at Phase 1
   implementation time (ADR-0006 fixes the minor line, not the patch).
3. Row-Level Security in Postgres is noted as a deferred defense-in-depth
   measure (threat-model.md § 9) — the application layer is the primary
   tenant-isolation enforcement point for v1.

## Phase 1 — Project skeleton & local developer loop

**Depends on:** Phase 0 sign-off.
**Scope:**
- `pom.xml` with Spring Boot 4.1.x parent, Java 21 toolchain, dependencies
  for web, data-jpa, security-oauth2-resource-server, actuator, validation.
- Two entry points: `NexusApiApplication`, `NexusWorkerApplication`.
- `compose.yaml`: PostgreSQL + LocalStack (S3, SQS) for local dev.
- `Dockerfile` (multi-stage: Maven build → slim JRE runtime), `.dockerignore`.
- `GET /actuator/health` reachable locally via compose.
- GitHub Actions workflow: build + unit test on push/PR (no deploy yet).
- ArchUnit dependency added (test-scope) with one passing placeholder rule,
  wired up for real in Phase 2.
**Acceptance criteria:**
- `./mvnw spring-boot:run` (api profile) starts against the compose
  Postgres and responds `200` on `/actuator/health`.
- `docker compose up` brings up Postgres + LocalStack cleanly from a clean
  checkout.
- CI workflow runs and passes on a trivial PR.
**Test commands:**
```
./mvnw -q verify
docker compose up -d
curl -f http://localhost:8080/actuator/health
```
**Explicitly not in scope yet:** no business endpoints, no Flyway
migrations beyond a placeholder, no real auth (health endpoint only).

## Phase 2 — Core domain & database

**Depends on:** Phase 1.
**Scope:**
**Delivered** (narrower than this section originally planned — see note
below):
- Flyway migrations `V1`–`V12` for all 12 tables in
  [data-model.md](data-model.md), with every constraint/index from that doc
  plus the composite-FK tenant-consistency pattern resolved (see
  [data-model.md § 7](data-model.md#7-phase-2-implementation-notes)).
- JPA entities + Spring Data repositories for all 12 tables, split by
  owning module, FKs as plain `UUID` columns (not `@ManyToOne`).
- Three narrowly-scoped, `@Transactional`-enforced persistence services:
  `IdempotencyKeyService`, `AuditEventRecorder`, `OutboxEventWriter`.
- Testcontainers PostgreSQL suite: migrations, constraints, cross-tenant FK
  rejection, optimistic locking, and real multi-row transaction rollback
  (`src/test/java/com/nexus/persistence/`).

**Deferred to Phase 3**, per this round's explicit instruction not to build
ingestion/reconciliation business logic yet — these were in this section's
original draft but are API/business-logic-shaped, not persistence:
- `identity`/`security` JWT validation config and `TenantContext`
  resolution from `tenant_memberships`.
- `POST /api/v1/integrations`, `GET /api/v1/integrations`.
- The ArchUnit module-boundary/tenant-scoping rule — there's nothing yet for
  it to meaningfully check without a service layer calling across modules;
  it lands with Phase 3's first real cross-module call.

**Acceptance criteria:**
- [x] All 12 migrations apply cleanly on an empty database and `migrate()`
      is a no-op on a second run (`FlywayMigrationIT`).
- [x] Every documented constraint is proven at the database level, not just
      assumed from Java annotations — including a raw-SQL test that
      bypasses the Java enum layer entirely (`ConstraintsIT`).
- [x] A stale-version concurrent update is rejected
      (`OptimisticLockingIT`).
- [x] A multi-row write rolls back completely on failure, verified from a
      separate transaction afterward, not just "an exception was thrown"
      (`TransactionalPersistenceServicesIT`).
- [ ] Tenant-isolation-at-the-API-level test (tenant A can't read tenant
      B's integration via a real endpoint) — deferred with the API
      endpoint itself to Phase 3.
**Test commands:**
```
./mvnw -q verify   # runs Testcontainers-backed integration tests, incl. migrations
```

## Phase 3 — Ingestion workflow (Workflow A)

**Depends on:** Phase 2 (schema, entities, repositories).
**Scope:**
- Carried forward from the original Phase 2 plan (deferred — see Phase 2's
  note above): `identity`/`security` JWT validation config,
  `TenantContext` resolution from `tenant_memberships`,
  `POST`/`GET /api/v1/integrations`, and the ArchUnit module-boundary rule.
- `ingestion`, `normalization`, `processing`, `messaging`, `storage`
  modules.
- `POST /api/v1/ingestions` (idempotency-key enforced), presigned S3 upload,
  `confirm-upload`, `GET /api/v1/ingestions/{id}`, `POST
  /api/v1/ingestions/{id}/retry`.
- Outbox writer + Outbox Publisher (polling component).
- `nexus-worker`: SQS consumer, CSV parsing/normalization for one concrete
  schema, idempotent `source_records` upsert, `processing_attempts`
  recording, job status transitions per the state machine in
  [architecture.md § 4](architecture.md#4-data-flow--workflow-a-import-customer-data).
- DLQ wired up with `maxReceiveCount` redrive policy (LocalStack locally,
  real SQS later).
- Scheduled sweep for abandoned `CREATED` jobs past TTL.
**Acceptance criteria:**
- Chaos test: kill the worker process mid-batch, restart, redeliver the
  same SQS message — assert zero duplicate `source_records` rows and a
  second `processing_attempts` row recorded.
- Retry test: call `/retry` twice on a `FAILED` job — assert exactly one
  `ingestion_jobs` row still exists and `processing_attempts` shows two
  attempts, not two jobs.
- A deliberately malformed row in an otherwise-valid file lands the job in
  `COMPLETED_WITH_ERRORS` with the bad row recorded, not a hard `FAILED`.
- A message that fails `maxReceiveCount` times lands in the DLQ and trips a
  (locally simulated) alarm condition.
**Test commands:**
```
./mvnw -q verify -Dtest=IngestionWorkflowIT
./mvnw -q verify -Dtest=WorkerIdempotencyChaosIT
```

## Phase 4 — Reconciliation engine (Workflow B)

**Depends on:** Phase 3 (`source_records` populated from at least two
integrations).
**Scope:**
- `reconciliation` module: business-key matching, amount-tolerance and
  timestamp-skew comparison, duplicate-key detection, explainable
  `discrepancies` generation.
- `POST /api/v1/reconciliation-runs`, `GET
  /api/v1/reconciliation-runs/{id}`, `GET /api/v1/discrepancies`, `GET
  /api/v1/discrepancies/{id}`.
- Load test harness (k6 or Gatling) seeding two integrations with a known
  set of matches/mismatches/missing/duplicate records.
**Acceptance criteria:**
- Golden-file test: a fixed input pair of CSVs produces an exact, asserted
  set of discrepancies with correct `reason` and `explanation` for each —
  this is the core "correctness, not vibes" test for the whole project.
- Tolerance edge cases are explicitly tested: an amount difference exactly
  at the tolerance boundary is *not* flagged; one cent over *is*.
- Timestamp comparison correctly treats two timestamps from different
  source timezones representing the same instant as matching.
- Load test result recorded against the throughput target in
  [architecture.md § 9](architecture.md#9-non-functional-targets) with
  actual numbers, replacing the placeholder target.
**Test commands:**
```
./mvnw -q verify -Dtest=ReconciliationEngineTest
./mvnw -q verify -Dtest=ReconciliationGoldenFileIT
k6 run scripts/load/reconciliation-run.js
```

## Phase 5 — Discrepancy resolution, audit, and cloud infrastructure

**Depends on:** Phase 4.
**Scope:**
- `discrepancy`, `audit` modules: `POST
  /api/v1/discrepancies/{id}/resolve` with optimistic locking, full
  `discrepancy_events`/`audit_events` write path.
- Terraform modules: VPC, RDS (Multi-AZ), S3 (raw imports + lifecycle
  rules), SQS + DLQ, ECR, ECS Fargate (api + worker services), Secrets
  Manager, IAM roles (least privilege per service per
  [threat-model.md § 9](threat-model.md#9-operational-access)).
- GitHub Actions: build → push to ECR → `terraform plan` on PR → manual
  approval → `terraform apply` + ECS deploy on merge to `main`, OIDC
  federation (no static AWS keys in GitHub secrets).
**Acceptance criteria:**
- Concurrent-resolution test: two requests resolve the same discrepancy at
  "the same time" (same starting `version`) — assert one succeeds and one
  gets `409 STALE_VERSION`, never both silently succeeding.
- `terraform plan` is clean (no drift) on a freshly `apply`-ed environment.
- A deployed `dev` environment answers `/actuator/health` over the ALB and
  a worker task processes a real (non-LocalStack) SQS message end to end.
**Test commands:**
```
./mvnw -q verify -Dtest=DiscrepancyResolutionConcurrencyIT
terraform -chdir=infra/terraform/environments/dev plan
```

## Phase 6 — Observability, failure injection, and operational runbooks

**Depends on:** Phase 5 (real AWS environment to exercise).
**Scope:**
- Micrometer metrics for outbox lag, ingestion throughput, discrepancy
  counts; OpenTelemetry traces across API → outbox → SQS → worker → DB
  (trace context propagated through the SQS message, not lost at the queue
  boundary).
- CloudWatch dashboards + alarms: DLQ depth > 0, outbox unpublished past
  threshold, ECS task health, RDS connection saturation.
- `docs/runbooks/`: DLQ inspection & replay, abandoned-upload cleanup
  verification, tenant offboarding/data deletion, break-glass DB access.
- Game day: deliberately inject a worker crash, an SQS outage (via
  LocalStack/chaos tooling), and a DB failover, and record what actually
  happened against what the runbook predicted.
**Acceptance criteria:**
- A trace for one ingestion job is visible end-to-end in CloudWatch/OTel
  with consistent trace id across API and worker logs.
- Each runbook has been executed at least once against a real (dev)
  environment, with the actual commands/output captured in the runbook
  itself — not written speculatively.
- Game day findings are written up, including at least one thing that
  didn't go as expected and what was fixed as a result.
**Test commands:**
```
./scripts/gameday/kill-worker-mid-batch.sh
./scripts/gameday/inject-sqs-outage.sh
```

## Phase 7 — Hardening, performance evidence, and resume-ready write-up

**Depends on:** Phase 6.
**Scope:**
- Re-review [threat-model.md](threat-model.md) against what was actually
  built (not just what was planned) and close or explicitly accept any gap.
- Replace every placeholder number in
  [architecture.md § 9](architecture.md#9-non-functional-targets) with a
  measured result and the methodology used to get it.
- Final README pass: real run instructions, real architecture diagram
  (exported, not just Mermaid source), a short "what I'd do differently"
  section — this is also the material that should drive the resume
  bullets and interview talking points, grounded in what was actually
  measured and decided, not aspirational claims.
**Acceptance criteria:**
- No "target, not yet achieved" caveats remain unaddressed — each is either
  measured-and-documented or explicitly deferred with a reason.
- A reviewer unfamiliar with the project can go from `README.md` to a
  running local environment in under 10 minutes following only the written
  instructions.
**Test command:** full suite, clean checkout:
```
git clone <repo> && cd nexus && ./mvnw -q verify && docker compose up -d
```
