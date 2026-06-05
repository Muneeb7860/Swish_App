# Architecture Diagrams — Swish Payment Ecosystem (Microservices)

This document contains the formal C4 architecture artifacts for the Swish Quick Commerce platform, reflecting the updated **database-per-service microservices architecture** and newer services (`platform-gateway`, `core-business-engine`, `notification-engine`, `shared-async-services`) added in recent sprints.

> **Migration Strategy**: Strangler Fig pattern with blue/green deployment and API versioning (`Accept-Version: v1, v2`).

---

## C4 Context Diagram

Shows all external personas, the DMZ boundary, API Gateways, new and legacy microservices, and shared infrastructure components.

```mermaid
flowchart TB
  subgraph Personas
    Customer["Customer (Mobile/Web)"]
    Rider["Rider (Mobile)"]
    DSWorker["Dark Store Worker / Picker"]
    DSManager["Dark Store Manager"]
    Business["Business / Wholesaler"]
    Admin["Platform Admin"]
  end

  subgraph DMZ
    Edge["Nginx Edge Proxy / WAF"]
    RateLimiter["Rate Limiter / DDoS Shield"]
  end

  subgraph API_Gateway["API Gateway Layer"]
    PlatformGateway["platform-gateway (Spring Cloud Gateway, Port 8080)"]
    LegacyBFF["Legacy BFF Gateway (Spring Cloud Gateway, Port 8081)"]
  end

  subgraph Microservices["Backend Microservices Suite"]
    BackendCore["backend (Hexagonal Core, Port 8080)"]
    CoreBusinessEngine["core-business-engine (Checkout/Inv/B2B, Port 8081)"]
    NotificationEngine["notification-engine (Kafka Consumer/WS, Port 8082)"]
    SharedAsyncServices["shared-async-services (AI Orchestrator/Ledger)"]
  end

  subgraph Datastores["Datastores & Caching"]
    PostgresDB["PostgreSQL (b2b_qcomm DB)"]
    RedisCache["Redis (logical DB0-DB3 isolation, Rate Limiting)"]
    Mongo["MongoDB (Immutable Audit Archive)"]
    Vault["HashiCorp Vault (Secrets)"]
  end

  subgraph SharedInfra["Shared Event Pipeline"]
    Kafka["Apache Kafka (9 topics + 9 DLQs)"]
  end

  subgraph Observability
    Zipkin["Zipkin Distributed Tracing"]
    Prometheus["Prometheus Metrics"]
    Grafana["Grafana Dashboards"]
  end

  subgraph ExternalGateways["External Payment Gateways"]
    Stripe["Stripe (Mocked)"]
    Swipe["Swipe"]
    PayPal["PayPal"]
    COD["Cash on Delivery"]
  end

  Customer -->|"HTTPS"| Edge
  Rider -->|"HTTPS"| Edge
  DSWorker -->|"HTTPS"| Edge
  DSManager -->|"HTTPS"| Edge
  Business -->|"HTTPS"| Edge
  Admin -->|"HTTPS"| Edge

  Edge --> RateLimiter
  RateLimiter -->|"TLS termination + JWT filter"| PlatformGateway
  RateLimiter -.->|"Legacy route"| LegacyBFF

  PlatformGateway -->|"/api/checkout/**, /api/inventory/**"| CoreBusinessEngine
  PlatformGateway -->|"/api/rewards/**, /api/ledger/**"| SharedAsyncServices
  PlatformGateway -->|"/ws/notifications/**"| NotificationEngine
  PlatformGateway -->|"/api/v1/auth/**, /api/transaction/**"| BackendCore

  CoreBusinessEngine --> PostgresDB
  BackendCore --> PostgresDB
  NotificationEngine --> PostgresDB
  SharedAsyncServices --> PostgresDB

  CoreBusinessEngine -->|"Strategy Pattern"| Stripe
  CoreBusinessEngine -->|"Strategy Pattern"| Swipe
  CoreBusinessEngine -->|"Strategy Pattern"| PayPal
  CoreBusinessEngine -->|"Strategy Pattern"| COD

  BackendCore -.->|"publish/consume"| Kafka
  CoreBusinessEngine -.->|"publish/consume"| Kafka
  NotificationEngine -.->|"consume"| Kafka
  SharedAsyncServices -.->|"publish/consume"| Kafka

  BackendCore -.-> RedisCache
  CoreBusinessEngine -.-> RedisCache
  NotificationEngine -.-> RedisCache
  PlatformGateway -.-> RedisCache

  BackendCore -.-> Mongo
  BackendCore -.-> Vault

  BackendCore -.-> Prometheus
  CoreBusinessEngine -.-> Prometheus
  NotificationEngine -.-> Prometheus
  PlatformGateway -.-> Prometheus
  Prometheus --> Grafana
```

---

## C4 Container Diagram

Database-per-service layout mapping the container boundaries, ports, and databases (PostgreSQL databases, Redis logical caching, Kafka topics) for the updated service suite.

