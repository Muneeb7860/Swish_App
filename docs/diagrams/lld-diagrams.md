# Low-Level Design Diagrams (Microservices)

This document captures the LLD artifacts for sequence, class, and use case diagrams in the new microservices architecture.

## 1. Payment Saga Sequence Diagram (Choreography)

```mermaid
sequenceDiagram
  participant Client
  participant BFF as BFF Gateway
  participant Pay as Payment Service
  participant Kafka
  participant Acc as Account Service
  participant Fraud as Fraud Service
  participant GW as Ext Gateway
  participant Tx as Transaction Service
  participant Notif as Notification Service

  Client->>BFF: POST /api/payments (+ X-Idempotency-Key)
  BFF->>Pay: Forward w/ X-Correlation-ID
  Pay->>Pay: Save Payment (INITIATED)
  Pay->>Kafka: publish payment.initiated
  
  Kafka->>Acc: consume payment.initiated
  Acc->>Acc: Deduplicate & Check Balance
  Acc->>Kafka: publish payment.balance-check (success)
  
  Kafka->>Pay: consume payment.balance-check
  Pay->>Pay: Update Payment (BALANCE_CHECKED)
  Pay->>Kafka: publish payment.fraud-screen
  
  Kafka->>Fraud: consume payment.fraud-screen
  Fraud->>Fraud: Deduplicate & Velocity Check
  Fraud->>Kafka: publish payment.authorized (fraud cleared)
  
  Kafka->>Pay: consume payment.authorized
  Pay->>Pay: Update Payment (FRAUD_SCREENED)
  Pay->>GW: REST call to Stripe/PayPal (Circuit Breaker)
  GW-->>Pay: 200 OK (Gateway Authorized)
  Pay->>Pay: Update Payment (AUTHORIZED)
  
  Pay->>Kafka: publish payment.captured
  
  par Parallel Async Processing
    Kafka->>Tx: consume payment.captured (Write Ledger)
    Kafka->>Notif: consume payment.captured (Send SMS)
    Kafka->>Acc: consume payment.captured (Debit Wallet)
  end
```

## 2. Compensation / Rollback Sequence (Failure Path)

```mermaid
sequenceDiagram
  participant Pay as Payment Service
  participant Kafka
  participant Acc as Account Service
  
  Note over Pay: Fraud Check Fails OR Gateway Declines
  Pay->>Pay: Update Payment (FAILED)
  Pay->>Kafka: publish payment.compensation (ROLLBACK)
  
  Kafka->>Acc: consume payment.compensation
  Acc->>Acc: Refund wallet balance (credit)
```

## 3. Class Diagram (Payment Service)

```mermaid
classDiagram
  class PaymentController {
    +createPayment()
    +capturePayment()
  }
  class CorrelationIdFilter {
    +doFilterInternal()
  }
  class IdempotencyFilter {
    +checkProcessed()
  }
  class SagaOrchestrator {
    +handleBalanceCheck()
    +handleFraudScreen()
    +handleGatewayAuth()
  }
  class PaymentStateMachine {
    +transitionTo(status)
  }
  class PaymentGatewayStrategy {
    <<interface>>
    +authorize()
    +capture()
  }
  class StripeAdapter {
    +authorize()
  }
  class CircuitBreakerConfig {
    +getGatewayResilience()
  }
  class OutboxPublisher {
    +publish(eventType, payload)
  }

  PaymentController --> CorrelationIdFilter
  CorrelationIdFilter --> IdempotencyFilter
  IdempotencyFilter --> SagaOrchestrator
  SagaOrchestrator --> PaymentStateMachine
  SagaOrchestrator --> PaymentGatewayStrategy
  SagaOrchestrator --> OutboxPublisher
  PaymentGatewayStrategy <|-- StripeAdapter
  PaymentGatewayStrategy --> CircuitBreakerConfig
```

## 4. Use Case Diagram

```mermaid
flowchart TB
  actor Customer
  actor Rider
  actor Admin
  actor System

  Customer -->|Place order| UC1[Place Order]
  Customer -->|Make payment| UC2[Authorize Payment]

  Rider -->|Onboard| UC4[Register Rider]
  Rider -->|Report telemetry| UC5[Submit Telemetry]

  Admin -->|Review tickets| UC9[Process HITL Tickets]
  Admin -->|Manage access| UC8[Approve B2B Onboarding]

  System -->|Detect anomalies| UC10[JWT Anomaly Detection]
  System -->|Rotate secrets| UC11[Vault Secret Rotation]
  System -->|Audit log| UC12[Persist Immutable Audit]
```
