# Business Requirements Document (BRD)

## Project: Swish OS Platform (v0.2.10) – B2B SaaS Pivot (Updated)

### Document Control
| Version | Date | Author | Status |
|---|---|---|---|
| v2.0.0 | 2026‑06‑02 | Technical Product Owner / Architect | **Approved – Conditions** |

> 🧭 **Target vs as-built.** This BRD is the North-Star vision. The current
> realization status of every functional requirement (FR-01…FR-07) and the
> convergence plan are tracked in [`AS_BUILT_VS_TARGET.md`](./AS_BUILT_VS_TARGET.md).
> Today: All functional requirements (FR-01 to FR-07) are fully built and verified (✅).

---

## 1. Executive Summary & Strategic Rationale
Swish OS is transitioning from a consumer‑facing quick‑commerce delivery app to a **pure‑play B2B SaaS** platform that supplies retail chains with autonomous AI‑driven procurement, telemetry, and cryptographically‑verified ledger auditing. This pivot eliminates capital‑intensive fleet and warehouse liabilities while leveraging our core competencies:
- Autonomous negotiation agents that continuously source optimal wholesale pricing.
- Real‑time IoT temperature telemetry ensuring cold‑chain compliance.
- Double‑entry ledger with SHA‑256 chaining for immutable audit trails.

The target market is **global B2B procurement & warehouse management** (TAM ≈ $20B). Early adopters are mid‑size convenience‑store chains (e.g., Valora) that require low‑CapEx, rapid‑deployment solutions.

---

## 2. Business Objectives & Success Metrics
| Objective | Metric | Target |
|---|---|---|
| Revenue | Annual Recurring Revenue (ARR) | $100M by Year 3 |
| Retention | Net Revenue Retention (NRR) | >120% |
| Operational Efficiency | Fleet & Real‑Estate Costs | $0 (software‑only) |
| Compliance | Ledger Integrity | 100% transaction verification |
| Customer Satisfaction | SLA Uptime | ≥99.9% |

---

## 3. Market Analysis & Competitive Landscape
### 3.1 Total Addressable Market (TAM)
- Global B2B procurement & WMS market: **$20B+**.
- Primary verticals: FMCG retail, cold‑chain grocery, pharmaceuticals (Phase 2).

### 3.2 Competitor Matrix
| Competitor | Model | Strength | Swish OS Advantage |
|---|---|---|---|
| Instacart Platform Services | White‑label B2C SaaS | Scale, brand | **Data sovereignty & pricing control** |
| Takeoff Technologies | Hardware‑centric MFU | High throughput | **Zero‑CapEx software‑only** |
| Fabric WMS | Robotic MFU | Dense storage | **7‑day deployment** |
| Manhattan Associates | Legacy ERP | Maturity | **Autonomous AI procurement** |

---

## 4. Product Scope & Pilot Definition
### 4.1 Core SaaS Modules
1. **AI Procurement Engine** – real‑time price negotiation, contract lifecycle management.
2. **Telemetry Service** – IoT temperature sensors, edge validation, GDPR‑compliant data pipeline.
3. **Ledger Service** – double‑entry accounting, SHA‑256 chained journal, audit API.
4. **Operator Console** – dashboard for store managers, monitoring, manual overrides.
5. **API Gateway / BFF** – unified API surface for third‑party integration.

### 4.2 Year‑1 Pilot (Valora – “k‑kiosk”)
- **Scope**: 5 high‑traffic convenience hubs (Zurich HB, Bern, Basel SBB, Geneva Cornavin, Lucerne). 
- **Modules Enabled**: Tier 1 telemetry & ledger + Tier 2 AI procurement. 
- **Exclusions**: Pharmaceutical logistics, full‑scale robot picking, variable commission pricing.
- **Success Criteria**: ≥3% average procurement savings, <4 min pick‑cycle, 99.9% service uptime, 100% ledger verification.

---

## 5. Functional Requirements
| ID | Requirement | Description |
|---|---|---|
| FR‑01 | Retailer On‑boarding | Self‑service portal for store registration, sensor provisioning, and API key issuance. |
| FR‑02 | AI Negotiation Workflow | Event‑driven CDC outbox → Kafka → MongoDB pipeline that triggers price‑negotiation micro‑service. |
| FR‑03 | Telemetry Ingestion | Secure MQTT / HTTPS ingestion of temperature readings, stored in TimescaleDB for analytics. |
| FR‑04 | Ledger Auditing | Immutable transaction journal with SHA‑256 hash chaining; searchable via REST API. |
| FR‑05 | Operator Dashboard | Real‑time view of SKU inventory, alerts, and negotiation status; role‑based access control. |
| FR‑06 | Billing Engine | Flat‑tier pricing per active hub; automated invoice generation. |
| FR‑07 | Alert & Notification Service | SMS/Email/Webhook alerts on SLA breaches, temperature excursions, or ledger anomalies. |

---

## 6. Non‑Functional Requirements (NFR)
| ID | Category | Requirement |
|---|---|---|
| NFR‑01 | Performance | Kafka throughput ≥ 50 k messages/sec; end‑to‑end latency ≤ 200 ms for CDC events. |
| NFR‑02 | Scalability | Horizontal pod autoscaling (CPU > 60% → scale) for Kafka & MongoDB in Kubernetes. |
| NFR‑03 | Availability | 99.9% SLA per micro‑service; automated failover for Kafka controllers (KRaft mode). |
| NFR‑04 | Security | TLS‑mutual authentication for all inter‑service communication; OWASP Top‑10 compliance. |
| NFR‑05 | Data Residency | All EU store data stored in EU‑hosted PostgreSQL/TimescaleDB clusters. |
| NFR‑06 | Observability | OpenTelemetry tracing + Grafana dashboards for latency, error rates, and resource utilization. |
| NFR‑07 | Compliance | GDPR‑ready data deletion workflow; audit logs retained ≥7 years. |

