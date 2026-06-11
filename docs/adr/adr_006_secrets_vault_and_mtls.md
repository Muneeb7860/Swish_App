# ADR-006: Secrets Management (Vault) & Inter-Service mTLS

- **Status:** Accepted (R4 architecture decision; adoption gated on ≥2 services)
- **Date:** 2026-06-11
- **Relates:** [ADR-005 Service Extraction](./adr_005_service_extraction_strangler_fig.md)

## Context

The HLD target names **HashiCorp Vault** (secrets, mTLS PKI) and **mutual TLS**
for all inter-service communication. As-built:

- Secrets: **GCP Secret Manager** in prod (`JWT_SECRET` via `sm://`, prod-only,
  see the secret-manager scoping), env vars in dev.
- AuthN/Z: **JWT + OPA** for *user* requests (works well).
- Inter-service mTLS: **not present** — there is effectively one deployable.

Vault and mTLS solve *service-to-service* identity and centralized secret
rotation. With a single deployable, they would be pure overhead.

## Decision

1. **Keep GCP Secret Manager + env** while the system is a monolith. Do **not**
   introduce Vault for a single service — it adds operational burden with no
   payoff. (Right-sizing per [DA / the framework evaluation](../ARCHITECTURE_FRAMEWORK_EVALUATION.md).)
2. **Adopt Vault when ≥2 services exist** (i.e. when ADR-005's first extraction
   lands). Migration path:
   - **Vault KV** replaces/augments GCP SM for application secrets; the existing
     `spring.config.import=sm://` pattern maps cleanly to a `vault://` import.
   - **Vault Agent sidecar** injects secrets; no secrets in images or env.
   - **Vault PKI** issues **short-lived service certificates** for mTLS.
3. **Inter-service mTLS** is service-to-service only and is introduced **with**
   the first extraction — via a service mesh (e.g. Linkerd/Istio) or Spring Cloud
   + Vault-issued certs. User-facing auth stays **JWT + OPA** (unchanged).
4. **Trust boundaries:** mesh mTLS for east-west traffic; the gateway terminates
   north-south TLS and forwards JWTs; OPA remains the policy decision point.

## Consequences

- **+** Centralized rotation, per-service identity, encrypted east-west traffic
  once services multiply.
- **+** Clean migration: the config-import + conditional-bean patterns already in
  use (GCP SM, Mongo archive) generalise to `vault://` and mesh injection.
- **−** Vault + mesh are real operational systems; adopting them prematurely
  would violate the right-sizing principle. **Gated on ADR-005 extraction.**
- **Execution deferred:** like ADR-005, the live Vault/mesh stand-up is a
  driver-triggered infra step, not runnable/verifiable in the current
  single-node environment. This ADR fixes the **decision and migration path**.
