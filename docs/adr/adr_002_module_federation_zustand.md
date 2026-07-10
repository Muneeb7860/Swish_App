# ADR 002: Adopting Module Federation and Zustand for State Management

## Status
Accepted

## Context
Swish App requires a multi-role dashboard cockpit simulating four critical quick-commerce views:
1. Customer Super App
2. Rider App
3. Dark Store Inventory Manager
4. Business Analytics & Admin Cockpit

A monolithic frontend codebase leads to scaling bottlenecks, coupling team release cycles, and slow build speeds.

## Decision
We adopt **Vite Module Federation** to establish a decoupled Micro-Frontend (MFE) architecture:
- **Host Shell (`frontend-host`)**: Resolves remote federation assets and renders the global cyberpunk frame, SSE tracking layer, and telemetry indicators.
- **Remote MFEs (`frontend-customer`, `frontend-rider`, `frontend-admin`)**: Independently compiled React applications containing role-specific logic.

To coordinate state across the host and remote MFEs:
- We adopt **Zustand** as our lightweight state engine.
- Refactored our global store in [store.ts](../../frontend-host/src/store.ts) into isolated, type-safe slice states (e.g., CartSlice, RoleSlice, TelemetrySlice, ChaosSlice). (docs: resolve path mismatches, document LLM strategy, service inventory, and database schema mappings)
- Remote MFEs consume state properties via standardized React props, ensuring the host serves as the single source of truth while remote MFEs remain stateless and reusable.

## Consequences
- **Pros**:
  - Independent deployment streams across the Customer, Rider, and Admin interfaces.
  - Zero performance degradation compared to Monolithic SPAs.
  - Lightweight, boilerplate-free global state synchronization.
- **Cons**:
  - Micro-frontends must be coordinated to share standard packages (React, Lucide) to avoid bundle duplicate weight.
