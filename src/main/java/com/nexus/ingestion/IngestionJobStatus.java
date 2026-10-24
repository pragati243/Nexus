package com.nexus.ingestion;

public enum IngestionJobStatus {
    CREATED,
    QUEUED,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED
}
