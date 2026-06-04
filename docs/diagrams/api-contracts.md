# API Contract Summary

The authoritative API contract is defined in `bff-openapi.yaml` and exposed by the BFF gateway.
This document summarizes the published contract surface for the current microservices deployment.

## Canonical BFF Contract Paths

### Authentication & User (User Service)
- `POST /api/auth/login`
- `POST /api/auth/mfa/verify`
- `GET /api/user/profile`

### Payments (Payment Service)
- `POST /api/payments`
- `POST /api/payments/{id}/capture`
- `POST /api/payments/{id}/refund`
- `POST /api/payments/{id}/compensate` (Admin only)
- `GET /api/payments`

### Account & Wallet (Account Service)
- `GET /api/accounts/wallet`
- `GET /api/accounts/ledger`

### Customer Order & Catalog (Order/Inventory Service)
- `GET /api/customer/catalog`
- `POST /api/customer/orders`
- `GET /api/customer/orders`
- `POST /api/customer/profile/purge`

### Rider Operations
- `POST /api/rider/onboard`
- `POST /api/rider/orders/{id}/telemetry`
- `POST /api/rider/orders/{id}/deliver`

### Inventory & Fulfillment
- `GET /api/inventory/picker/queue`
- `POST /api/inventory/rebalance`

### Security Engine
- `GET /api/security/audit`
- `POST /api/security/vault/rotate`
- `GET /api/security/compliance`

## BFF Gateway Responsibilities
- Authenticated routing to the 7 microservices
- JWT validation and `X-Correlation-ID` header injection
- Circuit breaking (fail-fast if a backend is down)
- Protocol translation (HTTP to downstream REST)

## API Versioning Strategy
- Backward compatibility is maintained via HTTP headers.
- Clients must pass `Accept-Version: v1` or `Accept-Version: v2`.
- `v2` introduces async payment processing responses (202 Accepted) instead of synchronous blocks.

> Reference: `bff-openapi.yaml` is the canonical design artifact for all API paths.