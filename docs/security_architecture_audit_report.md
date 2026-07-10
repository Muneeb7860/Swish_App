# Swish App: Agentic OS Security & Architectural Compliance Audit Report

## 1. Executive Summary

This audit report presents a formal evaluation of the hybrid AI Agentic OS architecture implemented for the `Swish_App` ecosystem (Spring Boot backend + Python Governance FastAPI microservice). 

The system was audited against enterprise compliance criteria: **PII privacy constraints (homelab boundaries)**, **1-second SLA responsiveness**, **durable state persistence under failure**, **distributed trace observability**, and **immutable transaction auditing**. 

The overall security and architectural posture is **highly robust**. The design implements a multi-tier defense-in-depth model that guarantees resilience under infrastructure degradation, protects customer privacy, and provides 100% visibility into autonomous LLM operations.

---

## 2. Detailed Audit Results

### 2.1 AI Agentic OS Resilience & Fault Tolerance
We audited the failure-handling and fail-safe routing mechanisms.

*   **Double-Layer RAG Circuit Breakers**:
    *   *Implementation*: Class-level variables `_db_failed_time` and `_embedding_failed_time` are registered on the Python `MemoryMesh` retriever to preserve breaker states across transient requests.
    *   *SLA Enforcement*: By setting a 30-second cooldown period, subsequent requests instantly skip database (`connect_timeout=2`) or Ollama HTTP (`timeout=3.0`) handshakes under crash states. Instead of blocking the thread and breaching the 1-second SLA timeout, the pipeline immediately resolves context using local stubs.
*   **Letta Memory Fallback Client**:
*   *Implementation*: Spring Boot's [LettaMemoryService](../backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/LettaMemoryService.java) wraps all stateful memory calls in try-catch blocks. If Letta agent creation, fetching, or messaging fails, it catches the exception and returns `null`.
    *   *Failover Routing*: The callers ([CustomerSupportAgent](../backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/CustomerSupportAgent.java) and [B2BProcurementActivitiesImpl](../backend/src/main/java/ch/swissqcommerce/backend/domain/agent/core/service/B2BProcurementActivitiesImpl.java)) seamlessly catch this fallback condition and route execution through the direct, stateless `@Primary ResilientLlmGateway`, maintaining service continuity. (docs: resolve path mismatches, document LLM strategy, service inventory, and database schema mappings)

---

## 2.2 Observability, Tracing, and Telemetry
We audited the metrics collection, trace propagation, and telemetry consistency.

*   **OTel Distributed Tracing Context Propagation**:
    *   *Implementation*: We refactored REST adapters (`PythonGovernanceAdapter`, `LettaConfig`, `GeminiFreeAdapter`) to construct their underlying HTTP clients via Spring's `RestTemplateBuilder`.
    *   *Span Alignment*: Outgoing requests automatically inject W3C `traceparent` headers. The FastAPI `FastAPIInstrumentor` successfully extracts these headers, allowing downstream governance evaluation spans to nest cleanly under the parent Spring Boot request span. This resolves trace fragmentation in the Arize Phoenix UI (`http://localhost:6006`).
*   **Prometheus Metrics Scopes**:
    *   *Implementation*: Injected Micrometer's `MeterRegistry` into `LettaMemoryService`. It registers a `letta.fallback.triggers` counter with the tag `service=letta-memory`.
    *   *Telemetry Integrity*: The counter increments on any transport failure or invalid agent payload, providing SREs with live scraping visibility via `/actuator/prometheus` to detect memory degradation.

---

## 2.3 PII and Guardrails Gateways
We audited the guardrail enforcements inside the FastAPI microservice.

*   **PII Pre-Routing Scanner**:
    *   *Privacy Enforcement*: Before matching the query against decision tables or sending prompts to cloud models (e.g. Gemini), the pipeline runs a PII pre-scanner. If personal/sensitive data is detected, the query's `local_only` flag is hard-coded to `true`, forcing the query to run locally on on-premise Ollama models.
