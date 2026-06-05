# Swish OS Platform: Architecture Blueprint 🏗️

Swish OS is a high-availability, B2B SaaS micro-fulfillment and agentic procurement platform. This document outlines the technology stack, system architecture, data flow patterns, and deployment configurations.

---

## 📊 System Architecture Diagram

```mermaid
graph TD
    Client[Enterprise Web/Mobile Clients] --> Edge[Nginx Edge Proxy (DMZ)]
    
    subgraph frontend-tier [MFE Frontends & Host Cockpit]
        Edge --> Host[frontend-host Cockpit]
        Edge --> MFEs[MFE Frontends: customer, rider, b2b, admin]
    end

    subgraph gateway-tier [API Routing & Security]
        Edge --> GW[platform-gateway (Port 8080)]
    end

    subgraph backend-tier [Core Microservice Suite]
        GW --> Backend[backend Core Service (Port 8080)]
        GW --> BusinessEngine[core-business-engine (Port 8081)]
        GW --> NotifEngine[notification-engine (Port 8082)]
        GW --> SharedAsync[shared-async-services]

        Backend --> SecurityEngine[Security Engine]
        Backend --> EventsEngine[Events Engine]
        SharedAsync --> RewardsEngine[Rewards Engine]
        BusinessEngine --> GovernanceEngine[Governance Engine]
        
        Backend & BusinessEngine & NotifEngine & SharedAsync & RewardsEngine & EventsEngine & GovernanceEngine --> DB_Tx[(PostgreSQL: transactional schema)]
        Backend & BusinessEngine & NotifEngine & GW & SecurityEngine & RewardsEngine --> Cache[(Redis Cache / Rate Limiting)]
        
        BusinessEngine -.->|Kafka Events| Kafka[(Apache Kafka Cluster)]
        EventsEngine -.->|Kafka Events| Kafka
        SecurityEngine -.->|Kafka Events| Kafka
        Kafka -.->|Notification Consuming| NotifEngine
        Kafka -.->|OlapEventSinkListener| Mongo[(MongoDB: Analytical Archive)]
    end
```

---

## 🛠️ Technology Stack

| Tier | Technology | Purpose |
| :--- | :--- | :--- |
| **Frontend** | React 18, Vite, TypeScript | Interactive UI & runtime bundling |
| **State Management** | Zustand (Sliced Stores) | Decentralized global MFE state sharing |
| **API Gateway** | Spring Cloud Gateway | Unified platform gateway, rate limiting, JWT verification |
| **Backend Core** | Spring Boot 3.2, Java 17 | Core B2B procurement and guardrails business logic |
| **Database (Transactions)** | PostgreSQL 15 | Persistent transactional ledger storage (ACID) |
| **Database (Telemetry)** | PostgreSQL + TimescaleDB | High-frequency time-series telemetry and SLA metrics storage |
| **Database (OLAP Archive)** | MongoDB Atlas | Horizontal scale decoupled telemetry and historical logs |
| **Message Broker** | Apache Kafka 3.6 | Zookeeper-less KRaft-based event streaming and decoupling |
| **Caching & Ingest Buffer** | Redis 7 | Telemetry ingestion queue and catalog caching |
| **Observability** | Prometheus, Grafana, Zipkin | Distributed metrics, tracing, and alerts |
| **Deployment** | Docker Compose, Kubernetes | Multi-environment container orchestration |

---

## 📦 Core Architecture Components

### 1. Frontend Tier (Micro-Frontends)
*   **`frontend-host` (Port 3000)**: The main shell. Incorporates Module Federation remotes and exposes the sliced Zustand state container.
*   **`frontend-customer` (Port 3001)**: Customer client portal supporting cart tracking, address records, and order submissions.
*   **`frontend-rider` (Port 3002)**: Rider portal driving geo-coordinate simulators, accept/reject logs, and wallet earnings.
*   **`frontend-admin` (Port 3003)**: Admin dashboard detailing business metrics, catalog modifiers, and system engine simulation triggers.

### 2. Platform Gateway
*   **Platform Gateway (Port 8080)**: Consolidates all routing, websocket forwarding, and security configurations (such as JWT token verification, CORS, and rate limiting).

### 3. Backend Core Service (Port 8080)
*   Implements core business logic under a structured **Hexagonal (Ports & Adapters) Architecture**.
*   Exposes APIs for orders, payments, riders, inventory, and telemetry processing.

---

## 🧩 Architectural & Resilience Design Patterns

*   **Hexagonal Architecture**: Isolates core business domain logic from infrastructure frameworks (databases, controllers, messaging systems), assuring full mockability.
*   **Pessimistic Database Locking**: Transactional restocks execute under `READ_COMMITTED` isolation with explicit `SELECT FOR UPDATE` blocks, preventing write contention failures.
*   **Event-Driven Communication**: Downstream CQRS updates and cache invalidations are driven asynchronously by Apache Kafka topics (`restock.events`), ensuring high system decoupling.
*   **Ingestion Backpressure Buffering**: High-frequency telemetry packets are buffered dynamically via Redis streams before bulk-writing to the TimescaleDB instance.

---

## ☸️ Production Deployment & Scalability

1.  **Local Environment**: Runs under standard Docker Compose ([docker-compose.yml](file:///C:/Users/DELL%209420/Documents/swiss_App/infrastructure/docker-compose.yml)) containing all support databases.
2.  **Kubernetes Orchestration**: Production-grade YAML manifests in the [k8s](file:///C:/Users/DELL%209420/Documents/swiss_App/infrastructure/k8s) directory enforce memory/CPU limits and readiness/liveness checks across the entire cluster.
