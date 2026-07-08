# Swish OS — UAT Test-Case Matrix

Maps the [BRD](BRD.md) functional requirements to concrete, executable test
cases against the demo/beta stack. Seed the environment first:

```bash
bash demo/start.sh            # stack + backend + tunnel (or run_demo.sh for local)
bash demo/seed-test-data.sh   # role accounts, catalog, riders, vendors, POs
```

## Test accounts (password: `Demo1234!`)

| Email | Role | Exercises |
|---|---|---|
| `customer@swish.local` | CUSTOMER | shop, checkout, orders, refunds |
| `rider@swish.local` | RIDER | onboarding, deliveries, academy |
| `business@swish.local` | WHOLESALER | B2B restock, procurement, invoices |
| `inventory@swish.local` | WHOLESALER | dark-store inventory, wastage |
| `admin@swish.local` | ADMIN | HITL, governance, observability |

Access surfaces: **Local** `http://localhost:3000` (beta / `m8FdHvyLDP`) · **Tunnel** printed by `start.sh`.

## Seeded data reference

- **Stores:** Central Store, East Store, store-test-1/2/3 (Zurich HB, Oerlikon, Geneva).
- **Catalog:** 30 SKUs across Dairy, Bakery, Produce, Drinks, Snacks, Pantry, Frozen, Meat, Household, Personal Care — incl. edge cases `LOWSTOCK-001` (stock 3), `OOS-001` (stock 0), `PREMIUM-001` (CHF 29.90).
- **Riders:** active (x3), onboarding (`rider-pending-1`), suspended (`rider-susp-1`).
- **Vendors (multi-vendor pricing):** see matrix below.

### Multi-vendor dynamic-pricing reference

| Vendor | base_invoice | trust | academy_disc | Profile |
|---|---|---|---|---|
| ValueMart Bulk | 19.50 | 74 | no | Cheapest, mid trust |
| Swiss Organic Collective | 26.50 | 85 | yes | Mid price, academy discount |
| Swiss Wholesale Distributors | 25.00 | 100 | no | Primary, top trust |
| Alpine Backups & Restock | 28.00 | 92 | no | Backup supplier |
| PremiumFresh Logistics | 34.00 | 99 | yes | Premium, highest reliability |
| Dormant Supplier AG | 22.00 | 60 | no | **Inactive — must be skipped** |

---

## Functional requirement test cases

### FR-01 Retailer On-boarding
| ID | Steps | Expected |
|---|---|---|
| TC-01.1 | `business@` opens B2B Retailer Hub → submit store registration | Application accepted, appears in admin onboard queue |
| TC-01.2 | `admin@` GET `/api/admin/onboard/queue` | Pending registration listed |
| TC-01.3 | Rider onboarding: `rider@` POST `/api/rider/onboard` | 201, application `submitted` with applicationId |
| TC-01.4 | Register duplicate email | 400/409 with clear error (no duplicate account) |

### FR-02 AI Negotiation Workflow (multi-vendor procurement)
| ID | Steps | Expected |
|---|---|---|
| TC-02.1 | `business@` raise restock RFQ for a low-stock SKU | Procurement workflow triggered; vendors bid |
| TC-02.2 | Compare bids across seeded vendors | Cheapest **active** vendor with acceptable trust wins; `Dormant Supplier AG` never selected |
| TC-02.3 | Drive daily LLM budget to the ADR-007 cap | Falls back to deterministic 10% discount bid (no budget breach) |
| TC-02.4 | Academy-discount vendor path | Discount applied when `academy_discount_active` |

### FR-03 Telemetry Ingestion
| ID | Steps | Expected |
|---|---|---|
| TC-03.1 | Rider POST `/api/rider/orders/{id}/telemetry` (temp reading) | Accepted; stored (TimescaleDB) |
| TC-03.2 | Cold-chain excursion (out-of-range temp) | Alert/flag raised (FR-07) |

### FR-04 Ledger Auditing
| ID | Steps | Expected |
|---|---|---|
| TC-04.1 | Place a paid order, inspect ledger | Balanced entries (debit customer / credit store + rider tip) |
| TC-04.2 | Tipped order with an active rider | Rider tip credit leg present → no "Unbalanced transaction" |
| TC-04.3 | Ledger hash chain | Each entry links prev_hash (SHA-256), tamper-evident |

