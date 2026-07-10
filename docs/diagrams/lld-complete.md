# Low-Level Design — Complete & Validated

The LLD comprises three diagram types. This document completes **use case** and
**class** diagrams for the whole project and validates them against the BRD,
HLD, and ERD. Sequence diagrams are already complete elsewhere.

| LLD artefact | Location | Status |
| :--- | :--- | :--- |
| **Use case** diagrams | this doc §1 | ✅ whole project (was payment-only) |
| **Sequence** diagrams | `domain-sequence-diagrams.md` (+ part 2), `lld-diagrams.md` (payment) | ✅ all stateful flows |
| **Class** diagrams | this doc §2, `lld-diagrams.md` (payment) | ✅ all core contexts |
| **Validation** (BRD / HLD / ERD) | this doc §3 | ✅ traceability + divergences |

---

## 1. Use Case Diagrams (per actor)

> mermaid has no native use-case notation; actors are stadium nodes, use cases
> are rounded nodes (replacing the earlier invalid `actor` keyword inside
> `flowchart`). The BRD FR each use case satisfies is shown in brackets.

### 1.1 Customer

```mermaid
flowchart LR
  C([Customer])
  C --> UC1(["Browse catalog"])
  C --> UC2(["Checkout / place order"])
  C --> UC3(["Track delivery (live telemetry)"])
  C --> UC4(["Request refund"])
  C --> UC5(["Redeem / earn loyalty points"])
  C --> UC6(["GDPR profile purge"])
  C --> UC7(["Register / login (MFA)"])
```

### 1.2 Rider

```mermaid
flowchart LR
  R([Rider])
  R --> UR1(["Submit onboarding application"])
  R --> UR2(["Complete academy course"])
  R --> UR3(["Submit cold-chain telemetry  [FR-03]"])
  R --> UR4(["Confirm delivery (PIN / photo)"])
  R --> UR5(["Reject delivery at door"])
  R --> UR6(["Inject coolant (dry ice)"])
```

### 1.3 Wholesaler / B2B & Operator

```mermaid
flowchart LR
  W([Wholesaler])
  OP([Operator / Admin])
  W --> UW1(["Submit RFQ auction bid  [FR-02]"])
  W --> UW2(["Fulfil purchase order"])
  OP --> UO1(["Operator dashboard: inventory + alerts  [FR-05]"])
  OP --> UO2(["Approve B2B procurement HITL  [FR-02]"])
  OP --> UO3(["3-gate rider onboarding  [FR-01]"])
  OP --> UO4(["Resolve refund / support HITL"])
  OP --> UO5(["Inject chaos fault (resilience test)"])
  OP --> UO6(["Sign delivery summary (RSA)  [FR-04]"])
```

### 1.4 AI Agent & System (automated actors)

```mermaid
flowchart LR
  AG([AI Agent])
  SYS([System / Schedulers])
  AG --> UA1(["Orchestrate support reply + HITL escalation"])
  AG --> UA2(["Run RFQ reverse auction  [FR-02]"])
  AG --> UA3(["Enforce budget / rate guardrail"])
  SYS --> US1(["Relay transactional outbox → Kafka  [FR-02]"])
  SYS --> US2(["Append hash-chained ledger entry  [FR-04]"])
  SYS --> US3(["Detect cold-chain breach + write-off  [FR-03]"])
  SYS --> US4(["Send SLA / temp / anomaly alerts  [FR-07]"])
  SYS --> US5(["ETL OLTP → OLAP warehouse"])
```

---

## 2. Class Diagrams (per bounded-context cluster)

Attributes mirror the validated as-built ERD
([`data-model-erd-asbuilt.md`](./data-model-erd-asbuilt.md)); methods mirror the
`core/service` implementations. Hexagonal: `*UseCase` = port-in, `*Port` =
port-out, `*ServiceImpl` = core.

### 2.1 Commerce & Order

```mermaid
classDiagram
  class OrderUseCase {
    <<interface>>
    +checkout(customerId, items, paymentMethod, tip, bags, idempotencyKey) Order
    +requestRefund(orderId, reason, lat, lng) Map
    +getCustomerOrders(customerId) List~Order~
  }
  class OrderServiceImpl {
    -OrderPort orderPort
    -InventoryPort inventoryPort
    -LedgerUseCase ledgerUseCase
    -OutboxEventPort outboxEventPort
    +checkout(...) Order
    -evaluateCheckoutRouting(items) String
    -findOptimalRider(perishable) Rider
  }
  class Order {
    +Integer orderId
    +String status
    +BigDecimal totalAmount
    +int slaCountdownSec
    +String idempotencyKey
  }
  class OrderItem {
    +int quantity
    +BigDecimal price
  }
  class Customer {
    +String customerId
    +BigDecimal walletBalance
    +int loyaltyPoints
    +int trustScore
    +boolean isOnProbation
  }
  class Inventory {
    +String itemId
    +int stock
    +boolean perishable
    +long version
  }
  OrderUseCase <|.. OrderServiceImpl
  OrderServiceImpl --> Order : creates
  Order "1" --> "*" OrderItem
  Order --> Customer
  OrderItem --> Inventory
  OrderServiceImpl ..> LedgerUseCase : posts legs
  OrderServiceImpl ..> OutboxEventPort : order.placed
```

