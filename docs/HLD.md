# High-Level Design (HLD)

## 1. System Context
The Swish_App system sits behind an Nginx Edge Proxy which acts as the primary DMZ barrier. Traffic flows through a Spring Boot BFF (Backend-For-Frontend) before reaching the core business domains.

## 2. Component Design
- **Nginx Proxy**: Handles SSL termination (conceptual), IP rate limiting, and HTTP Security Headers (CSP, HSTS).
- **BFF Service**: Acts as an API Gateway. Implements Resilience4j Circuit Breakers to fail fast if the core backend is unresponsive.
- **Backend Service**:
  - **Hexagonal Architecture**: Isolates core domain models from infrastructure adapters.
  - **Caching**: `@EnableCaching` backed by Redis for fast product lookups.
  - **Messaging**: Spring Kafka for asynchronous processing.
- **Frontend Host**: Webpack Module Federation shell that dynamically imports remote applications (Customer, Admin, Rider).

## 3. Data Architecture
- **PostgreSQL**: Primary transactional database for Orders and Users (managed via Flyway).
- **Redis**: Caching layer for Product Catalog and Geo-spatial data.
- **MongoDB**: Document store for semi-structured analytics data (future expansion).
- **Kafka**: Message broker holding topics like `rider-location-updates` and `order-events` (with Dead Letter Queues).

## 4. Observability Stack
- **Prometheus**: Scrapes `/actuator/prometheus` metrics.
- **Zipkin**: Ingests OpenTelemetry traces.
- **Grafana**: Visualizes Prometheus data.
