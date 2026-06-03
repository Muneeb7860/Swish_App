# API Contract Summary

The authoritative API contract is defined in `bff-openapi.yaml` and exposed by the BFF gateway.
This document summarizes the published contract surface for the current BFF deployment.

## Canonical BFF Contract Paths

### Authentication
- `POST /api/auth/login`
- `POST /api/auth/mfa/verify`

### Customer Order & Catalog
- `GET /api/customer/catalog`
- `POST /api/customer/orders`
- `GET /api/customer/orders`
- `POST /api/customer/orders/{id}/refund`
- `POST /api/customer/profile/purge`
- `GET /api/customer/ledger`

### Rider Operations
- `POST /api/rider/onboard`
- `POST /api/rider/orders/{id}/coolant`
- `POST /api/rider/orders/{id}/telemetry`
- `POST /api/rider/orders/{id}/deliver`
- `GET /api/rider/academy/courses`
- `POST /api/rider/academy/courses/{id}/complete`

### Inventory & Fulfillment
- `GET /api/inventory/picker/queue`
- `POST /api/inventory/rebalance`
- `POST /api/inventory/picker/handover`

### Wholesaler / B2B Supply
- `GET /api/wholesaler/restocks`
- `POST /api/wholesaler/restocks`
- `POST /api/wholesaler/restocks/{id}/fulfill`
- `GET /api/wholesaler/invoices`

### Admin / Operations
- `POST /api/admin/chaos/faults`
- `POST /api/admin/chaos/{id}/resolve`
- `GET /api/admin/chaos/active`
- `POST /api/admin/onboard/queue/{appId}/approve`
- `GET /api/admin/hitl/queue`
- `POST /api/admin/hitl/queue/{id}/resolve`

### Telemetry & Event Paths
- `POST /api/telemetry/tick`
- `GET /api/telemetry/stream/{orderId}`
- `POST /api/telemetry/{orderId}/dry-ice`

## BFF Gateway Responsibilities
- Authenticated routing from micro-frontend requests to backend services
- JWT validation and header management via `EdgeJwtVerificationFilter`
- OpenAPI documentation exposure for dev and contract validation
- Request proxying for micro-frontend remotes and API surface

## Contract Deployment Notes
- The contract is published through the BFF and used by `frontend-host` to call backend APIs.
- The BFF gateway uses SpringDoc to generate OpenAPI metadata from controller annotations and the `bff-openapi.yaml` contract.
- All frontend micro-applications route API requests through `frontend-host` and the BFF, preserving a single unified API surface.

> Reference: `bff-openapi.yaml` is the canonical design artifact for all API paths and schema definitions.