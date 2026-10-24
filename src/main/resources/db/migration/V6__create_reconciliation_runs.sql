CREATE TABLE reconciliation_runs (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    left_integration_id  UUID NOT NULL,
    right_integration_id UUID NOT NULL,
    status              TEXT NOT NULL,
    matched_count       INT NULL,
    discrepancy_count   INT NULL,
    tolerance_config    JSONB NOT NULL,
    started_at          TIMESTAMPTZ NULL,
    completed_at        TIMESTAMPTZ NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reconciliation_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_reconciliation_runs_distinct_integrations
        CHECK (left_integration_id <> right_integration_id),
    CONSTRAINT fk_reconciliation_runs_left_integration_tenant
        FOREIGN KEY (left_integration_id, tenant_id) REFERENCES integrations (id, tenant_id),
    CONSTRAINT fk_reconciliation_runs_right_integration_tenant
        FOREIGN KEY (right_integration_id, tenant_id) REFERENCES integrations (id, tenant_id),
    -- FK target for discrepancies.
    CONSTRAINT uq_reconciliation_runs_id_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_reconciliation_runs_tenant_status_created
    ON reconciliation_runs (tenant_id, status, created_at);

COMMENT ON COLUMN reconciliation_runs.tolerance_config IS
    'Amount tolerance and timestamp skew, SNAPSHOT at run time — later changes to an '
    'integration pair''s configured tolerance must not retroactively change the explanation of '
    'a past run.';
