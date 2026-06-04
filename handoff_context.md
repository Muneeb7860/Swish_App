# Swish App — Handoff Context 🚀

> **Last Updated**: 2026-06-04  
> **Current Version**: v0.2.16  
> **Active Branch**: `Mac_Machine` @ `9a07c7f`  
> **Repo**: [github.com/Muneeb7860/Swish_App](https://github.com/Muneeb7860/Swish_App)

---

## 1. What is Swish?

**Swish** is a 3-sided Q-Commerce (Quick Commerce) marketplace guaranteeing grocery delivery within 15 minutes. It connects **Customers**, **Riders**, and **Admins/Hosts** through a unified platform. It also includes a **B2B wholesale** channel.

The platform is designed as an **enterprise-grade, event-driven, hexagonal microservices** architecture following TOGAF and COBIT 2019 resilience frameworks.

**Target Pilot**: Valora ("k kiosk") — 5 high-traffic Swiss transport hubs (Zurich HB, Bern, Basel SBB, Geneva Cornavin, Lucerne).  
**Business Model**: Low-CapEx B2B SaaS that retrofits existing storefronts with decentralized micro-frontends and agentic AI procurement workflows.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| **Backend Core** | Java 17, Spring Boot 3.2, Spring Data JPA, Flyway |
| **BFF Gateway** | Spring Cloud Gateway (WebFlux), Resilience4j |
| **Platform Gateway** | Spring Cloud Gateway (new, replaces BFF for platform routing) |
| **Core Business Engine** | Spring Boot (new module — checkout, inventory, B2B orders) |
| **Notification Engine** | Spring Boot, Kafka consumer, Redis Pub/Sub, WebSockets |
| **Shared Async Services** | Spring Boot (AI agent orchestration, financial ledger) |
| **Database** | PostgreSQL (Flyway migrations: V1, V2, V3) |
| **Cache** | Redis (`@Cacheable`, rate limiting, pub/sub) |
| **Messaging** | Apache Kafka (with DLQ, Outbox pattern, Avro schemas) |
| **Frontends** | React 18, Vite, Module Federation |
| **Mobile** | React Native |
| **Observability** | Micrometer, Prometheus, Grafana, Zipkin/OpenTelemetry |
| **CI/CD** | GitHub Actions (CI + SemVer Release) |
| **AI** | Spring AI (OpenAI + Ollama) — B2B procurement agent |
| **Security** | JWT, mTLS (Envoy), OWASP Dependency Check, Trivy, Jacoco, GDPR Art.17 |
| **Infra** | Docker Compose, Kubernetes manifests, n8n workflows |
| **Logging** | Logstash Logback encoder (structured JSON) |

---

## 3. Architecture Overview

```
Client (Web/Mobile)
    │
    ▼
┌─────────────────────┐
│  platform-gateway    │  ← New Spring Cloud Gateway (rate limiting, idempotency, security)
│  (or bff/)           │  ← Legacy BFF Gateway (still present)
└─────────┬───────────┘
          │
    ┌─────┴─────┐
    ▼           ▼
┌────────┐  ┌──────────────────┐
│backend/│  │core-business-    │
│(legacy)│  │engine/ (new)     │
│Hex.Arch│  │Checkout/Inv/B2B  │
└───┬────┘  └───────┬──────────┘
    │               │
    ├───► PostgreSQL ◄──┤
    ├───► Redis      ◄──┤
    └───► Kafka      ◄──┤
              │
              ▼
    ┌─────────────────────┐
    │notification-engine/ │  ← Kafka consumer → WebSocket push
    └─────────────────────┘
```

### Architecture Pattern
- **Hexagonal Architecture** (Ports & Adapters) throughout `backend/` domain modules
- **Outbox Pattern** for reliable Kafka event publishing
- **Module Federation** for micro-frontends

---

## 4. Domain Modules (backend/)

Each domain follows: `domain/{name}/adapter/in/web`, `adapter/out/persistence`, `core/model`, `core/service`, `port/in`, `port/out`

| Domain | Description |
|---|---|
| **transaction** | Orders, order lifecycle, state machine |
| **payment** | Payment engine (mock gateway, webhooks, DTOs) — *recently added* |
| **inventory** | Product/inventory management — *recently refactored to hex arch* |
| **event** | Outbox event scheduler for Kafka |
| **auth** | Authentication (JWT) |
| **enrollment** | User/rider onboarding |
| **notification** | Push notification orchestration |
| **feedback** | Customer feedback/ratings |
| **reward** | Loyalty/reward system |
| **agent** | AI agent integration (Gemini adapter) |

### Legacy Code (non-hexagonal, at root level)
- `controller/` — `CustomerController` (partially cleaned)
- `model/` — JPA entities (`Inventory`, `B2BRestockOrder`, `CustomerPaymentCard`, etc.)
- `repository/` — JPA repositories
- `service/` — (mostly emptied, inventory service moved to domain)
- `config/` — `RedisCacheConfig`, `KafkaConfig`, security configs
- `exception/` — Custom exception handlers

---

## 5. New Microservice Modules (added recently)

| Module | Purpose |
|---|---|
| `core-business-engine/` | Checkout, payment (Stripe-mock), B2B wholesale orders, AI evaluation timeout sweeper, inventory models |
| `notification-engine/` | Kafka consumer for notifications, Redis pub/sub, WebSocket push to clients |
| `platform-gateway/` | New Spring Cloud Gateway with idempotency filter, Redis rate limiter, security config |
| `shared-async-services/` | AI model orchestration port, financial ledger entry |
| `quickcommerce_mvp/` | Standalone MVP — Python order service + Java order service + React frontend |
| `schemas/` | Avro schemas for wholesale order events |

---

## 6. Frontends

| App | Port | Tech |
|---|---|---|
| `frontend-customer/` | 5173 | React 18 + Vite + Module Federation |
| `frontend-rider/` | 5174 | React 18 + Vite (RiderDashboard) |
| `frontend-admin/` | 5175 | React 18 + Vite |
| `frontend-host/` | — | Module Federation host shell |
| `frontend-b2b/` | — | B2B wholesale dashboard (new) |
| `mobile/` | — | React Native |

---

## 7. Database Migrations

| Migration | Contents |
|---|---|
| `V1__init_schema.sql` | Core tables: customers, orders, order_items, products, inventory, riders, etc. |
| `V2__payment_engine.sql` | Payment tables (recently added) |
| `V3__critical_fixes.sql` | Critical schema fixes (recently added) |
| `core-business-engine/.../V1__init_core_engine.sql` | Separate DB for core engine |
| `core-business-engine/.../V3__b2b_wholesale_orders.sql` | B2B wholesale tables |

---

## 8. Infrastructure

- **Docker Compose**: `infrastructure/docker-compose.yml` (full stack), `docker-compose-local.yml`, `docker-compose-n8n.yml`
- **Kubernetes**: `infrastructure/k8s/` and `k8s/` — deployments for all services with resource limits, probes
- **Prometheus**: `prometheus.yml` + `infrastructure/prometheus/`
- **n8n**: `n8n-hybrid-workflow.json` — workflow automation

---

## 9. CI/CD Pipelines

| Pipeline | Trigger | Actions |
|---|---|---|
| `ci.yml` | PR to `develop`/`master` | Maven tests, Jacoco coverage, ESLint, OWASP scan, Trivy scan |
| `release.yml` | Push to `master` | SemVer tag + GitHub Release |
| `branch-protection.yml` | PR checks | Branch protection enforcement |

---

## 10. Branch Strategy

| Branch | Purpose |
|---|---|
| `Mac_Machine` | Primary development branch (macOS workstation) |
| `Windows_Machine` | Windows workstation mirror |
| `develop` | Integration branch |
| `master` | Production-ready releases |

All 4 branches are kept **in sync** — changes are committed on `Mac_Machine`, then merged to the other 3.

---

## 11. Recent Changes (Latest Sprint)

1. **Payment Engine** — Full hexagonal payment domain with mock gateway, webhooks, React UI
2. **Inventory Refactor** — Moved from legacy `controller/service` to hexagonal `domain/inventory/`
3. **Redis Cache** — `RedisCacheConfig` for `@Cacheable` support
4. **Core Business Engine** — New standalone microservice for checkout/B2B
5. **Notification Engine** — Kafka → WebSocket notification push
6. **Platform Gateway** — Replacement gateway with idempotency + rate limiting
7. **B2B Frontend** — New React dashboard for wholesale
8. **WebSocket Remediation** — Fixed 401 blocks, PII data leaks
9. **Notification Hardening** — 17/21 loopholes fixed
10. **TransactionPersistenceAdapter** — Compilation fix for signature mismatch

---

## 12. API Surface (via BFF)

~25 endpoints organized by persona:
- **Auth**: `/api/auth/login`, `/api/auth/mfa/verify`
- **Customer**: catalog, orders, AI-assisted refunds, GDPR purge, ledger
- **Payments**: authorize, capture, list
- **Rider**: onboarding, academy courses
- **Picker**: pick queue, handover
- **Inventory**: rebalance MFCs
- **Wholesaler**: restocking queue, fulfill, invoices
- **Admin**: chaos fault injection, onboarding queue, HITL ticket resolution

All mutation routes support `X-Idempotency-Key` headers.

---

## 13. Known Issues / Tech Debt

- **Legacy code coexists with hexagonal domains** — `controller/`, `model/`, `repository/`, `service/` directories still hold some non-refactored code
- **Two gateway modules** — `bff/` (legacy) and `platform-gateway/` (new) both exist
- **Multiple payment implementations** — `backend/domain/payment/` and `core-business-engine/checkout/`
- **Duplicate k8s configs** — `k8s/` and `infrastructure/k8s/` directories
- `patch.diff` and `test_video.mp4` — leftover files in root
- Branch renaming pending (`Mac_Machine` → `mac-machine`, etc.)

---

## 14. ADRs (Architecture Decision Records)

1. **ADR-001**: Hexagonal Architecture — isolate business logic from external adapters
2. **ADR-002**: Module Federation + Zustand — sliced micro-frontend state via `StateCreator` pattern
3. **ADR-003**: Kafka DLQ — guaranteed message processing with dead letter recovery

---

## 15. Disaster Recovery Runbooks

1. **Kafka Poison Pill & DLQ Recovery** — isolate, examine, hotfix, replay
2. **Database Replication Lag** — check health, failover, GDPR purge verification
3. **Redis Cache Crash** — restart, warm cache, verify hit rate > 85%

---

## 16. How to Run

```bash
# Full stack via Docker
docker-compose -f infrastructure/docker-compose.yml up -d --build

# Backend only
cd backend && ./mvnw spring-boot:run

# BFF Gateway
cd bff && ./mvnw spring-boot:run

# Any frontend
cd frontend-customer && npm install && npm run dev
```

---

## 17. Key Config Files

| File | Purpose |
|---|---|
| `backend/src/main/resources/application.properties` | Backend config (DB, Redis, Kafka) |
| `bff/src/main/resources/application.yml` | BFF Gateway routing, rate limiting |
| `platform-gateway/src/main/resources/application.yml` | New gateway config |
| `core-business-engine/src/main/resources/application.yml` | Core engine config |
| `notification-engine/src/main/resources/application.yml` | Notification service config |
| `infrastructure/docker-compose.yml` | Full infra stack |
| `.github/workflows/ci.yml` | CI pipeline |
| `.github/workflows/release.yml` | Release pipeline |
| `bff-openapi.yaml` | API specification (OpenAPI 3.0) |

---

> [!TIP]
> **Start here**: If you're making backend changes, the primary module is `backend/` with hexagonal domains under `domain/`. The newer `core-business-engine/` is a separate microservice for checkout/B2B flows.
