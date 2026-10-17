# Nexus — Initial API Contract

Status: Phase 0 draft, v1 of the contract. Implemented and verified against
real controller tests starting Phase 2 — this document is the spec, not a
description of shipped behavior.

## 1. Conventions

### Base path and versioning

All endpoints are under `/api/v1`. A breaking change gets `/api/v2`; it does
not reuse `/api/v1` with different semantics.

### Authentication & tenant resolution

Every request carries a `Bearer` JWT validated by Spring Security's OIDC
resource server support. The server resolves the caller's tenant(s) from
`tenant_memberships` keyed on the token's `sub` claim.

**A client may *suggest* a tenant via the `X-Tenant-Id` header for callers
with multiple memberships, but the server always verifies that header value
against the caller's own memberships before using it.** An `X-Tenant-Id` the
caller doesn't belong to is a `403 Forbidden`, not a silently-ignored hint —
see [threat-model.md](../threat-model.md).

### Standard error schema

```json
{
  "type": "https://nexus.dev/errors/validation-error",
  "title": "Validation failed",
  "status": 422,
  "detail": "field 'integrationId' must reference an active integration",
  "instance": "/api/v1/ingestions",
  "code": "INTEGRATION_NOT_ACTIVE",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

Follows [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457)
shape (`type`, `title`, `status`, `detail`, `instance`) plus two Nexus
extensions: a stable machine-readable `code` for client-side branching, and
`traceId` correlating to OpenTelemetry traces in CloudWatch.

| Status | When |
|---|---|
| 400 | Malformed request (bad JSON, missing required field) |
| 401 | Missing/invalid JWT |
| 403 | Authenticated but not authorized for this tenant/resource |
| 404 | Resource doesn't exist *or* exists in a tenant the caller can't see (never leak existence across tenants) |
| 409 | Conflict — e.g. idempotency key reused with a different payload, optimistic-lock version mismatch, discrepancy already resolved |
| 422 | Semantically invalid request (references an inactive integration, invalid state transition requested) |
| 429 | Rate limited |
| 500 | Unhandled server error — detail is intentionally generic; specifics go to logs/traces via `traceId`, not the response body |

**Rule: a `404` is returned for both "doesn't exist" and "exists but belongs
to another tenant."** Returning `403` for the second case would leak that the
resource exists at all, which is a tenant-isolation leak in itself.

### Pagination

Cursor-based, not offset-based, so that discrepancy/job lists stay correct
under concurrent inserts:

```
GET /api/v1/discrepancies?limit=50&cursor=eyJjcmVhdGVkQXQiOi...
```

Response envelope:

```json
{
  "items": [ ... ],
  "nextCursor": "eyJjcmVhdGVkQXQiOi...",
  "hasMore": true
}
```

`limit` default 20, max 100. Sorting is fixed to `created_at DESC, id DESC`
per endpoint (stable tiebreaker) in v1 — configurable sort is not a v1
requirement and would complicate cursor encoding for no demonstrated need.

### Idempotency

Any `POST` that creates a resource with a side effect (job creation,
reconciliation run start, discrepancy resolution) **requires** an
`Idempotency-Key` header.

```
POST /api/v1/ingestions
Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7
```

Server behavior:

1. Look up `(tenant_id, scope, key)` in `idempotency_keys`.
2. Not found → process normally, persist the response alongside the key in
   the same transaction as the business write.
3. Found, same request fingerprint → replay the stored response verbatim
   with the original status code (not a fresh `202`).
4. Found, different request fingerprint → `409 Conflict`,
   `code: IDEMPOTENCY_KEY_REUSED`.

A missing `Idempotency-Key` on an endpoint that requires one is `400`, not a
silently-generated server-side key — generating one server-side would defeat
the point, since the client couldn't retry safely across a lost response.

### Async job pattern

Any endpoint that triggers work executing outside the request/response cycle
returns `202 Accepted` with a `Location` header and a job resource body:

```http
HTTP/1.1 202 Accepted
Location: /api/v1/ingestions/3fa85f64-5717-4562-b3fc-2c963f66afa6
Content-Type: application/json

