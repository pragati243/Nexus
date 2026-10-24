CREATE TABLE discrepancy_events (
    id                  UUID PRIMARY KEY,
    discrepancy_id      UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    event_type          TEXT NOT NULL,
    actor_principal_id  TEXT NOT NULL,
    note                TEXT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_discrepancy_events_event_type CHECK (
        event_type IN ('OPENED', 'REVIEW_STARTED', 'RESOLVED', 'REOPENED', 'COMMENT_ADDED')
    ),
    CONSTRAINT fk_discrepancy_events_discrepancy_tenant
        FOREIGN KEY (discrepancy_id, tenant_id) REFERENCES discrepancies (id, tenant_id)
);

CREATE INDEX idx_discrepancy_events_discrepancy_id ON discrepancy_events (discrepancy_id);

COMMENT ON TABLE discrepancy_events IS
    'Resolution and review history. Append-only by convention — no UPDATE/DELETE code path '
    'exists for this table at the application layer (data-model.md Invariant #7); there is '
    'deliberately no updated_at column because rows are never updated.';
