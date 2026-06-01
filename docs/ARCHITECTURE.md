# Swish Q-Commerce Platform: Architecture Blueprint 🏗️

Swish is a high-availability, event-driven 3-sided quick-commerce marketplace designed for rapid, sub-15-minute grocery deliveries. This document outlines the technology stack, system architecture, data flow patterns, and deployment configurations.

---

## 📊 System Architecture Diagram

```mermaid
graph TD
    Client[Web/Mobile Clients] --> Edge[Nginx Edge Proxy (DMZ)]
    
    subgraph frontend-tier [Frontend Tier Network]
        Edge --> Host[MFE: frontend-host]
        Edge --> BFF[Spring Boot BFF Gateway]
    end

    subgraph backend-tier [Backend Tier Network]
        BFF --> Backend[Spring Boot Core Backend]
        
        Backend --> Cache[(Redis Cache)]
        Backend --> DB[(PostgreSQL)]
        Backend --> Mongo[(MongoDB)]
        
        Backend --> Kafka[Kafka Event Bus]
        Kafka --> DLQ[Dead Letter Topic]
    end
    
    subgraph observability [Mission Control]
        Backend -.-> Prom[Prometheus]
        BFF -.-> Prom
        Prom --> Grafana[Grafana Dashboards]
        
        Backend -.-> Zipkin[Zipkin Distributed Tracing]
        BFF -.-> Zipkin
    end
```

---

## 🛠️ Technology Stack

| Tier | Technology | Purpose |
| :--- | :--- | :--- |
| **Frontend** | React 18, Vite, TypeScript | Interactive UI & runtime bundling |
| **State Management** | Zustand (Sliced Stores) | Decentralized global MFE state sharing |
| **API Gateway** | Spring Cloud Gateway (BFF) | Rate limiting, JWT verification, CORS |
| **Backend Core** | Spring Boot 3.2, Java 17 | Core Q-Commerce business logics |
| **Database** | PostgreSQL 15 | Persistent transaction ledger storage |
| **Caching** | Redis 7 | High-performance catalog and session caching |
| **Messaging** | Redpanda (Kafka v23.2) | Real-time event streaming and DLQs |
| **Observability** | Prometheus, Grafana, Zipkin | Distributed metrics, tracing, and alerts |
| **Deployment** | Docker Compose, Kubernetes | Multi-environment container orchestration |

---

## 📦 Core Architecture Components

### 1. Frontend Tier (Micro-Frontends)
*   **`frontend-host` (Port 3000)**: The main shell. Incorporates Module Federation remotes and exposes the sliced Zustand state container.
*   **`frontend-customer` (Port 3001)**: Customer client portal supporting cart tracking, address records, and order submissions.
*   **`frontend-rider` (Port 3002)**: Rider portal driving geo-coordinate simulators, accept/reject logs, and wallet earnings.
*   **`frontend-admin` (Port 3003)**: Admin dashboard detailing business metrics, catalog modifiers, and system engine simulation triggers.

### 2. Edge Gateway BFF
*   **BFF Gateway (Port 8081)**: Resolves React requests. Bypasses security for Swagger documentation and coordinates OAuth2 JWT verification, Resilience4j circuit breakers, and rate limiters.

### 3. Backend Core Service (Port 8080)
*   Implements core quick-commerce logic under a structured **Hexagonal (Ports & Adapters) Architecture**.
*   Exposes APIs for orders, payments, riders, inventory, and telemetry processing.

---

## 🧩 Architectural & Resilience Design Patterns

*   **Hexagonal Architecture**: Isolates core business domain logic from infrastructure frameworks (databases, controllers, messaging systems), assuring full mockability.
*   **Event-Driven Communication**: Services trigger Kafka events (e.g., `order.placed`, `payment.charged`) to execute asynchronous updates.
*   **Resilience & Dead Letter Queues**: Mapped `DeadLetterPublishingRecoverer` handlers move failing Kafka events into dedicated DLQs to prevent message ingestion blocks.
*   **Cache-Aside Pattern**: Frequently fetched catalog inventories are cached via Redis, dropping lookups to milliseconds.

---

## ☸️ Production Deployment & Scalability

1.  **Local Environment**: Runs under standard Docker Compose (`infrastructure/docker-compose.yml`) containing all support databases.
2.  **Kubernetes Orchestration**: Production-grade YAML manifests in `infrastructure/k8s/` enforce memory/CPU limits and readiness/liveness checks across the entire cluster.
