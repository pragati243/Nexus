CREATE TABLE ingestion_jobs (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    integration_id     UUID NOT NULL,
    status             TEXT NOT NULL,
    idempotency_key    TEXT NOT NULL,
    source_object_key  TEXT NULL,
    total_rows         INT NULL,
    valid_rows         INT NULL,
    error_rows         INT NULL,
    failure_reason     TEXT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ingestion_jobs_status CHECK (
        status IN ('CREATED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')
    ),
    -- Enforces data-model.md Invariant #2 ("an ingestion job belongs to the tenant AND
    -- integration it processes") at the database level, not just in application code: this FK
    -- fails if integration_id refers to a real integration belonging to a DIFFERENT tenant than
    -- tenant_id claims. This was flagged as an open assumption in Phase 0 and is resolved here.
    CONSTRAINT fk_ingestion_jobs_integration_tenant
        FOREIGN KEY (integration_id, tenant_id) REFERENCES integrations (id, tenant_id),
    -- A retried request must not silently create a second business import (architecture.md
    -- Workflow A). The idempotency key is scoped per tenant+integration, not globally.
    CONSTRAINT uq_ingestion_jobs_tenant_integration_idempotency
        UNIQUE (tenant_id, integration_id, idempotency_key),
    -- FK target for processing_attempts, which only needs to prove tenant consistency (not the
    -- integration triple) against this table.
    CONSTRAINT uq_ingestion_jobs_id_tenant UNIQUE (id, tenant_id),
    -- FK target for source_records, which must prove its ingestion_job/integration/tenant
    -- triple is internally consistent. Postgres requires the referenced column SET to exactly
    -- match a unique constraint — a 2-column and a 3-column unique constraint are both needed
    -- here because they serve two different composite FKs with different column counts.
    CONSTRAINT uq_ingestion_jobs_id_tenant_integration UNIQUE (id, tenant_id, integration_id)
);

CREATE INDEX idx_ingestion_jobs_tenant_status_created ON ingestion_jobs (tenant_id, status, created_at);

COMMENT ON COLUMN ingestion_jobs.version IS
    'Optimistic lock for concurrent status transitions — a redelivered SQS message and an '
    'operator-triggered retry must not race each other into an inconsistent status.';
COMMENT ON COLUMN ingestion_jobs.failure_reason IS 'Populated only when status = FAILED.';
