# Nexus — Architecture Decision Records

Index of ADRs. Each one states the decision, the reason, and the trade-off
accepted — not just the choice. New ADRs are numbered sequentially and never
renumbered or deleted; a reversed decision gets a new ADR that supersedes the
old one.

| # | Title | Status |
|---|---|---|
| [0001](adr/0001-modular-monolith-vs-microservices.md) | Modular monolith with separately deployable API/worker, not microservices | Accepted |
| [0002](adr/0002-sqs-vs-kafka.md) | SQS + DLQ, not Kafka, for asynchronous processing | Accepted |
| [0003](adr/0003-transactional-outbox.md) | Transactional outbox for reliable event publication | Accepted |
| [0004](adr/0004-database-access-strategy.md) | Spring Data JPA plus targeted JDBC, not a pure-JPA or pure-JDBC approach | Accepted |
| [0005](adr/0005-ecs-fargate-vs-kubernetes.md) | ECS Fargate, not Kubernetes (EKS), for container orchestration | Accepted |
| [0006](adr/0006-spring-boot-4-vs-3.md) | Spring Boot 4.1.x, not 3.x, as the target framework version | Accepted |

## How to write a new ADR

1. Copy the format used in `adr/0001-*.md`: Context → Decision → Alternatives
   considered → Trade-offs accepted → Revisit when.
2. State the trade-off honestly — every real decision gives something up.
3. Link the ADR from this index and from any doc (architecture, CLAUDE.md)
   whose content depends on it.
4. Never silently reverse a decision by editing the old ADR. Write a new one
   and mark the old one "Superseded by ADR-00XX."
