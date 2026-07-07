# Swish_App Enterprise Roadmap (TPO & Architect Vision)

As your Technical Product Owner (TPO) and Enterprise Architect, I am incredibly proud of where we are. Over the past few sprints, we have elevated `Swish_App` from a basic monolithic prototype into a deeply resilient, TypeScript-driven, Hexagonal, event-driven micro-platform.

Below is our current backlog status and road map.

---

## Epic 1: CI/CD & Automated Governance (The "DevOps" Epic) [COMPLETED]
*We have a robust Git branching strategy, automated pipelines, and quality gates on every PR.*

- [x] **GitHub Actions Pipeline**: Automated CI pipeline triggers on every push/PR to `develop`, `master`, and feature branches (`macbook_machine`, `mac-machine`). Jobs: backend quality (test + JaCoCo 75% coverage + Spotless + OWASP), all 5 frontend builds + ESLint + Biome lint, Cypress E2E, Trivy vulnerability scan, AI governance tests, secret scan (TruffleHog), branch protection, conventional commits enforcement, and semantic release.
- [x] **Automated Quality Gates**: Spotless (Google Java Format/AOSP) blocks backend PRs with style violations. JaCoCo enforces 75% code coverage minimum. Biome + ESLint block frontend PRs with lint errors. OWASP dependency-check flags CVEs ≥ CVSS 7. Trivy scans for CRITICAL/HIGH vulnerabilities on every push.
- [x] **Flyway DB Migrations**: Replaced static `schema.sql` boot script with Flyway for professional, version-controlled database schema evolution.

## Epic 2: Enterprise Observability (The "Day-2 Ops" Epic) [COMPLETED]
*We wired up Micrometer OpenTelemetry and Actuator, and now have full trace/metric visualization.*

- [x] **Prometheus & Grafana**: Deploy Prometheus to scrape our Spring Boot `/actuator/prometheus` metrics, and wire up a Grafana dashboard container to visualize live memory, CPU, and API traffic.
- [x] **Distributed Tracing UI**: Deploy Zipkin in `docker-compose` to ingest our OTEL traces, allowing us to visually trace order travel from BFF -> Kafka -> Backend -> Postgres.

## Epic 3: Scale & Reliability (The "Performance" Epic) [COMPLETED]
*We solved the N+1 database queries and locked down the edge, but we can squeeze out more performance.*

- [x] **Letta Memory Service Integration**: Routed stateful support conversations through a Letta agent server.
- [x] **Durable B2B Procurement Workflow**: Implemented Temporal-driven negotiations with multi-wholesaler RFQ auctions and automated guardrails escalating to a unified human-in-the-loop (HITL) supervisor override queue.
- [x] **Redis `@Cacheable`**: Implement Spring Cache for the Product/Inventory catalog.
- [x] **Kafka Dead Letter Queues (DLQ)**: If a Rider crashes or a Kafka event fails to process, we need a DLQ to catch the failed event so it isn't lost forever.
- [x] **Cypress E2E Testing**: Write automated browser tests that simulate a Customer placing an order, the Admin accepting it, and the Rider delivering it.

## Epic 4: End-to-End (E2E) & User Acceptance Testing (UAT) [COMPLETED]
*Validate enterprise readiness across the entire micro-platform under realistic user journeys.*

- [x] **E2E Integration Validation**: Validate complete flows (Customer ordering -> Payment processor -> Rider dispatch -> Ledger balancing).
- [x] **Temporal Failure & Recovery Testing**: Verify durable state recovery of B2B procurement workflows under worker restarts and network partitions.
- [x] **UAT Supervisor Scenarios**: Validate supervisor interfaces for approving, rejecting, and adjusting escalated B2B restock proposals.
- [x] **Observability Audit**: Ensure all transactions emit correct trace spans to Zipkin and metrics to Prometheus under stress.

## Epic 5: Production Hardening & Agentic Scaling (Architect Recommendation Roadmap) [BACKLOG]
*Curated architecture recommendations to transition from MVP to a production-grade, highly scalable, and secure B2B platform.*

- [ ] **High-Performance Local Inference (vLLM)**: Replace basic Ollama serving with a production-grade **vLLM** engine for local model hosting (Qwen/Llama). Implement **PagedAttention** to increase concurrent request throughput by 10x–20x on on-premise hardware, and use **llama-cpp-python** for CPU-only edge environments.
- [ ] **Durable Stateful Agent Memory (Letta)**: Upgrade support and procurement agents from stateless REST cycles to **Letta** virtual context memory management. Isolate memory into **Core Memory** (agent self-managed) and **Archival Memory** (loaded via semantic vector search) to prevent context-window exhaustion.
- [ ] **Structured Guardrails & Output Validation**: Standardize prompt-safety and payload schema validation by integrating **Guardrails AI** (defining Pydantic `RAIL` safety schemas) and **NVIDIA NeMo Guardrails** (defining dialog flow boundaries) inside the Python governance layer.
- [ ] **Self-Hosted LLM Observability (Arize Phoenix)**: Deploy a self-hosted **Arize Phoenix** container. Export OpenTelemetry LLM trace logs directly to the local collector to ensure 100% PII privacy, auditing intermediate prompt/response spans and token costs locally.
- [ ] **Automated Prompt Red-Teaming (Promptfoo)**: Integrate **Promptfoo** into the CI pipeline to run automated regression and security tests (e.g., checking 100+ prompt injection, jailbreak, and PII leakage scenarios) against the governance service before merging PRs.

