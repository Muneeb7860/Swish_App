# Domain Sequence & Flow Diagrams — Part 2

Continuation of [`domain-sequence-diagrams.md`](./domain-sequence-diagrams.md).
Part 1 covers the order saga, AI agent orchestration, B2B RFQ + governance, and
dispatch. This part completes the remaining bounded contexts: authentication &
authorization, the customer purchase + refund flow, the double-entry ledger,
the transactional outbox, telemetry CQRS, rider enrollment/delivery, and rewards.

Every diagram cites the `core/service` (or `config`) source it was derived from.

---

## 5. Authentication, JWT Issuance & Per-Request Authorization

Source: `domain/auth/core/service/AuthServiceImpl.java`,
`domain/auth/adapter/out/security/TokenServiceAdapter.java`,
`config/JwtAuthenticationFilter.java`, `config/OpaAuthorizationManager.java`

Login verifies credentials in constant time (no user-enumeration), opens a
24h session, and mints an HS384 JWT carrying `sub` + `role` + `sid`. Every
later request is authenticated by the filter and authorized by the OPA manager
(which falls back to static deny-by-default rules when the OPA daemon is off).

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant AC as AuthController
    participant AS as AuthServiceImpl
    participant UR as UserRepositoryPort
    participant PE as PasswordEncoder (BCrypt)
    participant SR as SessionRepositoryPort
    participant TS as TokenServiceAdapter

    C->>AC: POST /api/v1/auth/login {email, password}
    AC->>AS: login(email, password, device, ip)
    AS->>UR: findByEmail(email)
    AS->>PE: matches(password, hash)
    Note over AS: encoder.matches runs even on lookup miss (timing-safe)
    alt invalid credentials or account LOCKED
        AS-->>AC: throw IllegalArgumentException
        AC-->>C: 401 Invalid credentials
    else valid
        AS->>SR: save(Session, 24h TTL)
        AC->>UR: findByEmail → role
        AC->>TS: generateToken(sessionId, userId, role)
        TS-->>AC: signed JWT (sub, role, sid)
        AC-->>C: 200 {token, sessionId, expiresAt}
    end
```

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant F as JwtAuthenticationFilter
    participant OPA as OpaAuthorizationManager
    participant Ctrl as Domain Controller

    C->>F: request + Authorization: Bearer <jwt>
    F->>F: validate signature + expiry → set ROLE_<role> authority
    F->>OPA: SecurityFilterChain.check(auth, /api/**)
    alt OPA daemon enabled
        OPA->>OPA: POST policy query {uri, method, roles}
    else OPA disabled / unreachable
        OPA->>OPA: evaluateFallbackRules(uri, roles) — deny-by-default
    end
    alt allowed
        OPA->>Ctrl: forward
        Ctrl->>Ctrl: method @PreAuthorize / assertOwnership (defense-in-depth)
        Ctrl-->>C: 2xx
    else denied
        OPA-->>C: 403 Forbidden
    end
```

---

## 6. Customer Checkout (Purchase)

Source: `domain/transaction/core/service/OrderServiceImpl.java#checkout`

The central commerce transaction. Idempotent (unique-key + retry), it routes to
a dark store, decrements stock per item, prices in weather surcharge / tip /
ESG rebate, assigns an optimal rider, posts a balanced double-entry ledger
transaction (which fails closed on insufficient funds), accrues loyalty points,
and writes an `order.placed` event to the transactional outbox — all in one
`@Transactional @TransactionalRetry` boundary.

```mermaid
sequenceDiagram
    autonumber
    participant API as CustomerController
    participant OS as OrderServiceImpl
    participant OP as OrderPort
    participant INV as InventoryPort
    participant CFG as SystemConfigPort
    participant RP as RiderPort
    participant LED as LedgerUseCase
    participant OBX as OutboxEventPort

    API->>OS: checkout(customerId, items, paymentMethod, tip, bags, idempotencyKey)
    Note over OS: validate items (qty>0, no dup SKU); assertOwnership upstream
    OS->>OP: findByIdempotencyKey(key)
    alt order already exists
        OS-->>API: existing Order (idempotent replay)
    else new order
        OS->>CFG: evaluateCheckoutRouting → Central / East store
        loop each cart item
            OS->>INV: findInventoryById → check stock
            OS->>INV: save(stock - qty)
        end
        Note over OS: weather surcharge + tip − ESG bag rebate; SLA by vehicle/perishable
        OS->>RP: findOptimalRider(perishable)
        OS->>OP: save(order) + flush (unique-key idempotency)
        OS->>LED: recordTransaction(ORDER-PAY, legs)
        Note over LED: rolls back whole checkout if customer wallet would go < 0
        OS->>OP: customer.loyaltyPoints += items×10
        OS->>OBX: save(OutboxEvent "order.placed", PENDING)
        OS-->>API: 201 Order
    end
```

