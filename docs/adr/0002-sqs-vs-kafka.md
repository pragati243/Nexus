# ADR-0002: SQS + DLQ, not Kafka, for asynchronous processing

Status: Accepted

## Context

Nexus needs to hand work off from the API (which accepts ingestion requests)
to the worker (which processes them) reliably and asynchronously, with
support for retries and a way to quarantine permanently-failing messages.
Kafka is the more commonly cited technology for this in "advanced" portfolio
projects and offers replay-from-offset and multi-consumer-group fan-out that
SQS doesn't.

## Decision

Use Amazon SQS (standard queue) with a dead-letter queue (DLQ) configured via
`maxReceiveCount` redrive policy.

## Alternatives considered

- **Apache Kafka (MSK or self-managed)**: rejected for v1. Nexus's actual
  requirement is "hand one job to one worker, reliably, with retry and
  quarantine" — a queue semantic, not a log/stream semantic. Kafka's value
  (ordered partitions, long retention replay, multiple independent consumer
  groups reading the same stream) isn't needed: there is exactly one logical
  consumer group (the worker fleet) per message type, and replay is handled
  by the outbox table and DLQ redrive runbook, not by rewinding a log.
  Running Kafka well (partition planning, consumer group rebalancing,
  broker/ZooKeeper-or-KRaft operations, or paying for MSK) is meaningfully
  more operational surface than this project's scope justifies.
- **SQS FIFO queue**: considered and rejected in favor of standard. FIFO
  guarantees ordering and exactly-once *processing* within a message group,
  but Nexus's correctness model is deliberately built around idempotent
  effects under at-least-once delivery regardless of queue type (see
  [architecture.md § 6](../architecture.md#6-data-flow--workflow-c-recover-from-failures))
  — ordering across different ingestion jobs isn't a real requirement, and
  FIFO's lower throughput ceiling and stricter message-group semantics would
  be cost paid for a guarantee the system doesn't rely on.

## Trade-offs accepted

- No native replay-from-arbitrary-point-in-time the way a Kafka log offers;
  replay is "move messages back from DLQ," which is coarser but sufficient
  for this system's actual failure modes (see
  [docs/runbooks/](../runbooks/)).
- If a second, independent consumer ever needs to react to the same
  ingestion events (e.g. a future analytics pipeline), SQS doesn't give
  multi-consumer-group fan-out for free — an SNS fan-out topic in front of
  multiple SQS queues would be the next step, not a reason to switch to
  Kafka preemptively.

## Revisit when

A concrete second consumer of the same event stream emerges, or message
ordering/replay-by-offset becomes an actual product requirement rather than
a theoretical one.