```mermaid
flowchart TB
  subgraph Clients
    WebApp["Web Application (frontend-host Shell)"]
    CustomerApp["Customer App (frontend-customer)"]
    RiderApp["Rider App (frontend-rider)"]
    B2bDashboard["B2B Wholesale Dashboard (frontend-b2b)"]
    AdminPortal["Admin Portal (frontend-admin)"]
  end

  subgraph Gateway["Gateway Tier"]
    PlatformGW["platform-gateway (Spring Cloud Gateway, Port 8080)"]
    IdempFilter["IdempotencyFilterFactory"]
    RateLimiterFilter["Redis Rate Limiter"]
    SecurityConf["SecurityConfig"]
  end

  subgraph CoreServices["Core Service Tier"]
    subgraph BackendModule["backend Service (Hexagonal Core, Port 8080)"]
      HexCore["Hexagonal Domains (Transaction, Payment, Inventory)"]
    end
    
    subgraph CoreBusiness["core-business-engine (Port 8081)"]
      CheckoutSvc["Checkout & Stripe-Mock"]
      B2bOrderSvc["B2bOrderService"]
      AiSweeper["AI Evaluation Timeout Sweeper"]
    end

    subgraph NotificationSvc["notification-engine (Port 8082)"]
      KafkaCons["NotificationKafkaConsumer"]
      RedisPubSub["Redis Pub/Sub & WebSockets"]
      Channels["Dispatcher & Channels (Email/Push/WS)"]
    end

    subgraph SharedAsync["shared-async-services"]
      AiOrch["AI Model Orchestration Port"]
      FinLedger["Financial Ledger Entry"]
    end
  end

  subgraph Storage["Datastore Backplane"]
    Postgres["PostgreSQL (b2b_qcomm DB)"]
    Redis["Redis (DB0: Sessions, DB1: Rate Limits, DB2: Pub/Sub)"]
    MongoDB["MongoDB (Audit Logs Archive)"]
    Vault["HashiCorp Vault"]
  end

  subgraph MessageBroker["Message Broker"]
    direction LR
    T1["b2b.checkout.events"]
    T2["b2b.wholesale.order.placed"]
    T3["b2b.wholesale.order.evaluated"]
    T4["security.audit"]
    DLQ["DLQ Companion Topics"]
  end

  WebApp --> PlatformGW
  CustomerApp --> PlatformGW
  RiderApp --> PlatformGW
  B2bDashboard --> PlatformGW
  AdminPortal --> PlatformGW

  PlatformGW --> IdempFilter
  PlatformGW --> RateLimiterFilter
  PlatformGW --> SecurityConf

  PlatformGW -->|"/api/checkout/**"| CoreBusiness
  PlatformGW -->|"/ws/notifications/**"| NotificationSvc
  PlatformGW -->|"/api/ledger/**"| SharedAsync
  PlatformGW -->|"/api/transaction/**"| BackendModule

  BackendModule --> Postgres
  CoreBusiness --> Postgres
  NotificationSvc --> Postgres

  BackendModule -.-> Redis
  CoreBusiness -.-> Redis
  NotificationSvc -.-> Redis
  PlatformGW -.-> Redis

  CoreBusiness -.->|"publish"| T1
  CoreBusiness -.->|"publish"| T2
  NotificationSvc -.->|"consume"| T3
  BackendModule -.->|"publish"| T4

  BackendModule -.-> MongoDB
  BackendModule -.-> Vault
```

---

## C4 Component Diagram — Payment Service Internals

Deep dive into the Payment Service showing the Saga Orchestrator, State Machine, Circuit Breaker, Outbox Publisher, Idempotency Filter, and Gateway Strategy.

```mermaid
flowchart TB
  subgraph PaymentServiceComponents["Payment Service Components"]
    API["PaymentController (REST API)"]
    IdempFilter["IdempotencyFilter"]
    StateMachine["PaymentStateMachine"]
    SagaOrch["SagaOrchestrator"]
    CircuitBreaker["CircuitBreaker (Resilience4j)"]
    OutboxPub["OutboxPublisher"]
    GatewayStrategy["PaymentGatewayStrategy"]
    RetryHandler["RetryHandler (Exponential Backoff)"]
    DLQRouter["DLQRouter"]
    CorrelationID["CorrelationIdFilter (MDC)"]
  end

  subgraph Persistence
    PayDB["payment_db (PostgreSQL)"]
    PaymentsTable["payments table"]
    OutboxTable["payment_outbox table"]
    ProcessedTable["processed_events table"]
  end

  subgraph ExternalSystems
    KafkaBroker["Kafka Broker"]
    StripeGW["Stripe Gateway"]
    SwipeGW["Swipe Gateway"]
    PayPalGW["PayPal Gateway"]
    CODGW["COD Handler"]
  end

  API -->|"1. Receive request"| CorrelationID
  CorrelationID -->|"2. Inject correlation ID"| IdempFilter
  IdempFilter -->|"3. Check processed_events"| ProcessedTable
  IdempFilter -->|"4. Deduplicated request"| SagaOrch

  SagaOrch -->|"5. Drive state transitions"| StateMachine
  StateMachine -->|"6. Persist state"| PaymentsTable

  SagaOrch -->|"7. Call gateway via circuit breaker"| CircuitBreaker
  CircuitBreaker -->|"8a. Delegate to strategy"| GatewayStrategy
  CircuitBreaker -->|"8b. On failure"| RetryHandler
  RetryHandler -->|"8c. Exhausted retries"| DLQRouter

  GatewayStrategy -->|"Stripe"| StripeGW
  GatewayStrategy -->|"Swipe"| SwipeGW
  GatewayStrategy -->|"PayPal"| PayPalGW
  GatewayStrategy -->|"COD"| CODGW

  SagaOrch -->|"9. Write outbox event"| OutboxPub
  OutboxPub -->|"10. Persist to outbox"| OutboxTable
  OutboxPub -->|"11. Publish event"| KafkaBroker

  DLQRouter -->|"Route to DLQ topic"| KafkaBroker
```