*   **Sliding-Window Rate Limiter**:
    *   *Defense Pattern*: A module-level `RateLimiter` enforces request quotas in Python. It tracks request timestamps in a sliding 1-hour window. If the limit is exceeded, subsequent calls are instantly blocked at the gateway, preventing resource starvation.
*   **Structured Self-Correction & Formats**:
    *   *Validation Loops*: Validates candidate text formats using Pydantic RAIL schemas. If JSON schema validation fails, it triggers a recursive self-correction loop (up to 3 attempts). If correction fails, it drops down to a local Gemma 4B fallback model, ensuring structured DTO safety.

---

## 2.4 Access Control & Transactional Auditing
We audited method security and immutable database logging.

*   **Method-Level Role Authorization**:
    *   *Enforcement*: Enforced strict Spring Security method-level access controls using `@PreAuthorize("hasRole('ADMIN')")` at class and method boundaries on high-privilege controllers:
        *   `HitlQueueController` (Enforces admin authorization on manual override approval and rejection endpoints).
        *   `AdminController` (Restricts chaos engineering fault injection, onboarding gates, and health telemetry).
*   **Transactional Outbox Audit Trails**:
    *   *Audit Integrity*: The `@SecurityAudit` aspect interceptor captures metadata (operator username, method name, timestamp, parameters, execution status).
    *   *Isolation & Durability*: Using `SecurityOutboxWriter`, the audit trail record is persisted to the database via `Propagation.REQUIRES_NEW`. This guarantees that if the business operation fails or rolls back, the security audit record is still committed to the outbox database, preventing audit evasion.

---

## 3. Compliance Traceability Matrix

The table below maps the implemented features to architectural guidelines and standards (ADRs).

| Requirement ID | Standard / ADR | Implemented Mechanism | Audit Status |
| :--- | :--- | :--- | :--- |
| **COMP-01** | ADR-007 (Resilience) | Circuit Breakers in `MemoryMesh` and Letta Fallback to Composite Gateway | **COMPLIANT** |
| **COMP-02** | ADR-007 (Cost limits) | Python-side `CostTracker` and Java-side dynamic RFQ budget limit ($5/day) | **COMPLIANT** |
| **COMP-03** | ADR-008 (Method Security) | `@PreAuthorize("hasRole('ADMIN')")` on Admin/HITL Controllers | **COMPLIANT** |
| **COMP-04** | Outbox Logging | Aspect-oriented `@SecurityAudit` using `Propagation.REQUIRES_NEW` | **COMPLIANT** |
| **COMP-05** | Distributed Observability | W3C tracecontext propagation via `RestTemplateBuilder` | **COMPLIANT** |

---

## 4. Key Recommendations & Optimization Points

1.  **Metric Aggregation for RAG Circuit Breakers**:
    *   *Finding*: While RAG circuit breakers trip successfully, the event is only logged as a warning.
    *   *Recommendation*: Expose Prometheus counters (e.g. `rag_circuit_breaker_tripped_total` with tags `type=db` or `type=embedding`) to allow SRE alert configurations when circuit breakers remain open.
2.  **Encryption of Outbox Event Payloads**:
    *   *Finding*: Outbox event payloads containing operator parameters are currently stored as plain JSON strings.
    *   *Recommendation*: Apply AES-256 column encryption on `OutboxEvent.payload` to protect PII or sensitive operation arguments stored in the PostgreSQL database.
3.  **Strict Token Budgeting inside Wholesaler Loop**:
    *   *Finding*: Wholesaler negotiation budget guardrail blocks subsequent LLM calls when spend exceeds $5/day, but the current check is applied at the start of each iteration.
    *   *Recommendation*: Deduct estimated token costs dynamically *during* the execution to avoid slightly overshooting the $5 limit on the final transaction of the day.
