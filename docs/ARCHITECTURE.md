# Swish OS Platform: Architecture Blueprint 🏗️

Swish OS is a high-availability, B2B SaaS micro-fulfillment and agentic procurement platform. This document outlines the technology stack, system architecture, data flow patterns, and deployment configurations.

---

## 📊 System Architecture Diagram

```mermaid
graph TD
    Client[Enterprise AI / Operator Console] --> Edge[Nginx Edge Proxy (DMZ)]
    
    subgraph frontend-tier [Edge API & Cockpit Tier]
        Edge --> Cockpit[Exception Feed: frontend-host]
        Edge --> BFF[Agentic BFF Gateway]
    end

    subgraph backend-tier [Core Agentic Network]
        BFF --> Backend[Spring Boot Core Backend]
        
        Backend --> Cache[(Redis Cache / Ingestion Buffer)]
        
        Backend --> DB_Tx[(PostgreSQL: Transaction & Ledgers)]
        Cache -.-> DB_Time[(PostgreSQL: TimescaleDB Telemetry)]
    end
```

---

## 🛠️ Technology Stack

| Tier | Technology | Purpose |
| :--- | :--- | :--- |
| **Frontend** | React 18, Vite, TypeScript | Interactive UI & runtime bundling |
| **State Management** | Zustand (Sliced Stores) | Decentralized global MFE state sharing |
| **API Gateway** | Spring Cloud Gateway (BFF) | Headless OpenAPI gateway, rate limiting, JWT verification |
| **Backend Core** | Spring Boot 3.2, Java 17 | Core B2B procurement and guardrails business logic |
| **Database (Transactions)** | PostgreSQL 15 | Persistent transactional ledger storage (ACID) |
| **Database (Telemetry)** | PostgreSQL + TimescaleDB | High-frequency time-series telemetry and SLA metrics storage |
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

### 2. Edge Gateway BFF
*   **BFF Gateway (Port 8081)**: Resolves requests. Bypasses security for Swagger documentation and coordinates OAuth2 JWT verification, Resilience4j circuit breakers, and rate limiters.

### 3. Backend Core Service (Port 8080)
*   Implements core business logic under a structured **Hexagonal (Ports & Adapters) Architecture**.
*   Exposes APIs for orders, payments, riders, inventory, and telemetry processing.

---

## 🧩 Architectural & Resilience Design Patterns

*   **Hexagonal Architecture**: Isolates core business domain logic from infrastructure frameworks (databases, controllers, messaging systems), assuring full mockability.
*   **Pessimistic Database Locking**: Transactional restocks execute under `READ_COMMITTED` isolation with explicit `SELECT FOR UPDATE` blocks, preventing write contention failures.
*   **Ingestion Backpressure Buffering**: High-frequency telemetry packets are buffered dynamically via Redis streams before bulk-writing to the TimescaleDB instance.

---

## ☸️ Production Deployment & Scalability

1.  **Local Environment**: Runs under standard Docker Compose (`infrastructure/docker-compose.yml`) containing all support databases.
2.  **Kubernetes Orchestration**: Production-grade YAML manifests in `infrastructure/k8s/` enforce memory/CPU limits and readiness/liveness checks across the entire cluster.
