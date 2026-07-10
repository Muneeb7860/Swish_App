# Swish OS v2.0.0 🚀

[![Quality Gates](https://img.shields.io/badge/Quality%20Gates-Passed-success?style=for-the-badge)](https://github.com/Muneeb7860/Swish_App/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-17-orange?style=for-the-badge)](https://www.oracle.com/java/technologies/javase/jdk17-archive.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?style=for-the-badge)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue?style=for-the-badge)](https://react.dev/)

Welcome to **Swish OS v2.0.0**, an enterprise-grade, multi-tenant B2B SaaS platform engineered to transform legacy convenience stores and micro-fulfillment centers (MFCs) into autonomous, high-velocity quick-commerce operations. Built on rigorous architectural principles (TOGAF, Hexagonal Architecture, COBIT 2019, ITIL v4).

**Key Stats:**
- **51.8%** Java backend logic
- **21.3%** TypeScript frontend applications  
- **9.2%** CSS styling & design system
- **7.9%** HTML markup
- **6.2%** Python AI agents & scripts

---

## 🎯 What is Swish OS?

Swish OS is a complete operational platform for:
- **Store Operators** — Manage inventory, fulfill orders, track restocks
- **CFOs & Finance Managers** — Real-time ledger tracking, savings analytics, cost controls
- **Platform Admins** — Configure guardrails, oversee HITL queues, manage system health
- **Suppliers & Wholesalers** — B2B procurement negotiation and fulfillment
- **Delivery Couriers** — GPS-guided routing and order logistics

---

## 🏗️ Architectural Vision

Swish OS follows industry-leading architectural frameworks:

- **TOGAF ADM Alignment:** Business-driven architecture (Phase A) through deployment (Phase D)
- **Hexagonal Architecture:** Isolates domain logic from adapters (databases, APIs, UI)
- **COBIT 2019 & ITIL v4 Resilience:** Circuit breakers, dead-letter recovery, pessimistic locking
- **Zero-Trust Networking (Roadmap):** mTLS with SPIFFE/SPIRE across Kubernetes service mesh
- **Transactional Outbox Pattern:** Prevents dual-write inconsistencies using Kafka KRaft

---

## 🌐 System Context (C4 Model)

### Level 1: System Context Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                                                               │
│  CFO          Store Operator      Platform Admin            │
│    ↓                 ↓                    ↓                  │
│    └─────────────────┴────────────────────┘                │
│                     │                                        │
│                 Swish OS v2.0.0 Platform                    │
│          (B2B SaaS | Multi-tenant | Autonomous)            │
│                     │                                        │
│    ┌────────────────┼────────────────┐                     │
│    ↓                ↓                ↓                      │
│ Primary         Secondary        GPS Navigation             │
│ Wholesaler      Wholesaler       Service (Maps)             │
│ ERP             ERP                                         │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Level 2: Container Architecture (Target Topology)
The platform targets a **Kubernetes service mesh** with:
- **NGINX Ingress Controller** — DMZ, TLS termination, rate limiting
- **Spring Cloud Gateway** — Custom routing with JWT verification, idempotency checks
- **Core Microservices** (Envoy mTLS sidecars):
  - `backend` — Hexagonal domain core (transactions, auth, agents)
  - `core-business-engine` — B2B checkout, inventory, timeout sweepers
  - `notification-engine` — Kafka consumer, WebSocket broadcasts
  - `shared-async-services` — AI routing, ledger, shared schemas
  - `platform-gateway` — API orchestration & security proxy
- **Data Tier**:
  - PostgreSQL (OLTP transactional source of truth)
  - MongoDB (Analytical archive, cold storage)
  - Redis (Session state, rate limiting, caching)
- **Event Broker**: Apache Kafka (KRaft mode)

**Current Deployment**: Google Cloud Run (prod) + Docker Compose (dev/demo)

---

## 🗄️ Core Architecture & Data Strategy

### 1. Traffic Flow
1. **API Ingress** → NGINX / Cloud Run load balancer → TLS termination
2. **Gateway Layer** → Spring Cloud Gateway with custom filters (JWT, rate limiting, circuit breakers)
3. **Service Layer** → Hexagonal microservices with Kafka event publication
4. **Data Layer** → PostgreSQL (primary), MongoDB (archive), Redis (cache)

### 2. Segmented Dual-Database Model
- **PostgreSQL (OLTP)**: Single source of truth for transactions, inventory, double-entry ledgers
  - Flyway migrations
  - Configured for `REPEATABLE_READ` isolation
- **MongoDB (OLAP Archive)**: Low-cost, tiered document store for telemetry, GPS logs, historical data
- **Redis (Distributed Cache)**: Token buckets, courier presence, autocomplete structures
  - Prevents 70% of relational disk writes

### 3. Asynchronous Event Pipeline (Transactional Outbox)
Prevents dual-write inconsistencies:
1. Database transaction + event metadata → `outbox` table (atomic)
2. Async relay polls `outbox`, publishes to Kafka
3. Kafka consumers process messages
4. Dead-letter queue isolates malformed payloads

---

## 🤖 B2B Agentic OS & Guardrails

```
Stock Alert (< 3 units)
        ↓
[B2BProcurementAgent] ← Queries primary/secondary wholesaler pricing
        ↓
[ProcurementGuardrailsEngine] ← Evaluates cost bounds & variance limits
        ├─ PASS (Cost < $5000, Variance < 10%) → REST API Restock → PostgreSQL Update
        └─ FAIL → Human-in-the-Loop Queue → L1/L2 Operator Release
```

**Domain Agents**:
- **B2BProcurementAgent** — Autonomous pricing negotiation
- **FraudAgent** — Order frequency & trust score detection
- **PricingAgent** — Dynamic delivery pricing (weather, congestion, inventory)
- **RoutingAgent** — Split-shipment logistics & carrier rate optimization
- **ProcurementGuardrailsEngine** — Financial bounds enforcement

---

## 📜 Compliance, Governance, & Safety

- **GDP Temperature Compliance** — Cold-chain integrity with cryptographically signed IoT sensor records (EU 2013/C 343/01)
- **Write-Once, Read-Many (WORM) Auditing** — Immutable logs for sensor diagnostics, ledger transactions, HITL approvals
- **GDPR Article 17 Support** — Right to be Forgotten with automatic anonymization (maintains referential integrity)

---

## 📁 Repository Structure

```
Swish_App/
├── backend/                          # Spring Boot Hexagonal microservice
│   └── src/main/java/ch/swissqcommerce/backend/domain/
│       ├── transaction/              # Order lifecycle & state machines
│       ├── payment/                  # Payment processing & webhooks
│       ├── inventory/                # Product structures & dark store ops
│       ├── event/                    # Transactional Outbox relay
│       ├── auth/                     # JWT authorization & security
│       └── agent/                    # Gemini AI adapter
│
├── platform-gateway/                 # Spring Cloud Gateway + rate limiter
├── core-business-engine/             # B2B order execution service
├── notification-engine/              # Kafka listener + WebSocket broadcaster
├── shared-async-services/            # AI routing, ledger, async pipelines
│
├── design-system/                    # Unified React component library
│   ├── tokens.css                   # Design tokens (colors, spacing, typography)
│   ├── components/                  # AuthPortal, Skeleton, etc.
│   └── index.ts                     # Exports
│
├── ds-bundle/                        # B2B-specific design system
│   ├── CheckoutPanel                # Wholesale checkout interface
│   ├── CreditCardMockup             # 3D card visualization
│   ├── OrderTimeline                # Multi-stage progress indicator
│   └── StatusIndicator              # WebSocket connection status
│
├── frontend-customer/                # Customer storefront MFE (Port 5173)
├── frontend-rider/                   # Courier navigation MFE (Port 5174)
├── frontend-admin/                   # Admin console MFE (Port 5175)
├── frontend-b2b/                     # Supplier dashboard MFE
├── frontend-host/                    # Micro-frontend shell (Port 3000)
│
├── mobile/                           # React Native mobile app
├── infrastructure/                   # Docker Compose & Kubernetes manifests
├── docs/                             # ADRs, HLD, LLD assets
├── scripts/                          # Build, sync, chaos engineering tools
├── tests/                            # Cypress E2E + regression suites
├── schemas/                          # Shared TypeScript/API schemas
│
├── k8s/                              # Kubernetes deployment manifests
├── nginx/                            # NGINX ingress configuration
├── figma/                            # Design files & component specs
│
└── Configuration Files:
    ├── docker-compose.demo.yml       # Full-stack local deployment
    ├── docker-compose-local.yml      # Infrastructure-only (Postgres/Kafka/Redis)
    ├── run_demo.sh                   # One-click deployment script
    ├── seed.sql                      # Database initialization
    ├── pom.xml                       # Maven root POM
    ├── package.json                  # Monorepo NPM scripts
    └── biome.json                    # Code formatter/linter config
```

---

## 🚀 Quick Start Guide (5 Minutes)

### 1. Clone & Setup
```bash
git clone https://github.com/Muneeb7860/Swish_App.git
cd Swish_App
git checkout master
```

### 2. Full Stack Deployment (One Command)
```bash
# Starts all services, databases, frontends, migrations
./run_demo.sh
```

**What this does:**
- ✅ Checks required ports (3000-3003, 5002, 5173-5175, 8080, 9092)
- ✅ Starts Docker Compose stack (Postgres, Kafka, Redis, MongoDB)
- ✅ Runs Flyway database migrations
- ✅ Builds Java microservices (Maven)
- ✅ Launches React MFEs on pinned ports
- ✅ Provides telemetry dashboards

### 3. Infrastructure Only (Databases)
```bash
docker compose -f docker-compose-local.yml up -d
```

> **Note:** Legacy `infrastructure/docker-compose.yml` is deprecated (references retired BFF).

### 4. Access Services
| Service | URL | Purpose |
|---------|-----|---------|
| 🛒 Customer Storefront | `http://localhost:5173` | End-user shopping |
| 🏍️ Rider Logistics | `http://localhost:5174` | Courier routing |
| 🛠️ Admin Console | `http://localhost:5175` | Platform management |
| 📊 Grafana Metrics | `http://localhost:3000` | System observability |
| 🔌 API Gateway | `http://localhost:8080` | REST endpoints |

---

## 🧪 Testing & Chaos Engineering

### Automated Regression Testing
```bash
# Backend: Spring Boot JUnit & ArchUnit architecture rules
mvn test

# Frontend: Cypress end-to-end workflows
npm run test:e2e
```

### Chaos Engineering
```bash
# Inject random failures: network partitions, Kafka broker drops, DB timeouts, API latency surges
bash scripts/chaos.sh
```

Swish OS enforces high-availability limits and graceful degradation under failure.

---

## 🏛️ Program Governance

- **SAFe 6.0** — 2-week Program Increment planning with feature toggles
- **ITIL v4 Service Value Chain** — Structures restocking workflows → margin optimization
- **veriSM Management Mesh** with weighted domains:
  - *Agile Development (SAFe)* — **Weight 5** (weekly deployment matrix checks)
  - *DevOps (CI/CD)* — **Weight 5** (automated backend container testing)
  - *Service Management (ITIL)* — **Weight 4** (SLA monitoring, cold-chain tracking)
  - *Governance (COBIT)* — **Weight 5** (tamper-evident ledgers, secrets rotation)

---

## 🎨 Design System

### @swish/design-system
A unified, consolidated design system reducing code duplication by **72%**:

| Component | Status | Use Cases |
|-----------|--------|-----------|
| **AuthPortal** | ✅ Ready | Multi-role authentication (customer, admin, rider) with MFA |
| **Skeleton** | ✅ Ready | Loading states (product grids, tables, generic cards) |
| **Glass Cards** | ✅ Ready | Premium frosted-glass UI effects |
| **Status Badges** | ✅ Ready | WebSocket connection, role indicators |

**Tokens (CSS Custom Properties):**
- Colors: backgrounds, text, status indicators, role-specific
- Spacing: 4px baseline grid (0–64px)
- Typography: xs (11px) → 2xl (30px)
- Shadows: sm, md, lg, xl, glow
- Animations: fade-in, slide, scale, pulse, hologram-shimmer

---

## 📊 Language Composition
| Language | Percentage | Primary Use |
|----------|-----------|------------|
| Java | 51.8% | Spring Boot microservices, domain logic |
| TypeScript | 21.3% | React frontends, design system |
| CSS | 9.2% | Styling & component library |
| HTML | 7.9% | Template markup |
| Python | 6.2% | AI agents, scripts, data pipelines |
| JavaScript | 1.5% | Legacy utilities |
| Other | 2.1% | Config, build files |

---

## 🤝 Contributing

Please read [CONTRIBUTING.md](./CONTRIBUTING.md) for development guidelines, branch strategies, and PR expectations.

### Key Branches
- `master` — Production-ready, stable releases
- `macbook_machine` — Active development (unstable, feature-in-progress)
- Feature branches follow `feature/JIRA-123-description` pattern

---

## 📚 Documentation

- [High-Level Design](./high_level_design.md) — System architecture, C4 diagrams
- [Low-Level Design](./low_level_design.md) — Detailed service specs, API contracts
- [Roadmap](./ROADMAP.md) — Planned features, Q3-Q4 initiatives
- [Changelog](./CHANGELOG.md) — Release notes & version history
- [Disaster Recovery](./DISASTER_RECOVERY.md) — Backup, failover, recovery procedures
- [Handover Guide](./handover.md) — Operational runbooks, troubleshooting
- [Branch Strategy](./BRANCH_STRATEGY.md) — Git workflow & merge policies

---

## 🔒 Security

See [SECURITY.md](./SECURITY.md) for vulnerability reporting and security policies.

**Key Safeguards:**
- JWT-based authentication with RS256 signing
- Role-based access control (RBAC) for customer/admin/rider/business
- Secrets rotation via GitOps + HashiCorp Vault
- SQL injection prevention (parameterized queries)
- CORS & CSRF protection

---

## 📄 License

This project is licensed under the **MIT License** — see [LICENSE](./LICENSE) for details.

---

## 🌟 Status & Support

- **Version**: 2.0.0
- **Repository Created**: 42 days ago
- **Last Updated**: 1 day ago
- **Issue Tracker**: [GitHub Issues](https://github.com/Muneeb7860/Swish_App/issues)
- **Discussions**: [GitHub Discussions](https://github.com/Muneeb7860/Swish_App/discussions)

---

**Made with ❤️ by Muneeb7860**  
*In a blink* — Fast, reliable, autonomous quick-commerce operations.
