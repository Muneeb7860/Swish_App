# ADR-005: Selective Service Extraction via Strangler Fig

- **Status:** Accepted (R4 architecture decision; execution gated — see Consequences)
- **Date:** 2026-06-11
- **Supersedes/relates:** [ADR-001 Hexagonal Architecture](./adr_001_hexagonal_architecture.md), [AS_BUILT_VS_TARGET.md](../AS_BUILT_VS_TARGET.md)

## Context

The as-built system is a **modular DDD monolith** (22 hexagonal contexts in one
`backend`, one PostgreSQL with 4 schemas). The HLD target is **database-per-
service microservices**. We need a path from here to there that is *incremental,
reversible, and driven by real need* — not a big-bang rewrite.

Thin service modules already exist (`platform-gateway`, `core-business-engine`,
`notification-engine`, `shared-async-services`), so the topology has precedent.

**Extraction-readiness assessment (measured 2026-06-11):**

| Context | Couples OUT to | Coupled IN from | Integration today | Readiness |
| :--- | :--- | :--- | :--- | :-- |
| **payment** | `transaction` (ledger) only — 6 imports | **none** | events + REST (`PaymentController`); no in-process callers | ✅ **strong** |
| telemetry | ledger (cold-chain write-off) | rider/enrollment (recordTelemetry) | mostly events | 🟡 medium |
| notification | — | many (events) | already a separate module | ✅ strong |

Payment is the cleanest seam: zero inbound coupling, one outbound seam
(payment → ledger), already event-integrated.

## Decision

1. **Strangler Fig, one context at a time.** Extract a context to its own
   service + database **only when a concrete driver exists** (independent scale,
   team ownership, or fault isolation) — never speculatively.
2. **First candidate: `payment`** (per the readiness table). Then `telemetry`
   (write-volume; TimescaleDB already separable), then formalise
   `notification-engine`.
3. **Seam = events first, REST second.** Reuse the existing **transactional
   outbox → Kafka** as the async integration backbone (already built). The
   payment→ledger seam becomes a Kafka event (`payment.captured` →
   ledger posting) rather than an in-process call.
4. **DB-per-service.** The extracted service owns its schema; no cross-service
   SQL. The monolith remains the system of record until cutover completes.
5. **Blue/green cutover** via the HLD's `Accept-Version` header — v2 routes to
   the extracted service, v1 stays on the monolith until traffic is migrated.
6. **Protect readiness with a fitness function:** payment's inbound coupling must
   stay **zero** (no new context may import `domain.payment.*`); enforce in
   review (and later via an ArchUnit test).

## Consequences

- **+** Independent scaling/deploy and fault isolation for the highest-value or
  highest-load contexts; distributed tracing already exists (`X-Correlation-ID`).
- **+** The hexagonal ports mean extraction is a swap of the outbound adapter
  (in-process → HTTP/Kafka), not a rewrite.
- **−** Operational complexity (more services, eventual consistency on the
  payment→ledger seam). This is why extraction is **gated on a real driver**.
- **Execution is deferred:** a full live extraction (separate deployable, its own
  DB, mesh) cannot be run/verified in the current single-node dev environment.
  This ADR fixes the **strategy, order, seams, and readiness baseline**; the
  physical split is a future, driver-triggered step. See the
  [extraction blueprint](../R4_SERVICE_EXTRACTION_BLUEPRINT.md).
