# Resolved Issues Log & Regression Test Registry 📋

This document tracks all critical issues resolved within the **Swish Q-Commerce Platform**. Each entry logs the issue description, root cause, resolution details, and—most importantly—the **automated regression test** added to ensure the issue is never reintroduced and doesn't need to be manually tested again.

---

## 🛡️ Regression Prevention Guidelines

To prevent fixing or testing the same issues multiple times:
1. **Never close an issue without an automated test**: Every bug fix MUST be accompanied by a unit test, integration test, or E2E test that validates the fix.
2. **Register the test in this log**: Map the issue ID to the test method and file path.
3. **Run registry checks on CI/CD**: The CI pipeline will automatically execute these tests on every pull request to guarantee no regression.

---

## 🗃️ Registry Entries

| Issue ID | Date Resolved | Description | Root Cause | Resolution Details | Regression Prevention Test |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **BUG-001** | 2026-06-04 | `CustomerControllerTest` compilation error. | A missing closing brace `}` in the test class broke Maven compilation. | Fixed the class syntax, balancing the brackets. | Executed as part of standard CI compilation: `mvn clean test-compile`. |
| **BUG-002** | 2026-06-04 | `TransactionPersistenceAdapter` signature mismatch. | Method `findByCustomerIdOrderByCreatedAtDesc` signature did not match the outgoing port definition. | Updated adapter class signatures to perfectly align with the interface ports. | Verified by compiler checks and [OrderIntegrationTest.java](file:///C:/Users/DELL%209420/Documents/swiss_App/backend/src/test/java/ch/swissqcommerce/backend/integration/OrderIntegrationTest.java). |
| **BUG-003** | 2026-06-03 | WebSocket Notification 401 Authorization Blocks. | WebSocket endpoints under `/ws/notifications/**` were intercepted and blocked by Platform Gateway JWT filters. | Configured security filter bypasses for WebSocket handshakes in the gateway configuration. | Verified by WebSocket integration test scenario in [WebSocketConfiguration.java](file:///C:/Users/DELL%209420/Documents/swiss_App/notification-engine/src/main/java/com/platform/notification/config/WebSocketConfiguration.java). |
| **BUG-004** | 2026-06-03 | WebSocket PII Data Leakage. | Raw user profiles and database audit traces were streamed over public notification channels. | Implemented `NotificationEnvelope` mapping filters to strip PII before serializing payload. | Unit test [NotificationEnvelope.java](file:///C:/Users/DELL%209420/Documents/swiss_App/notification-engine/src/main/java/com/platform/notification/model/NotificationEnvelope.java) asserts field exclusion. |

---

## 🛠️ Pending Architectural Enhancements (Critique Items)

These issues are currently scheduled for refactoring under the latest implementation plan:

### 1. Database Shared Instance (Distributed Monolith)
*   **Description**: Keycloak and Backend Core share `postgres-gis:5432/b2b_qcomm`.
*   **Resolution Plan**: Split into `keycloak_db` and `backend_db` logical schemas.
*   **Regression Test**: Scripted validation check in `docker-compose-local.yml` validation suite.

### 2. Redundant API Gateways (`bff/` & `platform-gateway/`)
*   **Description**: Redundant gateways running on ports 8080 and 8081.
*   **Resolution Plan**: Delete the legacy `bff/` service and route all traffic via `platform-gateway/`.
*   **Regression Test**: Integration checks via platform-gateway router.

### 3. Split-Brain Codebase Structure (Legacy vs. Hexagonal)
*   **Description**: Backend core has root controllers/services outside the `domain/` hexagonal packages.
*   **Resolution Plan**: Move remaining legacy root files to ports and adapters.
*   **Regression Test**: ArchUnit tests checking that no root-level controller is accessed directly by service modules.

### 4. 4-Second Event Polling Latency
*   **Description**: Delayed publishing to Kafka due to poller loop.
*   **Resolution Plan**: Implement `@TransactionalEventListener` for instantaneous event dispatch upon SQL commit.
*   **Regression Test**: Concurrency check in integration tests verifying event delay is < 50ms.

### 5. Volatile Telemetry Ingestion
*   **Description**: Telemetry coordinates kept in-memory and lost on restart.
*   **Resolution Plan**: Write ticks to a Redis List stream buffer and batch-persist asynchronously.
*   **Regression Test**: Telemetry integration test simulating server restart and verifying path persistence.
