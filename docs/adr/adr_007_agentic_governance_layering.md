# ADR-007: Agentic Governance — Layering & Fail-Safe Fallback

- **Status:** Accepted (decision); implementation owned by the R5 author
- **Date:** 2026-06-11
- **Author:** AI Agentic Architect review of the merged R5 integration
- **Relates:** [ADR-001 Hexagonal](./adr_001_hexagonal_architecture.md), [AS_BUILT_VS_TARGET R5](../AS_BUILT_VS_TARGET.md)

## Context

The agentic system is now two layers:
- **Java orchestration** (`MasterOrchestratorService` + `CustomerSupportAgent` /
  `B2BProcurementAgent` / `DynamicPricingAgent`) — cost-budget guardrail ($5/day,
  100 req/hr), HITL escalation, business logic (RFQ auction, refund autopilot).
- **Python governance** (`homelab-ai-governance`) — PII pre-scan ("never leaves
  the homelab"), semantic model routing, input/output guardrails, rate limiting.

R5 wired the Python layer in via `LlmGatewayPort`, with each agent selecting a
gateway through a fallback chain: **`PythonGovernanceAdapter` → `GeminiFreeAdapter`
(raw cloud Gemini) → `MockLlmAdapter`**.

Architectural review found three defects:

1. **🔴 Governance-bypassing fallback (security).** `GeminiFreeAdapter` calls
   Google `generativelanguage.googleapis.com` directly — no PII scan, no
   guardrails. The Python adapter falls back to it whenever the URL is unset
   (the **default**: `SWISH_GOVERNANCE_API_URL` empty in dev/CI/most envs) or the
   service is unreachable. There is **no Java-side PII pre-scan**. Result:
   PII-bearing prompts reach ungoverned cloud — the exact thing the governance
   layer exists to prevent. It **fails open, not safe**.
2. **🟡 Hexagonal violation.** The three agents (in `core/service`) depend on
   **concrete adapters** (`PythonGovernanceAdapter`, `GeminiFreeAdapter`,
   `MockLlmAdapter`) and embed the gateway-selection logic in the core. ADR-001
   requires core to depend on the **port** (`LlmGatewayPort`).
3. **🟡 Dual rate-limiting.** Request-rate is limited in both layers (Java
   `hourlyRequestCount` and the Python `RateLimiter`) with no defined owner.

## Decision

1. **Responsibility split (single owner each).**
   - *Java orchestration* owns **business governance**: cost budget, HITL,
     business rules.
   - *Python governance* owns **AI-safety governance**: PII gate, model routing,
     content guardrails, request-rate limiting.
   - Remove the duplicate request-rate cap from Java (keep cost-budget there);
     rate is Python's. They become orthogonal (cost vs rate), not redundant.

2. **🔴 Fail SAFE, never fail OPEN (mandatory).** The gateway must never send
   ungoverned content to a **cloud** LLM. Required fallback order:
   `Python governance → local model / Mock → explicit "governance degraded"
   response or HITL`. If a cloud model is ever in the chain, a **Java-side PII
   pre-scan** (reuse the Python regex set) MUST gate it so PII-bearing prompts
   are blocked from cloud even when the Python service is down. The current
   `→ GeminiFreeAdapter` (raw cloud) fallback is **prohibited** until gated.

3. **Hexagonal correctness.** Agents depend only on `LlmGatewayPort`. A single
   `ResilientLlmGateway` composite adapter encapsulates the fallback chain +
   `isConfigured()` selection. This removes the 3-concrete-adapter coupling from
   all three core agents and centralises fail-safe logic in one place.

## Consequences

- **+** The "PII never leaves the homelab" guarantee actually holds, including on
  outage and in the default (unconfigured) state.
- **+** Clean hexagonal boundary; one place owns fallback; clear layer ownership.
- **−** The default dev/CI path must change from "raw Gemini" to "Mock / local",
  so local behaviour without the Python service is governed-or-mock, not cloud.
- **Implementation** belongs to the R5 author (this reviewer is scoped to ≤R4);
  defect #1 is security-critical and should be prioritised.
