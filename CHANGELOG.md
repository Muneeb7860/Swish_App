# Changelog 📋

All notable changes to the **Swish Q-Commerce Platform** are documented in this file. This project adheres to Semantic Versioning (`SemVer`).

---

## [[`v0.3.0-routing-hardened`]](https://github.com/Muneeb7860/Swish_App/releases/tag/v0.3.0-routing-hardened) - 2026-06-19

### 🚀 Added (Sprint 5 Routing Hardening & Quality Gate Gaps)
*   **Warehouse Capacity Constraints**: Added `daily_order_capacity` to dark stores and disqualified overloaded facilities from warehouse routing selection.
*   **Carrier Rate API Client**: Integrated a REST carrier rate service client with strict 200ms connection/read timeouts and a graceful straight-line Haversine math fallback.
*   **5-Minute Redis Rate Cache**: Enabled a Redis-backed caching store with a 5-minute TTL to reduce repetitive high-latency network queries.
*   **Asynchronous Parallel Pre-fetching**: Leveraged `CompletableFuture` to fetch warehouse rates in parallel under a strict 300ms budget limit.
*   **Historical Scarcity Confidence Penalty**: Enforced a 20% baseline cost penalty on routing scores if a warehouse has fewer than 5 historical shipments.
*   **Ops Alerting Rules**: Configured Prometheus alerts to page operations when the human-in-the-loop task rate exceeds 10/hour (Warning) or 50/hour (Critical).

### 🛠️ Fixed
*   **BUG-012 (Transactional Outbox Test Hang)**: Resolved Kafka connection blocks during integration test executions by declaring a mocked `KafkaTemplate` in [PaymentIntegrationTest.java](file:///Users/muneeb/GitHub/Swish_App-1/backend/src/test/java/ch/swissqcommerce/backend/integration/PaymentIntegrationTest.java) and auto-loading [TestConfig.java](file:///Users/muneeb/GitHub/Swish_App-1/backend/src/test/java/ch/swissqcommerce/backend/integration/TestConfig.java) globally.
*   **BUG-013 (Onboarding Null Constraint Violation)**: Resolved database crashes during E2E onboarding flows by wrapping boolean DTO fields with `Boolean.TRUE.equals(...)` in [EnrollmentPersistenceAdapter.java](file:///Users/muneeb/GitHub/Swish_App-1/backend/src/main/java/ch/swissqcommerce/backend/domain/enrollment/adapter/out/persistence/EnrollmentPersistenceAdapter.java).

---

## [[`v0.2.0`]](https://github.com/Muneeb7860/Swish_App/releases/tag/v0.2.0) - 2026-06-01

### 🛡️ Added (6-Hour Enterprise Hardening Campaign)
*   **BFF Edge Swagger Aggregation**: Installed `springdoc-openapi-starter-webflux-ui` in the gateway, aggregated core backend API documentation paths, configured Nginx proxy rerouting, and implemented filter JWT verification bypasses.
*   **Strict DTO Validation**: Integrated `@jakarta.validation.constraints` (such as `DecimalMin`, `DecimalMax`, and `NotNull`) across `TelemetryTickRequest` payloads and secured REST gateways with `@Valid` controllers.
*   **Architecture Decision Records (ADRs)**: Standardized three logs in `/docs/adr/` regarding Hexagonal boundaries, Zustand sliced MFE stores, and Kafka DLQ architectures.
*   **Disaster Recovery SOP Runbook**: Created root-level `DISASTER_RECOVERY.md` detailing troubleshooting rules for Kafka DLQs, PostgreSQL replication lag, and Redis server outages.
*   **Kubernetes Hardening**: Applied container CPU and memory requests/limits constraints and added liveness/readiness probes to **all** deployments in `infrastructure/k8s/` (`postgres`, `redis`, `mongodb`, `kafka`, `nginx`, `prometheus`, `zipkin`, `grafana`, `backend`, and `bff`).
*   **Custom MFE READMEs**: Overwrote standard templates in all frontends with bespoke guides explaining exposed Module Federation portals, ports, and Zustand store hooks.
*   **Jacoco 75% Coverage Enforcer**: Enforced strict Maven testing coverage thresholds at **75% minimum covered ratio** across backend core instruction sets.
*   **Enterprise Quality Gate CI/CD Pipeline**: Configured `.github/workflows/ci.yml` to trigger matricized Node builds, ESLint validations, Maven test executions, Jacoco thresholds verification, OWASP dependency scans, and Trivy filesystem scans.
*   **Automated Release Pipeline**: Configured `.github/workflows/release.yml` to trigger semantic release creation and tagging on pushes to master.
*   **Legal Compliance & Badges**: Added MIT `LICENSE`, root discoverability `package.json`, and status badges on `README.md`.

---

## [[`v0.1.0`]](https://github.com/Muneeb7860/Swish_App/releases/tag/v0.1.0) - 2026-05-31

### 🧪 Added (Testing, Quality & CI/CD Milestone)
*   **Multi-Threaded Spring Boot Integration Tests**: Built `OrderIntegrationTest.java` to test thread-safety, transaction boundaries, and JPA state transitions under high concurrency (8 worker threads attempting to purchase a single, last item in stock).
*   **Cypress E2E Customer Journey Flows**: Implemented E2E integration validation testing coupon codes, tipping parameters, cart logic, substitution alerts, and dynamic progress trackers in `/frontend-host/cypress/e2e/shopping_flow.cy.ts`.
*   **K6 Load Testing Scenarios**: Configured SLA stress-testing suites auditing response times under a peak load of 500 virtual users.
*   **CODEOWNERS Review Controls**: Assigned repository-level reviews and pull-request templates to enforce high-quality standards.
