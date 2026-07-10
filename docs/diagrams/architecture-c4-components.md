# C4 Component Diagrams — Whole Project (completion)

Completes the C4 **Level 3 (Component)** coverage. The existing
[`architecture-diagrams.md`](./architecture-diagrams.md) carries L1 (Context),
L2 (Container), and one L3 diagram (Payment Service Internals). This document
adds the missing L3 views for the two containers that actually run the bulk of
the system:

1. the **backend modular monolith** (the 22 hexagonal bounded contexts), and
2. the **AI Governance Platform** (`homelab-ai-governance`) — the distributed
   hybrid-agentic layer, previously absent from every diagram.

> Reality note: L1/L2 in `architecture-diagrams.md` describe a *target*
> database-per-service microservices topology. As-built, the 22 contexts run in
> a **single `backend` deployable** (modular monolith, schemas `oltp/olap/
> dispatch/wholesaler`) alongside a few thin services and the new Python
> governance library. These component diagrams reflect the as-built code.

---

## L3 — Backend Modular Monolith (`backend`, port 8083)

Every context follows the same hexagonal shape: an inbound web adapter → a
use-case **port (in)** → a `core/service` → outbound **ports (out)** →
persistence/event adapters. Contexts are grouped by cluster; cross-context calls
go through ports, never directly into another context's core.

```mermaid
flowchart TB
  subgraph Inbound["Inbound Adapters (REST /api/v1/**)"]
    direction LR
    WebControllers["Domain Controllers<br/>(Customer, Order, Catalog, Payment,<br/>Rider, Wholesaler, Agent, Governance...)"]
    JwtFilter["JwtAuthenticationFilter"]
    Opa["OpaAuthorizationManager<br/>(+ deny-by-default fallback)"]
  end

  subgraph Core["Core / Service layer (per bounded context)"]
    direction TB
    subgraph Commerce["Commerce"]
      OrderSvc["OrderServiceImpl (checkout/refund)"]
      CatalogSvc["CatalogServiceImpl"]
      PricingSvc["PricingServiceImpl"]
      InvSvc["InventoryServiceImpl"]
    end
    subgraph Finance["Finance"]
      LedgerSvc["LedgerServiceImpl<br/>(double-entry + hash chain)"]
      PaymentSvc["Payment core"]
    end
    subgraph Logistics["Logistics"]
      SagaMgr["OrderSagaManager"]
      DispatchSvc["DispatchServiceImpl"]
      RiderSvc["RiderServiceImpl (3-gate)"]
      TelemetrySvc["TelemetryServiceImpl (CQRS)"]
    end
    subgraph Agentic["Agent & Governance"]
      Master["MasterOrchestratorService<br/>(HITL + budget guardrail)"]
      Procure["B2BProcurementAgent + Guardrails"]
      Govern["GovernanceServiceImpl<br/>(HITL override, RSA sign)"]
    end
    subgraph Platform["Auth / Reward / Eventing"]
      AuthSvc["AuthServiceImpl + TokenServiceAdapter"]
      RewardSvc["RewardServiceImpl (factory)"]
      OutboxSched["OutboxEventScheduler"]
    end
  end

  subgraph Outbound["Outbound Adapters (ports/out)"]
    direction LR
    Jpa["JPA Persistence Adapters"]
    KafkaTmpl["KafkaTemplate (outbox relay)"]
    RedisGeo["Redis (cache + geo + sessions)"]
    LlmPort["LLM adapter (Spring AI: Gemini/Ollama)"]
  end

  subgraph Stores["Datastores"]
    PG["PostgreSQL<br/>oltp / olap / dispatch / wholesaler"]
    RD["Redis"]
    KFK["Kafka (+ DLQ)"]
  end

  WebControllers --> JwtFilter --> Opa --> Core
  Commerce --> Jpa
  Finance --> Jpa
  Logistics --> Jpa
  Platform --> Jpa
  Agentic --> LlmPort
  OrderSvc -->|"posts legs"| LedgerSvc
  OrderSvc -->|"order.placed"| OutboxSched
  Master -->|"escalates"| Govern
  OutboxSched --> KafkaTmpl --> KFK
  TelemetrySvc --> RedisGeo
  Jpa --> PG
  RedisGeo --> RD
```

Key cross-context invariants visible above: checkout posts to the ledger
(balance + hash-chain triggers in the DB), writes to the outbox (relayed to
Kafka by the scheduler), and the agent escalates low-confidence/over-budget work
to governance HITL. Authorization is layered: filter → OPA → method-level
`@PreAuthorize`/`assertOwnership`.

