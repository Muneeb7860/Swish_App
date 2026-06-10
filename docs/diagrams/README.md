# Design Artifact References

This folder contains the formal design artifacts that complement the existing HLD and LLD documentation.

## Documents
- `architecture-diagrams.md` — C4 context, container, and component (Payment) diagrams.
- `architecture-c4-components.md` — **completes C4 Level-3** for the whole project: the backend modular monolith (22 hexagonal contexts) and the `homelab-ai-governance` AI platform, plus an as-built container addendum + coverage matrix.
- `lld-diagrams.md` — low-level sequence, class, and use case diagrams (payment domain).
- `lld-complete.md` — **complete & validated LLD**: whole-project use case + class diagrams, with traceability validation against BRD (functional requirements), HLD (patterns), and the ERD.
- `domain-sequence-diagrams.md` — core cross-domain flow sequences (part 1): order saga, AI agent orchestration + HITL, B2B RFQ auction + governance, rider dispatch.
- `domain-sequence-diagrams-part2.md` — remaining domain flows (part 2): auth/JWT + authorization, checkout + refund, double-entry ledger, transactional outbox, telemetry CQRS, rider enrollment/delivery, rewards. Ends with a 22-domain coverage matrix.
- `api-contracts.md` — BFF/OpenAPI API contract summary.
- `data-model-erd.md` — **aspirational target** ERD (7-DB sharded microservices model; not yet adopted).
- `data-model-erd-asbuilt.md` — **as-built, validated** ERD: the real `oltp/olap/dispatch/wholesaler` schema from the Flyway migrations, cross-checked against the JPA entities, with a validation-findings table (drift + prod-blockers).

## Usage
- Use `architecture-diagrams.md` when reviewing system architecture and deployment boundaries.
- Use `lld-diagrams.md` when reviewing flow control, class interactions, and functional use cases.
- Use `domain-sequence-diagrams.md` when verifying that a bounded context's multi-step flow matches its `core/service` implementation (each diagram cites its source file).
- Use `api-contracts.md` when validating the API surface and gateway contract.
- Use `data-model-erd.md` when validating database relationships and schema structure.