---

## Payment State Machine

```mermaid
stateDiagram-v2
  [*] --> INITIATED
  INITIATED --> BALANCE_CHECKED : Balance sufficient
  INITIATED --> FAILED : Balance insufficient
  BALANCE_CHECKED --> FRAUD_SCREENED : Fraud check passed
  BALANCE_CHECKED --> FAILED : Fraud check failed
  FRAUD_SCREENED --> AUTHORIZED : Gateway pre-auth success
  FRAUD_SCREENED --> FAILED : Gateway pre-auth rejected
  AUTHORIZED --> CAPTURED : Capture confirmed
  AUTHORIZED --> FAILED : Capture timeout
  CAPTURED --> REFUNDED : Refund requested
  CAPTURED --> [*] : Terminal success
  REFUNDED --> [*] : Terminal refund
  FAILED --> [*] : Terminal failure
```

---

## Kafka Topic Topology

| # | Topic Name                       | Publisher            | Consumer(s)                      | DLQ Companion                           |
|---|----------------------------------|----------------------|----------------------------------|-----------------------------------------|
| 1 | `b2b.checkout.events`            | Core Business Engine | Platform Gateway                 | `b2b.checkout.events.dlq`               |
| 2 | `b2b.wholesale.order.placed`     | Core Business Engine | B2B Procurement Agent            | `b2b.wholesale.order.placed.dlq`        |
| 3 | `b2b.wholesale.order.evaluated`  | Core Business Engine | Notification Engine              | `b2b.wholesale.order.evaluated.DLQ`     |
| 4 | `payment.initiated`              | Backend Core         | Account Service                  | `payment.initiated.dlq`                 |
| 5 | `payment.authorized`             | Backend Core         | Transaction Service              | `payment.authorized.dlq`                |
| 6 | `payment.captured`               | Backend Core         | Transaction, Notification        | `payment.captured.dlq`                  |
| 7 | `payment.failed`                 | Backend Core         | Notification Service             | `payment.failed.dlq`                    |
| 8 | `payment.refunded`               | Backend Core         | Account, Transaction, Notif.     | `payment.refunded.dlq`                  |
| 9 | `security.audit`                 | All Services         | Security Engine                  | `security.audit.dlq`                    |

## Redis Logical Database Allocation

| DB  | Purpose              | Service(s)                | Data Pattern             |
|-----|----------------------|---------------------------|--------------------------|
| DB0 | Session Store        | User Service / Backend    | JWT session tokens       |
| DB1 | Rate Limiting Cache  | Platform Gateway          | Sliding window counters  |
| DB2 | WebSocket Pub/Sub    | Notification Engine       | Redis Channels           |
| DB3 | Product Catalog Cache| Catalog Service / Engine  | Cache-aside products    |

---

## Design Patterns Applied

| Pattern                        | Implementation                                      |
|--------------------------------|-----------------------------------------------------|
| Outbox Pattern                 | `payment_outbox` table + `OutboxPublisher` CDC       |
| Idempotency                    | Redis-based `IdempotencyFilter` in gateway          |
| Circuit Breaker                | Resilience4j wrapping client gateway calls          |
| Retry with Exponential Backoff | `RetryHandler` with jitter on transient failures     |
| Dead Letter Queue              | DLQ companion topics for poison pill isolation      |
| Choreography Saga              | Kafka-driven event chain across services             |
| Correlation ID                 | MDC filter + Kafka header propagation                |
| Strategy Pattern               | `PaymentGatewayStrategy` for Stripe/Swipe/PayPal/COD |
| Strangler Fig                  | Gateway-driven routing to new vs legacy backend     |

> **Notes:**
> - Each microservice owns its PostgreSQL database schema exclusively — no cross-service joins.
> - Async communication uses Kafka; synchronous fallback is only for health checks.
> - The Security Engine consumes audit events from all services and archives to MongoDB.
> - HashiCorp Vault manages all secrets; services fetch credentials at startup via Vault Agent sidecar.
