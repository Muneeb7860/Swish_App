# Low-Level Design Diagrams

This document captures the missing LLD artifacts for sequence, class, and use case diagrams.

## Use Case Diagram

```mermaid
flowchart TB
  actor Customer
  actor Rider
  actor Admin
  actor System

  Customer -->|Place order| UC1[Place Order]
  Customer -->|Track order| UC2[Track Order]
  Customer -->|Authenticate| UC3[Login / MFA]

  Rider -->|Onboard| UC4[Register Rider]
  Rider -->|Report telemetry| UC5[Submit Delivery Telemetry]
  Rider -->|Complete delivery| UC6[Deliver Order]

  Admin -->|Manage operations| UC7[Inject / Resolve Chaos Faults]
  Admin -->|Approve onboarding| UC8[Approve Rider or Merchant]
  Admin -->|Review HITL| UC9[Process Human-in-the-Loop Tickets]

  System -->|Publish events| UC10[Dispatch Outbox Events]
  System -->|Archive analytics| UC11[Persist Telemetry Archive]
```

## Sequence Diagram: Order Placement

```mermaid
sequenceDiagram
  participant CustomerUI as Customer Frontend
  participant BFF as BFF Gateway
  participant Backend as Swish Backend
  participant Postgres as PostgreSQL
  participant Outbox as Outbox Scheduler
  participant Kafka as Kafka Broker

  CustomerUI->>BFF: POST /api/customer/orders
  BFF->>Backend: forward authenticated request
  Backend->>Backend: validate checkout and reserve inventory
  Backend->>Postgres: insert order + order_items
  Backend->>Postgres: insert outbox_events record
  Backend-->>BFF: 201 Created
  Outbox->>Postgres: poll pending outbox events
  Outbox->>Kafka: publish event and mark PUBLISHED
  Kafka-->>Backend: downstream consumers process event
```
```

## Sequence Diagram: Telemetry Ingestion

```mermaid
sequenceDiagram
  participant RiderApp as Rider Frontend
  participant BFF as BFF Gateway
  participant Backend as Swish Backend
  participant Redis as Redis Geo Store
  participant Kafka as Kafka Broker
  participant Mongo as MongoDB Archive

  RiderApp->>BFF: POST /api/telemetry/tick
  BFF->>Backend: forward telemetry payload
  Backend->>Redis: update geo/telemetry cache
  Backend->>Kafka: emit telemetry event
  Kafka->>Mongo: archive event document
  Backend-->>BFF: 202 Accepted
```
```

## Class Diagram

```mermaid
classDiagram
  class AuthController {
    +login()
    +verifyMfa()
  }
  class CustomerController {
    +getCatalog()
    +purgeProfile()
  }
  class OrderController {
    +createOrder()
    +getOrders()
    +refundOrder()
  }
  class RiderController {
    +onboard()
    +submitTelemetry()
    +deliverOrder()
    +completeCourse()
  }
  class InventoryController {
    +getPickerQueue()
    +rebalance()
    +handover()
  }
  class WholesalerController {
    +getRestocks()
    +createRestock()
    +fulfillRestock()
    +listInvoices()
  }
  class AdminController {
    +createChaosFault()
    +resolveChaosFault()
    +getActiveChaos()
    +approveOnboard()
    +getHitlQueue()
    +resolveHitlTicket()
  }
  class TelemetryController {
    +tick()
    +streamTelemetry()
    +dryIce()
  }
  class OutboxEventScheduler {
    +processPendingEvents()
  }
  class OutboxEventRepository {
    +save()
    +findAll()
  }
  class OutboxEvent {
    +id
    +aggregateType
    +aggregateId
    +eventType
    +payload
    +status
  }

  AuthController --> CustomerController
  CustomerController --> OrderController
  OrderController --> OutboxEventRepository
  RiderController --> TelemetryController
  InventoryController --> OrderController
  AdminController --> TelemetryController
  OutboxEventScheduler --> OutboxEventRepository
```

> Notes:
> - The class diagram reflects the broader backend controller surface discovered in the repository.
> - The sequence diagrams are aligned with the live BFF contract and actual telemetry/outbox implementation.
