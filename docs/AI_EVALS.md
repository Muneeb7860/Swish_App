# Swish OS — AI Evaluation & Integration Guide

How the agentic AI stack is composed, how each agent performs, how we evaluate
them, and how to run the evals + integration tests end-to-end.

> **Standing policy:** free/local LLMs only — local via **Ollama**, plus
> **free-tier** OpenAI-compatible APIs (Groq, OpenRouter, Gemini). No paid
> models (Opus/GPT). Enforced by a **$5/day budget guardrail (ADR-007)**.

---

## 1. AI infrastructure — the three layers

```
┌───────────────────────────────────────────────────────────────────────┐
│ Spring Boot backend (Java)                                              │
│   Agents ──> MasterOrchestratorService ──> AgentToolExecutor           │
│                        │                         │                      │
│                        │ (governed LLM calls)    │ (tool calls)         │
│                        ▼                          ▼                      │
│            ResilientLlmGateway            domain services               │
│             (LlmGatewayPort)              (orders, pricing, routing)     │
│                 │        │                                               │
│    cloud frontier│        │local Ollama fallback                        │
│                 ▼        ▼                                               │
│         PythonGovernanceAdapter ──HTTP──> FastAPI governance service    │
│         AgentBudgetTracker (ADR-007)      LettaMemoryService (stateful)  │
└───────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────────────────────────────────────────┐
│ homelab-ai-governance (Python / FastAPI)                                │
│   SLM intent classifier (qwen2.5:3b) ──> Semantic Router (0.60 conf)    │
│   Guardrails (PII detect + enforce)  ──> Agent registry (Ollama/Groq)   │
│   Evaluator (quality gates + self-correction)  RAG mesh (pgvector)      │
└───────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────────────────────────────────────────┐
│ LLM providers:  Ollama (local, CPU)   |   Groq free-tier   |   Letta    │
│   qwen2.5:3b/7b · gemma3:4b · mistral · deepseek-coder · llama-3.3-70b   │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 2. Agent inventory

### 2a. Suggestion agents (`backend/.../agent/`) → `AgentOrchestrator`
Produce advisory `AgentSuggestion`s persisted to `oltp.agent_suggestion`
(recommendation is a **jsonb** payload) and routed through the HITL/policy layer.

| Agent | Domain | Purpose |
|---|---|---|
| `SupportAgent` | support | Customer-support triage & response suggestions |
| `PricingAgent` | pricing | Price / surge / discount recommendations |
| `RoutingAgent` | logistics | Order → store/rider routing (SLA, multi-package) |
| `RiskAgent` (FraudAgent) | risk | Fraud / anomaly detection → hold-order suggestions |
| `OpsAgent` | ops | Operational / inventory recommendations |

### 2b. Conversational / tool-using agents (`domain/agent/core/service/`)
Hexagonal agents driven by `MasterOrchestratorService`, with inter-agent
delegation ("agent mesh") and per-hop token cost metering.

| Agent | Role | Tools / delegation | LLM |
|---|---|---|---|
| `CustomerSupportAgent` | Front-line support | `ORDER_STATUS`, `DYNAMIC_PRICING` (delegates to pricing) | governed gateway |
| `DynamicPricingAgent` | Pricing engine | competitorPrice / vipDensity inputs → price | governed gateway |
| `B2BProcurementAgent` | Wholesale procurement | Multi-wholesaler RFQ / negotiation (Temporal workflow) | governed gateway + Letta memory |

### 2c. Orchestration & infra services
| Component | Responsibility |
|---|---|
| `MasterOrchestratorService` | Routes requests, accumulates token cost, enforces ADR-007 budget, falls back to deterministic bids on budget breach |
| `AgentToolExecutor` | Executes tools, returns `ToolResult{content, cost}`, parse-guards malformed LLM args |
| `ResilientLlmGateway` (`LlmGatewayPort`) | Governed LLM calls: **cloud frontier → local Ollama fallback** on failure |
| `PythonGovernanceAdapter` | HTTP bridge to the FastAPI governance service (5s connect / 10s read timeout) |
| `AgentBudgetTracker` | Daily $5 spend ledger; blocks over-budget LLM calls |
| `LettaMemoryService` | Stateful multi-turn agent memory (auto-provisions agents, resilient fallback to direct LLM) |

### 2d. Governance agent registry (`routing_config.yaml`)
| Registry key | Backend | Model | Role |
|---|---|---|---|
| `gemma_reasoner` | Ollama | gemma3:4b | reasoner (fallback) |
| `mistral_summarizer` | Ollama | mistral:latest | summarizer |
| `deepseek_coder` | Ollama | deepseek-coder:latest | coder |
| `cloud_frontier` | Groq (free) | llama-3.3-70b-versatile | frontier |
| `letta_support` | Letta | ollama/qwen2.5:7b | stateful support |

---

## 3. Current performance

### 3a. SLM intent classifier (the routing brain) — selected via 5-model benchmark
Dataset: **63 queries across 9 intents**. Targets: **Intent Accuracy ≥ 85%**,
**Keyword Fallback ≤ 5%**. Source: `homelab-ai-governance/benchmarks/results/benchmark_report.md`.

| Model | JSON | Intent Acc | Fallback | False Refusal | p50 | p95 | Score |
|---|---|---|---|---|---|---|---|
| **qwen2.5:3b ⭐** | 100% | **90.5%** ✅ | 0.0% ✅ | 0.0% | **1063ms** | 1234ms | **0.843** |
| mistral:7b | 100% | 88.9% | 0.0% | 0.0% | 1505ms | 1759ms | 0.833 |
| qwen2.5:7b | 100% | 84.1% ❌ | 0.0% | 0.0% | 2055ms | 2487ms | 0.805 |
| gemma3:4b | 100% | 79.4% ❌ | 0.0% | 0.0% | 3445ms | 3574ms | 0.776 |
| phi3:mini | 74.6% | 61.9% ❌ | 25.4% ❌ | 25.0% | 2660ms | 21110ms | 0.570 |

**Selected: `qwen2.5:3b`** — only model meeting both accuracy and fallback
targets, and fastest (p50 ~1.06s on CPU). Weakest intents: `summarization`
(71%) and `general_knowledge` (71%) — watch on re-benchmark.

### 3b. Response-quality evaluator (per governance output)
Weighted quality score, **passing threshold 0.75**, up to **3 self-correction
retries** (`routing_config.yaml → evaluation_rules`):

| Metric | Weight |
|---|---|
| format_integrity | 0.40 |
| completeness | 0.30 |
| context_conservation | 0.30 |

---

## 4. How we evaluate — methodology

| Layer | What's measured | Mechanism | Pass bar |
|---|---|---|---|
| **Classifier** | intent accuracy, JSON compliance, fallback rate, latency | `slm_benchmark.py` vs `dataset.json` | acc ≥85%, fallback ≤5% |
| **Response quality** | completeness, format, context retention | evaluator + self-correction loop | score ≥0.75 |
| **Guardrails** | PII leakage, unsafe-content refusal | detectors + enforcer (pytest) | 0 leaks; correct refusals |
| **Budget** | daily USD spend, hourly req rate | `AgentBudgetTracker` (ADR-007) | ≤ $5/day, ≤100 req/hr |
| **Resilience** | behaviour when cloud/Ollama/Letta offline | fallback chains (unit + integration) | graceful degrade, no crash |
| **Agent logic** | tool routing, cost metering, mesh delegation | backend `*AgentTest` + `AgentEvalIntegrationTest` | assertions green |
| **HITL** | low-confidence / high-impact escalation | governance queue + `@PreAuthorize` admin gate | routed to queue, admin-only |

### Test assets
- **Classifier eval:** `homelab-ai-governance/benchmarks/slm_benchmark.py` + `dataset.json`
- **Governance suite (74 tests):** `homelab-ai-governance/tests/` — `test_router/` (decision table, PII scan, memory mesh, server metrics), `test_guardrails/` (detectors, enforcer, loader), `test_evaluator/test_metrics.py`, `test_pipeline.py`, `test_letta_agent.py`, `test_governance_contract.py`
- **Backend agent evals:** `AgentEvalIntegrationTest`, `MasterOrchestratorServiceTest`, `DynamicPricingAgentTest`, `FraudAgentE2ETest`, `RoutingAgentIntegrationTest`, `AgentControlPlaneIntegrationTest`, `RewardsAndGovernanceIntegrationTest`, `GovernanceServiceIdempotencyTest`

---

## 5. Running the evals

### Prereqs
```bash
ollama serve                       # local LLMs (qwen2.5:3b required; 0.5b for tiny tasks)
ollama pull qwen2.5:3b
# optional cloud frontier (free tier):
export GROQ_API_KEY=<free-tier-key>
```

### 5a. Classifier benchmark (re-run / re-select model)
```bash
cd homelab-ai-governance
./.venv/bin/python benchmarks/slm_benchmark.py
#   → refreshes benchmarks/results/benchmark_report.md + benchmark_raw.json
```

### 5b. Governance test suite (guardrails, router, evaluator, pipeline)
```bash
cd homelab-ai-governance
./.venv/bin/python -m pytest -q         # 74 tests; expect green in <10s solo
# NOTE: run SOLO — never concurrent with Ollama inference or another suite
#       (memory contention on the 16GB Mac mini inflates a <10s run to 40min).
```

### 5c. Backend agent + orchestration evals
```bash
cd backend
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./mvnw test -Dtest='*Agent*Test,*Orchestrat*Test,AgentEvalIntegrationTest,FraudAgentE2ETest'
# Postgres-backed integration tests need Testcontainers + Colima socket:
#   DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
#   TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

