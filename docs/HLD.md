# High-Level Design (HLD): Swish OS
**Version**: 2.0.0 (Agentic Headless Edition)

---

## 1. System Context & Network Topology

```mermaid
graph TD
    Client[Enterprise AI / Operator Console] --> Edge[Nginx Edge Proxy (DMZ)]
    
    subgraph frontend-tier [Edge API & Cockpit Tier]
        Edge --> Cockpit[Exception Feed: frontend-host]
        Edge --> BFF[Agentic BFF Gateway]
    end

    subgraph backend-tier [Core Agentic Network]
        BFF --> Backend[Spring Boot Core Backend]
        
        Backend --> Cache[(Redis Cache)]
        Backend --> DB[(PostgreSQL Ledger)]
        
        Backend --> Outbox[Outbox Event Relay]
        Outbox --> Kafka[Kafka Message Broker]
        
        Kafka --> SLM[Local SLM Fine-Tuning Store]
    end
```

---

## 2. Component Design

### A. Nginx Proxy & DMZ
*   Serves as the SSL/TLS termination layer.
*   Enforces IP-based rate limiting on external routes, separating high-frequency telemetry from transactional APIs.

### B. Agentic BFF Gateway (Port 8081)
*   Acts as a Headless API Gateway, exposing structured OpenAPI specifications.
*   Bypasses validation for preflight `OPTIONS` calls and intercepts security headers.
*   Implements Resilience4j circuit breakers to fail-fast under database latency.

### C. Backend Core & Hexagonal Architecture
*   Isolates core B2B business logic (`domain.agent`, `domain.transaction`, `domain.event`) from infrastructural frameworks.
*   Uses a **deterministic ledger validator** to inspect and approve AI agent orders before commit.

### D. Message Broker & Data Warehousing
*   **PostgreSQL**: Handles persistent transactional double-entry ledger lines with active MD5 hash chaining.
*   **Redis**: Caches rolling product availability and catalog prices.
*   **Kafka**: Streams asynchronously published events (e.g. `order.placed`, `restock.negotiated`).
*   **Heuristics Store**: Collects and hashes all negotiation logs to feed local Small Language Model (SLM) training sets.

---

## 3. Observability & Mission Control
*   **Zipkin**: Captures correlation tracking (`correlationId`) across distributed agent transactions.
*   **Prometheus**: Scrapes `/actuator/prometheus` to monitor agent processing rates and budget consumption logs.
