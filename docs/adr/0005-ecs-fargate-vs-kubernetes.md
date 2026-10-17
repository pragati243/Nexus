# ADR-0005: ECS Fargate, not Kubernetes (EKS), for container orchestration

Status: Accepted

## Context

Nexus needs to run two container workloads (`nexus-api`, `nexus-worker`)
that scale independently, with health checks, rolling deploys, and
log/metric shipping to CloudWatch. Kubernetes (via EKS) is the more
resume-visible choice and offers a richer ecosystem (HPA, custom operators,
service mesh) that this project doesn't currently need.

## Decision

Use AWS ECS on Fargate: one ECS service per deployable (`nexus-api`,
`nexus-worker`), task definitions sharing the same ECR image with different
entry points, Application Auto Scaling on `nexus-api` (request count /
CPU) and on `nexus-worker` (SQS `ApproximateNumberOfMessagesVisible`).

## Alternatives considered

- **EKS (Kubernetes)**: rejected for v1. EKS adds a control plane to pay for
  and operate (version upgrades, add-on management, IRSA setup, ingress
  controller choice) for a workload that is two services with
  straightforward scaling triggers — the operational complexity of
  Kubernetes isn't exercised by this project's actual requirements, and
  "I used Kubernetes" without a concrete reason doesn't demonstrate judgment
  as clearly as choosing the right-sized tool does. Fargate gives container
  deployment, autoscaling, and rolling updates without a cluster to
  patch.
- **EC2 (self-managed instances, no containers)**: rejected — loses the
  immutable-deploy and local/prod parity benefits of containers for no
  offsetting gain.
- **AWS Lambda for the worker**: considered. Lambda's SQS trigger integration
  is a legitimate alternative for the worker specifically. Not chosen because
  (a) ingestion processing can run long enough per batch that Lambda's
  timeout model adds complexity (chunking, checkpointing) that a
  long-running Fargate task avoids, and (b) keeping API and worker on the
  same compute model (ECS Fargate) keeps the deployment pipeline and
  observability setup uniform across both — a deliberate simplicity choice,
  not a Lambda limitation verdict in general.

## Trade-offs accepted

- Less resume keyword overlap with teams standardized on Kubernetes.
  Accepted because the project's stated goal is demonstrable engineering
  judgment, not maximum buzzword coverage — this trade-off itself is a
  talking point in an interview.
- Fargate has less flexibility than self-managed EC2/EKS for things like
  custom kernel modules or sidecar patterns beyond what ECS natively
  supports (ECS does support sidecars via multiple containers per task
  definition, which covers the OTel collector sidecar this project uses).

## Revisit when

A specific requirement needs something Fargate can't do (e.g. GPU workloads,
a service mesh requirement, multi-cluster federation) — not before.
