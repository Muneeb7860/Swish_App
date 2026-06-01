# Business Requirements Document (BRD)
**Project**: Swish OS Platform (v2.0.0)  
**Classification**: Proprietary Strategic Document  
**Status**: APPROVED WITH CONDITIONS (SR-2026-B2B-004)  
**Date**: June 2, 2026  

---

## 🏛️ Document Governance & Control

| Version | Date | Author(s) | Description |
| :--- | :--- | :--- | :--- |
| **v2.0.0** | June 2, 2026 | Technical Product Owner / Architect | Transferred to B2B SaaS automated procurement, telemetry, and ledger auditing model under Board directive. |

---

## 1. Executive Summary & Strategic Pivot

### A. The Pivot Rationale
**Swish OS v2.0.0** represents a complete strategic transition from a consumer-facing quick-commerce delivery platform to a pure-play **B2B Software-as-a-Service (SaaS)** replenishment, telemetry, and ledger compliance platform. 

Consumer logistics in Switzerland suffer from severe capital overheads (vehicle depreciation, warehouse leases, courier hourly rates, and payroll taxes). Moving upstream to license software directly to established retail chains (e.g., Valora) eliminates operational liability while capitalizing on our core technology: autonomous agentic negotiation, IoT thermal telemetry, and cryptographic double-entry auditing.

### B. Core Vision
To provide regional and global retail chains with a lightweight, software-first solution that retrofits existing physical brick-and-mortar storefronts into autonomous, high-velocity distribution hubs without expensive mechanical automation (Zero-CapEx WMS).

---

## 2. Market Analysis & Competitive Positioning

### A. Total Addressable Market (TAM)
Swish OS targets the global B2B procurement and warehouse management software market, estimated at **$20B+**. We prioritize FMCG retail convenience networks, grocery hubs, and temperature-sensitive food/beverage distributors.

### B. Competitor Matrix

| Competitor / System | Product Model | Strengths | Swish OS Advantage |
| :--- | :--- | :--- | :--- |
| **Instacart Platform Services** | White-label B2C grocery SaaS | Huge scale, pre-integrated courier pool, strong brand recognition. | **Data Sovereignty**: Instacart retains user profiles and sets pricing. Swish OS yields 100% data ownership and pricing control to the retail client. |
| **Takeoff Technologies** | Hardware-centric Micro-Fulfillment | Heavy automated picking machinery, high throughput. | **Zero-CapEx Software-Only**: Takeoff requires multi-million dollar mechanical installs. Swish OS retrofits existing store shelves with simple mobile picking apps. |
| **Fabric WMS** | Robotic Micro-Fulfillment | High storage density, advanced physical sorting. | **Rapid Deployment**: Fabric builds take 6–12 months. Swish OS deploys within 7 days via federated micro-frontends and API gateway mapping. |
| **Manhattan Associates WMS** | Legacy Enterprise WMS | Highly mature, deeply integrated database systems. | **Autonomous Negotiations**: Manhattan WMS has no automated negotiation capabilities. Swish OS features autonomous AI procurement agents that actively drive down wholesaler costs. |

---

## 3. Business Objectives & Revenue Model

### A. Core Objectives
- Achieve **$100M+ ARR** within 36 months of pilot rollout.
- Maintain **Net Revenue Retention (NRR) > 120%** through expansion licensing per store hub.
- Ensure **Zero Fleet and Real Estate Liability** by forcing retail clients to provide physical infrastructure.

### B. Tiered SaaS Pricing Model
To resolve the billing dispute risks and auditing overhead associated with variable "savings commission" pricing, the pricing model is structured into two transparent flat tiers:

1.  **Tier 1 (Telemetry & Ledger Compliance)**: **$1,000/month per active hub**.
    - Covers IoT thermal coordinate tracking, spoilage alarm notifications, and SHA-256 ledger security auditing.
    - Fits the operational budget constraints of regional convenience store nodes.
2.  **Tier 2 (Agentic Replenishment & Automation)**: **$1,500/month per active hub**.
    - Adds B2B autonomous procurement negotiations, pricing guardrail validations, and primary/secondary wholesaler failovers.
    - Represents the high-value automated SaaS layer.

---

## 4. Product Scoping & Pilot Boundaries

### A. Year 1 Pilot Scope (Valora - k kiosk)
To enforce capital-efficiency and satisfy the Board of Directors, the Year 1 pilot is subject to strict boundaries:
*   **Target Segment**: Restricted exclusively to **retail convenience cold-chain and fresh food shelf-life optimization** (sandwiches, sushi, cold beverages).
*   **Scale**: Deployed across 5 high-traffic transport hub stores (Zurich HB, Bern, Basel SBB, Geneva Cornavin, Lucerne).
*   **Pharma Deferral**: Entry into the clinical pharmaceutical logistics sector is formally deferred until a secondary certified pharmaceutical distributor is secured as a pilot partner.

---

## 5. Key Performance Indicators (KPIs)

*   **Procurement Savings**: Target **3% to 7%** average invoice cost savings negotiated dynamically by AI agents against wholesale market baselines.
*   **Fulfillment Speed**: Maintain picking cycles completed in **under 4 minutes** (SLA target).
*   **SLA Uptime**: Auto-replenishment service uptime $\ge 99.9\%$. A 5% base fee penalty discount applies to any active hub if picking times exceed the 4-minute SLA by more than 15% on average for that month.
*   **System Integrity**: **100%** of ledger transactions must verify mathematically against the cryptographically chained journal (`oltp.journal_entries`).

---

## 6. Financial Projections & EBITDA Roadmap

All figures in USD ($). Projections assume a Year 1 pilot scaling to 100 stores in Year 2 and 500 stores in Year 3.

| Financial Metric | Year 1 (Pilot) | Year 2 (Rollout) | Year 3 (Scale) | Year 4 | Year 5 |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Active Store Nodes** | 5 | 100 | 500 | 2,000 | 5,000 |
| **Tier 1 SaaS Revenue** | $60,000 | $0 | $0 | $0 | $0 |
| **Tier 2 SaaS Revenue** | $12,000 | $1,800,000 | $9,000,000 | $36,000,000 | $90,000,000 |
| **Gross Revenue** | **$72,000** | **$1,800,000** | **$9,000,000** | **$36,000,000** | **$90,000,000** |
| **Operating Expenses (OpEx)** | $90,000 | $1,200,000 | $4,500,000 | $12,000,000 | $25,000,000 |
| **Capital Expenditures (CapEx)** | $10,000 | $50,000 | $150,000 | $400,000 | $1,000,000 |
| **EBITDA** | **-$28,000** | **+$550,000** | **+$4,350,000** | **+$23,600,000** | **+$64,000,000** |
| **EBITDA Margin** | **-38.8%** | **+30.5%** | **+48.3%** | **+65.5%** | **+71.1%** |

### **Notes on Financial Assumptions**:
1.  **OpEx**: Covers cloud hosting (Postgres, Mongo, Redis, Kafka), LLM API token billing, developer salaries, and account management commissions.
2.  **CapEx**: Covers local hardware testing kits (calibrated IoT thermal sensors) deployed during onboarding. GDP-grade medical sensors are deferred, keeping Year 2 CapEx capped at $50,000.
3.  **Revenue Math**:
    - Year 1: 5 stores × $1,000/month × 12 months = $60,000 (Tier 1 base) + onboarding setup fees.
    - Year 2: 100 stores × $1,500/month × 12 months = $1,800,000 (Tier 2 base). This flat pricing replaces the legacy variable commission while matching the identical gross revenue trajectory.