---

## L3 — AI Governance Platform (`homelab-ai-governance`)

A standalone Python service (not yet wired into the Java backend) implementing
**distributed hybrid-agentic governance**: a semantic router fans queries across
local **Ollama** models and a **cloud frontier** model, wrapped in guardrails,
PII-gated local routing, a recursive self-correction loop, and audit. Mirrors
the Java agent's budget guardrail ($5/day, 100 req/hr).

Source: `homelab-ai-governance/src/governance/pipeline.py` (`execute_pipeline`).

```mermaid
flowchart TB
  Query["Incoming query"] --> Pipeline

  subgraph Pipeline["Governance Pipeline (execute_pipeline)"]
    direction TB
    Rate["RateLimiter<br/>(sliding window)"]
    PII["PII Scanner<br/>→ forces local_only"]
    Ctx["Context Enrichment<br/>(MemoryMesh + ContextConstructor)"]
    Classify["Intent Classifier<br/>(gemma3:4b, 9 intents × complexity)"]
    Token["Token Validator<br/>(<= 8k context / 4k output)"]
    Route["Decision Table Router<br/>(intent × complexity → agent)"]
    GuardIn["Guardrail Enforcer (input)<br/>redact / block / strip / warn"]
    Infer["Model Inference<br/>(+ context-isolation prompt)"]
    GuardOut["Guardrail Enforcer (output)"]
    Loop["Self-Correction Loop<br/>(<=3 retries, threshold 0.75)"]
    Sanitize["Telemetry-tag Sanitizer"]
  end

  subgraph Agents["Hybrid Agent Backends"]
    direction LR
    Gemma["gemma_reasoner<br/>(ollama gemma3:4b, fallback)"]
    Mistral["mistral_summarizer<br/>(ollama mistral:7b)"]
    Deepseek["deepseek_coder<br/>(ollama deepseek-coder-v2)"]
    Cloud["cloud_frontier<br/>(Groq llama-3.3-70b)"]
  end

  subgraph Support["Cross-cutting"]
    direction LR
    Audit["Audit Logger"]
    Detectors["Detectors<br/>(PII, profanity, hate-speech)"]
    Cfg["routing_config.yaml<br/>(agents, budget, eval weights)"]
  end

  Rate --> PII --> Ctx --> Classify --> Token --> Route --> GuardIn --> Infer
  Infer --> GuardOut --> Loop --> Sanitize --> Response["Clean response + routing/loop metadata"]

  Route -.->|"local_only ⇒ Ollama only"| Agents
  Infer --> Agents
  Loop -.->|"escalate on fail"| Gemma
  GuardIn -.-> Detectors
  GuardOut -.-> Detectors
  Pipeline -.-> Audit
  Route -.-> Cfg
```

**Hardening controls (Phase 9):** rate limiting, PII-driven local-only routing,
input+output guardrails, prompt-injection-resistant context isolation, recursive
self-correction with a local Gemma fallback, and full audit logging.

---

## As-Built Container Addendum

The L2 container diagram in `architecture-diagrams.md` predates the AI governance
layer. As-built, add:

| Container | Tech | Role |
| :--- | :--- | :--- |
| `homelab-ai-governance` | Python (pytest) | Hybrid-agentic router + guardrails + eval |
| Ollama runtime | local LLMs | gemma3:4b · mistral:7b · deepseek-coder-v2 |
| Groq API | cloud LLM | `cloud_frontier` frontier model |
| `backend` | Spring Boot (8083) | The 22 hexagonal contexts (monolith) |

**Integration gap (open):** `homelab-ai-governance` is standalone — no call path
from the Java `backend` (`MasterOrchestratorService`) into the Python governance
pipeline yet. Wiring those two agentic layers together is the natural next step
of the distributed hybrid-agentic build.

---

## C4 coverage status

| Level | Coverage |
| :--- | :--- |
| L1 Context | ✅ `architecture-diagrams.md` |
| L2 Container | ✅ `architecture-diagrams.md` + addendum above (AI governance) |
| L3 Component — Payment | ✅ `architecture-diagrams.md` |
| L3 Component — Backend monolith | ✅ this doc |
| L3 Component — AI Governance Platform | ✅ this doc |
| L4 Code | ▫ intentionally omitted (covered by the sequence diagrams) |