### FR-05 Operator Dashboard (RBAC)
| ID | Steps | Expected |
|---|---|---|
| TC-05.1 | `admin@` GET `/api/admin/health` | 200 with inventory/order counts |
| TC-05.2 | `customer@` GET `/api/admin/health` | **403** (role enforced) |
| TC-05.3 | Unauthenticated GET `/api/admin/*` | **401** |
| TC-05.4 | `admin@` GET `/api/governance/hitl` | HITL queue returned |
| TC-05.5 | HITL approve/reject as non-admin | 403 (`@PreAuthorize` on HITL endpoints) |

### FR-06 Billing Engine
| ID | Steps | Expected |
|---|---|---|
| TC-06.1 | `business@` GET `/api/wholesaler/invoices` | Invoice list for the wholesaler |
| TC-06.2 | Invoice with `base_invoice_amount` per vendor | Flat-tier amount reflected |

### FR-07 Alert & Notification
| ID | Steps | Expected |
|---|---|---|
| TC-07.1 | SLA breach on delivery routing | Alert generated |
| TC-07.2 | Temperature excursion (TC-03.2) | Notification dispatched |

---

## Domain-flow happy paths

### Customer journey
1. Login `customer@` → catalog loads (store-scoped SKUs).
2. Add items incl. perishable + fragile → checkout with `paymentMethod: wallet`.
3. Order created (201), appears in `/api/customer/orders`.
4. Payment variants (see below), tip + bags-returned ESG discount.
5. Refund a delivered order → `/api/customer/orders/{id}/refund`.

### Rider journey
1. Login `rider@` → academy courses (200 for RIDER role).
2. Assigned order → deliver `/api/rider/orders/{id}/deliver` with PIN/proof.
3. Reject flow `/api/rider/orders/{id}/reject` with photo.

### Wholesaler / B2B
1. Login `business@` → restocks `/api/wholesaler/restocks` (200).
2. Raise restock → multi-vendor procurement (FR-02).
3. Wastage log `/api/wholesaler/wastage`.

### Admin / governance
1. Login `admin@` → HITL queue, onboarding gate, chaos controls, health.
2. Approve/reject HITL items → audited.

---

## Payment-method matrix (orders_payment_method_check)

| Input | Expected | Note |
|---|---|---|
| `wallet` / `Wallet` | 201 | case-insensitive (normalized) |
| `swipe`, `paypal`, `paytm`, `cash on delivery` | 201 | all canonical variants |
| `bitcoin` (unsupported) | **400** | clear message, not a 500 SQL error |
| blank / missing | **400** | validation error |

## Edge cases

| ID | Scenario | Expected |
|---|---|---|
| EC-1 | Order `OOS-001` (stock 0) | Rejected — out of stock |
| EC-2 | Order `LOWSTOCK-001` qty > 3 | Rejected — insufficient stock |
| EC-3 | Duplicate item lines in one cart | Rejected — consolidate message |
| EC-4 | Zero / negative quantity | Rejected — validation |
| EC-5 | Idempotency-Key replay | Same order returned, not duplicated |
| EC-6 | Checkout with 0 active riders (tipped) | Handled — no unbalanced ledger |

## Cross-cutting (NFR) spot checks

| ID | Area | Check |
|---|---|---|
| NFR-04 | Security | `/api` requires JWT; UI gated by basic-auth; customer↔admin isolation (TC-05.2) |
| NFR-06 | Observability | Traces flow to Grafana/Zipkin; `/actuator/prometheus` exposes metrics |
| NFR-07 | Compliance | GDPR delete workflow anonymizes customer PII |

---

## Access-control matrix (quick regression)

| Endpoint | anon | CUSTOMER | RIDER | WHOLESALER | ADMIN |
|---|---|---|---|---|---|
| `/api/v1/auth/register,login` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/customer/**` | 401 | ✅ | 403 | 403 | varies |
| `/api/rider/academy/**` | 401 | 403 | ✅ | 403 | varies |
| `/api/wholesaler/**` | 401 | 403 | 403 | ✅ | varies |
| `/api/admin/**`, `/api/governance/hitl` | 401 | 403 | 403 | 403 | ✅ |

> Status legend: ✅ allowed · 401 unauthenticated · 403 forbidden.
> Verified over the tunnel on 2026-07-08: customer→admin 403, anon→wholesaler 401,
> customer→wholesaler 403, admin→admin 200.