### 2.2 Finance — Double-Entry Ledger

```mermaid
classDiagram
  class LedgerUseCase {
    <<interface>>
    +recordTransaction(reference, description, legs) JournalEntry
    +getCustomerLedger(customerId) List~LedgerLine~
  }
  class LedgerServiceImpl {
    +recordTransaction(...) JournalEntry
    -adjustActorWallet(type, actorId, debit, credit)
    -computeSHA256Hash(...) String
  }
  class JournalEntry {
    +Integer entryId
    +UUID entryUuid
    +String reference
    +String previousEntryHash
    +String entryHash
  }
  class LedgerLine {
    +String accountType
    +String actorId
    +BigDecimal debit
    +BigDecimal credit
  }
  LedgerUseCase <|.. LedgerServiceImpl
  JournalEntry "1" --> "1..*" LedgerLine : balanced legs
  LedgerServiceImpl --> JournalEntry : hash-chains
  note for JournalEntry "DB triggers enforce SUM(debit)=SUM(credit) + SHA-256 chain"
```

### 2.3 Logistics — Dispatch & Telemetry

```mermaid
classDiagram
  class DispatchUseCase {
    <<interface>>
    +assignOrder(orderId, riderId, weightKg) ActiveShipment
    +updateRiderGps(riderId, lat, lng) ActiveShipment
    +runReallocationAudit() List~Integer~
  }
  class TelemetryUseCase {
    <<interface>>
    +queueTick(orderId, lat, lng, temp, dryIce)
    +flushTickBuffer()
    +recordTelemetry(...) OrderTelemetryLog
  }
  class Rider {
    +String riderId
    +String onboardingStatus
    +int trustScore
    +boolean gearExempt
  }
  class ActiveShipment {
    +int orderId
    +ShipmentStatus status
    +OffsetDateTime stationarySince
  }
  class OrderTelemetryLog {
    +BigDecimal temperature
    +boolean alertTriggered
  }
  DispatchUseCase <|.. DispatchServiceImpl
  TelemetryUseCase <|.. TelemetryServiceImpl
  DispatchServiceImpl --> Rider : eligibility
  DispatchServiceImpl --> ActiveShipment
  TelemetryServiceImpl --> OrderTelemetryLog : cold-chain
  TelemetryServiceImpl ..> LedgerUseCase : COLD-BREACH write-off
```

### 2.4 Agent, Wholesaler & Governance

```mermaid
classDiagram
  class AgentUseCase {
    <<interface>>
    +processMessage(AgentRequest) AgentResponse
    +negotiateProcurement(NegotiationRequest) NegotiationResponse
  }
  class MasterOrchestratorService {
    -CustomerSupportAgent supportAgent
    -B2BProcurementAgent procurementAgent
    -ProcurementGuardrailsEngine guardrails
    +processMessage(...) AgentResponse
    -triggerHitl(...) String
  }
  class GovernanceUseCase {
    <<interface>>
    +auditNegotiation(restockId, wholesalerId, amount)
    +approveOverride(approvalId, operator, reason)
    +signDeliverySummary(orderId, podHash) String
  }
  class Wholesaler {
    +String wholesalerId
    +boolean isActive
    +int trustScore
  }
  class B2BRestockOrder {
    +Integer restockOrderId
    +String status
  }
  class ProcurementApproval {
    +String status
    +String overrideBy
  }
  AgentUseCase <|.. MasterOrchestratorService
  GovernanceUseCase <|.. GovernanceServiceImpl
  MasterOrchestratorService --> Wholesaler : RFQ auction
  MasterOrchestratorService --> B2BRestockOrder : on guardrail reject
  MasterOrchestratorService ..> GovernanceUseCase : HITL escalate
  B2BRestockOrder --> ProcurementApproval : gated by
```

### 2.5 Auth & Security

```mermaid
classDiagram
  class AuthenticationUseCase {
    <<interface>>
    +login(email, password, device, ip) Session
    +logout(sessionId)
  }
  class TokenServicePort {
    <<interface>>
    +generateToken(sessionId, userId, role) String
    +validateToken(token) boolean
  }
  class UserAccount {
    +String id
    +EmailAddress email
    +String role
  }
  class Session {
    +String id
    +OffsetDateTime expiresAt
    +boolean active
  }
  AuthenticationUseCase <|.. AuthServiceImpl
  TokenServicePort <|.. TokenServiceAdapter
  AuthServiceImpl --> Session : issues
  AuthServiceImpl --> UserAccount
  JwtAuthenticationFilter ..> TokenServicePort : validates
  JwtAuthenticationFilter --> OpaAuthorizationManager : delegates authz
```

