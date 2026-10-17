# Flyway migrations

Empty on purpose in Phase 1 — no business tables exist yet. Phase 2 adds the
full schema from [docs/data-model.md](../../../../../docs/data-model.md) here
as versioned `V{n}__{description}.sql` files. Flyway runs cleanly against
zero migrations (it just creates `flyway_schema_history`); this is verified
by the Phase 1 Testcontainers test.
