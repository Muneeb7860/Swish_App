# Swish App 🚀

[![Quality Gates](https://github.com/Muneeb7860/Swish_App/actions/workflows/ci.yml/badge.svg)](https://github.com/Muneeb7860/Swish_App/actions)
[![Code Coverage](https://img.shields.io/badge/coverage-80%25-brightgreen.svg)](#)
[![Branch Protection](https://github.com/Muneeb7860/Swish_App/actions/workflows/branch-protection.yml/badge.svg)](https://github.com/Muneeb7860/Swish_App/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot 3.2](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![React 18](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev)

Welcome to **Swish**, a highly scalable, event-driven Quick Commerce (Q-Commerce) platform designed to guarantee grocery delivery within 15 minutes. 


Swish is not a simple prototype. It is a **true 3-sided enterprise marketplace** built with advanced architectural frameworks (TOGAF, COBIT 2019 resilience) and features a cutting-edge **Module Federation** micro-frontend architecture backed by a **Spring Boot Hexagonal Microservices** backend.

---

## 🏗️ 30-Second Architecture
Swish routes all traffic through a highly-resilient Edge Gateway to protect the core domain, while Kafka handles asynchronous event streams for high scalability.

```mermaid
graph LR
    Client[Web/Mobile Clients] -->|HTTPS| BFF[Spring Cloud Gateway BFF]
    BFF -->|Rate Limiting & JWT Checking| Core[Spring Boot Hexagonal Backend]
    Core -->|@Cacheable| Redis[(Redis Cache)]
    Core -->|JPA| Postgres[(PostgreSQL DB)]
    Core -->|Events| Kafka[Kafka Event Bus]
    Kafka -->|DLQ Recovery| DeadLetter[Dead Letter Queue]
```

## 🚀 Quick Start Guide (5 Minutes to Prod)

You can launch the entire ecosystem (Micro-Frontends, Backend, Gateway, Postgres, Redis, Kafka, Zipkin, Prometheus, Grafana) with a single command:

1. **Clone the Repo**
   ```bash
   git clone https://github.com/Muneeb7860/Swish_App.git
   cd Swish_App
   ```
2. **Boot the Cluster**
   ```bash
   docker-compose -f infrastructure/docker-compose.yml up -d --build
   ```
3. **Access the Applications**
   - 🛒 **Customer UI**: [http://localhost:5173](http://localhost:5173)
   - 🏍️ **Rider UI**: [http://localhost:5174](http://localhost:5174)
   - 🛠️ **Admin Chaos UI**: [http://localhost:5175](http://localhost:5175)
   - 📊 **Grafana Mission Control**: [http://localhost:3000](http://localhost:3000)

---

## 🤖 Active AI Agents (Agentic OS)
Swish operates a multi-domain agentic pipeline orchestration engine with automatic governance, dynamic thresholds, and human-in-the-loop fallback:
- **FraudAgent (Fraud Detection)**: Monitors telemetry tick streams, trust scores, and transaction history to detect and prevent payment/identity fraud.
- **PricingAgent (Dynamic Pricing)**: Evaluates temporal factors (weather, driver congestion, demand surge) to optimize restock/order pricing dynamically.
- **RoutingAgent (Logistics Routing)**: Implements split-shipment logistics, capacity constraint validation, and cached carrier rate pre-fetching.

## 🏆 Epic Deliverables & Proof of Concept

This repository has been fully upgraded over multiple sprints to achieve Tier-1 Operational Readiness. **The code is here.**

### Epic 1: CI/CD & Flyway 🔄
We use GitHub Actions to automatically run `mvn test` and `npm run build` on all PRs to `develop` and `master`.
- **Proof**: See the pipeline config at `.github/workflows/ci.yml`.
- **Proof**: See the schema migrations in `backend/src/main/resources/db/migration/V1__init_schema.sql`.

### Epic 2: Observability & Telemetry 📊
The platform is fully instrumented with Micrometer, Prometheus, and Zipkin.
- **Proof**: View the `docker-compose.yml` in `/infrastructure` for the telemetry sidecars.
- **Proof**: Check `bff/src/main/resources/application.yml` and `backend/pom.xml` to see OpenTelemetry tracing enabled natively.

### Epic 3: Scale, Reliability & QA 🏎️
The core platform has been fortified with caching, dead letter queues, and E2E testing.
- **Proof**: View `backend/src/main/java/ch/swissqcommerce/backend/config/KafkaConfig.java` for the Spring Kafka `DeadLetterPublishingRecoverer` implementation.
- **Proof**: The React Micro-Frontends have been successfully sliced out of a monolithic Zustand store using the `StateCreator` pattern (see `frontend-host/src/store.ts`).
- **Proof**: The BFF Gateway utilizes a highly concurrent Redis Token Bucket Rate Limiter (`bff/src/main/resources/application.yml`).
- **Proof**: Foundational Cypress E2E tests are implemented in `frontend-host/cypress/e2e/journey.cy.ts`.

---

## 🧪 Testing & Chaos Engineering (COBIT 2019)

We don't just hope the system stays up—we verify it.

- **Unit Testing**: See `backend/src/test/java/ch/swissqcommerce/backend/domain/OrderTest.java` for domain-level assertions on Trust Score calculation and State transitions.
- **Chaos Engineering**: Run `bash scripts/chaos.sh` to inject random container deaths into the Postgres and BFF layers to test our `Resilience4j` Circuit Breakers.

## 📁 Repository Structure
To ensure maximum discoverability, the repository is split into distinct domains:
- `/docs` - All Enterprise Architecture diagrams, HLDs, LLDs, and PRDs.
- `/backend` - The Hexagonal Java Spring Boot core.
- `/bff` - The Spring Cloud Gateway Edge Proxy.
- `/frontend-*` - The React Vite Module Federation user interfaces.
- `/infrastructure` - Docker Compose and Kubernetes manifests.
- `/scripts` - Utilities, chaos engineering, and Python validators.
