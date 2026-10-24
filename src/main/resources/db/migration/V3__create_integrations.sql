CREATE TABLE integrations (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    name            TEXT NOT NULL,
    source_system   TEXT NOT NULL,
    connection_type TEXT NOT NULL,
    config          JSONB NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_integrations_connection_type CHECK (connection_type IN ('FILE_UPLOAD', 'REST_PULL')),
    CONSTRAINT ck_integrations_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    -- Redundant with the primary key on its own, but required as the target side of the
    -- composite foreign keys below: every child table that references an integration also
    -- carries tenant_id, and must be provably the SAME tenant as the integration row it points
    -- at. A plain FK on integration_id alone cannot express that; this composite unique
    -- constraint is what makes it possible (see ingestion_jobs, source_records, reconciliation_runs).
    CONSTRAINT uq_integrations_id_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_integrations_tenant_status ON integrations (tenant_id, status);

COMMENT ON TABLE integrations IS
    'Configured external systems per tenant. An integration belongs to exactly one tenant '
    '(data-model.md Invariant #1).';
COMMENT ON COLUMN integrations.config IS
    'Connection-type-specific configuration ONLY. Never store raw credentials here — for '
    'REST_PULL sources, config holds a reference (name/ARN) to a secret in Secrets Manager, '
    'fetched by the worker at call time (threat-model.md Secrets, CLAUDE.md rule 3).';
