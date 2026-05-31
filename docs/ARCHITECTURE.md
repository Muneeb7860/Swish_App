# Swish_App Architecture Document

## Overview
Swish_App is a highly scalable, event-driven Quick Commerce platform designed for high-availability and fault tolerance.

## Architecture Diagram (Mermaid)

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

## Core Components
- **Nginx Edge**: Provides DDOS protection, rate limiting, and strict CSP headers.
- **BFF (Backend-For-Frontend)**: Routes requests, handles Resilience4j circuit breakers, and serves as the DMZ bridge.
- **Backend Service**: Implements Hexagonal architecture, caching (Redis), and event-driven patterns (Kafka).
- **Module Federation Frontends**: TypeScript/React apps isolated into `host`, `customer`, `rider`, and `admin` portals using Zustand and TanStack Query.
