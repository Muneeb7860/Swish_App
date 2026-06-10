# Swish — As-Built vs Target Reconciliation (Hybrid Model)

**Purpose.** The BRD and HLD describe an aspirational **B2B-SaaS, database-per-
service microservices** platform. The code, as built, is a **B2C quick-commerce
modular monolith (PostgreSQL) + B2B procurement**, plus a standalone Python
hybrid-agentic governance library. Neither is "wrong":

- the **BRD/HLD are the North-Star target** (the approved vision), and
- the **as-built is current reality** (what runs and is CI-green today).

This document is the **single bridge** between them — a *hybrid* posture that
keeps both first-class. It (A) reconciles every requirement/component to its
current status, and (B) captures the delta as a phased convergence roadmap.
Nothing in either vision is discarded; everything is either **built**, **partial
(tracked)**, or **roadmapped**.

Legend: ✅ built · 🟡 partial · 🔴 not yet (roadmap).

---

## A. Reconciliation

### A.1 Functional requirements (BRD §5)

| FR | Requirement (target) | As-built today | Status | Converge in |
| :-- | :--- | :--- | :-: | :-- |
| FR-01 | Retailer self-service onboarding (sensor provisioning, API keys) | 3-gate onboarding exists for **riders**; no retailer portal / sensor provisioning | 🟡 | R2 |
| FR-02 | AI negotiation: outbox → Kafka → **MongoDB** → price negotiation | RFQ reverse auction (`MasterOrchestratorService`) + outbox→Kafka relay; Mongo CDC sink not wired | 🟡 | R3 |
| FR-03 | Telemetry ingestion (MQTT/HTTPS → **TimescaleDB**) | `TelemetryServiceImpl` cold-chain CQRS on **PostgreSQL**; HTTP not MQTT | 🟡 | R3 |
| FR-04 | Ledger auditing (SHA-256 hash chain, REST search) | `LedgerServiceImpl` + DB hash-chain & balance triggers + `/ledger` API | ✅ | — |
| FR-05 | Operator dashboard + RBAC | `frontend-admin` + JWT/OPA RBAC + unified HITL queue | ✅ | — |
| FR-06 | Billing engine (per-hub flat tier, invoicing) | `billing` context (V22): flat-tier subscription BASIC/PRO/ENTERPRISE + per-period invoicing | ✅ | **done (R2)** |
| FR-07 | Alert & notification (SMS/Email/Webhook) | `notification-engine` (Email/SMS/Push/WS) + SLA/temp/anomaly alerts | ✅ | — |

### A.2 Architecture & patterns (HLD)

| HLD element (target) | As-built today | Status | Converge in |
| :--- | :--- | :-: | :-- |
| Transactional Outbox | `OutboxEventScheduler` + `oltp.outbox_events` | ✅ | — |
| Choreography Saga + compensation | `OrderSagaManager` / `OrderSagaListener` | ✅ | — |
| Idempotency / Retry / DLQ / Circuit Breaker | unique keys + `@TransactionalRetry` + Resilience4j + outbox retry→FAILED | ✅ | — |
| Correlation ID + OTel tracing | `X-Correlation-ID` MDC; Zipkin wired | ✅ | — |
| **Database-per-service** | **modular monolith** — one PostgreSQL, 4 schemas (`oltp/olap/dispatch/wholesaler`) | 🟡 | R4 |
| **MongoDB** (event-sourced CDC, audit archive) | not wired | 🟡 | R3 |
| **TimescaleDB** (telemetry analytics) | PostgreSQL `order_telemetry_logs` + `olap` warehouse | 🟡 | R3 |
| **HashiCorp Vault** (secrets, mTLS) | GCP Secret Manager (prod) + env (dev) | 🟡 | R4 |
| **mTLS inter-service** | single deployable; no inter-service mTLS yet | 🟡 | R4 |
| Hybrid on-prem AI governance | `homelab-ai-governance` (Ollama + Groq) — **exists, standalone** | ✅* | R5 (integrate) |

### A.3 Data model (BRD §8)

| BRD entity (target store) | As-built table (PostgreSQL) | Status |
| :--- | :--- | :-: |
| Store (PostgreSQL) | `oltp.dark_stores` | ✅ |
| SKU (PostgreSQL) | `oltp.inventory` / `oltp.product_listings` | ✅ |
| Telemetry (TimescaleDB) | `oltp.order_telemetry_logs` (Postgres) | 🟡 |
| NegotiationEvent (MongoDB) | `oltp.b2b_restock_orders` + `procurement_approvals` (Postgres) | 🟡 |
| LedgerEntry (PostgreSQL) | `oltp.journal_entries` + `ledger_lines` (hash-chained) | ✅ |

**Capability alignment: ~70%.** The *what* (ledger, telemetry, AI negotiation,
alerts, RBAC) largely exists; the divergence is mostly the *how* (single Postgres
monolith vs polyglot microservices) plus three functional items (R2/R3 below).

---

## B. Convergence Roadmap (as-built → target)

Phased so each step ships value without a big-bang rewrite. The hybrid principle:
**stay a modular monolith until a context earns extraction**, and graft the
target's polyglot pieces in only where they pay off.

| Phase | Theme | Work |
| :-- | :--- | :--- |
| **R1** | Doc reconciliation *(done)* | This file + status banners on BRD/HLD; validated ERD/LLD/C4/sequence set. |
| **R2** | Close functional gaps | **FR-06 billing engine** ✅ *(done — `billing` context, V22)*; **FR-01 retailer self-service portal** (registration, API-key issuance) reusing the 3-gate onboarding engine ← next. |
| **R3** | Polyglot data path | **FR-02 Mongo CDC sink** for `NegotiationEvent`; **FR-03 TimescaleDB** hypertable for telemetry + optional MQTT ingestion adapter. |
| **R4** | Selective decomposition | Extract the highest-contention contexts (payment, telemetry) to their own DB/service; introduce Vault + inter-service mTLS where a real service boundary exists. |
| **R5** | Distributed hybrid agentic | Wire `MasterOrchestratorService` → `homelab-ai-governance` pipeline (the Java↔Python bridge) so support/procurement routes through the governed hybrid router. |

**Sequencing rationale (A before B):** reconciling the docs first (R1) makes the
gap list authoritative and prevents building toward a stale spec. R2 ships the
only hard functional gap (billing) and the headline B2B feature (retailer
portal). R3–R5 converge topology and AI — each independently shippable.

---

## Posture

This is deliberately **hybrid, not either/or**: the BRD/HLD remain the target the
roadmap converges toward, while the as-built monolith is treated as a legitimate
*current* architecture (not technical debt to apologise for). Re-evaluate the
monolith→microservices split per-context at each phase boundary, extracting only
when scale, team, or fault-isolation demand it.