---

## 7. Refund & SLA Auto-Approval

Source: `domain/transaction/core/service/OrderServiceImpl.java#requestRefund`

Refunds are gated by the customer's trust score and a GPS telemetry-correlation
audit. A verified SLA breach is auto-approved by the AI autopilot (instant
wallet credit + ledger entry); everything else is filed as a pending HITL ticket
for an admin.

```mermaid
sequenceDiagram
    autonumber
    participant API as CustomerController
    participant OS as OrderServiceImpl
    participant CP as CustomerPort
    participant HQ as HitlQueuePort
    participant LED as LedgerUseCase

    API->>OS: requestRefund(orderId, claimReason, lat, lng)
    alt trustScore < 65
        OS-->>API: rejected (trust threshold)
    else GPS audit fails (|Δlat| > 0.05)
        OS->>CP: trustScore −25
        OS-->>API: rejected (telemetry correlation failed)
    else late claim AND slaCountdownSec <= 0
        OS->>HQ: save HITL ticket (status=approved, AI-AUTOPILOT)
        OS->>CP: walletBalance += totalAmount
        OS->>LED: recordTransaction(REFUND-AUTO)
        OS-->>API: approved + ticketId
    else otherwise
        OS->>HQ: save HITL ticket (status=pending)
        OS-->>API: pending_admin_approval + ticketId
    end
```

---

## 8. Double-Entry Ledger with Hash Chain

Source: `domain/transaction/core/service/LedgerServiceImpl.java#recordTransaction`

Every money movement is a balanced journal entry. Entries are **hash-chained**
(`entryHash = SHA-256(uuid, reference, description, previousHash)`) for
tamper-evidence, and actor wallets are adjusted with a non-negative invariant.

```mermaid
sequenceDiagram
    autonumber
    participant Caller as Checkout / Refund / Telemetry
    participant L as LedgerServiceImpl
    participant JE as JournalEntryRepository
    participant W as Customer/Rider wallet

    Caller->>L: recordTransaction(reference, description, legs)
    L->>L: sum debits vs credits
    alt totalDebit != totalCredit
        L-->>Caller: throw Unbalanced transaction
    else balanced
        L->>JE: findFirstByOrderByEntryIdDesc → previousHash
        L->>L: entryHash = SHA-256(uuid, ref, desc, prevHash)
        L->>JE: save(JournalEntry, chained hash)
        loop each leg
            L->>JE: save(LedgerLine)
            L->>W: wallet += (credit − debit)
            Note over W: reject if balance would drop below $0.00
        end
        L-->>Caller: JournalEntry (with ledger lines)
    end
```

---

## 9. Transactional Outbox → Kafka Relay

Source: `domain/event/core/service/OutboxEventScheduler.java`

Domain writes persist an `OutboxEvent(PENDING)` inside the business transaction.
A scheduler (every 4s) validates each event against the schema registry, routes
it to the correct Kafka topic, stamps an `X-Correlation-ID`, and publishes —
with retry counting and dead-letter (`FAILED` after 3 attempts).

```mermaid
sequenceDiagram
    autonumber
    participant SCH as OutboxEventScheduler (@Scheduled 4s)
    participant DB as OutboxEventRepository
    participant SR as TelemetrySchemaRegistry
    participant K as Kafka

    SCH->>DB: findByStatus("PENDING")
    loop each pending event
        SCH->>SR: validate(eventType, payload)
        alt schema invalid
            SCH->>DB: status = FAILED
        else valid
            SCH->>SCH: resolveTopicForEvent(eventType)
            SCH->>K: send(payload, topic, X-Correlation-ID, key=aggregateId)
            alt publish ok
                SCH->>DB: status = PUBLISHED
            else publish error
                SCH->>DB: retryCount++ (FAILED after 3)
            end
        end
    end
```

