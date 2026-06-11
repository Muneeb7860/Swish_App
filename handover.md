# Handover: Swish App Sprint 5 & Phase R5 Completion

## 📌 Context
During this session, we completed two major work areas:
1. **Sprint 5: API Contract & DTO Payload Alignment**: Resolved contract mismatches between React micro-frontends and Spring Boot controllers.
2. **Phase R5: Java-Python Bridge (Distributed Hybrid Agentic Governance)**: Exposed the Python AI governance pipeline as a REST microservice and integrated it with the Spring Boot backend as the primary LLM gateway with offline fallback protection.

---

## 🛠️ Changes Implemented

### 1. DTO & API Contract Realignment
* **JWT Fallbacks**: Updated controller endpoints in [CustomerController.java](file:///Users/muneeb/Documents/GitHub/Swish_App-1/backend/src/main/java/ch/swissqcommerce/backend/domain/customer/adapter/in/web/CustomerController.java) and [RiderController.java](file:///Users/muneeb/Documents/GitHub/Swish_App-1/backend/src/main/java/ch/swissqcommerce/backend/domain/enrollment/adapter/in/web/RiderController.java) to make `customerId` and `riderId` optional. When missing, they default automatically to the authenticated JWT principal.
* **Jackson Compatibilities**: Added `@JsonAlias` annotations on request DTOs inside `CustomerController`, `RiderController`, and `PaymentController` to support both **snake_case** (production frontend & OpenAPI specs) and **camelCase** (existing unit/security tests) payloads.
* **Wholesaler Invoices Endpoint**: Updated `/api/wholesaler/invoices` in [WholesalerController.java](file:///Users/muneeb/Documents/GitHub/Swish_App-1/backend/src/main/java/ch/swissqcommerce/backend/domain/wholesaler/adapter/in/web/WholesalerController.java) to return `B2BInvoiceDto[]` mapped from restock orders instead of returning a summary map.

### 2. Python Governance REST Microservice (`homelab-ai-governance`)
* **REST API Wrapper**: Added dependencies (`fastapi`, `uvicorn`, `pydantic`) in [pyproject.toml](file:///Users/muneeb/Documents/GitHub/Swish_App-1/homelab-ai-governance/pyproject.toml) and standardized the build-backend to `setuptools.build_meta`. Created:
  - [server.py](file:///Users/muneeb/Documents/GitHub/Swish_App-1/homelab-ai-governance/src/governance/server.py): Exposes `POST /api/v1/govern` (runs `execute_pipeline`) and `GET /health`.
  - [cli.py](file:///Users/muneeb/Documents/GitHub/Swish_App-1/homelab-ai-governance/src/governance/cli.py): CLI wrapper starting the Uvicorn FastAPI server on localhost.
* **Pipeline Hardening**:
  - Normalized tokenization inside `metrics.py` to strip punctuation and retain numeric entities, allowing correct overlap evaluation of digits.
  - Fixed `RateLimiter` inside `audit.py` to prioritize explicit limit overrides passed during initialization.
  - Set default rule `enabled: True` state in `loader.py` if not explicitly declared in `shared_guardrails.yaml`.
  - Updated PII scan regex patterns to use uppercase name constants to match test assertions.

### 3. Java-Python Bridge (`backend`)
* **Properties Config**: Declared `swish.governance.api.url=${SWISH_GOVERNANCE_API_URL:}` in `application.properties`.
* **Outbound Adapter**: Created [PythonGovernanceAdapter.java](file:///Users/muneeb/Documents/GitHub/Swish_App-1/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/adapter/out/governance/PythonGovernanceAdapter.java) implementing `LlmGatewayPort`. It calls the Python FastAPI service, and automatically falls back to `GeminiFreeAdapter` or `MockLlmAdapter` if the Python service is offline or fails.
* **Agent Integration**: Wired the new adapter into all three AI agents as the primary gateway:
  - `CustomerSupportAgent`
  - `B2BProcurementAgent`
  - `DynamicPricingAgent` (along with constructor mockup updates in [DynamicPricingAgentTest.java](file:///Users/muneeb/Documents/GitHub/Swish_App-1/backend/src/test/java/ch/swissqcommerce/backend/service/DynamicPricingAgentTest.java)).

---

## 🧪 Verification & Testing

### 1. Python Governance Tests
All **50 tests** pass successfully inside the `homelab-ai-governance` virtual environment:
```bash
cd homelab-ai-governance
.venv/bin/pytest
```
**Result**:
`50 passed in 0.14s`

### 2. Spring Boot Backend Tests
All **262 tests** (including 7 new test cases under `PythonGovernanceAdapterTest`) pass successfully:
```bash
cd backend
mvn clean test
```
**Result**:
`Tests run: 262, Failures: 0, Errors: 0, Skipped: 0`
`BUILD SUCCESS`

---

## 🚀 How to Run locally

1. **Activate Environment and Run Python Server**:
   ```bash
   cd homelab-ai-governance
   .venv/bin/governance --port 5000
   ```
2. **Start the Backend**:
   ```bash
   export SWISH_GOVERNANCE_API_URL=http://localhost:5000
   cd backend
   mvn spring-boot:run
   ```
3. **Trigger B2B Negotiation or support requests**:
   - `POST /api/agent/chat` (Customer Support)
   - `POST /api/agent/negotiate` (B2B Procurement RFQ)
   - `POST /api/agent/price-recommendation` (Dynamic Pricing)

All requests will route through the governed Python pipeline with full semantic routing, rate-limiting, and guardrail checks active.
