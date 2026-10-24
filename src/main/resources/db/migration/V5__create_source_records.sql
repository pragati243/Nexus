CREATE TABLE source_records (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL REFERENCES tenants (id),
    integration_id    UUID NOT NULL,
    ingestion_job_id  UUID NOT NULL,
    source_system     TEXT NOT NULL,
    business_key      TEXT NOT NULL,
    payload           JSONB NOT NULL,
    external_record_id TEXT NOT NULL,
    amount            NUMERIC(18, 2) NULL,
    record_timestamp  TIMESTAMPTZ NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_source_records_integration_tenant
        FOREIGN KEY (integration_id, tenant_id) REFERENCES integrations (id, tenant_id),
    -- Proves the job/integration/tenant triple on this row is consistent with a real
    -- ingestion_jobs row, not just that each FK independently points somewhere valid.
    CONSTRAINT fk_source_records_ingestion_job_tenant_integration
        FOREIGN KEY (ingestion_job_id, tenant_id, integration_id)
        REFERENCES ingestion_jobs (id, tenant_id, integration_id),
    -- Deliberately NOT unique on (tenant_id, integration_id, business_key): a duplicate
    -- business key within one source is a real, detectable state (DUPLICATE_BUSINESS_KEY
    -- discrepancy in Phase 4), not a constraint violation. The idempotency guarantee against a
    -- redelivered SQS message instead comes from the source system's OWN row identity:
    CONSTRAINT uq_source_records_tenant_integration_external_id
        UNIQUE (tenant_id, integration_id, source_system, external_record_id)
);

CREATE INDEX idx_source_records_tenant_integration_business_key
    ON source_records (tenant_id, integration_id, business_key);
CREATE INDEX idx_source_records_ingestion_job_id ON source_records (ingestion_job_id);

COMMENT ON COLUMN source_records.external_record_id IS
    'Identity of the row AS ASSIGNED BY THE SOURCE SYSTEM (its own ID column, or a hash of '
    'natural key + batch for sources with no stable ID) — this, not business_key, is what makes '
    'reprocessing a redelivered row an idempotent upsert instead of a duplicate insert. Exact '
    'derivation finalized per source type in Phase 3 normalization rules (data-model.md).';
COMMENT ON COLUMN source_records.amount IS
    'Hoisted out of payload for indexed, numeric comparison during reconciliation. NUMERIC, '
    'never floating-point, for currency.';
COMMENT ON COLUMN source_records.record_timestamp IS
    'Normalized to UTC; original offset is preserved inside payload.';
