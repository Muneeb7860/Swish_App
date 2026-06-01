# Low-Level Design (LLD)

## 1. Backend Hexagonal Implementation
- **Domain Layer**: [ch.swissqcommerce.backend.domain](file:///C:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend/domain). Contains pure Java objects (e.g. `Order`, `Product`) with no framework dependencies.
- **Port Layer**: [ch.swissqcommerce.backend.port](file:///C:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend). Interfaces defining inbound (use cases) and outbound (repository/messaging) operations.
- **Adapter Layer**: [ch.swissqcommerce.backend.adapter](file:///C:/Users/DELL%209420/Documents/swiss_App/backend/src/main/java/ch/swissqcommerce/backend).
  - *Web (Inbound)*: Spring REST Controllers implementing DTO translation via MapStruct.
  - *Persistence (Outbound)*: Spring Data JPA Repositories handling `FetchType.LAZY` and `@EntityGraph` to prevent N+1 queries.
  - *Messaging (Outbound)*: Outbox event persist adapters.

## 2. Ingestion Backpressure & Telemetry Pipeline
- **Backpressure Buffer**: Real-time IoT temperature feeds and rider GPS updates are written to high-performance Redis streams.
- **Relational Ledger**: All business transactions are processed under PostgreSQL `READ_COMMITTED` isolation with explicit database locks (`SELECT FOR UPDATE`).
- **Telemetry Archive**: Unstructured telemetry records are archived directly to MongoDB to prevent transaction locks on the primary database, enabling auto-scaling of analytical data.

## 3. Frontend Module Federation
- [frontend-host/vite.config.ts](file:///C:/Users/DELL%209420/Documents/swiss_App/frontend-host/vite.config.ts) defines `remotes` via `@originjs/vite-plugin-federation`:
  ```typescript
  remotes: {
    customer: 'http://localhost:3001/assets/remoteEntry.js',
    rider: 'http://localhost:3002/assets/remoteEntry.js',
    admin: 'http://localhost:3003/assets/remoteEntry.js'
  }
  ```
- **State Management**: `Zustand` is used for global state (e.g., User Authentication) shared across remotes via context providers.
- **Data Fetching**: `TanStack Query` caches API responses and handles loading/error states gracefully.

## 4. CI/CD Pipeline
- [.github/workflows/ci.yml](file:///C:/Users/DELL%209420/Documents/swiss_App/.github/workflows/ci.yml) defines a matrix build:
  - Java 17 `mvn test` execution for `backend` and `bff`.
  - Node.js 20 `npm run build` execution for all 4 micro-frontends.