---

## 10. Telemetry CQRS Ingestion + Cold-Chain Spoilage

Source: `domain/telemetry/core/service/TelemetryServiceImpl.java`

High-frequency ticks are buffered in memory (write-side) and flushed in batches
by a scheduler (read/persist-side) to minimise DB write-amplification. Persisting
a tick can trip cold-chain rules: ≥12 °C in transit spoils the cargo, docks the
rider's trust score (audited), and books a write-off ledger entry. GPS outliers
(>150 km/h) are discarded.

```mermaid
sequenceDiagram
    autonumber
    participant Rider as Rider device
    participant TS as TelemetryServiceImpl
    participant BUF as tickBuffer (in-memory queue)
    participant TP as TelemetryPort
    participant LED as LedgerUseCase
    participant GEO as GeoLocationPort (Redis)

    Rider->>TS: queueTick(orderId, lat, lng, temp)
    TS->>BUF: enqueue (write-side, non-blocking)

    Note over TS,TP: Scheduled flush (read/persist-side)
    TS->>BUF: drain ticks
    loop each tick
        TS->>TP: save(OrderTelemetryLog, alert = temp > 8°C)
        alt temp >= 12°C AND status = shipping
            TS->>TP: order.status = spoiled
            TS->>TP: rider.trustScore −30 + SecurityTrustLedger audit
            TS->>LED: recordTransaction(COLD-BREACH write-off)
        end
    end

    Rider->>TS: updateLocation(orderId, lat, lng)
    alt computed speed > 150 km/h
        TS-->>Rider: discard GPS outlier
    else
        TS->>GEO: updateLocation(orderId, lat, lng, temp)
    end
```

---

## 11. Rider Enrollment — 3-Gate Onboarding + Academy

Source: `domain/enrollment/core/service/RiderServiceImpl.java`

Onboarding requires three sequential approval gates (ops → compliance → admin);
each gate enforces that the prior one passed. Only when all three pass does the
rider become `active`. Academy course completion boosts the trust score (audited).

```mermaid
sequenceDiagram
    autonumber
    participant R as Applicant
    participant API as RiderController
    participant RS as RiderServiceImpl
    participant EN as EnrollmentOutPort
    participant Ops as Ops
    participant Comp as Compliance
    participant Adm as Admin

    R->>API: submitOnboarding(name, vehicleType)
    API->>RS: submitOnboarding(...)
    RS->>EN: save Application + Rider(status=pending_review)

    Ops->>RS: approveOnboarding(appId, "ops")
    RS->>EN: approvalOps = true
    Comp->>RS: approveOnboarding(appId, "compliance")
    Note over RS: rejects if ops not yet approved
    RS->>EN: approvalCompliance = true
    Adm->>RS: approveOnboarding(appId, "admin")
    Note over RS: rejects unless ops AND compliance approved
    RS->>EN: approvalAdmin = true
    alt all three gates approved
        RS->>EN: rider.onboardingStatus = approved
        RS-->>Adm: fully_approved (rider active)
    else
        RS-->>Adm: gate_approved (awaiting remaining)
    end
```

---

## 12. Delivery Confirmation / Rejection

Source: `domain/enrollment/core/service/RiderServiceImpl.java#confirmDelivery / rejectDelivery`

Handover requires a matching delivery PIN **or** a proof-of-delivery photo.
Success raises rider + customer trust (and exits customer probation after 3
consecutive orders); a door rejection requires reason + photo and issues an
instant wallet refund.

```mermaid
sequenceDiagram
    autonumber
    participant Rider as Rider
    participant RS as RiderServiceImpl
    participant EN as EnrollmentOutPort

    Rider->>RS: confirmDelivery(orderId, pin, photoUrl)
    alt order not in "shipping"
        RS-->>Rider: IllegalState
    else PIN matches OR photo provided
        RS->>EN: order.status = delivered + cleanup telemetry
        RS->>EN: rider.trust +5 (audited); customer.trust +3
        Note over RS: consecutiveOrders++ → exit probation at 3
        RS-->>Rider: delivered
    else neither PIN nor photo
        RS-->>Rider: Invalid handover
    end

    Rider->>RS: rejectDelivery(orderId, reason, rejectionPhoto)
    alt reason + photo present
        RS->>EN: order.status = rejected_at_door
        RS->>EN: customer.walletBalance += totalAmount (instant refund)
        RS-->>Rider: rejected_at_door
    else missing reason/photo
        RS-->>Rider: Invalid rejection
    end
```

