# R4 — Service Extraction Blueprint

The executable plan behind [ADR-005](./adr/adr_005_service_extraction_strangler_fig.md)
and [ADR-006](./adr/adr_006_secrets_vault_and_mtls.md). R4's *decision, order,
seams, readiness baseline, and runbook* are delivered here; the **physical split
is gated** on a concrete driver (scale / team / fault-isolation) and on
infrastructure that isn't runnable in the current single-node dev environment.

## 1. Extraction-readiness baseline (measured 2026-06-11)

| Context | Couples OUT | Coupled IN | Seam to manage | Order |
| :--- | :--- | :--- | :--- | :-: |
| **payment** | `transaction`/ledger (6 imports) | none | payment → ledger posting | **1** |
| **telemetry** | ledger (write-off) | rider/enrollment | telemetry → ledger; device ingest | 2 |
| **notification** | none (consumes events) | many (events) | already a module | 3 |

Inbound coupling is the extraction blocker; payment has zero. Notification is
already a separate Maven module (`notification-engine`) consuming events.

## 2. Per-extraction runbook (strangler fig)

For each context, in order:

1. **Freeze the seam.** Confirm the outbound dependency is expressible as an
   event or REST contract (payment→ledger ⇒ `payment.captured` event the ledger
   service consumes). Add a fitness check that no new inbound coupling appears.
2. **Stand up the service + its own DB.** New deployable (reuse a thin module
   skeleton); migrate the context's tables to the service schema.
3. **Dual-run.** The monolith keeps serving; the new service consumes the same
   Kafka topics (idempotent via existing `idempotency_key`s). Shadow-compare.
4. **Cut over by version.** Route `Accept-Version: v2` to the new service; v1
   stays on the monolith. Ramp traffic.
5. **Verify** with the existing fitness functions (coverage, security, E2E) +
   distributed traces (`X-Correlation-ID`).
6. **Decommission** the monolith's copy of the context once v1 traffic is zero.
7. **Rollback:** route v2 back to v1; the monolith copy is the safety net until
   step 6.

## 3. Fitness functions (protect readiness now, pre-extraction)

- **payment inbound coupling = 0** — no context may `import …domain.payment.*`.
  Enforce in review today; add an ArchUnit test when extraction begins.
- **No cross-schema SQL** — each context touches only its own schema (the
  validated ERD already aligns entities to schemas).
- **All cross-context writes go through ports** (hexagonal) — so extraction is an
  adapter swap (in-process → HTTP/Kafka), not a rewrite.

## 4. Secrets & mTLS rollout (ADR-006), triggered by extraction #1

1. Stand up **Vault**; migrate `JWT_SECRET` etc. from GCP SM (`sm://`) to
   `vault://` (same Spring config-import pattern).
2. **Vault PKI** issues short-lived service certs; enable **mTLS** east-west via
   a mesh (Linkerd/Istio) or Spring Cloud.
3. Gateway keeps north-south TLS + JWT forwarding; **OPA** stays the PDP.

## 5. Status

| Item | State |
| :--- | :-- |
| Strategy, order, seams (ADR-005) | ✅ decided |
| Secrets/mTLS path (ADR-006) | ✅ decided |
| Readiness baseline + fitness functions | ✅ measured/documented |
| **Physical extraction (live services, Vault, mesh)** | ⏸️ **gated** on a real driver + infra; not runnable in single-node dev |

**Bottom line:** R4 is delivered as *governed, executable architecture* with a
measured readiness baseline — the responsible state for a solo/small team that
should not pay microservices/Vault/mesh overhead until scale demands it (per the
[framework evaluation](./ARCHITECTURE_FRAMEWORK_EVALUATION.md)). When the driver
fires, this runbook executes context-by-context.
