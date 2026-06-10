# Design Artifact References

This folder contains the formal design artifacts that complement the existing HLD and LLD documentation.

## Documents
- `architecture-diagrams.md` — C4 context, container, and component diagrams.
- `lld-diagrams.md` — low-level sequence, class, and use case diagrams (payment domain).
- `domain-sequence-diagrams.md` — core cross-domain flow sequences: order saga, AI agent orchestration + HITL, B2B RFQ auction + governance, rider dispatch.
- `api-contracts.md` — BFF/OpenAPI API contract summary.
- `data-model-erd.md` — transactional ERD and data model relationships.

## Usage
- Use `architecture-diagrams.md` when reviewing system architecture and deployment boundaries.
- Use `lld-diagrams.md` when reviewing flow control, class interactions, and functional use cases.
- Use `domain-sequence-diagrams.md` when verifying that a bounded context's multi-step flow matches its `core/service` implementation (each diagram cites its source file).
- Use `api-contracts.md` when validating the API surface and gateway contract.
- Use `data-model-erd.md` when validating database relationships and schema structure.
