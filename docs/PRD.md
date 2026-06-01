# Product Requirements Document (PRD): Swish OS
**Version**: 2.0.0 (AI-Tsunami Resilient Edition)

---

## 1. Product Vision
To provide global retail and grocery chains with an autonomous, AI-driven B2B supply chain operating system (**Swish OS**) that automates inventory restock negotiations, secures B2B transaction execution via a cryptographically chained double-entry ledger, and optimizes vendor relations without operational overhead.

---

## 2. Core Target Audience
*   **Retail Enterprise CFOs**: Seeking to reduce procurement overhead and secure discounts from wholesalers.
*   **Supply Chain Operators / Store Managers**: Who oversee inventory levels and only want to manage logistics by exception.
*   **Wholesaler Distributors**: Interfacing with Swish OS via standard API or conversational channels.

---

## 3. Core Capabilities & Feature Set

### A. Autonomous B2B Procurement Agents
*   **Trigger**: Real-time inventory depletion alarms (monitored via `Inventory` levels).
*   **Heuristics**: AI agents initiate multi-party negotiations with wholesale vendors (`Wholesaler`), drafting email and API bids based on volume, payment speed (e.g. net-10 vs. net-30), and historical supplier trust scores.
*   **Outcome**: Automated purchase orders and invoice settlement.

### B. Agentic API Gateway & Headless Interface
*   **Headless-First Design**: The BFF Gateway exposes machine-readable OpenAPI schemas allowing external enterprise AI agents to authenticate and query Swish OS natively.
*   **Exception Control Cockpit**: The UI is optimized as a passive "Mission Control Feed" summarizing active negotiations, ledger flows, and dynamic exceptions requiring human input.

### C. Cryptographic Ledger & HITL Safeguards
*   **Rule Validation Engine**: Hard-coded, deterministic rules enforce transaction limits (e.g. max $5,000 per order) and price variance checks (within 10% of historical average).
*   **Human-in-the-Loop (HITL)**: Failing validations freeze the transaction state and route the order to the `HitlQueue` for supervisor approval.

### D. Anonymized Negotiation Heuristics Moat
*   **Immutable Logs**: Every bid, counter-offer, time-to-respond, and outcome is logged to `olap.negotiation_history_logs` with all PII hashed (SHA-256).
*   **SLM Training Loop**: Serves as a proprietary dataset to train local, lightweight models specializing in retail trade procurement.

---

## 4. Non-Functional Requirements (NFRs)
*   **Security & Compliance**: All financial transactions must balance to zero and verify the cryptographic integrity of the journal chain before commit.
*   **Scale**: Capable of processing 100,000 concurrent agent negotiations.
*   **Resiliency**: Zero dependency on external LLM availability for core order persistence (utilizing Transactional Outbox pattern).
