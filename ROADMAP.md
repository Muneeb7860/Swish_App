# Swish_App Enterprise Roadmap (TPO & Architect Vision)

As your Technical Product Owner (TPO) and Enterprise Architect, I am incredibly proud of where we are. Over the past few sprints, we have elevated `Swish_App` from a basic monolithic prototype into a deeply resilient, TypeScript-driven, Hexagonal, event-driven micro-platform.

Below is our current backlog status and road map.

---

## Epic 1: CI/CD & Automated Governance (The "DevOps" Epic)
*We have a robust Git branching strategy, but we are still manually compiling and testing code.*

- **GitHub Actions Pipeline**: Implement automated CI pipelines that trigger on every Pull Request to `develop`.
- **Automated Quality Gates**: Integrate SonarQube or ESLint/Checkstyle to automatically block PRs that contain code smells or vulnerabilities.
- [x] **Flyway DB Migrations**: Replace our static `schema.sql` boot script with Flyway for professional, version-controlled database schema evolution.

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
