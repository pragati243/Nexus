# ADR-0004: Spring Data JPA plus targeted JDBC, not a pure-JPA or pure-JDBC approach

Status: Accepted

## Context

Nexus has two distinct kinds of database work: straightforward CRUD-shaped
domain persistence (tenants, integrations, ingestion jobs, discrepancies) and
performance/correctness-sensitive operations (bulk upsert of `source_records`
during ingestion, the reconciliation comparison join, the outbox poll query)
where ORM-generated SQL and entity-level locking semantics make it harder to
reason about exact behavior.

## Decision

Use Spring Data JPA (Hibernate) for standard aggregate persistence and
repository-shaped queries. Use plain JDBC (`JdbcTemplate` /
`NamedParameterJdbcTemplate`) for:

- The `source_records` bulk upsert (`INSERT ... ON CONFLICT DO UPDATE`) during
  worker processing.
- The reconciliation comparison query (set-based matching between two
  integrations' records) — this is naturally a SQL join/aggregation problem,
  not an object-graph traversal.
- The outbox poll-and-claim query, where row-level locking semantics
  (`SELECT ... FOR UPDATE SKIP LOCKED`) need to be exact and visible, not
  mediated by an ORM's optimistic-locking defaults.

## Alternatives considered

- **Pure JPA/Hibernate everywhere**: rejected for the hot paths above.
  Expressing `ON CONFLICT` upserts and `FOR UPDATE SKIP LOCKED` through JPA
  is awkward (native query escapes are needed anyway) and obscures exactly
  what SQL runs, which matters when the whole point of this project is to
  demonstrate precise control over correctness-critical operations.
- **Pure JDBC everywhere (no ORM)**: rejected. Most of the domain model
  (`tenants`, `integrations`, `discrepancies` CRUD, simple status lookups) is
  genuinely well-served by JPA's repository pattern, optimistic locking
  (`@Version`), and reduced boilerplate. Hand-writing mapping code for every
  simple entity would be premature rigor with no corresponding benefit.
- **jOOQ**: a reasonable middle ground that gives typed SQL without full ORM
  overhead. Not chosen for v1 to keep the stack to one query-building
  approach (JPA) plus plain JDBC for the exceptions, rather than introducing
  a third library; revisit if the JDBC hot-path code grows large/complex
  enough that typed query building would meaningfully reduce bugs.

## Trade-offs accepted

- Two data-access idioms exist in the codebase instead of one, which asks
  more of a new contributor ("why is this repository JPA and this one raw
  JDBC?"). Mitigated by making the rule explicit here and in
  [CLAUDE.md](../../CLAUDE.md): JPA by default, JDBC only where a specific,
  named reason (upsert semantics, explicit locking, set-based comparison)
  applies.
- Native/JDBC SQL is PostgreSQL-specific (`ON CONFLICT`, `SKIP LOCKED`) —
  acceptable because PostgreSQL is a committed dependency
  ([architecture.md](../architecture.md)), not an abstraction Nexus is trying
  to stay portable across.

## Revisit when

A specific JDBC hot-path query becomes complex enough that hand-written SQL
is a readability/safety liability — evaluate jOOQ at that point rather than
preemptively.
