# Low-Level Design (LLD)

> **Diagrams (use case · sequence · class) and BRD/HLD/ERD validation:** see
> [`diagrams/lld-complete.md`](./diagrams/lld-complete.md). Sequence diagrams for
> every domain flow are in [`diagrams/domain-sequence-diagrams.md`](./diagrams/domain-sequence-diagrams.md)
> (+ part 2); the validated data model is in
> [`diagrams/data-model-erd-asbuilt.md`](./diagrams/data-model-erd-asbuilt.md).

## 1. Design Patterns Implementation

### Transactional Outbox
Implemented per-service using a `payment_outbox` table. The `OutboxPublisher` CDC polls this table and pushes to Kafka.

### Idempotency (Consumer-Side)
Every Kafka consumer checks the local `processed_events` table before acting. If the `event_id` exists, the message is acknowledged and skipped.

### Circuit Breaker (Resilience4j)
Applied to synchronous external gateway calls (e.g., Stripe, PayPal).
*   **Thresholds**: 5 failures in 60s
*   **Wait Duration**: 30s half-open
*   **Fallback**: Route to COD (Cash on Delivery) via Strategy Pattern fallback

### Retry with Exponential Backoff
Transient Kafka consumer failures trigger retries via Spring Kafka:
*   **Max Attempts**: 3
*   **Backoff**: 1000ms initial, 2.0 multiplier, 10000ms max
*   After 3 failures, the message is routed to the `.dlq` topic.

### Dead Letter Queue (DLQ)
Every Kafka topic has a companion `.dlq` topic (e.g., `payment.initiated.dlq`). DLQ messages contain enriched headers (`X-Original-Topic`, `X-Error-Message`, `X-Retry-Count`) allowing administrative inspection and replay.

### Choreography Saga & Compensation
The `Payment Service` acts as the orchestrator listening to events. On failure, it publishes `payment.compensation` which triggers **Rollback-First**: downstream services completely reverse partial operations (e.g., Account Service issues a credit to negate a debit).

### Correlation ID
A UUID is generated at the BFF gateway and propagated via `Accept-Version` and `X-Correlation-ID`. The `CorrelationIdFilter` binds this to the MDC context for SLF4J, and `OutboxEventScheduler` propagates it into Kafka headers.

## 2. API Versioning
Endpoints support backward compatibility through HTTP headers. The `Accept-Version: v2` header routes traffic to the newly extracted microservices. 

## 3. External Payment Strategy
The `PaymentGatewayStrategy` interface abstract external providers:
*   `StripeAdapter`
*   `SwipeAdapter`
*   `PayPalAdapter`
*   `CODAdapter`

## 4. Frontend Module Federation
- [frontend-host/vite.config.ts](file:///C:/Users/DELL%209420/Documents/swiss_App/frontend-host/vite.config.ts) defines `remotes` via `@originjs/vite-plugin-federation`.
- State Management: `Zustand`
- Data Fetching: `TanStack Query`

## 5. CI/CD Pipeline
- `.github/workflows/ci.yml` matrix build executes Java 21 tests and Node 20 builds.
