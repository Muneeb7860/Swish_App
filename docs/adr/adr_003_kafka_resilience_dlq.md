# ADR 003: Implementing Kafka Dead Letter Queues (DLQ) for Message Resilience

## Status
Accepted

## Context
When orders are placed, the backend publishes events to Kafka (`order.placed`). Downstream consumers (such as the notification engine, ledger booking system, and rider matching algorithms) ingest these events. If a downstream consumer experiences an outage (such as database locking or network disruption), the event parsing fails, which can block subsequent event streams or lead to silent data losses.

## Decision
We implement a **Kafka Dead Letter Queue (DLQ)** pattern backed by Spring Kafka's `DeadLetterPublishingRecoverer` and `DefaultErrorHandler`:
- When a consumer fails to parse or process an event after 3 retries with a 1-second backoff interval, the event is automatically captured.
- The `DeadLetterPublishingRecoverer` republishes the failed event to a dedicated DLQ topic suffixed with `.DLQ` (e.g. `order.placed.DLQ`).
- We append failure diagnostics directly into the Kafka record headers (e.g., target exception, trace ID, stack trace, and execution timestamp) to allow rapid forensic tracing.

## Consequences
- **Pros**:
  - Unlocked 100% data durability—no event is lost due to transient microservice failures.
  - Retains event-driven microservice isolation. The poison pill event is isolated cleanly to the DLQ without blocking other healthy events in the primary queue.
- **Cons**:
  - Minor storage overhead in the message broker due to maintaining DLQ topics.
