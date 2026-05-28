# Low-Level Design (LLD): Swiss Quick Commerce System

This Low-Level Design (LLD) document translates the High-Level Design (HLD) architecture and Business Requirements Document (BRD) into concrete operational models. It maps directly to the API contracts (`bff-openapi.yaml`) and database schemas (`schema.sql`).

---

## 1. Use Case Diagrams

### 1.1 Core Marketplace Operations
This diagram maps the interactions of primary actors (Customer, Rider, Picker) with the system's core capabilities.

```mermaid
usecaseDiagram
    actor Customer
    actor Rider
    actor Picker
    
    package "Core Operations" {
        usecase "Browse Catalog" as UC1
        usecase "Place Checkout Order" as UC2
        usecase "Request AI Refund" as UC3
        usecase "Complete Picking" as UC4
        usecase "Inject Cold Chain Coolant" as UC5
        usecase "Confirm Delivery" as UC6
    }
    
    Customer --> UC1
    Customer --> UC2
    Customer --> UC3
    
    Picker --> UC4
    
    Rider --> UC5
    Rider --> UC6
```

### 1.2 Back-Office & Governance
This diagram maps the interactions of administrative and B2B actors (Admin, Wholesaler) managing capacity, risk, and stability.

```mermaid
usecaseDiagram
    actor Admin
    actor Wholesaler
    
    package "Governance & B2B" {
        usecase "Toggle Chaos Faults" as UC7
        usecase "Resolve HITL Tickets" as UC8
        usecase "Approve Onboarding" as UC9
        usecase "Fulfill Restock Invoice" as UC10
        usecase "Rebalance MFC Stock" as UC11
    }
    
    Admin --> UC7
    Admin --> UC8
    Admin --> UC9
    Admin --> UC11
    
    Wholesaler --> UC10
```

---

## 2. Class Diagrams (Domain Model)

This class diagram represents the Object-Relational mapping directly derived from `schema.sql`.

```mermaid
classDiagram
    class Customer {
        +String customerId
        +String email
        +BigDecimal walletBalance
        +int trustScore
        +boolean isOnProbation
        +purgeGDPR()
    }
    
    class Rider {
        +String riderId
        +String vehicleType
        +int trustScore
        +String onboardingStatus
        +BigDecimal walletBalance
    }
    
    class Order {
        +int orderId
        +BigDecimal totalAmount
        +BigDecimal weatherSurcharge
        +String status
        +int slaCountdownSec
        +calculateSLA()
    }
    
    class Inventory {
        +String itemId
        +String name
        +int stock
        +BigDecimal price
        +boolean perishable
        +decrementStock()
    }
    
    class B2BRestockOrder {
        +int restockOrderId
        +BigDecimal invoiceAmount
        +boolean isFallback
        +String status
    }
    
    class Wholesaler {
        +String wholesalerId
        +int trustScore
        +boolean academyDiscountActive
        +BigDecimal baseInvoiceAmount
    }
    
    class JournalEntry {
        +String entryUuid
        +String reference
        +String previousEntryHash
        +String entryHash
    }

    Customer "1" -- "*" Order : places
    Rider "1" -- "*" Order : delivers
    Order "*" -- "*" Inventory : contains
    Wholesaler "1" -- "*" B2BRestockOrder : fulfills
    JournalEntry "1" -- "*" Customer : debits/credits
```

---

## 3. Sequence Diagrams

### 3.1 Order Checkout Flow & SLA Engine
Maps to: `POST /api/customer/orders`