### 5d. Governance quality gate is also enforced in CI
`AI Governance Quality Gate (Python)` runs the pytest suite on every PR.

---

## 6. Agent-level eval scenarios (the eval matrix)

Concrete cases a reviewer/CI should assert per agent. Extend `dataset.json`
(classifier) and the backend `*AgentTest` classes with these.

| ID | Agent | Scenario | Expected |
|---|---|---|---|
| AE-01 | Classifier | 9-intent labelled set | ≥85% accuracy, 100% JSON, ≤5% fallback |
| AE-02 | CustomerSupport | "where is my order?" | routes `ORDER_STATUS` tool, grounded reply |
| AE-03 | CustomerSupport | pricing question | delegates to `DYNAMIC_PRICING`, cost metered |
| AE-04 | CustomerSupport | garbled LLM tool args | parse-guard → safe default, no exception |
| AE-05 | DynamicPricing | surge inputs (competitorPrice, vipDensity) | bounded price, no runaway |
| AE-06 | B2BProcurement | multi-vendor RFQ | picks cheapest **active** acceptable vendor; skips inactive |
| AE-07 | B2BProcurement | daily budget hit mid-negotiation | deterministic 10% discount fallback (no budget breach) |
| AE-08 | RiskAgent | fraud-signal order | hold-order suggestion → HITL queue |
| AE-09 | Routing | SLA-tight / multi-package order | valid store+rider assignment within SLA |
| AE-10 | Any | cloud LLM offline | falls back to local Ollama, request still served |
| AE-11 | Any | Ollama + cloud both offline | graceful error, no crash, logged |
| AE-12 | Letta | multi-turn context | prior turn recalled; offline → direct-LLM fallback |
| AE-13 | Guardrails | PII in prompt/response | detected + enforced (redact/refuse) |
| AE-14 | Budget | >100 req/hr or >$5/day | blocked with clear signal |
| AE-15 | HITL | low-confidence (<0.60) suggestion | escalated to admin queue, non-admin 403 |

