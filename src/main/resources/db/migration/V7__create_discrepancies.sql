CREATE TABLE discrepancies (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL REFERENCES tenants (id),
    reconciliation_run_id UUID NOT NULL,
    business_key           TEXT NOT NULL,
    reason                 TEXT NOT NULL,
    explanation            JSONB NOT NULL,
    status                 TEXT NOT NULL DEFAULT 'OPEN',
    resolved_by            TEXT NULL,
    resolved_at            TIMESTAMPTZ NULL,
    version                INT NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_discrepancies_reason CHECK (
        reason IN ('AMOUNT_MISMATCH', 'MISSING_IN_LEFT', 'MISSING_IN_RIGHT',
                   'DUPLICATE_BUSINESS_KEY', 'TIMESTAMP_MISMATCH')
    ),
    CONSTRAINT ck_discrepancies_status CHECK (
        status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'ACCEPTED_AS_EXPECTED')
    ),
    CONSTRAINT fk_discrepancies_reconciliation_run_tenant
        FOREIGN KEY (reconciliation_run_id, tenant_id) REFERENCES reconciliation_runs (id, tenant_id),
    -- FK target for discrepancy_events.
    CONSTRAINT uq_discrepancies_id_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_discrepancies_tenant_status_created ON discrepancies (tenant_id, status, created_at);
CREATE INDEX idx_discrepancies_reconciliation_run_id ON discrepancies (reconciliation_run_id);

COMMENT ON COLUMN discrepancies.tenant_id IS
    'Denormalized from reconciliation_runs rather than reached by join, specifically so every '
    'tenant-scoping check can filter discrepancies.tenant_id directly — the same reason this is '
    'cheap to enforce uniformly across the API (data-model.md Invariant #4).';
COMMENT ON COLUMN discrepancies.version IS
    'Optimistic lock — prevents two concurrent resolve requests from both succeeding '
    '(architecture.md #10). A stale version on resolve is a 409, not a silent double-resolution.';
COMMENT ON COLUMN discrepancies.explanation IS
    'The comparison that produced this discrepancy (field, left value, right value, tolerance '
    'applied) — the explainability requirement, not just a boolean mismatch flag.';
