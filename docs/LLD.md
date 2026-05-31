# Low-Level Design (LLD)

## 1. Backend Hexagonal Implementation
- **Domain Layer**: `ch.swissqcommerce.backend.domain`. Contains pure Java objects (e.g. `Order`, `Product`) with no framework dependencies.
- **Port Layer**: `ch.swissqcommerce.backend.port`. Interfaces defining inbound (use cases) and outbound (repository/messaging) operations.
- **Adapter Layer**: `ch.swissqcommerce.backend.adapter`.
  - *Web (Inbound)*: Spring REST Controllers implementing DTO translation via MapStruct.
  - *Persistence (Outbound)*: Spring Data JPA Repositories handling `FetchType.LAZY` and `@EntityGraph` to prevent N+1 queries.
  - *Messaging (Outbound)*: KafkaTemplate publishers.

## 2. Kafka Dead-Letter Queue (DLQ)
- `KafkaConfig.java` defines a `ConcurrentKafkaListenerContainerFactory`.
- It uses a `DefaultErrorHandler` paired with a `DeadLetterPublishingRecoverer`.
- If processing a `RiderLocationEvent` fails, it retries, then publishes the payload to `rider-location-updates.DLT`.

## 3. Frontend Module Federation
- `frontend-host/webpack.config.js` defines `remotes`:
  ```javascript
  remotes: {
    customer: 'customer@http://localhost:5174/remoteEntry.js',
    rider: 'rider@http://localhost:5175/remoteEntry.js',
    admin: 'admin@http://localhost:5176/remoteEntry.js',
  }
  ```
- **State Management**: `Zustand` is used for global state (e.g., User Authentication) shared across remotes via context providers.
- **Data Fetching**: `TanStack Query` caches API responses and handles loading/error states gracefully.

## 4. CI/CD Pipeline
- `.github/workflows/ci.yml` defines a matrix build:
  - Java 17 `mvn test` execution for `backend` and `bff`.
  - Node.js 20 `npm run build` execution for all 4 micro-frontends.
