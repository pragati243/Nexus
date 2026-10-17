# ADR-0003: Transactional outbox for reliable event publication

Status: Accepted

## Context

When an ingestion job transitions to `QUEUED`, the worker needs to be
notified via SQS. If the API handler commits the database transaction and
*then* calls `SendMessage`, there's a window where the DB commit succeeds but
the SQS call fails (network blip, SQS throttling, process crash between the
two calls) — the job is durably `QUEUED` in Postgres but the worker is never
told, and the job silently never processes. The reverse order (publish, then
commit) is worse: a successfully sent message can reference a job that then
fails to commit, so the worker processes something that, from the database's
point of view, doesn't exist.

## Decision

Use the transactional outbox pattern: write an `outbox_events` row in the
**same database transaction** as the business state change. A separate
Outbox Publisher process polls `outbox_events WHERE published_at IS NULL`,
calls SQS `SendMessage`, and marks the row published — all outside the
original request's transaction.

## Alternatives considered

- **Dual write (commit DB, then call SQS inline in the request handler)**:
  rejected — this is exactly the "lost event" failure mode described above.
  It's the simplest option and the most common mistake in systems that
  haven't hit this bug yet.
- **2PC / XA transactions across Postgres and SQS**: rejected. SQS has no
  distributed-transaction protocol to participate in, so this isn't even
  available as an option — included here because it's worth being explicit
  that it was considered and isn't a real alternative, not just "the hard
  option."
- **Debezium / CDC reading the Postgres WAL directly** instead of an
  application-polled outbox table: a legitimate alternative that removes
  polling latency and poller operational burden. Deferred, not rejected
  outright — it adds a Kafka Connect or Debezium Server component to
  operate, which isn't justified at this project's message volume. If outbox
  publish latency becomes a measured problem (see
  [architecture.md § 9](../architecture.md#9-non-functional-targets),
  `outbox_event_publish_lag_seconds`), CDC is the documented next step.

## Trade-offs accepted

- There is publish latency between the DB commit and the SQS send — bounded
  by the Outbox Publisher's poll interval, not instantaneous. Acceptable
  because the business-critical property is "never lost," not "sub-100ms
  dispatch."
- The `outbox_events` table needs its own retention/cleanup (see
  [data-model.md § 6](../data-model.md#6-retention-considerations)) and a
  monitored "unpublished past threshold" alarm — it's an extra piece of
  infrastructure, accepted because the alternative (silent event loss) is
  worse and harder to detect.
- The Outbox Publisher is itself a process that needs to be running and
  healthy; it's a new single point to monitor, mitigated by making it a
  lightweight scheduled component inside `nexus-worker` rather than a third
  deployable.

## Revisit when

Measured publish lag under real load exceeds the target in
[architecture.md § 9](../architecture.md#9-non-functional-targets) and
tuning the poll interval/batch size doesn't fix it — at that point, evaluate
CDC-based publication instead of tightening the poll loop further.