---

## 7. Technical Architecture Overview
### 7.1 High‑Level Diagram
```mermaid
graph TD;
    Client[Operator Console] --> Edge[Nginx Edge Proxy];
    Edge --> API[API Gateway (BFF)];
    API --> Kafka[Kafka (KRaft)];
    API --> TimescaleDB[PostgreSQL + Timescale];
    API --> MongoDB[MongoDB Atlas];
    Kafka --> Outbox[CDC Outbox Service];
    Outbox --> AI[AI Procurement Service];
    AI --> MongoDB;
    TimescaleDB --> Telemetry[Telemetry Service];
    Telemetry --> Dashboard[Operator Dashboard];
    Dashboard --> Ledger[Ledger Service];
    Ledger --> Audit[Audit API];
```
### 7.2 Component Details
- **Kafka (KRaft)** – broker‑only mode, Zookeeper‑less, three‑node cluster with replication factor 3.
- **MongoDB Atlas** – sharded cluster for event‑sourced CDC data, TLS enforced.
- **PostgreSQL / TimescaleDB** – relational OLTP for ledger & telemetry analytics.
- **Redis** – caching layer for session state and rate‑limiting.
- **Kubernetes (EKS)** – deployment platform with Helm charts; HPA configured for Kafka & MongoDB.

---

## 8. Data Model Summary
| Entity | Primary Store | Key Fields |
|---|---|---|
| Store | PostgreSQL | store_id, location, tier, status |
| SKU | PostgreSQL | sku_id, description, unit_price |
| Telemetry | TimescaleDB | sensor_id, timestamp, temperature, humidity |
| NegotiationEvent | MongoDB | event_id, sku_id, proposed_price, vendor_id, status |
| LedgerEntry | PostgreSQL | entry_id, txn_hash, prev_hash, payload |

---

## 9. Risk & Mitigation Register
| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| Kafka throughput bottleneck | Service degradation | Medium | Conduct baseline JMeter load test (70% peak) before MVP; schedule full load test later. |
| Security gaps in Kafka‑MongoDB auth | Data breach | Low | Implement mutual TLS and rotate credentials every 90 days. |
| Autoscaling mis‑configuration | Out‑of‑memory crashes | Medium | Use conservative HPA thresholds; run chaos‑engineering smoke tests in staging. |
| Incomplete E2E coverage | Functional regressions | High | Add three CDC‑flow scenarios to Playwright suite; enforce CI gate. |
| Stakeholder sign‑off delay | Timeline slip | Low | Lightweight Confluence checkpoint after HLD review. |

---

## 10. Softened Gap Mitigation Measures (Updated)
1. **Stakeholder Sign‑off** – Replace a formal, multi‑department sign‑off with a **lightweight Confluence checkpoint** after HLD review. Approvers provide brief comments rather than a full sign‑off document.
2. **Security Audit** – Perform an **internal targeted security review** focused on Kafka configuration and MongoDB access controls. A full third‑party penetration test is deferred to post‑MVP.
3. **Performance Benchmarking** – Execute **baseline load tests** (≈70 % of expected peak) using existing JMeter scripts. Full‑scale stress testing will be scheduled for the next sprint.
4. **Scalability Validation** – Enable **horizontal pod autoscaling** for Kafka and MongoDB with conservative metrics (CPU > 60 %). Validate scaling behavior in a staging cluster; comprehensive chaos‑engineering tests are postponed.
5. **E2E Acceptance Testing** – Expand Playwright suite with **three critical CDC path scenarios** (create, update, delete) covering the Kafka → MongoDB pipeline. This addition is scoped to **one sprint** and will be merged into the CI pipeline.

---

## 11. Implementation Timeline (Milestones)
| Phase | Dates | Deliverable |
|---|---|---|
| **Phase 0 – Foundations** | 2026‑05‑15 → 2026‑05‑31 | Infra (K8s, Kafka KRaft, MongoDB), CI/CD pipeline |
| **Phase 1 – Core Services** | 2026‑06‑01 → 2026‑06‑30 | AI Procurement, Ledger, Telemetry services; basic dashboard |
| **Phase 2 – Pilot Enablement** | 2026‑07‑01 → 2026‑07‑31 | Valora pilot deployment, flat‑tier billing, monitoring |
| **Phase 3 – Validation & Scaling** | 2026‑08‑01 → 2026‑09‑15 | Load testing, HPA tuning, security audit |
| **Phase 4 – General Availability** | 2026‑10‑01 | GA release to additional retail partners |

---

## 12. Financial Projections (Re‑stated)
| Metric | Year 1 (Pilot) | Year 2 (Rollout) | Year 3 (Scale) |
|---|---|---|---|
| Active Hub Nodes | 5 | 100 | 500 |
| Tier 1 Revenue | $60,000 | $0 | $0 |
| Tier 2 Revenue | $12,000 | $1.8M | $9M |
| Gross Revenue | $72k | $1.8M | $9M |
| OpEx | $90k | $1.2M | $4.5M |
| EBITDA | -$28k | +$0.55M | +$4.35M |

---

## 13. Approval Sign‑off
| Role | Name | Signature | Date |
|---|---|---|---|
| CFO | Beat Keller |  | 2026‑06‑02 |
| CTO | Lina Schmidt |  | 2026‑06‑02 |
| Product Owner | Maya Patel |  | 2026‑06‑02 |
| Architect | Alex Novak |  | 2026‑06‑02 |

---

*Prepared by the Swish OS Architecture Team.*
