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
        
        Backend --> Cache[(Redis Cache / Ingest Buffer)]
        Backend --> DB_Tx[(PostgreSQL: Transaction & Ledgers)]
        
        Cache -.-> DB_Time[(PostgreSQL: TimescaleDB Telemetry)]
        DB_Tx -.->|Transactional Outbox| Kafka[(Apache Kafka Cluster)]
        Kafka -.->|OlapEventSinkListener| Mongo[(MongoDB: Analytical Telemetry Archive)]
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
*   **PostgreSQL**: Handles persistent transactional double-entry ledger lines with active SHA-256 hash chaining.
*   **TimescaleDB**: Handles high-frequency time-series telemetry data and SLA log metrics.
*   **Redis**: Ingests and buffers real-time coordinate and status changes (e.g. active rider updates).
*   **Apache Kafka (KRaft Mode)**: Serves as the high-throughput message broker implementing the transactional outbox pattern to decouple transactional database operations from downstream analytical storage systems.
*   **MongoDB (NoSQL)**: Persists high-throughput, unstructured telemetry data (e.g. coordinates and weather streams) received from Kafka, scaling write loads horizontally.

---

## 3. Observability & Mission Control
*   **Zipkin**: Captures correlation tracking (`correlationId`) across distributed agent transactions.
*   **Prometheus**: Scrapes `/actuator/prometheus` to monitor agent processing rates and budget consumption logs.

## 4. Formal Architecture Artifacts
For the complete C4 context, container, and component diagrams, see `docs/diagrams/architecture-diagrams.md`.