```mermaid
sequenceDiagram
    actor Customer
    participant BFF as BFF Gateway
    participant OrderSrv as Order Processor
    participant DB as OLTP DB
    participant Ledger as LedgerService
    
    Customer->>BFF: POST /api/customer/orders (Cart JSON)
    BFF->>BFF: Validate JWT & Rate Limits
    BFF->>OrderSrv: Process Checkout (Idempotency Key)
    
    OrderSrv->>DB: Check Trust Score (>= 65)
    DB-->>OrderSrv: Trust Score OK
    
    OrderSrv->>DB: Check Stock Availability
    DB-->>OrderSrv: Stock Available
    
    OrderSrv->>OrderSrv: Calculate Weather Surcharge & SLA Offset
    
    OrderSrv->>Ledger: Debit Customer Wallet & Credit System
    Ledger->>DB: Insert LedgerLines & Hash JournalEntry
    DB-->>Ledger: Ledger Hash Validated
    
    OrderSrv->>DB: Insert Order (Status: pending)
    DB-->>OrderSrv: Order Created
    
    OrderSrv-->>BFF: 201 Created (Order ID)
    BFF-->>Customer: Checkout Success
```

### 3.2 Human-In-The-Loop (HITL) Refund Flow
Maps to: `POST /api/customer/orders/{id}/refund` and `POST /api/admin/hitl/queue/{id}/resolve`

```mermaid
sequenceDiagram
    actor Customer
    actor Admin
    participant Bot as AI Support Bot
    participant AdminSrv as Admin Service
    participant DB as OLTP DB
    participant Ledger as LedgerService
    
    Customer->>Bot: POST .../{id}/refund (EXIF & GPS Data)
    Bot->>DB: Check Customer Fraud Index
    Bot->>DB: Create HITL Ticket (Status: pending)
    DB-->>Bot: Ticket ID Created
    Bot-->>Customer: 200 Pending Admin Approval
    
    Admin->>AdminSrv: POST /api/admin/hitl/queue/{id}/resolve (Approve)
    AdminSrv->>DB: Update Ticket Status -> approved
    
    AdminSrv->>Ledger: Credit Customer Wallet (Refund)
    Ledger->>DB: Commit Refund JournalEntry
    
    AdminSrv-->>Admin: 200 Ticket Resolved
```

### 3.3 Dynamic B2B Restock & Fallback Failover
Maps to backend `WholesalerService.createRestockOrder()` cron job.

```mermaid
sequenceDiagram
    participant StockSrv as Inventory Engine
    participant B2BSrv as Wholesaler Service
    participant DB as OLTP DB
    
    StockSrv->>DB: Detect Stock < 3 Units
    StockSrv->>B2BSrv: Trigger Replenishment
    
    B2BSrv->>DB: Fetch Primary Wholesaler Status
    DB-->>B2BSrv: TrustScore=55 (Low) OR Inactive
    
    Note over B2BSrv: Primary Wholesaler rejected.<br/>Initiating Failover Protocol.
    
    B2BSrv->>DB: Query Fallback Wholesaler (Trust >= 60)
    DB-->>B2BSrv: Secondary Wholesaler Selected
    
    B2BSrv->>B2BSrv: Apply Fallback Invoice Rate
    B2BSrv->>B2BSrv: Apply 10% Academy Discount (if active)
    
    B2BSrv->>DB: Insert B2BRestockOrder (is_fallback: true)
    DB-->>B2BSrv: Restock Logged
```

### 3.4 IoT Cold Chain Spoilage Mitigation
Maps to: `POST /api/rider/orders/{id}/coolant`

```mermaid
sequenceDiagram
    actor Rider
    participant IoT as IoT Telemetry Log
    participant DispSrv as Dispatch Service
    participant DB as OLTP DB
    
    IoT->>DB: Log Temp = 12.0°C (Alert Triggered)
    
    Note over Rider: Rider receives push notification<br/>to inject Dry Ice.
    
    Rider->>DispSrv: POST /api/rider/orders/{id}/coolant
    DispSrv->>DB: Update order_telemetry_logs (dry_ice_injected = true)
    DispSrv->>DB: Reset current transit temperature -> 4.0°C
    
    DispSrv-->>Rider: 200 Coolant Injected Successfully
```

