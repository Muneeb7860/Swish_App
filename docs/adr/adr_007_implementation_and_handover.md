# ADR-007: Agentic Governance Implementation Plan, Task List, and Handover

This document compiles the **Implementation Plan**, **Task List**, and **Handover Report** for the implementation of **ADR-007: Agentic Governance — Layering & Fail-Safe Fallback**.

The implementation is **100% complete and verified** on the Windows development workstation. This document serves as the guide for the sync and verification of these changes on the `Mac_Machine` environment.

> [!IMPORTANT]
> **Reconciliation note (Mac_Machine, 2026-06-13).** This ADR-007 implementation is **already integrated on `Mac_Machine`** — `ResilientLlmGateway`, `PiiPreScanner`, and their tests are **byte-for-byte identical** across `Mac_Machine` and `Windows_Machine`, and the full backend suite is green at **302 tests** (grown from the 269 cited in §3 after Phases 8A/8B landed). **No code sync is pending;** the Windows → Mac sync steps in §3 are retained as a historical record only.
>
> **Actual file layout vs this plan.** §1–§2 reference `adapter/out/resilient/ResilientLlmGateway.java` with an *inline* PII scanner — that was the original plan. The integrated code instead places the gateway at **`adapter/out/resilient/ResilientLlmGateway.java`** and extracts the scanner into a dedicated **`adapter/out/pii/PiiPreScanner.java`** (cleaner single-responsibility split). The *design* — Python governance → Java PII gate → Gemini → Mock, failing **safe** to the HITL queue on PII — is implemented exactly as described below.

---

## 📖 1. Implementation Plan

### Context & Objectives
ADR-007 addresses three primary defects identified in the AI orchestration and safety stack:
1. **🔴 Governance-bypassing fallback (Security)**: Preventing prompts containing PII from bypassing governance and reaching the Gemini cloud LLM when the Python Governance service is down or unconfigured.
2. **🟡 Hexagonal violation (Architecture)**: Direct coupling of core agents to concrete LLM adapters. Core agents should only depend on the `LlmGatewayPort` interface.
3. **🟡 Dual rate-limiting (Redundancy)**: Dual request rate limits enforced at both the Java (100 req/hr) and Python (RateLimiter) levels.

### Architecture Design
We introduced a composite wrapper pattern: the `ResilientLlmGateway`.

```
                    ┌──────────────────────────┐
                    │    Core Agents (Core)    │
                    │ - CustomerSupportAgent   │
                    │ - DynamicPricingAgent    │
                    │ - B2BProcurementAgent    │
                    └────────────┬─────────────┘
                                 │
                                 ▼
                    ┌──────────────────────────┐
                    │     LlmGatewayPort       │
                    └────────────┬─────────────┘
                                 │
                                 ▼
                    ┌──────────────────────────┐
                    │   ResilientLlmGateway    │◄─── [Primary Bean]
                    │      (Composite)         │
                    └──────┬────────────┬──────┘
            ┌──────────────┘            └──────────────┐
            │ (If Configured)                          │ (Fallback / Gated)
            ▼                                          ▼
┌────────────────────────┐                 ┌────────────────────────┐
│PythonGovernanceAdapter │                 │   GeminiFreeAdapter    │
└────────────────────────┘                 │      (Cloud LLM)       │
                                           └───────────┬────────────┘
                                                       │ (If Clean & Configured)
                                                       ▼
                                           ┌────────────────────────┐
                                           │     MockLlmAdapter     │
                                           └────────────────────────┘
```

### Fallback Routing & PII Gating Logic
The routing workflow executed by the `ResilientLlmGateway` is as follows:
1. **Primary Route**: Check if `PythonGovernanceAdapter` is configured. If yes, forward the prompt.
   - If Python returns `success`, return the governed response.
   - If Python returns `blocked`, propagate the block response.
   - If Python is unreachable (throws exception), fall back to step 2.
