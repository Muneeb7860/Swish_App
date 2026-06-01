# Changelog 📋

All notable changes to the **Swish Q-Commerce Platform** are documented in this file. This project adheres to Semantic Versioning (`SemVer`).

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
