-- The Phase 0 draft named the read-only role VIEWER (see V2__create_tenant_memberships.sql).
-- Phase 3 settled on READ_ONLY as the name for the same role when the permission model was
-- written down (docs/authorization-model.md). V2 is already committed, so this is a forward-only
-- rename rather than an edit to an applied migration.
ALTER TABLE tenant_memberships DROP CONSTRAINT ck_tenant_memberships_role;

UPDATE tenant_memberships SET role = 'READ_ONLY' WHERE role = 'VIEWER';

ALTER TABLE tenant_memberships
    ADD CONSTRAINT ck_tenant_memberships_role CHECK (role IN ('TENANT_ADMIN', 'OPERATOR', 'READ_ONLY'));