2. **PII Verification Gate**: If routing falls back to Gemini (cloud) or Python is unconfigured:
   - Run a Java-side PII regex scan (matching Python's PII rules: Email, Credit Card, IP, Connection String, SSN, API Key, Phone Number).
   - **If PII is present**: Block cloud request immediately. Return an explicit degraded response (`confidence: 0.0`) which routes the interaction directly to the human-in-the-loop (HITL) queue.
   - **If PII is absent**: Safely call the `GeminiFreeAdapter` if configured.
3. **Final Fallback**: If Gemini is unconfigured or fails, route to `MockLlmAdapter`.

---

## 📝 2. Task List

All task items have been implemented and checked off as part of the Windows workstation development cycle:

- [x] **Task 1: Create Resilient Gateway Adapter**
  - [x] Create [ResilientLlmGateway.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/adapter/out/resilient/ResilientLlmGateway.java) implementing `LlmGatewayPort`.
  - [x] Mark with `@Primary` to intercept all injections of the port.
  - [x] Implement the Java-side regex PII scanner using Python-identical regex patterns.
  - [x] Program the fallback logic chain (Python Governance $\to$ local PII Gate $\to$ Gemini $\to$ Mock).

- [x] **Task 2: Refactor Core Agents to depend on LlmGatewayPort**
  - [x] Refactor [CustomerSupportAgent.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/CustomerSupportAgent.java).
  - [x] Refactor [DynamicPricingAgent.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/DynamicPricingAgent.java).
  - [x] Refactor [B2BProcurementAgent.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/B2BProcurementAgent.java).
  - [x] Delete all legacy `getLlmGateway()` methods from the core agents.

- [x] **Task 3: Refactor PythonGovernanceAdapter**
  - [x] Remove fields, constructor args, and autowiring for `GeminiFreeAdapter` and `MockLlmAdapter` from [PythonGovernanceAdapter.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/adapter/out/governance/PythonGovernanceAdapter.java).
  - [x] Ensure it throws exceptions on failure/unconfigured states rather than carrying out direct fallback calls.

- [x] **Task 4: Remove Redundant Rate Limiter Check**
  - [x] Modify [MasterOrchestratorService.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/MasterOrchestratorService.java) to delete the `hourlyRequestCount >= 100` restriction.
  - [x] Preserve daily budget cost accumulation (`dailyCost >= 5.0`) for HITL routing.

- [x] **Task 5: Update Unit and Integration Test Suites**
  - [x] Adjust [DynamicPricingAgentTest.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/test/java/ch/swissqcommerce/backend/service/DynamicPricingAgentTest.java) to mock `LlmGatewayPort` directly.
  - [x] Rewrite [PythonGovernanceAdapterTest.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/test/java/ch/swissqcommerce/backend/domain/agent/adapter/out/governance/PythonGovernanceAdapterTest.java) to reflect exception throwing instead of internal fallbacks.
  - [x] Create [ResilientLlmGatewayTest.java](file:///c:/Users/DELL%209420/Documents/swiss_App/backend/src/test/java/ch/swissqcommerce/backend/domain/agent/adapter/out/resilient/ResilientLlmGatewayTest.java) to thoroughly test PII detection, routing paths, and fail-safe blocking.
  - [x] Verify complete integration test suite passes via Maven.

---

## 🚚 3. Handover Instructions (Windows $\to$ Mac)

### Current Code Status
All code changes are located on the local Windows branch `develop`. A `git status` check lists the following modifications:

```bash
Changes not staged for commit:
  modified:   backend/src/main/java/ch/swissqcommerce/backend/domain/agent/adapter/out/governance/PythonGovernanceAdapter.java
  modified:   backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/B2BProcurementAgent.java
  modified:   backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/CustomerSupportAgent.java
  modified:   backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/DynamicPricingAgent.java
  modified:   backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/MasterOrchestratorService.java
  modified:   backend/src/test/java/ch/swissqcommerce/backend/domain/agent/adapter/out/governance/PythonGovernanceAdapterTest.java
  modified:   backend/src/test/java/ch/swissqcommerce/backend/service/DynamicPricingAgentTest.java

Untracked files:
  backend/src/main/java/ch/swissqcommerce/backend/domain/agent/adapter/out/resilient/ResilientLlmGateway.java
  backend/src/test/java/ch/swissqcommerce/backend/domain/agent/adapter/out/resilient/ResilientLlmGatewayTest.java
```

### Verification & Validation Results
We executed the verification test run on the Windows workstation:
```bash
cmd /c .\apache-maven-3.9.6\bin\mvn.cmd clean test
```
**Output Summary**:
- **Result**: `BUILD SUCCESS`
- **Tests Run**: 269
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0

### Sync & Run Guide for the Mac Agent

#### Step 1: Commit and Push Changes on Windows
Commit the changes on the Windows machine and push to `origin/develop`:
```bash
git add .
git commit -m "feat(agentic): implement ADR-007 composite resilient gateway with PII filter and hexagonal decoupling"
git push origin develop
```

#### Step 2: Retrieve Changes on Mac
Checkout and pull the branch on the `Mac_Machine` environment:
```bash
git fetch origin
git checkout develop
git pull origin develop
```

#### Step 3: Run the Test Suites on Mac
With a live Python and Ollama instance running on the Mac machine, execute:
```bash
# Run Maven tests to verify Java build compile and tests pass
./mvnw clean test

# Run the complete Python Governance test suite
cd homelab-ai-governance
pytest tests/ -v
```

#### Step 4: Validate Live API E2E
Start the Python Governance API and execute the Spring Boot backend with the configured environment:
```bash
# Start Python API
cd homelab-ai-governance
python -m src.governance.cli --port 5000

# In a new terminal, run the Java Backend
export SWISH_GOVERNANCE_API_URL=http://localhost:5000
cd backend
./mvnw spring-boot:run
```
Validate that prompts containing PII are correctly intercepted and blocked.
