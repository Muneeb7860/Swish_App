# Product Requirements Document (PRD)
**Project**: Swish OS Platform (v2.0.0)  
**Classification**: Proprietary Strategic Document  
**Status**: APPROVED WITH CONDITIONS (SR-2026-B2B-004)  
**Date**: June 2, 2026  

---

## 1. Product Vision & Target Users

### A. Product Vision
To provide global retail convenience networks and grocery chains with an autonomous, AI-driven B2B supply chain operating system (**Swish OS**). The platform automates inventory restock negotiations, secures transaction audits via a cryptographically chained double-entry ledger, and handles vendor coordination autonomously, reducing operating expenses.

### B. Core Target Users
*   **Retail Enterprise CFOs**: Seek transparent software licensing costs, audit compliance, and procurement savings without fleet depreciation liability.
*   **Store Managers / MFC Operators**: Oversee stock levels and only want to manage inventory by exception (relying on automated replenishment).
*   **Wholesale Distributors**: Connect directly with Swish OS through standard REST APIs or conversational channels to fulfill restocks.
*   **Compliance Auditors**: Require mathematical, tamper-evident proof of cold-chain thermal integrity and financial transaction validity.

---

## 2. Core Capabilities & Feature Set

### A. Autonomous B2B Procurement Agent
*   **Depletion Alarm**: Triggered when any item's stock drops below 3 units (configured in `Inventory` domain).
*   **Autonomous Negotiation**: The `B2BProcurementAgent` queries wholesale pricing matrices, calculates discount proposals, and constructs replenishment contracts based on supplier volume discounts and credit terms.
*   **Active Fallback Routing**: If the primary wholesaler (`WHOLESALER-1`) API fails, the service automatically fails over to the secondary distributor (`wholesaler-2`), eliminating stockout risks.

### B. Edge Agent Gateway (BFF)
*   **Headless OpenAPI Exposure**: Spring Cloud Gateway BFF exposes structured, machine-readable specifications (`/v3/api-docs`) on port 8081 for enterprise AI engines.
*   **Security Token Relay**: The BFF acts as the security boundary, performing OIDC OAuth2 token verification and relaying HttpOnly session cookies to client browsers. This keeps tokens strictly inside the cluster boundary.

### C. Cryptographic Ledger & Auditing
*   **Double-Entry Balances**: Enforces debit/credit balancing in the transaction database using deferred constraints. Debits and credits cannot be both zero or both positive.
*   **Tamper-Evident Hash Chain**: Every journal entry saved in PostgreSQL triggers a PL/pgSQL database procedure that calculates a SHA-256 hash incorporating the previous entry's hash, forming an immutable audit trail.
*   **GDPR Profile Purge (Article 17)**: Clears personal emails, sets `is_anonymized = true`, but retains a SHA-256 hash of the email to block duplicate customer onboarding scams.

### D. Governance & HITL Queue
*   **Deterministic Guardrails**: The `ProcurementGuardrailsEngine` validates B2B agent actions against strict, non-AI bounds:
    - Maximum spend limit: **$5,000 per restock**.
    - Acceptable price variance: **10% deviation** compared to historical averages.
*   **Human-in-the-Loop (HITL) Queue**: Violations instantly freeze the transaction and route the task to `HitlQueue` for manual supervisor review and override. Every override justification is logged as a balanced ledger entry.

---

## 3. Data Schema & Specifications

To ensure database consistency, Swish OS v2.0.0 maps entities to a segmented relational (OLTP) and document (OLAP) tier:

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PostgreSQL DB (OLTP)                          │
├────────────────────────────────────────────────────────────────────────┤
│  - oltp.customers (customer_id, email, is_anonymized, hashed_email)    │
│  - oltp.orders (order_id, customer_id, total_amount, tip_amount, status)│
│  - oltp.journal_entries (entry_id, entry_uuid, previous_entry_hash,    │
│                         entry_hash, timestamp, reference)              │
│  - oltp.ledger_lines (line_id, entry_id, account_type, actor_id,       │
│                      debit, credit)                                    │
│  - oltp.outbox_events (id, aggregate_id, aggregate_type, event_type,   │
│                       payload, status)                                 │
└──────────────────┬─────────────────────────────────────────────────────┘
                   │ (Asynchronous Kafka Outbox Polling)
                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          MongoDB Atlas (OLAP)                          │
├────────────────────────────────────────────────────────────────────────┤
│  - olap.negotiation_history_logs                                       │
│    - { negotiation_id, store_id, supplier_id, raw_conversation_log,    │
│        final_discount, audit_hash, timestamp }                         │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. 12–36 Month Tactical Execution Roadmap

The rollout is divided into a three-year progression path:

```
  [Year 1: Telemetry & Pilot] ➔ [Year 2: Replenishment & Scale] ➔ [Year 3: Multi-Region & GDP Pharma]
```

### 📅 Year 1 (Telemetry & Pilot Validation)
*   **Q1 - Pilot Setup & Telemetry**:
    - Build and launch the 5-store Valora pilot hubs in Swiss transport hubs.
    - Set up **Tier 1 ($1,000/month)** subscription.
    - Validate IoT thermal telemetry streaming and Redis geofencing buffers.
*   **Q2 - Cryptographic Auditing**:
    - Roll out the PostgreSQL SHA-256 hash-chaining journal and ledger balance validators.
    - Validate GDPR Article 17 profile purge API commands.
*   **Q3 - Replenishment Launch**:
    - Deploy the `B2BProcurementAgent` and `ProcurementGuardrailsEngine` (Tier 2, $1,500/month upgrade).
    - Initiate automated restock negotiations and verify the Wholesaler API failover mechanism.
*   **Q4 - Quality Gates Audit**:
    - Execute automated visual Playwright E2E browser validations.
    - Run load testing simulating 10,000 concurrent negotiation cycles.

### 📅 Year 2 (Commercial Expansion & Swiss Rollout)
*   **Q1-Q2 - SaaS Expansion**:
    - Expand SaaS licensing GTM sales targeting regional convenience store networks (Valora, Migrolino).
    - Deploy to 100 active store nodes.
*   **Q3-Q4 - EBITDA Break-Even**:
    - Standardize SaaS billing around flat Tier 2 pricing ($1,500/month/hub).
    - Turn EBITDA positive ($+\$240\text{K}$) through automated, zero-liability software delivery.

### 📅 Year 3 (Multi-Region & Pharmaceutical Entry)
*   **Q1-Q2 - GDP Pharma Certification**:
    - Upgrade thermal sensors to high-accuracy, calibrated hardware modules.
    - Acquire GDP (Good Distribution Practice) audit certification for medical/vaccine logistics.
    - Sign a clinical pharmaceutical pilot partner.
*   **Q3-Q4 - Multi-Region Scale**:
    - Scale platform globally to 500+ store nodes (EU/India/GCC).
    - Reach EBITDA target of **$2.2M+**.

---

## 5. Non-Functional Requirements (NFRs) & Quality Gates

*   **Code Coverage Gate**: JaCoCo maven plugin enforces a **minimum 45% (or 100% on specific service packages)** covered ratio on instructions to block regressions.
*   **Scalability**: Ingest buffer (Redis) handles up to 5,000 active telemetry updates/second.
*   **Resiliency**: Transactional Outbox pattern decouples main transaction threads from message broker availability.
*   **Security & Encryption**: API endpoints behind the BFF utilize TLS 1.3, strict CORS deduplication, and OIDC JWT header stripping to secure downstream microservices.
