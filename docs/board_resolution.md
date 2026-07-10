# Board Resolution: Swish OS Strategic Pivot & EBITDA Roadmap

**Date of Resolution**: June 1, 2026  
**Document Reference**: SR-2026-B2B-004  
**Status**: APPROVED WITH CONDITIONS  
**Location**: Swiss Q-Commerce HQ / Virtual Boardroom  

---

## 👥 Attendees & Roles
*   **Dr. Marcus Vance** — VC Director (representing Alpine Ventures)
*   **Helena Reinhardt** — Board Chairwoman
*   **Beat Keller** — Chief Financial Officer (CFO)
*   **Ad-hoc invitees**: Systems Architect, General Manager (GM)

---

## 📝 Background & Context
The Board of Directors met to review the strategic proposal for pivoting Swish OS from a consumer-focused quick-commerce delivery model to a B2B Software-as-a-Service (SaaS) procurement and telemetry platform. The review was conducted on the basis of two core planning documents:
1.  **Technical Plan**: [pivot_implementation_plan.md](../docs/pivot_implementation_plan.md) by the Systems Architect.
2.  **Commercial & EBITDA Plan**: [gm_business_strategy.md](../docs/gm_business_strategy.md) by the General Manager.

---

## 💬 Boardroom Discussion Transcript (Simulated)

### 1. Consensus on B2B SaaS Pivot Viability
*   **VC Director (Marcus Vance)**: "The shift to B2B SaaS is long overdue. Consumer quick-commerce unit economics in Switzerland are brutal due to high labor costs. Moving upstream to automated procurement and telemetry leverages our technology stack without the heavy operational overhead. It's a high-margin, scalable software model that VC markets will actually value."
*   **CFO (Beat Keller)**: "I agree on the business model shift. Eliminating courier payroll, delivery vehicle depreciation, and warehouse leases drastically reduces our burn rate. However, the transition from transactional revenue to SaaS licensing requires a bridge period where cash flow must be monitored closely."
*   **Chairwoman (Helena Reinhardt)**: "The consensus is clear: the core B2B SaaS model is highly viable. It plays directly to our strengths—automated agent-based procurement and real-time ledger compliance—while moving away from low-margin B2C logistics."

### 2. Critiques on the Test Flight Niche (Pharmaceuticals vs. Valora)
*   **Chairwoman (Helena Reinhardt)**: "I have a major concern regarding the pilot alignment. The GM's strategy focuses on temperature-sensitive pharmaceuticals and GDP (Good Distribution Practice) guidelines. Yet, the designated pilot partner is **Valora (k kiosk)**. Valora is a convenience retailer, not a pharmaceutical distributor. While they carry chocolate and cold drinks, they do not operate in a clinical, GDP-compliant environment."
*   **VC Director (Marcus Vance)**: "Helena is spot on. If we want to capture the high-margin pharma market, we cannot validate GDP audits using snack kiosk telemetry. The regulatory stakes are completely different. If Valora is the pilot, we should focus on high-frequency shelf-life management for fresh foods and perishables, not medical vaccines. If we want to target pharma, we must sign a secondary pilot with a clinical distributor like Galenica or Zur Rose."
*   **CFO (Beat Keller)**: "Indeed. The compliance costs of true pharmaceutical validation (GDP) are extremely high. The Systems Architect's [ProcurementGuardrailsEngine.java](../backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/ProcurementGuardrailsEngine.java) and WMS schemas must be audited and certified if we enter medical supply chains. That is a capital-intensive process not factored into the Year 1 EBITDA neutral target."

### 3. Evaluation of the EBITDA Roadmap
*   **CFO (Beat Keller)**: "Looking at the GM's roadmap: Year 1 neutral (5 stores), Year 2 +$240K (100 stores), Year 3 +$2.2M (500 stores). Let's talk about the Year 2 pricing model of **$1,500/month/hub + 1% savings commission**. For small retail kiosks (like Valora's k kiosks), $1,500/month is excessively steep—it represents a massive chunk of their operating profit. Furthermore, tracking and auditing a '1% savings commission' on procurement is incredibly difficult. We need clean baselines to calculate 'savings' without triggering disputes."
*   **VC Director (Marcus Vance)**: "Beat is right. SaaS buyers hate variable 'commission' pricing unless the savings are contractually simple to prove. I propose we simplify the pricing model. Let's aim for a tiered subscription model: a base fee for telemetry and ledger tracking, plus an optional performance-based fee for B2B procurement automation once we have historical baselines."
*   **Chairwoman (Helena Reinhardt)**: "We also need to look at the leap from 5 hubs in Year 1 to 100 hubs in Year 2. That requires a structured partner program or integration with major retail networks, not just individual store outreach."

---

## 🏛️ Board Resolutions

### Resolution 1: Approval of the B2B SaaS Strategic Pivot
*   **Resolved**: The Board formally approves the strategic pivot from consumer quick-commerce to a B2B SaaS procurement and ledger platform, as outlined in the technical architectural framework in [pivot_implementation_plan.md](../docs/pivot_implementation_plan.md).

### Resolution 2: Conditional Approval of the Commercial Strategy & Niche Focus
*   **Resolved**: The commercial strategy outlined in [gm_business_strategy.md](../docs/gm_business_strategy.md) is **APPROVED WITH CONDITIONS**. The GM must address the following mandates before Phase 2 of the implementation plan begins:
    1.  **Pilot Alignment**: If Valora is retained as the sole pilot partner, the scope must be restricted to **retail cold-chain and fresh food shelf-life optimization**. Entry into the pharmaceutical sector is deferred until a certified pharmaceutical distributor is secured as a co-pilot.
    2.  **Pricing Revision**: The GM and CFO must jointly revise the pricing model to replace or simplify the 1% savings commission with a transparent tiered subscription model that fits Valora's unit economics.
    3.  **Audit Readiness**: The Systems Architect must add compliance logging modules specifically to trace telemetry integrity for audit compliance, ensuring the ledger can support future GDP audits if the pharmaceutical sector is pursued.

---

## 🗳️ Voting Results

| Board Member | Vote | Conditions / Comments |
| :--- | :---: | :--- |
| **Helena Reinhardt (Chair)** | **Approved with Conditions** | Demands clear separation between retail convenience pilots and GDP medical compliance. |
| **Dr. Marcus Vance (VC)** | **Approved with Conditions** | Demands a simplified, auditable SaaS pricing model without messy commission tracking in Year 2. |
| **Beat Keller (CFO)** | **Approved with Conditions** | Demands detailed unit economics for the $1,500/month/hub pricing model to prove value vs cost. |

*   **Final Status**: **RESOLVED BY UNANIMOUS CONDITIONAL APPROVAL.**

---

*Signatures on file.*  
*Helena Reinhardt, Board Chairwoman*  
*Beat Keller, Chief Financial Officer*  
