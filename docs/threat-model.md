# Nexus — Threat Model

Status: Phase 0 draft. STRIDE-style walk through the trust boundaries defined
in [architecture.md § 8](architecture.md#8-trust-boundaries). Each item lists
the threat, why it matters here specifically, and the mitigation this
architecture commits to. Re-reviewed at the end of each phase that touches
auth, data handling, or external input.

## 1. Authentication

| Threat | Why it matters here | Mitigation |
|---|---|---|
| Forged or expired JWT accepted | Full account/tenant takeover | Spring Security OIDC resource server validates signature against the issuer's live JWK set (`OIDC_JWK_SET_URI`), checks `exp`/`nbf` (`JwtTimestampValidator`), `iss` (`JwtIssuerValidator`), and `aud` (custom validator — Spring ships none; see `com.nexus.security.OidcJwtDecoderConfig`). No custom JWT parsing. Implemented Phase 3; see [docs/authorization-model.md § 3](authorization-model.md#3-authentication-production-vs-localtest). |
| Local/test auth bypass reachable in production | Would make every tenant trivially impersonable | `com.nexus.security.devauth.DevJwtAuthConfig` (ephemeral per-JVM HMAC key, no real IdP) is `@Profile({"local","test"})`-gated — the beans don't exist otherwise — plus a `@PostConstruct` check that fails startup if `prod` is *also* active. Two independent controls, not one. |
| Token replay after logout/revocation | Stale session used after a user is offboarded | Short-lived access tokens (issuer-controlled, not Nexus-issued); Nexus does not implement its own session store, so revocation is the IdP's responsibility — documented as a dependency, not solved twice. |
| Service-to-service calls (worker → nothing external-facing) | Worker has no inbound listener at all, so this class of threat is architecturally removed rather than mitigated by a control (see [architecture.md § 7](architecture.md#7-deployment-topology)). | N/A by design |

## 2. Tenant isolation (the highest-priority threat class in this system)

| Threat | Why it matters here | Mitigation |
|---|---|---|
| Client-supplied `tenantId` used as authorization proof | This is the single most damaging possible bug in a multi-tenant reconciliation platform — one tenant reading another's financial discrepancies | `TenantContext` is derived server-side by `TenantContextResolver` from `tenant_memberships` keyed on the JWT `sub`, injected into controllers only via `@CurrentTenant` (no other constructor exists outside tests). An `X-Tenant-Id` header is treated as a *request to filter*, verified against actual memberships, never trusted at face value — implemented and tested in `TenantAuthorizationIT` (see [api/initial-contract.md](api/initial-contract.md), [authorization-model.md § 4](authorization-model.md#4-tenant-resolution-algorithm), and [CLAUDE.md](../CLAUDE.md) rule 2). |
| IDOR via sequential/guessable resource IDs | An operator incrementing a job/discrepancy ID to probe other tenants' data | All primary keys are `uuid` (v4/v7), not sequential integers; every lookup-by-id query is additionally filtered by the caller's resolved `tenant_id`, so a correct-but-foreign UUID still returns `404`, not the record. |
| Existence leak across tenants (403 vs 404) | A `403` on someone else's resource confirms it exists; over many probes this maps out another tenant's data volume | Any resource outside the caller's tenant returns `404`, identical to a non-existent resource (see [api/initial-contract.md § 1](api/initial-contract.md#standard-error-schema)). |
| Worker processes a message for a tenant suspended after the message was enqueued | A message can sit in SQS across a tenant/integration being disabled | Worker re-validates `integrations.status = ACTIVE` and `tenants.status = ACTIVE` immediately before processing, not just at enqueue time (see [architecture.md § 8, point 4](architecture.md#8-trust-boundaries)). |
| Cross-tenant data bleed via a missing `tenant_id` filter in a new repository method | The realistic failure mode isn't a sophisticated attack — it's a developer forgetting a `WHERE tenant_id = ?` in a new query | Repository methods are required to take a `TenantContext`, not a bare tenant UUID (harder to accidentally omit); an ArchUnit test (Phase 2) flags any new JPA query method on a tenant-owned entity that doesn't include `tenantId` in its derived query/`@Query`. |

## 3. Sensitive data handling

| Threat | Why it matters here | Mitigation |
|---|---|---|
| PII/financial data in logs | Source records can contain customer names, order amounts — logging the full `payload` on error is an easy mistake | Structured logging with an explicit allowlist of fields (job id, tenant id, row number, error code); raw `payload` is never logged, only referenced by `source_records.id` for later DB lookup. |
| Sensitive data in `jsonb` columns readable by any DB role | `integrations.config` / `source_records.payload` are flexible blobs; broad DB access = broad data access | Application DB role used by `nexus-api`/`nexus-worker` is least-privilege (no `DROP`/`ALTER`, no access to other schemas); a separate, more restricted read-only role is available for reporting/BI use cases, per the existing `readonly role` migration precedent noted in the build plan. |
| Data retained longer than needed | `source_records`/`audit_events` are indefinite by design (see [data-model.md § 6](data-model.md#6-retention-considerations)) — this is a deliberate trade-off, not an oversight, but it raises the stakes of a breach | Tenant-offboarding deletion path is a named, tested operation (Phase 6), not an afterthought; indefinite retention is documented as a decision needing sign-off if the project were to handle real regulated data. |

## 4. Secrets

| Threat | Why it matters here | Mitigation |
|---|---|---|
| Source-system credentials (for `REST_PULL` integrations) stored in plaintext | `integrations.config` is a convenient place to accidentally dump an API key | `config` schema explicitly excludes credential fields; credentials for `REST_PULL` sources are stored only as a reference (ARN/name) to an AWS Secrets Manager secret, fetched by the worker at call time, never persisted in Postgres (see [CLAUDE.md](../CLAUDE.md) rule 3). |
| Static AWS credentials in the running task | Long-lived keys are a standing liability if leaked | ECS task roles (IAM, short-lived, auto-rotated) in every real AWS environment; static keys only ever appear in `.env` for local dev against LocalStack, and `.env` is gitignored. |
| Secret committed to git history | Hard to fully undo once pushed | `.env.example` contains placeholder names only (verified in this phase — see file); pre-commit/CI secret-scanning is a named Phase 1 CI step (e.g. gitleaks) rather than relying on review discipline alone. |

## 5. Upload handling (S3)

| Threat | Why it matters here | Mitigation |
|---|---|---|
| Arbitrarily large/zip-bomb-style file exhausts worker memory | Worker streams and parses operator-supplied files | Worker streams rows (no full-file buffering); `expectedSizeBytes` is validated against a hard per-tenant cap at job creation, and the presigned URL itself carries an S3 `content-length-range` condition. |
| Malicious file content (formula injection in CSV, polyglot files) | CSV is a common ingestion format and a common injection vector if ever opened in spreadsheet software downstream | Worker treats CSV purely as structured data (no shell-out, no macro execution); any value starting with `=`, `+`, `-`, `@` is treated as a literal string, never evaluated — relevant if discrepancy exports are ever opened in Excel. |
| Path traversal / key injection via `sourceRef` | Client-influenced value feeding into an S3 key | `sourceRef` is sanitized and only used as a display label; the actual S3 object key is server-generated (`{tenantId}/{jobId}/raw`), never derived from client-supplied path segments. |
| Presigned URL leaked/reused beyond intended scope | Presigned URLs are bearer credentials for the duration of their validity | Short expiry (15 min), scoped to a single object key, single HTTP method; `confirm-upload` additionally verifies the object via `HeadObject` server-side rather than trusting client-reported success. |
| Orphaned uploads / unconfirmed files accumulate | Cost and a reconnaissance surface if bucket listing were ever misconfigured | S3 lifecycle rule expires unconfirmed objects under `incoming/`; bucket policy denies `s3:ListBucket` to the application role beyond what's needed (no directory enumeration), blocks public access at the bucket level (Block Public Access on). |

## 6. Queue messages (SQS)

| Threat | Why it matters here | Mitigation |
|---|---|---|
| Message tampering in transit/at rest | Messages carry `tenant_id`/`integration_id` driving downstream authorization-adjacent decisions | SQS server-side encryption (SSE-SQS or KMS) at rest; TLS in transit (default for the SDK); the worker re-validates referenced entities against the DB rather than trusting message content as authoritative (defense in depth, see § 2). |
| Duplicate delivery causing duplicate business effects | SQS standard queues are at-least-once by design, not a bug to "fix" | Every consumer effect is idempotent by construction — upserts keyed on stable identity, never a naked `INSERT` for anything a redelivery could repeat (see [architecture.md § 6](architecture.md#6-data-flow--workflow-c-recover-from-failures)). |
| Poison message causing a crash loop that starves the queue for other tenants | One tenant's bad data shouldn't degrade another tenant's processing | Bounded retries (`maxReceiveCount`) → DLQ, not infinite redelivery; worker catches and classifies errors per-message rather than per-batch so one bad row doesn't fail an entire batch if batched consumption is used. |
| DLQ message inspected by someone without authorization to see that tenant's data | DLQ messages contain the same tenant data as the main queue | DLQ access is an IAM-gated operational action (same least-privilege posture as prod DB access), governed by the replay runbook in [docs/runbooks/](runbooks/), not ad hoc console access. |

## 7. SSRF risk

| Threat | Why it matters here | Mitigation |
|---|---|---|
| `REST_PULL` integration config lets an operator point the worker at an arbitrary internal URL | The worker making outbound HTTP calls to an operator-configured endpoint is a textbook SSRF vector against the VPC's internal services (e.g. the ECS task metadata endpoint, RDS, internal admin tools) | Allowlist of permitted destination hosts/schemes validated at integration-creation time (`422` on anything not matching); outbound calls use a dedicated HTTP client with redirects disabled by default and a connect timeout; the worker's security group has no route to sensitive internal endpoints beyond what it explicitly needs (deny-by-default egress, not "allow all outbound"). |
| DNS rebinding bypassing a host allowlist | Allowlisting by hostname alone is bypassable if DNS is attacker-influenced | Resolve and validate the destination IP against a denylist of private/link-local ranges (RFC 1918, 169.254.169.254, etc.) at request time, not just at config-save time — flagged as a Phase 3 implementation detail to get right when `REST_PULL` is actually built, not deferred indefinitely. |

## 8. Audit integrity

| Threat | Why it matters here | Mitigation |
|---|---|---|
| Audit trail edited or deleted to cover up an unauthorized resolution | Defeats the entire point of Invariant #7 | `audit_events` and `discrepancy_events` are insert-only from the application's perspective — no `UPDATE`/`DELETE` code path exists for these tables; the DB role used by the app can be further restricted (`REVOKE UPDATE, DELETE`) as a Phase 2 migration hardening step. |
| Audit event written without the business change actually succeeding (or vice versa) | A trail that doesn't match reality is worse than no trail | Audit insert happens in the same transaction as the business mutation (commit together or roll back together) — not a fire-and-forget async write. |
| Clock skew/manipulation undermining event ordering | Discrepancy history needs a trustworthy timeline | `created_at` uses the database server's `now()`, not client-supplied or application-server wall-clock time, removing one source of skew between multiple app instances. |

## 9. Operational access

| Threat | Why it matters here | Mitigation |
|---|---|---|
| Overly broad IAM role for ECS tasks | Blast radius of a compromised container | Separate, narrowly-scoped IAM roles per service (`nexus-api` role: S3 put/head on its prefix, SQS send on the ingestion queue, Secrets Manager read on its own secrets; `nexus-worker` role: S3 get, SQS receive/delete, Secrets Manager read) — not one shared "app role." |
| Direct prod DB access bypassing the app's tenant-scoping logic | An operator or script running raw SQL against prod has no TenantContext to enforce isolation | Documented in the runbooks as a break-glass action requiring approval, never a routine debugging habit; longer-term candidate: Postgres Row-Level Security policies as defense-in-depth (noted as a deferred hardening item, not required for v1 since the app layer is the primary enforcement point). |
| CI/CD pipeline as an attack vector (GitHub Actions) | A compromised workflow can push an image or apply Terraform with elevated AWS permissions | OIDC federation from GitHub Actions to AWS (no long-lived AWS keys in GitHub secrets); environment protection rules requiring approval before a workflow can apply to the `prod` Terraform environment. |

## 10. Explicitly out of scope for v1

- Multi-region/DR failover — single-region deployment, documented as a known
  gap, not silently ignored.
- Customer-managed encryption keys (CMK) for S3/RDS — AWS-managed KMS keys by
  default; CMK is a plausible enterprise-tier addition, not a v1 requirement.
- Formal penetration test — this threat model is a design-time review, not a
  substitute for one.

## 11. Phase 3 update — identity, tenant membership, authorization

Re-reviewed because this phase touched auth directly, per this document's
own header ("re-reviewed at the end of each phase that touches auth, data
handling, or external input").
Sections 1 and 2 above were updated in place to describe what's actually
built rather than what was planned; this section records the assumptions
made along the way and what's still open.

### Assumptions made explicit

- **The JWT carries no role or tenant claims.** Nexus roles are per-tenant
  (`tenant_memberships.role`), not identity-provider-wide, so they can't
  live in a token issued by a shared IdP across all of a customer's
  internal tools. This is why role checks are plain Java
  (`TenantContext.require(...)`) against a DB-resolved role, never
  `@PreAuthorize("hasRole(...))` against JWT-derived authorities — see
  [authorization-model.md § 1](authorization-model.md#1-two-separate-questions-two-separate-mechanisms).
  If a future requirement needs IdP-asserted roles/groups (e.g. SSO group
  sync), that's a deliberate new design decision, not an extension of this
  one.
- **`X-Tenant-Id` mismatches are 403, not 404.** Unlike a resource-id
  lookup, the tenant id in the header came from the caller themselves —
  there's no existence fact being newly revealed by a 403 here. Documented
  explicitly in [authorization-model.md § 4](authorization-model.md#4-tenant-resolution-algorithm)
  so this isn't mistaken for an inconsistency with the 404-for-resources
  rule in § 2 above.
- **Audit logging of auth outcomes is structured SLF4J logs, not
  `audit_events` rows.** `AuditEventRecorder` requires an existing
  transaction (`Propagation.MANDATORY`) and a resolved tenant id — neither
  exists yet when authentication itself fails (there's no tenant to
  attribute the row to, and no transaction has started). Authentication/
  authorization *failures* are logged via SLF4J in `SecurityConfig`'s entry
  point and access-denied handler (principal id and path only — never the
  token or full claim set); successful *authorized actions* (e.g.
  `POST /api/v1/integrations`) do write a proper `audit_events` row, since
  a tenant and transaction genuinely exist by then.
- **The dev token endpoint accepts any subject with zero credential
  check.** This is intentional and is the entire isolation story's point of
  leverage — see § above and `DevJwtAuthConfig`'s Javadoc. It is exactly as
  dangerous as a feature that mints valid credentials for anyone should
  sound, which is why both independent guards (profile gate +
  `@PostConstruct` check) exist rather than one.

### Residual risks, not yet closed

| Risk | Why it's residual | Planned closure |
|---|---|---|
| No tenant membership management API | `tenant_memberships` rows can currently only be created by direct DB/test access, not through the product itself | A real "invite member" / "change role" endpoint, itself `TENANT_ADMIN`-gated, is unbuilt — tracked as follow-up work, not silently dropped |
| No token revocation story beyond short expiry | A compromised access token is valid until it naturally expires; Nexus has no session store to force early invalidation | Explicitly the identity provider's responsibility (see § 1) — acceptable as long as access-token TTLs issued by the real IdP are kept short, which is a provider-side configuration choice outside this codebase |
| `TenantIntegrationAccessGuard` exists and is tested, but nothing calls it yet | The worker/messaging code that would actually invoke it per real queue message doesn't exist until Phase 4 | Tracked in docs/build-plan.md; the check itself is verified in isolation now (`TenantIntegrationAccessGuardIT`) specifically so Phase 4 wires it in rather than re-deriving it |
| CORS defaults to no origins anywhere, including a real frontend's eventual origin | No frontend exists yet to need one | `CORS_ALLOWED_ORIGINS` is a per-environment property for exactly this reason — set it when a real frontend origin exists, never widen it speculatively |
| Rate limiting (429 is documented in the API contract) is not implemented | No abuse-prevention layer exists yet at any tier | Deferred; not a Phase 3 concern, noted so the `429` row in docs/api/initial-contract.md isn't mistaken for already-true |