{ "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "status": "CREATED" }
```

## 2. Endpoints

### `POST /api/v1/integrations`

Register an integration.

**Request**

```json
{
  "name": "Acme CRM — production",
  "sourceSystem": "CRM",
  "connectionType": "FILE_UPLOAD",
  "config": { "expectedSchema": "crm_orders_v1" }
}
```

**Response `201 Created`**

```json
{
  "id": "e2d6...",
  "tenantId": "9b1a...",
  "name": "Acme CRM — production",
  "sourceSystem": "CRM",
  "connectionType": "FILE_UPLOAD",
  "status": "ACTIVE",
  "createdAt": "2026-10-17T09:00:00Z"
}
```

Requires role `TENANT_ADMIN`. `config` is validated against a per-
`connectionType` JSON schema; secrets (API keys for `REST_PULL` sources) are
never accepted in this body — see [threat-model.md](../threat-model.md) §
Secrets.

### `GET /api/v1/integrations`

List integrations visible to the caller's tenant. Paginated per § 1.
Query params: `status` (optional filter).

### `POST /api/v1/ingestions`

Create an ingestion job. Requires `Idempotency-Key`.

**Request**

```json
{
  "integrationId": "e2d6...",
  "sourceRef": "2026-10-order-export.csv",
  "expectedSizeBytes": 2048576
}
```

**Response `202 Accepted`**

```json
{
  "id": "3fa8...",
  "status": "CREATED",
  "uploadUrl": "https://nexus-raw-imports.s3.ap-south-1.amazonaws.com/...(presigned, 15 min expiry)",
  "uploadMethod": "PUT"
}
```

`uploadUrl` is omitted when the integration's `connectionType` is
`REST_PULL` (the worker pulls from the source instead of waiting for a
client upload) — in that case `status` moves directly from `CREATED` to
`QUEUED` once validated.

### `POST /api/v1/ingestions/{id}/confirm-upload`

Client calls this after successfully `PUT`-ing to `uploadUrl`. Server does a
`HeadObject`, verifies size, transitions `CREATED → QUEUED`, writes the
outbox event. `404` if the job isn't in `CREATED`; `409` if already
confirmed (`code: UPLOAD_ALREADY_CONFIRMED`).

### `GET /api/v1/ingestions/{id}`

Read job status and summary.

```json
{
  "id": "3fa8...",
  "integrationId": "e2d6...",
  "status": "COMPLETED_WITH_ERRORS",
  "totalRows": 1500,
  "validRows": 1487,
  "errorRows": 13,
  "attempts": 1,
  "createdAt": "2026-10-17T09:00:00Z",
  "updatedAt": "2026-10-17T09:02:14Z"
}
```

### `POST /api/v1/ingestions/{id}/retry`

Request an authorized retry. Requires role `OPERATOR` or above. Only valid
when `status = FAILED`. Increments `processing_attempts`, re-publishes an
outbox event referencing the **same** job id (never creates a new
`ingestion_jobs` row — see
[architecture.md](../architecture.md#4-data-flow--workflow-a-import-customer-data)).
`422` with `code: INVALID_STATE_FOR_RETRY` if called on a job not in
`FAILED`.

### `POST /api/v1/reconciliation-runs`

Start a reconciliation run. Requires `Idempotency-Key`.

**Request**

```json
{
  "leftIntegrationId": "e2d6...",
  "rightIntegrationId": "a71f...",
  "toleranceConfig": {
    "amountAbsolute": 1.00,
    "amountRelativePct": 0.1,
    "timestampSkewSeconds": 60
  }
}
```

**Response `202 Accepted`** — job-style envelope, `status: RUNNING`.

### `GET /api/v1/reconciliation-runs/{id}`

```json
{
  "id": "c4a2...",
  "status": "COMPLETED",
  "matchedCount": 4820,
  "discrepancyCount": 17,
  "startedAt": "...",
  "completedAt": "..."
}
```

### `GET /api/v1/discrepancies`

Filter and paginate. Query params: `status`, `reconciliationRunId`,
`reason`, plus `limit`/`cursor` per § 1.

```json
{
  "items": [
    {
      "id": "d19b...",
      "reconciliationRunId": "c4a2...",
      "businessKey": "ORD-100245",
      "reason": "AMOUNT_MISMATCH",
      "status": "OPEN",
      "explanation": {
        "field": "amount",
        "leftValue": "5000.00",
        "rightValue": "4500.00",
        "toleranceApplied": { "amountAbsolute": 1.00, "amountRelativePct": 0.1 }
      },
      "createdAt": "..."
    }
  ],
  "nextCursor": "...",
  "hasMore": true
}
```

### `GET /api/v1/discrepancies/{id}`

Full detail including `discrepancy_events` history.

### `POST /api/v1/discrepancies/{id}/resolve`

Resolve with a reason and audit event. Requires role `OPERATOR` or above and
an `If-Match` header carrying the discrepancy's current `version` (optimistic
lock, maps to `discrepancies.version`).

**Request**

```json
{
  "resolution": "ACCEPTED_AS_EXPECTED",
  "note": "ERP total excludes a ₹500 discount applied only in CRM; confirmed with finance."
}
```

**Response `200 OK`** — updated discrepancy. `409 Conflict`,
`code: STALE_VERSION`, if `If-Match` doesn't match the current `version`
(someone else resolved it first). `422` if the discrepancy is not in
`OPEN`/`UNDER_REVIEW`.

Writes `discrepancies.status` update, a `discrepancy_events` row, and an
`audit_events` row in one transaction — never partially (Invariant #7).

## 3. Explicit non-goals for v1

- No write-back to source systems (CRM/ERP are read-only inputs).
- No bulk/batch discrepancy resolution endpoint — one resolution, one audit
  trail entry, reviewed deliberately. A bulk endpoint is a plausible Phase 6+
  addition once the audit implications are designed, not assumed now.
- No fuzzy-matching configuration surface — matching is exact business-key
  only (see [architecture.md § 5](../architecture.md#5-data-flow--workflow-b-reconcile-records)).
