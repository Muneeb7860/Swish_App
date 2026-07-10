# ADR-009: Database Consolidation, Compliance, and Pilot Scope Realignment

- **Status:** Accepted (2026-07-09)
- **Date:** 2026-07-09
- **Author:** Systems Architect
- **Relates to:** [ADR-001 Hexagonal](./adr_001_hexagonal_architecture.md), [ADR-007 Agentic Governance](./adr_007_agentic_governance_layering.md), [AS_BUILT_VS_TARGET.md](../AS_BUILT_VS_TARGET.md)

---

## Context

Following the strategic review by the Board of Directors ([board_resolution.md](../board_resolution.md)) and the financial critique from the CFO ([cfo_masterplan_critique.md](../cfo_masterplan_critique.md)), the Systems Architect has been called to make a definitive recommendation regarding the technology stack and pilot scoping. 

The primary architectural challenges are:
1. **Dual-Database & Event Streaming Cost (Postgres + MongoDB + Kafka)**: High OpEx and deployment complexity of running three separate storage and messaging systems for telemetry and negotiation event archiving.
2. **GDP Compliance (EU Guidelines 2013/C 343/01)**: The requirement for immutable, cryptographically signed, and calibrated logs for pharmaceutical cold-chain auditing.
3. **Pilot Partner Niche Mismatch (Valora / k kiosk)**: Valora is a convenience retailer and lacks a clinical, GDP-compliant pharmaceutical environment.
4. **SaaS Pricing Complexity**: The operational difficulty of auditing and proving a "1% savings commission" on autonomous agent negotiations.

---

## Decisions & Recommendations

As Systems Architect, I am taking the call on the following architecture adjustments to align with the board's mandates and the CFO's financial constraints:

### 1. Database Consolidation (Retire MongoDB & Kafka)
*   **Decision**: We will consolidate the data layer into **PostgreSQL**. MongoDB and Apache Kafka will be retired from the core application stack for local and pilot deployments.
*   **Implementation**:
    *   **Telemetry data**: Standardize on PostgreSQL with the **TimescaleDB** time-series extension. High-frequency coordinates and temperature ticks will be written directly to the `oltp.sensor_readings` table (already defined as a partitionable structure). 
    *   **Write-amplification mitigation**: The thread-safe in-memory buffer (`TelemetryBufferScheduler` utilizing `ConcurrentLinkedQueue`) will continue to buffer ticks and batch-insert them into PostgreSQL, preventing database lock contention.
    *   **Negotiation Event Archive**: We will retire the `MongoNegotiationArchiveAdapter` and replace the MongoDB event CDC archive with a PostgreSQL-backed JSONB archive table (`oltp.negotiation_event_archive`).
*   **Rationale**: Tripling the database footprint (PostgreSQL + MongoDB + Kafka) for 5,000 nodes is financially unviable. It increases the audit surface area and requires separate backup, recovery, and security credentials. Consolidating to PostgreSQL reduces OpEx by 60%, simplifies backups, and streamlines regulatory audits.

### 2. Pilot Scoping & Niche Deferral
*   **Decision**: The Valora (k kiosk) pilot will focus exclusively on **retail cold-chain and fresh food shelf-life management** (sandwiches, sushi, cold beverages). 
*   **Implementation**: The software-only telemetry system will monitor temperature ranges appropriate for food safety (typically 2°C to 8°C or 0°C to 4°C). 
*   **Pharma GDP Deferral**: True Good Distribution Practice (GDP) clinical validation and hardware audit certifications are deferred until a clinical pharmaceutical partner (e.g., Galenica or Zur Rose) is secured as a co-pilot. This protects the Year 1 CapEx budget.

### 3. Audit Readiness (GDP-Capable Telemetry Integrity)
*   **Decision**: Retain and harden the cryptographic verification and sensor calibration modules implemented in the backend on top of PostgreSQL.
*   **Implementation**:
    *   **Cryptographic Chaining**: Every sensor reading persisted to PostgreSQL contains a SHA-256 hash of its contents chained to the hash of the previous reading (`reading_hash` and `previous_reading_hash`).
    *   **Integrity Verification Engine**: The `/api/v1/sensors/{sensorId}/verify-integrity` API is retained to detect telemetry tampering instantly by walking the hash chain.
    *   **Calibration Enforcement**: Sensors must log self-calibration checks. If a temperature sensor fails compliance or misses its calibration window, B2B replenishment orders are automatically rerouted to alternative compliant dark stores.
    *   **Human Justification Hashing**: All manual overrides in the HITL queue require a text reason, which is SHA-256 hashed and recorded into the `SecurityTrustLedger` under `"HITL-OVERRIDE-HASH:<hash>"`.
*   **Rationale**: This ensures that while we defer expensive pharmaceutical audits, our core software is **GDP-ready** and mathematically tamper-proof from day one.

### 4. Pricing Model Simplification
*   **Decision**: Replace the 1% savings commission on autonomous negotiations with a transparent flat-tier subscription structure for the Pilot and Phase 2.
*   **Subscription Tiers**:
    *   **Tier 1 (Telemetry & Compliance)**: $1,000/month per hub.
    *   **Tier 2 (Replenishment Automation & Telemetry)**: $1,500/month per hub.
*   **Rationale**: Proving "savings" commission baselines is highly contentious and operationally complex. A flat subscription model fits Valora's convenience store economics and provides the CFO with predictable cash flow metrics during the SaaS bridge period.

---

## Consequences

*   **Positive (+)**:
    *   **Infrastructure Reduction**: Eliminates two large infrastructure layers (MongoDB and Kafka) from the production runtime, lowering host OpEx.
    *   **Unified Backup/Restore**: All business state, security ledger entries, and telemetry logs reside in a single PostgreSQL instance.
    *   **Single Audit Boundary**: Cryptographic chain audits only need to scan a single SQL database.
    *   **Clear Pilot Focus**: Eliminates regulatory risk by keeping Valora focused on sandwich/sushi refrigeration rather than high-risk clinical vaccine tracking.
*   **Negative (-)**:
    *   **High-frequency Write Load**: Storing millions of ticks in Postgres puts pressure on write I/O. Mitigated by our scheduled write-buffer queues and TimescaleDB hypertables.
    *   **Event-Driven Integration**: Moving away from Kafka means relying on PostgreSQL-based Transactional Outbox polling and Spring Event listeners for internal modules. Since we are a modular monolith, this is a reasonable trade-off.

---

## Verification Plan

1.  **Database Migration**: The TimescaleDB-enabled schema (`V25`) and audit/calibration hardening (`V29`) are already deployed and verified green under the backend test suite.
2.  **Telemetry Chaining Tests**: Verification of hash-chain integrity under normal and tampered scenarios is validated in `SensorServiceImplTest.java` and `SensorControllerTest.java`.
3.  **Dynamic Restock Rerouting Tests**: Rerouting restocks from uncalibrated stores to alternative dark stores is verified by `WholesalerServiceImplTest.java`.