---

## 7. AI integration testing (full stack)

End-to-end path: **backend agent → governance HTTP → Ollama/Groq → back**,
exercised with all sidecars up. Bring the stack up with `run_demo.sh` (local)
or `demo/start.sh` (tunnel), plus:

```bash
ollama serve && ollama pull qwen2.5:3b            # classifier + local agents
cd homelab-ai-governance && ./.venv/bin/uvicorn governance.server:app --port 8000 &
# (optional) Letta on :8283 for stateful memory; postgres-letta pgvector for RAG
```

Integration smoke checks:
1. Backend `PythonGovernanceAdapter` reaches governance `/` health.
2. A support query flows agent → governance → classifier → agent registry → reply.
3. Kill Ollama mid-request → `ResilientLlmGateway` cloud/local fallback holds.
4. Kill governance service → backend degrades gracefully (offline fallback), no 500 cascade.
5. Budget ledger increments; breach triggers deterministic fallback.

---

## 8. Known gaps / roadmap

- **Governance lib ~95% built** — Phase-5 **MinHash dedup** is the remaining gap.
- **Agent-level eval automation** is thinner than the classifier benchmark —
  the AE-02…AE-15 matrix above should be codified into repeatable harnessed
  evals (backend `*AgentTest` + governance eval fixtures) with tracked scores.
- **Re-benchmark cadence:** re-run `slm_benchmark.py` whenever the classifier
  prompt (`system_prompts/classifier_v2.txt`) or model changes; watch the two
  71% intents (`summarization`, `general_knowledge`).
- **Letta / RAG** paths need live-dependency integration coverage (currently
  unit-tested with resilient offline fallbacks).
