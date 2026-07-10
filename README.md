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
│    └─────────────────────────────────┘                      │
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

#### 🗃️ Data Schema Map
| Data Type | Primary DB | Archive | TTL / Expiry | Example Fields |
| :--- | :--- | :--- | :--- | :--- |
| **Orders & Checkout** | PostgreSQL | MongoDB | ∞ | `order_id`, `status`, `total_amount` |
| **Ledger Auditing** | PostgreSQL | WORM (S3/GCS) | ∞ | `transaction_id`, `hash_chain`, `signature` |
| **Inventory & Products**| PostgreSQL | Redis | 1 hour cache | `product_id`, `stock_count`, `price` |
| **GPS Telemetry** | Redis (Buffer) | MongoDB | 24 hours | `rider_id`, `latitude`, `longitude` |
| **Bidding Logs** | MongoDB | — | 30 days | `bid_id`, `wholesaler_name`, `proposed_price` |

### 3. Asynchronous Event Pipeline (Transactional Outbox)
Prevents dual-write inconsistencies:
1. Database transaction + event metadata → `outbox` table (atomic)
2. Async relay polls `outbox`, publishes to Kafka
3. Kafka consumers process messages
4. Dead-letter queue isolates malformed payloads

---

## 🤖 B2B Agentic OS, LLM Strategy & Safety Guardrails

```
Stock Alert (< 3 units)
        ↓
[B2BProcurementAgent] ← Queries primary/secondary wholesaler pricing
        ↓
[ProcurementGuardrailsEngine] ← Evaluates cost bounds & variance limits
        ├─ PASS (Cost < $5000, Variance < 10%) → REST API Restock → PostgreSQL Update
        └─ FAIL → Human-in-the-Loop Queue → L1/L2 Operator Release
```

### 🧠 AI & LLM Execution Strategy

To ensure zero external cloud dependencies, offline functionality, and predictable token costs, the platform implements a tiered hybrid execution model:

#### 1. Local Inference (Primary)
*   **Execution Engine:** Self-hosted **Ollama** serving containerized models.
*   **Default Model:** `qwen:14b` or `llama2:13b` serving B2B negotiations and agent mesh calls locally.
*   **Memory Preserving layer:** Uses **Letta (formerly MemGPT)** to manage stateful memory contexts (Core Memory + Archival Database Vector search using pgvector) for long-running multi-turn Wholesaler RFQ negotiations.

#### 2. Cloud Fallback (Secondary)
*   **Execution Engine:** **Spring AI** (`spring-ai-openai-spring-boot-starter`).
*   **Trigger Condition:** Automatically trips via the `ResilientLlmGateway` circuit breaker if the local Ollama instance timeouts or crashes.
*   **Cost Management:** PII is redacted at the gateway before sending requests to public cloud endpoints; a strict `$5/day` token budget counter checks usage dynamically.

---

### 🛡️ Safety Guardrails & Payload Verification

Before query routing and model output deliveries, the governance layer executes two safety guardrail systems:

#### 1. NVIDIA NeMo Guardrails (Colang Dialog Safety)
*   **Safety Scripting:** Active rails defined in [config.yml](./homelab-ai-governance/config/nemo_guardrails/config.yml) and [flows.co](./homelab-ai-governance/config/nemo_guardrails/flows.co) enforce conversation flow boundaries.
*   **Input Blocking:** Matches prompts against safety intents (e.g., system configuration overrides, malicious bypasses, or requests for competitor pricing). If violated, the flow triggers a direct bot safety response, short-circuiting downstream LLM costs.

#### 2. Guardrails AI (Structured Output Validation)
*   **Schema Enforcement:** Model outputs are parsed and validated against strict Pydantic schemas (e.g. `CustomerSupportSchema`, `DynamicPricingSchema`).
*   **Recursive Self-Correction:** If the model outputs malformed JSON or invalid values (e.g., negative prices, invalid barcodes), the enforcer extracts field-level error messages and re-submits a structured correction request to the model (up to 3 retries) before escalating to local fallback.

---

