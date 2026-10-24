CREATE TABLE audit_events (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    actor_principal_id TEXT NOT NULL,
    action             TEXT NOT NULL,
    target_type        TEXT NOT NULL,
    target_id          UUID NOT NULL,
    metadata           JSONB NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_tenant_created ON audit_events (tenant_id, created_at);
CREATE INDEX idx_audit_events_target ON audit_events (target_type, target_id);

COMMENT ON TABLE audit_events IS
    'Security- and business-relevant audit trail. Append-only: no UPDATE/DELETE path exists at '
    'the application layer. Retained indefinitely by default — this is the one table where '
    '"add a TTL" is a decision needing explicit sign-off, not a scheduled job default '
    '(data-model.md #6). "action" is an open, unconstrained text value by design, like '
    'idempotency_keys.scope — new audit actions are added without a migration.';
