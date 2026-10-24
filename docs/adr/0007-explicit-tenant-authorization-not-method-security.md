# ADR-0007: Explicit `TenantContext` role checks, not `@PreAuthorize`/method security

Status: Accepted

## Context

Nexus needs role-based authorization for three roles
(`READ_ONLY`/`OPERATOR`/`TENANT_ADMIN`) scoped *per tenant membership*, not
globally per user. Spring Security's standard answer for role checks is
method security — `@EnableMethodSecurity` plus `@PreAuthorize("hasRole(...)")`
on controller or service methods, evaluated against the `Authentication`'s
`GrantedAuthority` collection.

## Decision

Role checks are plain Java: `TenantContext.require(MembershipRole.X)`,
called explicitly inside a service method, throwing
`TenantAccessDeniedException` (→ 403) on failure. No `@EnableMethodSecurity`,
no `@PreAuthorize`, no custom `GrantedAuthoritiesMapper` deriving roles from
JWT claims.

## Alternatives considered

- **`@PreAuthorize("hasRole('TENANT_ADMIN')")` against JWT-derived
  authorities**: rejected — it doesn't fit the data. A Nexus role lives in
  `tenant_memberships`, scoped to one tenant; the same principal can be
  `TENANT_ADMIN` in tenant A and `READ_ONLY` in tenant B. The JWT has no
  tenant-scoped role claims (nor should it — Nexus roles aren't the identity
  provider's concern), so there is nothing correct for a
  `GrantedAuthoritiesMapper` to extract from the token. Faking it by
  granting every authenticated user a maximal authority and checking
  something else underneath would make the annotation decorative rather
  than load-bearing.
- **`@PreAuthorize` with a SpEL expression against an already-resolved
  `TenantContext` method parameter** (e.g. referencing `#context.role()`):
  technically possible for controller methods specifically, since MVC
  argument resolution runs before the method-security interceptor sees the
  resolved arguments. Rejected because it only works for controllers (no
  `@CurrentTenant` argument resolution exists at the service layer), so the
  same check would need two different mechanisms depending on which layer
  it's written in — a worse-to-maintain outcome than one explicit mechanism
  used everywhere.
- **A custom `PermissionEvaluator`**: a legitimate pattern for a real
  permission engine. Rejected for the same reason ADR-grade engines are
  rejected elsewhere in this project: three roles and a handful of actions
  (docs/authorization-model.md § 2) don't justify the machinery; the flat
  permission table is the honest representation of the current
  requirement.

## Trade-offs accepted

- Role checks are not declarative/annotation-visible at a glance the way
  `@PreAuthorize` is — a reader has to look inside the method body to find
  `context.require(...)`. Mitigated by always putting it as the first line
  of a method that needs it, and by docs/authorization-model.md § 2 being
  the single source of truth for which actions require which role, rather
  than expecting annotations scattered across the codebase to self-document
  the matrix.
- If Nexus ever adopts IdP-asserted groups/roles for SSO purposes, that is
  a genuinely new design question (does an IdP group map to a
  `MembershipRole`? per tenant, how?) — not an extension of this decision.

## Revisit when

A real requirement for fine-grained, per-resource permissions emerges (see
docs/authorization-model.md § 2's stated trigger), or the permission matrix
grows past "three roles checked at a handful of call sites" into something
that benefits from centralized, declarative enforcement.
