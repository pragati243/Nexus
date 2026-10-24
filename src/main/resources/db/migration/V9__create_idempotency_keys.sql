CREATE TABLE idempotency_keys (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL REFERENCES tenants (id),
    scope                TEXT NOT NULL,
    key                  TEXT NOT NULL,
    request_fingerprint  TEXT NOT NULL,
    response_status      INT NOT NULL,
    response_body        JSONB NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- data-model.md Invariant #5: an idempotency key cannot create duplicate effects within its
    -- scope. "scope" is intentionally an open, unconstrained text value (one per logical
    -- operation, e.g. CREATE_INGESTION) rather than a CHECK-enumerated set — new operations get
    -- a new scope value without a migration, unlike the closed state-machine enums elsewhere in
    -- this schema.
    CONSTRAINT uq_idempotency_keys_tenant_scope_key UNIQUE (tenant_id, scope, key)
);

COMMENT ON COLUMN idempotency_keys.request_fingerprint IS
    'Hash of the normalized request body. A repeat request with the same key but a different '
    'fingerprint is a client bug (key reuse with a different payload) and gets 409, not a '
    'silently replayed response.';
COMMENT ON COLUMN idempotency_keys.response_body IS
    'Cached response, replayed verbatim on a repeat request with the same key + fingerprint.';
