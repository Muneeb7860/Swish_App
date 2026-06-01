# 💼 CFO Critique: Swish OS Strategic Masterplan (EBITDA, Pricing, and DB Consolidation)
**Prepared By**: Beat Keller, CFO, Swish OS  
**Date**: June 1, 2026  
**Classification**: Strict Proprietary Confidential  

---

## 📈 1. EBITDA Projections & OpEx/CapEx Rationale

The 5-Year EBITDA projections present an extremely aggressive hockey-stick trajectory, scaling from a **-$28,000 EBITDA (-38.8% margin)** in Year 1 to **+$64,000,000 EBITDA (+71.1% margin)** in Year 5. 

### Key Concerns:
1. **OpEx Scaling Disconnect**: 
   - OpEx scales from $90k (Year 1) to $1.2M (Year 2), then to $4.5M (Year 3), $12M (Year 4), and $25M (Year 5).
   - In Year 2, active nodes grow from 5 to 100 (a 20x increase). The OpEx scales by 13.3x.
   - In Year 5, supporting **5,000 active nodes** with a $25M OpEx budget implies a support/operations cost of just **$5,000 per node per year ($416 per node per month)**. This budget must cover SaaS hosting (Postgres, Mongo, Kafka), LLM API tokens (which are expensive for agentic B2B negotiations), developer salaries, account managers, sales commissions, and regulatory compliance audits. This is likely a significant underestimation.
2. **CapEx Under-provisioning for GDP Compliance**:
   - CapEx is budgeted at $10k in Year 1 and $50k in Year 2.
   - If Swish OS transitions to clinical pharmaceutical logistics in Year 2, complying with **Good Distribution Practice (GDP - EU Guidelines 2013/C 343/01)** requires high-accuracy calibrated IoT sensors, WORM-compliant hardware security modules, and strict temperature invariant auditing. The $50k CapEx budget in Year 2 is highly inadequate to purchase, calibrate, and support hardware kits across 100 store nodes.

---

## 🏷️ 2. Pricing Model & Revenue Realism

### Key Concerns:
1. **LTV:CAC Discrepancy & Pilot Pricing**:
   - The pilot base fee is **$1,000/month per active node**.
   - However, the LTV projection assumes a subscription base fee of **$1,200/month per active node**. There is no strategy outlined for how we successfully command a 20% price premium ($1,200 vs. $1,000) post-pilot.
   - The Performance Commission Fee is projected at **$300/store/month** on average. To hit this, our `B2BProcurementAgent` (earning a 1.2% commission on documented savings) must secure **$25,000 in monthly invoice savings per store** ($300 / 0.012 = $25,000). For a typical convenient store (e.g., k kiosk), a monthly restock invoice is rarely large enough to yield $25,000 in *pure savings* unless wholesale margins are ridiculously inflated.
2. **SLA Penalties Exposure**:
   - The 5% discount penalty if picking times exceed the 4-minute SLA by more than 15% poses a high risk to Year 1-2 subscription revenue margins, especially during early deployment when software/integration bugs are common.

---

## 🗄️ 3. Database Consolidation & Dual-Hosting Cost Critique

The current architecture employs a **dual-database strategy**:
*   **PostgreSQL** for transactional B2B replenishment and ledger data (leveraging `Isolation.READ_COMMITTED` and pessimistic locks).
*   **MongoDB** (NoSQL) for telemetry, GPS, and temperature logs streamed via **Apache Kafka**.

### Financial and Operational Impacts:
1. **Infrastructure Costs**: 
   - Dual hosting of PostgreSQL + MongoDB + Apache Kafka triples the baseline infrastructure costs. We must provision, scale, and maintain three separate stateful services.
   - For 5,000 nodes, streaming telemetry will generate billions of data points. Scaling MongoDB and Kafka will drastically eat into the $25M OpEx budget.
2. **Operational Overhead & Security/Compliance Audits**:
   - Maintaining two separate databases increases developer/DBA salary costs.
   - Under **GDP compliance**, data must be WORM (write-once, read-many) or cryptographically signed. Implementing and auditing GDP compliance across both PostgreSQL and MongoDB is twice as complex as consolidating into a single database system.
3. **Consolidation Alternative**:
   - We should urge the CTO to explore consolidating telemetry into **PostgreSQL** using **TimescaleDB** (time-series extension) or storing JSONB records. This would eliminate MongoDB and Kafka from the core infrastructure, significantly reducing OpEx, simplifying backup/recovery strategies, and streamlining GDP regulatory audits.
