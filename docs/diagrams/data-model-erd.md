# Data Model ERD (Sharded Architecture)

This ERD represents the target microservices data model. The monolith's `oltp` schema has been sharded across 7 dedicated PostgreSQL databases (one per service).

## 1. User Service Database (`user_db`)

```mermaid
erDiagram
  USERS {
    varchar user_id PK
    varchar full_name
    varchar email
    varchar hashed_email
    boolean is_anonymized
  }
  USER_ADDRESSES {
    int address_id PK
    varchar user_id FK
    text address_line
  }
  USER_SESSIONS {
    varchar session_id PK
    varchar user_id FK
    timestamp expires_at
  }
  USERS ||--o{ USER_ADDRESSES : has
  USERS ||--o{ USER_SESSIONS : active
```

## 2. Payment Service Database (`payment_db`)

```mermaid
erDiagram
  PAYMENTS {
    int payment_id PK
    int order_id
    varchar customer_id
    numeric amount
    varchar currency
    varchar payment_method
    varchar status
    varchar idempotency_key
    timestamp created_at
  }
  PAYMENT_OUTBOX {
    int id PK
    varchar aggregate_type
    varchar aggregate_id
    varchar event_type
    text payload
    varchar status
    int retry_count
  }
  PROCESSED_EVENTS {
    varchar event_id PK
    timestamp processed_at
  }
```

## 3. Account Service Database (`account_db`)

```mermaid
erDiagram
  ACCOUNTS {
    varchar account_id PK
    varchar owner_id
    varchar owner_type
    numeric balance
    varchar status
  }
  ACCOUNT_TRANSACTIONS {
    int tx_id PK
    varchar account_id FK
    varchar ref_id
    numeric debit
    numeric credit
    timestamp created_at
  }
  PROCESSED_EVENTS {
    varchar event_id PK
  }
  ACCOUNTS ||--o{ ACCOUNT_TRANSACTIONS : records
```

## 4. Transaction Service Database (`transaction_db`)

```mermaid
erDiagram
  JOURNAL_ENTRIES {
    int entry_id PK
    uuid entry_uuid
    varchar reference
    varchar previous_entry_hash
    varchar entry_hash
    timestamp created_at
  }
  LEDGER_LINES {
    int line_id PK
    int entry_id FK
    varchar account_type
    varchar actor_id
    numeric debit
    numeric credit
  }
  PROCESSED_EVENTS {
    varchar event_id PK
  }
  JOURNAL_ENTRIES ||--o{ LEDGER_LINES : contains
```

## 5. Fraud Detection Database (`fraud_db`)

```mermaid
erDiagram
  FRAUD_RULES {
    int rule_id PK
    varchar condition
    int weight
    boolean active
  }
  FRAUD_DECISIONS {
    int decision_id PK
    int payment_id
    varchar customer_id
    int risk_score
    varchar outcome
    timestamp created_at
  }
  PROCESSED_EVENTS {
    varchar event_id PK
  }
```

## 6. Notification Service Database (`notification_db`)

```mermaid
erDiagram
  USER_PREFERENCES {
    varchar user_id PK
    boolean allow_sms
    boolean allow_email
    boolean allow_push
  }
  NOTIFICATION_LOG {
    int log_id PK
    varchar user_id FK
    varchar channel
    varchar template
    varchar status
    timestamp sent_at
  }
  PROCESSED_EVENTS {
    varchar event_id PK
  }
  USER_PREFERENCES ||--o{ NOTIFICATION_LOG : tracks
```

## 7. Security Engine Database (`security_db`)

```mermaid
erDiagram
  COMPLIANCE_SNAPSHOTS {
    int snapshot_id PK
    varchar report_type
    text summary
    timestamp generated_at
  }
  PROCESSED_EVENTS {
    varchar event_id PK
  }
```

> **Note on Auditing**: The Security Engine also writes immutable audit logs to an external `MongoDB` archive collection.

## Notes on Microservices Data Boundaries
- **No Foreign Keys across databases**. A `user_id` in the `PAYMENTS` table is just a `VARCHAR`, not an FK to the `USERS` table.
- **Outbox Pattern**: Each database producing Kafka events needs its own outbox table (e.g., `PAYMENT_OUTBOX`).
- **Idempotency**: Every consuming service implements a `PROCESSED_EVENTS` table to deduplicate consumed Kafka messages.
