# Data Model ERD

This ERD represents the primary transactional data model used by the backend.
It is aligned with the `backend/src/main/resources/db/migration/V1__init_schema.sql` schema definitions.

## Entity Relationship Diagram

```mermaid
erDiagram
  CUSTOMERS {
    varchar customer_id PK
    varchar full_name
    varchar email
    varchar hashed_email
    numeric wallet_balance
    int loyalty_points
    boolean vip_status
    int trust_score
  }
  CUSTOMER_ADDRESSES {
    int address_id PK
    varchar customer_id FK
    text address_line
    numeric latitude
    numeric longitude
  }
  CUSTOMER_PAYMENT_CARDS {
    int card_id PK
    varchar customer_id FK
    varchar card_type
    varchar last_four_digits
    varchar token_reference
  }
  RIDERS {
    varchar rider_id PK
    varchar full_name
    varchar vehicle_type
    boolean active
    int trust_score
  }
  INVENTORY {
    varchar item_id PK
    varchar store_id FK
    varchar name
    numeric price
    int stock
  }
  DARK_STORES {
    varchar store_id PK
    varchar store_name
    text address
  }
  ORDERS {
    int order_id PK
    varchar customer_id FK
    varchar store_id FK
    varchar rider_id FK
    numeric total_amount
    varchar status
    varchar idempotency_key
  }
  ORDER_ITEMS {
    int order_id FK
    varchar item_id FK
    int quantity
    numeric price
  }
  ORDER_TELEMETRY_LOGS {
    int log_id PK
    int order_id FK
    timestamp device_timestamp
    numeric latitude
    numeric longitude
    numeric temperature
  }
  JOURNAL_ENTRIES {
    int entry_id PK
    uuid entry_uuid
    varchar reference
    text description
  }
  LEDGER_LINES {
    int line_id PK
    int entry_id FK
    varchar account_type
    varchar actor_id
    numeric debit
    numeric credit
  }
  OUTBOX_EVENTS {
    int id PK
    varchar aggregate_type
    varchar aggregate_id
    varchar event_type
    text payload
    varchar status
  }

  CUSTOMERS ||--o{ CUSTOMER_ADDRESSES : has
  CUSTOMERS ||--o{ CUSTOMER_PAYMENT_CARDS : owns
  CUSTOMERS ||--o{ ORDERS : places
  ORDERS ||--o{ ORDER_ITEMS : contains
  ORDER_ITEMS }o--|| INVENTORY : references
  ORDERS }o--|| DARK_STORES : fulfilled_by
  ORDERS }o--|| RIDERS : assigned_to
  ORDERS ||--o{ ORDER_TELEMETRY_LOGS : logs
  ORDERS ||--o{ JOURNAL_ENTRIES : accounts_for
  JOURNAL_ENTRIES ||--o{ LEDGER_LINES : contains
  OUTBOX_EVENTS : ||..|| ORDERS : references
```

## Notes
- The schema is predominantly normalized for OLTP order processing, rider telemetry, and financial journal auditing.
- `OUTBOX_EVENTS` is now included in the transactional schema and used for eventual consistency and publish/subscribe dispatch.
- Customer identifiers are `VARCHAR(50)` in the OLTP schema, not `BIGINT`.
- Telemetry logs are persisted separately from the core order write path to reduce transactional contention.
- The schema also includes additional OLTP tables such as `hitl_queue`, `security_trust_ledger`, `system_configurations`, `chaos_fault_logs`, and OLAP warehouse tables under the `olap` schema.
