CREATE TABLE outbox_events (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL REFERENCES tenants (id),
    aggregate_type    TEXT NOT NULL,
    aggregate_id      UUID NOT NULL,
    event_type        TEXT NOT NULL,
    payload           JSONB NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at      TIMESTAMPTZ NULL,
    publish_attempts  INT NOT NULL DEFAULT 0
);

-- The Outbox Publisher's poll query is "find unpublished events, oldest first" (ADR-0003).
-- Indexing created_at under the partial predicate serves that directly; indexing published_at
-- itself would be pointless since every row in this partial index shares the same NULL value.
CREATE INDEX idx_outbox_events_unpublished_created_at
    ON outbox_events (created_at) WHERE published_at IS NULL;

COMMENT ON TABLE outbox_events IS
    'Transactional outbox (ADR-0003). A row here is inserted in the SAME transaction as the '
    'business state change that caused it — never a separate best-effort call to SQS. '
    'aggregate_type/aggregate_id are deliberately untyped (no FK) since they reference whichever '
    'table owns the business change, not a fixed parent.';
COMMENT ON COLUMN outbox_events.published_at IS
    'Set by the Outbox Publisher after a successful SendMessage. NULL means still pending.';