---

## 13. Rewards — Loyalty Accrual & Redemption

Source: `domain/reward/core/service/RewardServiceImpl.java`, `RewardFactory.java`,
accrual also occurs inline in `OrderServiceImpl.checkout`

Points accrue on checkout (items × 10) and via the reward use-case, which
delegates to a strategy `RewardProcessor` selected by `RewardFactory`
(Points / Cashback / Badge). Redemption enforces a sufficient-balance rule.

```mermaid
sequenceDiagram
    autonumber
    participant API as RewardController / Checkout
    participant RS as RewardServiceImpl
    participant RF as RewardFactory
    participant P as RewardProcessor (Points/Cashback/Badge)
    participant OUT as RewardOutPort

    API->>RS: addPoints(customerId, amount)
    RS->>RF: getProcessor(POINTS)
    RF-->>RS: PointsRewardProcessor
    RS->>P: process(customerId, amount, reason)
    P->>OUT: persist loyalty points

    API->>RS: redeemPoints(customerId, amount)
    RS->>OUT: findRewardPointsByCustomerId
    alt balance < amount
        RS-->>API: RuleViolation (insufficient points)
    else
        RS->>OUT: save(points − amount)
        RS-->>API: redeemed
    end
```

---

## Full Domain Coverage Matrix

All 22 bounded contexts, with their flow-documentation status. "Diagram"
domains have a sequence diagram; "CRUD/port" domains are straightforward
read/write adapters whose behaviour is fully captured by their port interface
and need no separate sequence.

| # | Bounded context | Flow documented | Where |
| :-- | :--- | :--- | :--- |
| 1 | payment | ✅ saga + compensation | `lld-diagrams.md` |
| 2 | ordermanagement | ✅ order saga (choreography) | part 1 §1 |
| 3 | agent | ✅ orchestration + HITL + RFQ | part 1 §2–3 |
| 4 | governance | ✅ HITL override (in RFQ) | part 1 §3 |
| 5 | wholesaler | ✅ RFQ auction (in §3) | part 1 §3 |
| 6 | dispatch | ✅ assign / track / reallocate | part 1 §4 |
| 7 | auth | ✅ login + JWT issuance | part 2 §5 |
| 8 | security | ✅ per-request authz (filter+OPA) | part 2 §5 |
| 9 | transaction (order) | ✅ checkout + refund | part 2 §6–7 |
| 10 | transaction (ledger) | ✅ double-entry + hash chain | part 2 §8 |
| 11 | event | ✅ transactional outbox relay | part 2 §9 |
| 12 | telemetry | ✅ CQRS + cold-chain | part 2 §10 |
| 13 | enrollment | ✅ 3-gate onboarding + delivery | part 2 §11–12 |
| 14 | reward | ✅ accrual + redemption | part 2 §13 |
| 15 | catalog | ▫ CRUD (get/create listing) | `CatalogServiceImpl` |
| 16 | pricing | ▫ stateless cart calc + promo code | `PricingServiceImpl` |
| 17 | customer | ▫ profile read + GDPR purge | `CustomerProfileServiceImpl` |
| 18 | inventory | ▫ stock read/decrement (used in §6) | `InventoryServiceImpl` |
| 19 | notification | ▫ multi-channel send adapter | `NotificationServiceImpl` |
| 20 | support | ▫ ticket CRUD + bot escalation | `SupportServiceImpl` |
| 21 | feedback | ▫ feedback submit CRUD | `FeedbackServiceImpl` |
| 22 | fleet / geospatial | ▫ shift CRUD / geo nearest-store | `FleetServiceImpl`, `GeospatialServiceImpl` |

**Verdict:** every multi-step / stateful flow now has a source-verified sequence
diagram; the remaining contexts are thin CRUD/port adapters. The design is solid
and ready for the ERD pass.
