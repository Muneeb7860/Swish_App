# Swish_App Enterprise Roadmap (TPO & Architect Vision)

As your Technical Product Owner (TPO) and Enterprise Architect, I am incredibly proud of where we are. Over the past few sprints, we have elevated `Swish_App` from a basic monolithic prototype into a deeply resilient, TypeScript-driven, Hexagonal, event-driven micro-platform. 

To achieve true "Tier-1 Enterprise" status, here is my proposed backlog. I have categorized the next logical steps into three distinct Epics. 

---

## Epic 1: CI/CD & Automated Governance (The "DevOps" Epic)
*We have a robust Git branching strategy, but we are still manually compiling and testing code.*

- **GitHub Actions Pipeline**: Implement automated CI pipelines that trigger on every Pull Request to `develop`.
- **Automated Quality Gates**: Integrate SonarQube or ESLint/Checkstyle to automatically block PRs that contain code smells or vulnerabilities.
- **Flyway DB Migrations**: Replace our static `schema.sql` boot script with Flyway or Liquibase for professional, version-controlled database schema evolution.

## Epic 2: Enterprise Observability (The "Day-2 Ops" Epic)
*We wired up Micrometer OpenTelemetry and Actuator, but we are flying blind without a dashboard to visualize the data.*

- **Prometheus & Grafana**: Deploy Prometheus to scrape our Spring Boot `/actuator/prometheus` metrics, and wire up a Grafana dashboard container to visualize live memory, CPU, and API traffic.
- **Distributed Tracing UI**: Deploy Jaeger or Zipkin in `docker-compose` to ingest our OTEL traces, allowing us to visually trace exactly how long an order takes to travel from the BFF -> Kafka -> Backend -> Postgres.

## Epic 3: Scale & Reliability (The "Performance" Epic)
*We solved the N+1 database queries and locked down the edge, but we can squeeze out more performance.*

- **Redis `@Cacheable`**: Implement Spring Cache for the Product/Inventory catalog. Customers shouldn't be hitting Postgres every time they look at the menu.
- **Kafka Dead Letter Queues (DLQ)**: If a Rider crashes or a Kafka event fails to process, we need a DLQ to catch the failed event so it isn't lost forever.
- **Cypress E2E Testing**: Write automated browser tests that simulate a Customer placing an order, the Admin accepting it, and the Rider delivering it.

---

> [!TIP]
> **Architect's Recommendation**
> I highly recommend tackling **Epic 2 (Enterprise Observability)** next. Building a Grafana Dashboard will give us a "Mission Control" screen to actually *see* the incredible architecture we've built in action!
