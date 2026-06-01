# ADR 001: Adopting Hexagonal Architecture for Core Microservices

## Status
Accepted

## Context
Our Q-Commerce core backend handles complex domain models including Rider Trust, Customer Credits, SLA countdowns, and real-time inventory management. Standard layered architectures ("MVC") leak database models (entities) and framework annotations (Spring Web/JPA) directly into core business calculations. This makes domain testing extremely difficult and couples the business logic tightly to specific storage and web technologies.

## Decision
We adopt **Hexagonal Architecture (Ports and Adapters)** for all core microservices (e.g., `backend` module).

The directory structure is strictly decoupled into:
- `ch.swissqcommerce.backend.domain.<bounded-context>.core.model`: Rich domain models containing business logic (completely free of Hibernate/Spring annotations).
- `ch.swissqcommerce.backend.domain.<bounded-context>.port.in`: Inbound ports (Java interfaces representing use cases).
- `ch.swissqcommerce.backend.domain.<bounded-context>.port.out`: Outbound ports (Java interfaces representing SPI database adapters/event publishers).
- `ch.swissqcommerce.backend.domain.<bounded-context>.adapter.in.web`: Inbound adapters (Controllers parsing requests and executing ports).
- `ch.swissqcommerce.backend.domain.<bounded-context>.adapter.out.persistence`: Outbound adapters (Spring Data JPA entities and repositories writing to PostgreSQL/H2).

## Consequences
- **Pros**:
  - 100% pure domain modeling without external library pollution.
  - Core logic can be unit-tested thoroughly without mocking database connections or spinning up heavy Spring contexts.
  - Simple migrations from Postgres to NoSQL or different frameworks since adapters are isolated.
- **Cons**:
  - Minor boilerplate overhead due to interface mappings and model-to-entity converter utilities.
