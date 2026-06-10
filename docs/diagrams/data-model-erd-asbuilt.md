# Data Model ERD — As-Built (validated)

This ERD reflects the **actual implemented schema**: the Flyway migrations
(`V1`–`V20`, authoritative) cross-checked against the JPA `@Entity` mappings.

> ⚠️ It is distinct from [`data-model-erd.md`](./data-model-erd.md), which
> describes an **aspirational target** — a 7-database "sharded microservices"
> model that the code has **not** adopted. The code is a modular DDD monolith
> with four PostgreSQL **schemas**: `oltp` (transactional), `olap` (warehouse),
> `dispatch`, and `wholesaler`. This document is the source of truth for the
> data model as it exists today.

Validation method: `@Table(name, schema)` on every entity was diffed against the
`CREATE TABLE` statements and `REFERENCES`/`CHECK`/`TRIGGER` clauses in the
migrations. Findings are in [§Validation](#validation-findings) at the end.

---

## A. Commerce & Orders (`oltp`)

```mermaid
erDiagram
    CUSTOMERS {
        varchar customer_id PK
        varchar full_name
        varchar email UK "NULL on GDPR purge"
        varchar hashed_email UK "SHA-256, retained post-purge"
        numeric wallet_balance "CHECK >= 0"
        int loyalty_points "CHECK >= 0"
        int trust_score "CHECK 0..100"
        boolean is_anonymized
        boolean is_on_probation
        int consecutive_orders_completed
        bigint version "optimistic lock"
    }
    CUSTOMER_ADDRESSES {
        serial address_id PK
        varchar customer_id FK
        numeric latitude
        numeric longitude
    }
    CUSTOMER_PAYMENT_CARDS {
        serial card_id PK
        varchar customer_id FK
        varchar token_reference UK "tokenized, GDPR"
        varchar last_four_digits
    }
    DARK_STORES {
        varchar store_id PK
        varchar store_name
        numeric latitude
        numeric longitude
        int storage_capacity_limit
    }
    INVENTORY {
        varchar item_id PK
        varchar store_id FK
        numeric price "CHECK >= 0"
        int stock "CHECK >= 0"
        boolean perishable
        bigint version "optimistic lock"
    }
    ORDERS {
        serial order_id PK
        varchar customer_id FK
        varchar store_id FK
        varchar rider_id FK
        numeric total_amount
        numeric tip_amount
        numeric weather_surcharge
        varchar status "pending..delivered/spoiled/cancelled"
        int sla_countdown_sec
        varchar idempotency_key UK
    }
    ORDER_ITEMS {
        varchar order_id PK,FK
        varchar item_id PK,FK
        int quantity "CHECK > 0"
        numeric price
    }
    SAGA_CUSTOMER_ORDERS {
        varchar order_id PK
        varchar customer_id
        varchar status "CREATED..DELIVERED"
        varchar saga_state "PENDING/COMPLETED/ABORTED"
    }
    HITL_QUEUE {
        varchar ticket_id PK
        varchar customer_id FK
        int order_id FK
        varchar type
        varchar status
        numeric amount
    }

    CUSTOMERS ||--o{ CUSTOMER_ADDRESSES : has
    CUSTOMERS ||--o{ CUSTOMER_PAYMENT_CARDS : has
    CUSTOMERS ||--o{ ORDERS : places
    DARK_STORES ||--o{ INVENTORY : stocks
    DARK_STORES ||--o{ ORDERS : fulfils
    ORDERS ||--o{ ORDER_ITEMS : contains
    INVENTORY ||--o{ ORDER_ITEMS : "line item"
    CUSTOMERS ||--o{ HITL_QUEUE : raises
    ORDERS ||--o{ HITL_QUEUE : "may escalate"
```

> Note — **two parallel order models** coexist: the rich legacy `orders`
> (written by `transaction/OrderServiceImpl.checkout`) and the hexagonal
> `saga_customer_orders` (the `ordermanagement` choreography saga). They are not
> FK-linked; see Validation F-5.

---

## B. Finance — Double-Entry Ledger & Payments (`oltp`)

```mermaid
erDiagram
    JOURNAL_ENTRIES {
        serial entry_id PK
        uuid entry_uuid UK
        varchar reference "ORDER-PAY, REFUND-AUTO, COLD-BREACH..."
        varchar previous_entry_hash "hash chain"
        varchar entry_hash "SHA-256 (set by trigger)"
        timestamptz timestamp
    }
    LEDGER_LINES {
        serial line_id PK
        int entry_id FK
        varchar account_type "customer|rider|picker|wholesaler|system"
        varchar actor_id "polymorphic; NULL for system"
        numeric debit "CHECK >= 0"
        numeric credit "CHECK >= 0"
    }
    PAYMENTS {
        int payment_id PK
        int order_id "FK dropped in V3 (decoupled)"
        varchar customer_id
        numeric amount
        varchar currency
        varchar status "AUTHORIZED/CAPTURED/REFUNDED..."
        varchar idempotency_key UK
    }
    SECURITY_TRUST_LEDGER {
        serial audit_id PK
        varchar actor_type
        varchar actor_id
        varchar event
        int delta
        int current_value "CHECK 0..100"
    }
    JOURNAL_ENTRIES ||--|{ LEDGER_LINES : "balanced legs"
```

**Integrity (DB-enforced, `V1`):**
- `trg_enforce_ledger_balance` — *deferrable* constraint trigger: at commit,
  `SUM(debit) = SUM(credit)` per `entry_id`, else the whole transaction aborts.
- `trg_hash_journal_entry` — `BEFORE INSERT`: chains
  `entry_hash = sha256(entry_uuid ‖ reference ‖ description ‖ previous_entry_hash)`.
- `ledger_lines` CHECKs: `debit>0 OR credit>0` **and** `NOT(debit>0 AND credit>0)`
  — exactly one side positive per leg.

---

## C. Logistics — Riders, Dispatch & Telemetry (`oltp` + `dispatch`)

```mermaid
erDiagram
    RIDERS {
        varchar rider_id PK
        varchar full_name
        varchar vehicle_type "V1 chk relaxed by V8/V9"
        varchar onboarding_status
        numeric wallet_balance
        numeric active_lat
        numeric active_lng
        int trust_score "CHECK 0..100"
        boolean gear_exempt "added V9"
    }
    ONBOARDING_APPLICATIONS {
        varchar application_id PK
        varchar applicant_type
        boolean approval_ops
        boolean approval_compliance
        boolean approval_admin
    }
    RIDER_ACADEMY_CERTIFICATES {
        serial certificate_id PK
        varchar rider_id FK
        varchar course_name
    }
    ORDER_TELEMETRY_LOGS {
        serial log_id PK
        int order_id FK
        timestamptz device_timestamp
        timestamptz server_timestamp
        numeric temperature
        boolean dry_ice_injected
        boolean alert_triggered
    }
    ACTIVE_SHIPMENTS {
        int shipment_id PK
        int order_id
        varchar rider_id
        varchar status "ASSIGNED/PICKING_UP/DELIVERING/REALLOCATED"
        timestamptz stationary_since
    }
    GEAR_SCANS {
        varchar scan_id PK
        varchar rider_id
        varchar gear_type
        varchar verification_status
    }
    VEHICLE_CONFIGS {
        int id PK
        varchar vehicle_type
        numeric max_weight_kg
    }
    RIDER_SHIFTS {
        int shift_id PK
        varchar rider_id FK
        timestamptz start_time
        timestamptz end_time "CHECK end > start"
    }
    DELIVERY_ZONES {
        varchar zone_id PK
        varchar status "ACTIVE/PAUSED/RETIRED"
    }

    RIDERS ||--o{ RIDER_ACADEMY_CERTIFICATES : earns
    RIDERS ||--o{ RIDER_SHIFTS : works
    ORDERS ||--o{ ORDER_TELEMETRY_LOGS : "cold-chain ticks"
    ORDERS ||--o| ACTIVE_SHIPMENTS : "dispatched as"
    RIDERS ||--o{ GEAR_SCANS : submits
```

---

## D. Wholesaler & B2B Procurement (`oltp` + `wholesaler`)

```mermaid
erDiagram
    WHOLESALERS {
        varchar wholesaler_id PK
        varchar name
        boolean is_active
        int trust_score "CHECK 0..100"
        numeric base_invoice_amount
        numeric fallback_invoice_amount
    }
    B2B_RESTOCK_ORDERS {
        serial restock_order_id PK
        varchar store_id FK
        varchar wholesaler_id FK
        numeric invoice_amount
        varchar status "pending/fulfilled/failed"
        varchar idempotency_key UK
    }
    PROCUREMENT_APPROVALS {
        serial id PK
        int restock_order_id FK "ON DELETE SET NULL"
        varchar wholesaler_id
        numeric amount
        varchar status "PENDING/APPROVED/REJECTED"
        varchar override_by
    }
    PURCHASE_ORDERS {
        varchar po_id PK
        varchar wholesaler_id
        varchar status "DRAFT/SENT/PARTIALLY_RECEIVED/RECEIVED/REJECTED"
    }
    PURCHASE_ORDER_ITEMS {
        int id PK
        varchar po_id FK
        varchar product_id
        int requested_qty "CHECK >= 0"
        int received_qty "CHECK >= 0"
    }
    WHOLESALER_WASTAGE_LOGS {
        int id PK
        int qty_wasted "CHECK > 0"
    }

    WHOLESALERS ||--o{ B2B_RESTOCK_ORDERS : supplies
    DARK_STORES ||--o{ B2B_RESTOCK_ORDERS : "restocks to"
    B2B_RESTOCK_ORDERS ||--o| PROCUREMENT_APPROVALS : "HITL gate"
    PURCHASE_ORDERS ||--o{ PURCHASE_ORDER_ITEMS : contains
```

---

## E. Auth, Identity & Security (`oltp`)

```mermaid
erDiagram
    USER_ACCOUNTS {
        varchar user_id PK
        varchar email UK
        varchar password_hash "BCrypt"
        varchar status
        varchar role "CUSTOMER/ADMIN/RIDER/WHOLESALER (V20)"
    }
    SESSIONS {
        varchar session_id PK
        varchar user_id
        timestamptz expires_at
        boolean active
    }
    CUSTOMER_PROFILES {
        varchar profile_id PK
        varchar user_id FK,UK
        boolean marketing_opt_in
    }
    CHAOS_FAULT_LOGS {
        serial fault_id PK
        varchar fault_type
        timestamptz triggered_at
        timestamptz resolved_at
    }
    USER_ACCOUNTS ||--o| CUSTOMER_PROFILES : "1:1 profile"
    USER_ACCOUNTS ||--o{ SESSIONS : "issues (logical)"
```

---

## F. Catalog, Rewards, Eventing & Support (`oltp` + default schema)

```mermaid
erDiagram
    PRODUCT_LISTINGS {
        varchar product_id PK
        varchar title
        numeric base_price "CHECK >= 0"
        varchar status "DRAFT/ACTIVE/OUT_OF_STOCK/ARCHIVED"
    }
    PROMOTIONS {
        varchar promo_id PK
        varchar type "PERCENT/FLAT/FREE_SHIPPING"
        numeric value "CHECK >= 0"
    }
    CUSTOMER_LOYALTY {
        varchar id PK
        varchar customer_id
        int points
    }
    OUTBOX_EVENTS {
        serial id PK
        varchar aggregate_type
        varchar aggregate_id
        varchar event_type
        text payload
        varchar status "PENDING/PUBLISHED/FAILED"
        int retry_count
    }
    DOMAIN_EVENTS {
        varchar event_id PK
        varchar event_type
        varchar aggregate_id
        text payload
    }
    NOTIFICATIONS {
        int id PK
        varchar channel "EMAIL/SMS/PUSH/WEBHOOK"
        varchar status "QUEUED/SENT/FAILED/CANCELLED"
    }
    SUPPORT_TICKETS {
        int id PK
        varchar priority "LOW/NORMAL/HIGH/URGENT"
        varchar status "OPEN..RESOLVED/CLOSED"
    }
    FEEDBACKS {
        int id PK
        varchar customer_id
        int rating
    }
```

> `outbox_events` is the transactional-outbox relay table (see sequence §9).
> `domain_events` and `inventory_items` are **entity-only** — no migration
> creates them (Validation F-1).

---

## G. OLAP Analytical Warehouse (`olap`)

Populated from `oltp` by the stored procedure `olap.etl_sync_replication_process()` (`V1`).

```mermaid
erDiagram
    DW_REVENUE_FACTS {
        serial fact_id PK
        varchar payment_method
        numeric total_sales
        numeric surcharges
    }
    DW_DELIVERY_SLA_DIMENSION {
        serial dimension_id PK
        int order_id
        varchar sla_status "met/violated"
    }
    DW_IOT_TEMPERATURE_VIOLATIONS {
        serial violation_id PK
        int order_id
        numeric max_temperature_recorded
    }
    DW_CUSTOMER_FRAUD_RISK_SCORES {
        varchar customer_id PK
        int order_count
        int refund_count
        int fraud_risk_percentage
        boolean audit_flagged
    }
    DW_ESG_CARBON_FACTS {
        serial fact_id PK
        int order_id
        int bags_returned
        int co2_offset_grams
    }
```

ETL derives: revenue facts ← `orders`; SLA dimension ← delivered `orders`;
temperature violations ← `order_telemetry_logs ⋈ order_items ⋈ inventory`
(perishable + alert); fraud scores ← `customers ⋈ orders ⋈ hitl_queue`
(refund ratio ≥ 0.30 ⇒ flagged); ESG carbon ← `orders.bags_returned × 250 g`.

---

## Validation Findings

Severity: 🔴 fix before prod · 🟡 drift / inconsistency · 🟢 verified-correct.

| ID | Sev | Finding | Recommendation |
| :-- | :-: | :--- | :--- |
| F-1 | 🔴 | `DomainEventEntity → domain_events` and `InventoryItemEntity → inventory_items` have **no `CREATE TABLE` in any migration**. They exist only via Hibernate `ddl-auto`. Under prod (`ddl-auto=validate` + Flyway-only), these tables are absent → context fails. | Add a Flyway migration creating both (in `oltp`), or drop the unused entities. |
| F-2 | 🟡 | Schema mismatch: `saga_customer_orders` & `promotions` are created in **`oltp`**, `delivery_zones` & `rider_shifts` in **`dispatch`**, but their entities (`CustomerOrderEntity`, `PromotionEntity`, `DeliveryZoneEntity`, `RiderShiftEntity`) declare **no `schema`**. Resolution depends on the connection `search_path`. | Add the explicit `schema=` to each `@Table` to remove search_path ambiguity. |
| F-3 | 🟡 | `RewardPointsEntity` and `Customer` **both map to `oltp.customers`** (RewardPoints is a read-projection over `loyalty_points`). Two writable entities on one table risks divergent updates. | Make `RewardPointsEntity` `@Immutable`/read-only, or fold into `Customer`. |
| F-4 | 🟡 | Two `wastage_logs` tables: `WastageLog` (`oltp`) and `WastageLogEntity` (`wholesaler`). Same name, different schemas/shapes. | Rename one (e.g. `wholesaler.wholesaler_wastage_logs`) to avoid confusion. |
| F-5 | 🟡 | Two unlinked order models: rich `oltp.orders` (transaction domain) and `oltp.saga_customer_orders` (ordermanagement saga). No FK relates them. | Decide the system of record; bridge by `order_id` or merge the saga state onto `orders`. |
| F-6 | 🟡 | `V1` CHECK constraints encode early enums later outgrown by code — e.g. `riders.vehicle_type IN ('E-Bike','Scooter')` (code uses Van/Bike), `riders.onboarding_status IN ('unapplied','pending','active')` (code uses `pending_review`/`approved`), `hitl_queue.type` lacks `agent_escalation`. Later migrations (`V6`,`V8`,`V9`) realign some. | Audit that every status string written by services satisfies the *current* CHECK set; add a migration for any gap. |
| F-7 | 🟢 | Ledger integrity is **DB-enforced**, not just app-enforced: deferrable balance trigger + hash-chain trigger + debit/credit XOR checks. Strong. | None — keep. |
| F-8 | 🟢 | GDPR model is sound: `customers.email` nullable + `hashed_email` retained for fraud; `is_anonymized` excludes rows from OLAP fraud ETL. | None — keep. |
| F-9 | 🟢 | Optimistic-locking `version` columns on `customers` and `inventory` back the `@TransactionalRetry` checkout path. | None — keep. |

**Verdict:** the as-built data model is coherent and the financial core is
rigorously constrained. Two concrete prod-blocking gaps (F-1) and a set of
low-risk drifts (F-2…F-6) are itemised above. The aspirational sharded ERD in
`data-model-erd.md` should be marked as target-state, not current.
