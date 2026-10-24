CREATE TABLE tenant_memberships (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants (id),
    principal_id TEXT NOT NULL,
    role         TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_memberships_tenant_principal UNIQUE (tenant_id, principal_id),
    CONSTRAINT ck_tenant_memberships_role CHECK (role IN ('TENANT_ADMIN', 'OPERATOR', 'VIEWER'))
);

CREATE INDEX idx_tenant_memberships_principal_id ON tenant_memberships (principal_id);

COMMENT ON TABLE tenant_memberships IS
    'User-to-tenant membership and role. This table — never a client-supplied tenantId — is the '
    'source of truth TenantContext is resolved from on every request (CLAUDE.md rule 2).';
COMMENT ON INDEX idx_tenant_memberships_principal_id IS
    'Lookup path for resolving TenantContext from the authenticated principal; must stay fast as '
    'principal/tenant counts grow.';