### 🧑‍💻 Core Platform Agents
*   **B2BProcurementAgent:** Autonomous AI agent that queries pricing structures from primary and secondary wholesalers and conducts restock negotiations.
*   **ProcurementGuardrailsEngine:** Evaluates contract proposals against strict financial bounds (e.g., maximum cost thresholds and wholesale price variance ceilings).
*   **Human-in-the-Loop (HITL) Queue:** If guardrail thresholds are violated, the proposed transaction is locked in `hitl_queue` and requires manual release by an authorized operator.
*   **Additional Domain Agents:**
    *   *FraudAgent:* Checks order frequencies, trust scores, and transactions to detect identity/payment fraud.
    *   *PricingAgent:* Adapts delivery pricing dynamically based on local congestion, weather, and inventory counts.
    *   *RoutingAgent:* Directs split-shipment logistics, calculating carrier rates and courier capacity constraints.

---

## 📜 Compliance, Governance, & Safety

Swish OS enforces high-availability limits, rate-limits, and encryption at-rest. Operational standards are structured around:

### ⚙️ Service Inventory

The platform is transitioning from a monolithic core to a microservices architecture. The current deployment state of each service is as follows:

| Service / Component | Purpose | Local Port | Status / Deployment |
| :--- | :--- | :--- | :--- |
| **`backend/`** | Hexagonal Core Engine. Manages order lifecycles, payments, sensor calibration, and agent mesh execution. | `8083` | **Active** (Java Spring Boot) |
| **`platform-gateway/`** | API Ingress gateway. Executes JWT checks, routing, and token bucket rate-limiting. | `8080` | **Active** (Spring Cloud Gateway) |
| **`core-business-engine/`** | Standalone B2B checkout & catalog management engine. | `8081` | *Under Extraction / Development* |
| **`notification-engine/`** | Kafka listener broadcasting real-time updates over WebSockets. | `8082` | *Under Extraction / Development* |
| **`shared-async-services/`** | Universal domain entities & accounting schemas. | — | *Under Extraction / Development* |

---

## 🎨 Module Federation & Micro-Frontends

Micro-Frontends (MFEs) are decoupled client apps federated at runtime using `@originjs/vite-plugin-federation` (v1.4.1) or custom module configuration. Mismatches are avoided by pinning shared library versions in `vite.config.ts` across all MFEs:

*   **`react` / `react-dom`:** Pinned to `^18.2.0`
*   **`zustand`:** Pinned to `^4.5.2` for shared client store state
*   **`@swish/design-system`:** Local UI component library ensuring style uniformity across Customer, Rider, and Admin screens.

---

## 🚀 Quick Start Guide (5 Minutes)

### 1. Clone & Setup
```bash
git clone https://github.com/Muneeb7860/Swish_App.git
cd Swish_App
```

### 2. Stand Up Core Infrastructure
Use the unified local script:
```bash
./dev/infra/stand_up_infra.sh
```

---

## 🏛️ Program Governance

*   **Scaled Agile Framework (SAFe):** Managed on a 2-week Program Increment (PI) planning cycle with feature toggles dynamically controling the chaos engines and procurement guardrail limits.
*   **ITIL v4 Service Value Chain:** Structures the flow of restocking requests to business margin optimization.
*   **veriSM Management Mesh Weights:**
    *   *Agile Development (SAFe):* **Weight 5** (Weekly deployment matrix checks).
    *   *DevOps (CI/CD):* **Weight 5** (Automated matrix testing of backend containers).
    *   *Service Management (ITIL v4):* **Weight 4** (SLA monitoring, cold chain telemetry tracking).
    *   *Governance (COBIT 2019):* **Weight 5** (Tamper-evident ledger, secrets rotation).

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

## 🔗 Related Documentation Index
*   📐 **[System Architecture](./docs/ARCHITECTURE.md)**: Architectural patterns, structural layout, and C4 context levels.
*   💼 **[Business Requirements Document (BRD)](./docs/BRD.md)**: Enterprise scope, customer segments, subscription tiers, and picking/delivery SLAs.
*   📈 **[High Level Design (HLD)](./docs/HLD.md)**: Network topology, distributed database layouts, and outbox schema structures.
*   🔍 **[Low Level Design (LLD)](./docs/LLD.md)**: Interface bindings, class/object relations, and micro-frontend federation configs.
*   🔒 **[Security Architecture Audit](./docs/SECURITY.md)**: Cryptographic signature chains, TLS termination details, and GDPR purge rules.
*   🧪 **[User Acceptance Testing (UAT)](./docs/UAT_TEST_CASES.md)**: Detailed test scripts, validations, and administrative scenario flows.

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