### 3.5 Authentication & MFA Flow
Maps to: `POST /api/auth/login` and `POST /api/auth/mfa/verify`

```mermaid
sequenceDiagram
    actor User
    participant AuthSrv as Auth Service
    participant DB as OLTP DB
    
    User->>AuthSrv: POST /api/auth/login (credentials)
    AuthSrv->>DB: Validate Username & Password
    DB-->>AuthSrv: Valid Credentials (MFA Enabled)
    
    AuthSrv-->>User: 200 OK (mfa_required: true, session_token)
    
    User->>AuthSrv: POST /api/auth/mfa/verify (session_token, OTP)
    AuthSrv->>AuthSrv: Validate OTP
    AuthSrv-->>User: 200 OK (JWT Bearer Token)
```

### 3.6 GDPR Erasure (Right to be Forgotten)
Maps to: `POST /api/customer/profile/purge`

```mermaid
sequenceDiagram
    actor Customer
    participant CustSrv as Customer Service
    participant DB as OLTP DB
    
    Customer->>CustSrv: POST /api/customer/profile/purge (JWT)
    CustSrv->>DB: Nullify PII (Name, Email, Payment Tokens)
    CustSrv->>DB: Set probation_status = true
    CustSrv->>DB: Reset Trust Score (e.g., 75)
    
    CustSrv-->>Customer: 200 OK (Profile Purged)
```

### 3.7 Rider Onboarding & Academy Certification
Maps to: `POST /api/rider/onboard`, `POST /admin/onboard/queue/{id}/approve`, and `POST /api/rider/academy/courses/{id}/complete`

```mermaid
sequenceDiagram
    actor Rider
    actor Admin
    participant RiderSrv as Rider Service
    participant AdminSrv as Admin Service
    participant DB as OLTP DB
    
    Rider->>RiderSrv: POST /api/rider/onboard (DL, Vehicle)
    RiderSrv->>DB: Insert OnboardingApplication (pending)
    RiderSrv-->>Rider: 202 Accepted
    
    Admin->>AdminSrv: POST /api/admin/onboard/queue/{id}/approve
    AdminSrv->>DB: Update Application (approved)
    AdminSrv-->>Admin: 200 OK
    
    Rider->>RiderSrv: POST /api/rider/academy/courses/{id}/complete
    RiderSrv->>DB: Issue Certificate
    RiderSrv->>DB: Increment Trust Score (+10)
    RiderSrv-->>Rider: 200 OK
```

### 3.8 Warehouse Picking & Lightning Bonus
Maps to: `POST /api/inventory/picker/handover`

```mermaid
sequenceDiagram
    actor Picker
    participant InvSrv as Inventory Service
    participant DB as OLTP DB
    
    Picker->>InvSrv: POST /api/inventory/picker/handover (duration_seconds)
    InvSrv->>DB: Update Order Status (picking -> picked)
    
    alt duration_seconds <= 4.0
        InvSrv->>DB: Award Lightning Picker Badge
    end
    
    InvSrv-->>Picker: 200 OK (lightning_bonus_awarded: true/false)
```

### 3.9 Chaos Engineering Fault Injection
Maps to: `POST /api/admin/chaos/faults`

```mermaid
sequenceDiagram
    actor Admin
    participant ChaosSrv as Chaos Service
    participant Sys as System Components (DB, Cache)
    
    Admin->>ChaosSrv: POST /api/admin/chaos/faults (fault_type, inject)
    
    alt fault_type == traffic_congestion
        ChaosSrv->>Sys: Simulate Rider GPS Latency
    else fault_type == cold_chain_breakdown
        ChaosSrv->>Sys: Override IoT Sensor Temps (>8.0°C)
    else fault_type == wholesaler_outage
        ChaosSrv->>Sys: Disable Primary B2B Gateway
    end
    
    ChaosSrv-->>Admin: 200 OK (Fault Injected)
```
