# Swish OS: B2B Pivot Technical Implementation Plan
**Author**: Systems Architect  
**Version**: 2.0.0  

---

## 🏗️ Architectural Core Changes

### 1. Backend Agent Domain Expansion (`backend`)
*   **Target Files**: 
    - [NEW] [B2BProcurementAgent.java](file:///C:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/B2BProcurementAgent.java)
    - [MODIFY] [AgentController.java](file:///C:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/adapter/in/web/AgentController.java)
*   **Design**:
    - Build `B2BProcurementAgent` to consume inventory alarm logs.
    - Inject the LLM gateway interface (`LlmGatewayPort`) to generate optimized restock contracts.
    - Interface with `WholesalerRepository` to compare pricing tables.

### 2. Safeguard & HITL Validator
*   **Target Files**:
    - [NEW] [ProcurementGuardrailsEngine.java](file:///C:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/ProcurementGuardrailsEngine.java)
*   **Design**:
    - Implement hard bounds: Max limit $5,000 per order, and price variance within 10% of historical average.
    - Violations block the transaction and write records directly to the `HitlQueue`.

### 3. Headless API Schema Exposure (`bff`)
*   **Target Files**:
    - [MODIFY] [application.yml](file:///C:/Users/DELL%209420/Documents/swiss_App/bff/src/main/resources/application.yml)
*   **Design**:
    - Expose machine-readable OpenAPI endpoints (`/v3/api-docs`).
    - Add custom security policies to intercept and parse headers for external AI client authentication.

---

## 🚀 Execution Phases

### Phase 1: Core Service & Guardrails (Week 1–2)
*   Implement `B2BProcurementAgent` and the `ProcurementGuardrailsEngine`.
*   Establish unit tests in `MasterOrchestratorServiceTest.java`.

### Phase 2: Headless API & Gateway Exposure (Week 3)
*   Expose the REST endpoints on `AgentController` and map gateway routes.
*   Verify preflight handling and header mapping.

### Phase 3: MFE Exception Dashboard Reskin (Week 4)
*   Replace manual checkout elements in `CustomerApp.tsx` and `SystemEngineRoom.jsx` with the **AI Procurement Exception & Ledger Console**.
*   Wired websocket threads to stream live agent negotiations.