---

## 3. Validation

### 3.1 LLD ↔ BRD (use cases ⇒ functional requirements)

| BRD FR | Requirement | As-built realization | Status |
| :-- | :--- | :--- | :-: |
| FR-01 | Retailer on-boarding | 3-gate onboarding exists, but for **riders**, not retailers; no sensor provisioning / API-key issuance | 🟡 partial |
| FR-02 | AI negotiation (outbox→Kafka→Mongo) | `MasterOrchestratorService.negotiateProcurement` RFQ auction + outbox→Kafka relay; **Mongo leg not wired** | 🟡 partial |
| FR-03 | Telemetry ingestion | `TelemetryServiceImpl` cold-chain CQRS, but **PostgreSQL not TimescaleDB**, HTTP not MQTT | 🟡 partial |
| FR-04 | Ledger auditing (SHA-256 chain, REST) | `LedgerServiceImpl` + DB hash-chain trigger + `/ledger` REST | ✅ full |
| FR-05 | Operator dashboard + RBAC | `frontend-admin` + JWT/OPA RBAC + HITL queue | ✅ full |
| FR-06 | Billing engine (per-hub flat tier) | **Not implemented** — no billing/invoicing module | 🔴 gap |
| FR-07 | Alert & notification | `notification-engine` (Email/SMS/Push/WS) + cold-chain/SLA alerts | ✅ full |

### 3.2 LLD ↔ ERD (class ⇒ table)

| Class | ERD table | Match |
| :--- | :--- | :-: |
| Customer / Order / OrderItem / Inventory | `oltp.customers/orders/order_items/inventory` | ✅ |
| JournalEntry / LedgerLine | `oltp.journal_entries/ledger_lines` | ✅ |
| Rider / ActiveShipment / OrderTelemetryLog | `oltp.riders` / `dispatch.active_shipments` / `oltp.order_telemetry_logs` | ✅ |
| Wholesaler / B2BRestockOrder / ProcurementApproval | `oltp.wholesalers/b2b_restock_orders/procurement_approvals` | ✅ |
| UserAccount / Session | `oltp.user_accounts/sessions` | ✅ |

All class entities resolve to validated ERD tables; the ERD's own open items
(F-3 `RewardPointsEntity`↔`customers`, F-5 dual order models) are noted there.

### 3.3 LLD ↔ HLD (pattern ⇒ realization)

| HLD pattern | LLD realization | Status |
| :--- | :--- | :-: |
| Transactional Outbox | `OutboxEventScheduler` + `oltp.outbox_events` | ✅ |
| Choreography Saga + compensation | `OrderSagaManager` / `OrderSagaListener` | ✅ |
| Idempotency | unique `idempotency_key` + `@TransactionalRetry` | ✅ |
| Circuit Breaker / Retry / DLQ | Resilience4j + outbox retry→FAILED | ✅ (gateway side) |
| Correlation ID | `X-Correlation-ID` MDC in outbox relay | ✅ |
| Kafka topic topology | `OutboxEventScheduler.TOPIC_ROUTING` | ✅ |
| **Database-per-service (HLD)** | as-built is a **modular monolith** (4 schemas, 1 DB) | 🟡 divergent |
| **MongoDB / TimescaleDB / Vault (HLD)** | not wired (PostgreSQL + H2/Postgres only) | 🟡 divergent |

### 3.4 Architect's divergence summary

The **BRD/HLD describe a B2B-SaaS, database-per-service, MongoDB+TimescaleDB+
Vault microservices platform**; the **as-built is a B2C quick-commerce modular
monolith (PostgreSQL) with B2B procurement** plus a standalone Python
hybrid-agentic governance lib. The two are ~70% aligned at the capability level
(ledger, telemetry, AI negotiation, alerts, RBAC all exist) but diverge on
deployment topology and three concrete items:

1. **FR-06 Billing engine** — not implemented (🔴 the only hard functional gap).
2. **FR-01** — onboarding is rider-centric, not retailer self-service.
3. **Topology** — monolith vs database-per-service; Mongo/Timescale/Vault/MQTT unrealised.

**Recommendation:** either (a) reconcile the BRD/HLD to the as-built reality
(re-baseline as "modular monolith, PostgreSQL, hybrid on-prem AI"), or (b) treat
the gaps as a forward backlog (billing module, retailer portal, Mongo CDC leg).
The diagram layer (use case · sequence · class · ERD · C4) is now complete and
internally consistent.
