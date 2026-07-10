# Swiss Quick Commerce Network: Architecture & API Registry

This document serves as the **incremental, single source of status and architectural registry** for the Swish Q-Commerce application. Any modification to service routing, endpoints, database schemas, or micro-frontend state contracts must be logged here.

---

## 🌐 Network Topology & Routing Matrix

Traffic enters exclusively via the unified entry point **Port 8080** managed by the consolidated gateway.

| Service Name | Port | Base Path | Responsibilities & Filters |
| :--- | :--- | :--- | :--- |
| **`platform-gateway`** | `8080` | `/` | Entry point. Routes requests to downstreams, coordinates SSL termination, and applies gateway filters (resilience & circuit-breaking). |
| **`backend` (Core)** | `8083` | `/api/**` | Core logic, PostgreSQL storage, governance telemetry signing, and Kafka event listener processing. |
| **`shared-async`** | `8082` | `/api/rewards/**`, `/ws/**` | Loyalty processing, Redis sorted set leaderboard, and live push notifications via WebSocket streams. |
| **`core-business`** | `8081` | `/api/checkout/**` | Inventory checkouts and B2B Saga transaction orchestrations. |

---

## 🔌 API Endpoints Catalog

### 1. Governance & HITL Overrides
*   `GET /api/governance/hitl` — Retrieve all pending B2B procurement budget override requests.
*   `POST /api/governance/hitl/{id}/approve` — Approve restock budget override and set order status to `fulfilled`.
*   `POST /api/governance/hitl/{id}/reject` — Reject restock override and cancel order.
*   `POST /api/governance/sign/{orderId}` — RSA cryptographically signs delivery run summaries (temperature, SLA checks, and final doorstep `podHash` handshake) upon final drop.

### 2. General Administrative Controls
*   `GET /api/admin/hitl/queue` — Fetch pending customer support tickets (e.g. AI-triggered refund approvals).
*   `POST /api/admin/hitl/queue/{ticketId}/resolve` — Approve or reject a general support ticket.
*   `POST /api/admin/chaos/faults` — Inject a resilience chaos fault (LATENCY_SPIKE, STORE_OFFLINE).
*   `POST /api/admin/onboard/queue/{appId}/approve` — Progress 3-gate onboarding (ops -> compliance -> admin).

### 3. Agentic & Negotiation Core
*   `POST /api/agent/chat` — Connect to the LLM agentic assistant.
*   `POST /api/agent/negotiate` — Solicit real-time bids/quotes from all active wholesalers (RFQ / Auction Engine). Selects the lowest bid. If proposed prices exceed limits, a pending B2BRestockOrder is saved for the winning wholesaler and routed to `/api/governance/hitl`.

### 4. Telemetry Ingestion (CQRS)
*   `POST /api/telemetry/tick` — Batch coordinate/temperature ticks (persisted asynchronously to minimize DB write-amplification).
*   `GET /api/telemetry/stream/{orderId}` — Server-Sent Events (SSE) live coordinate tracking for riders.

---

## 🎨 Micro-Frontend (MFE) State Sharing Contracts

All MFEs utilize the central Zustand state manager defined in [store.ts](../frontend-host/src/store.ts). (docs: resolve path mismatches, document LLM strategy, service inventory, and database schema mappings)

### Unified `hitlQueue` Schema
To present a single queue inside the MFE Admin Panel, tickets from both backend queues are mapped as follows:

| Target Field | B2B Overrides (`/api/governance/hitl`) | General Queue (`/api/admin/hitl/queue`) |
| :--- | :--- | :--- |
| **`id`** | `b2b-{id}` (prefixed) | `ticketId` |
| **`type`** | `'b2b_funds'` | `type` |
| **`desc`** | Restock description with order and wholesaler IDs | `description` |
| **`amount`** | `amount` | `amount` |

---

## 💎 Design Patterns Directory

### System-Level (HLD)
1.  **Saga Pattern**: Governs inventory checkouts. If payment fails, checkout steps roll back. Rewards logic is decoupled asynchronously to prevent checkout failures.
2.  **Eventual Consistency**: Loyalty processing and audits consume transactional outbox events asynchronously via Kafka listeners.
3.  **Circuit Breaker**: Resilience4j protects backend ports. Degradations in rewards or compliance services trip the gateway circuit breaker, failing gracefully.
4.  **CQRS Segregation**: Telemetry splits real-time tick ingestion queueing from read-heavy SSE streams.

### Class-Level (LLD)
1.  **Observer**: `RewardsListener` and `ComplianceListener` register to transaction outbox events.
2.  **Factory**: `RewardFactory` generates `PointsReward`, `CashbackReward`, or `BadgeReward` dynamically.
3.  **Singleton**: Manages thread-safe queue buffers and key pairs.
