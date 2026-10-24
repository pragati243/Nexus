CREATE TABLE tenants (
    id         UUID PRIMARY KEY,
    name       TEXT NOT NULL,
    slug       TEXT NOT NULL,
    status     TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

COMMENT ON TABLE tenants IS
    'Customer organizations. Root of every authorization decision — see docs/data-model.md.';
COMMENT ON COLUMN tenants.status IS
    'Checked by the worker immediately before processing a message, not just at enqueue time (architecture.md #8).';
