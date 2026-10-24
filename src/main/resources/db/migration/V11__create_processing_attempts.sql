CREATE TABLE processing_attempts (
    id               UUID PRIMARY KEY,
    -- Not in the original data-model.md column list for this table, but its own "Tenant
    -- ownership summary" section explicitly describes processing_attempts as "tenant-tagged for
    -- safe filtering" — this column makes that true rather than leaving it only reachable via a
    -- join through ingestion_jobs. Documented here per the instruction to explain, not silently
    -- redesign, any deviation from the Phase 0 column table.
    tenant_id        UUID NOT NULL REFERENCES tenants (id),
    ingestion_job_id UUID NOT NULL,
    sqs_message_id   TEXT NOT NULL,
    attempt_number   INT NOT NULL,
    outcome          TEXT NOT NULL,
    error_detail     TEXT NULL,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_processing_attempts_outcome CHECK (
        outcome IN ('SUCCESS', 'TRANSIENT_FAILURE', 'PERMANENT_FAILURE')
    ),
    CONSTRAINT fk_processing_attempts_ingestion_job_tenant
        FOREIGN KEY (ingestion_job_id, tenant_id) REFERENCES ingestion_jobs (id, tenant_id)
);

CREATE INDEX idx_processing_attempts_job_attempt ON processing_attempts (ingestion_job_id, attempt_number);

COMMENT ON TABLE processing_attempts IS
    'One row per consume attempt, successful or not — what makes an SQS redelivery observable '
    'instead of invisible (architecture.md Workflow C).';
