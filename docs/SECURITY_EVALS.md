# Swish OS — Security Evaluation & OWASP Top-10 Traceability

How security is evaluated: the control in code, the test/tool that checks it,
where it runs, and its status per OWASP Top-10 (2021). Companion to
[AI_EVALS.md](AI_EVALS.md) — the two overlap on **PII / guardrails** (the
AI-safety ∩ security intersection), cross-linked below.

Legend: ✅ covered · 🟡 partial · ❌ gap

---

## 1. OWASP Top-10 (2021) traceability matrix

| # | Category | Control(s) in code | Evaluated by | Where | Status |
|---|---|---|---|---|---|
| **A01** | Broken Access Control | `@PreAuthorize("hasRole('ADMIN')")` on HITL/Admin; JWT role claim; IDOR ownership checks | `Customer/Order/PaymentControllerSecurityTest` (explicit IDOR guards), `SecurityHardeningIntegrationTest`, `OrderControllerSecurityTest` | backend test suite | ✅ |
| **A02** | Cryptographic Failures | BCrypt cost-12 passwords; SHA-256 `hashed_email`; JWT HS256; TLS at edge | `AuthControllerSecurityTest`; TruffleHog (leaked keys) | tests + `secret-scan.yml` | 🟡 |
| **A03** | Injection | JPA parameterized queries; Bean Validation on DTOs | *no dedicated injection suite / no SAST* | — | ❌ |
| **A04** | Insecure Design | Budget guardrail (ADR-007), HITL for high-impact, fail-closed OPA fallback | `security_architecture_audit_report.md` (manual) | doc | 🟡 |
| **A05** | Security Misconfiguration | Trivy config scan; staging/prod profile separation | Trivy FS scan | `ci.yml` | 🟡 |
| **A06** | Vulnerable / Outdated Components | dependency pinning | **OWASP Dependency-Check** (fail on CVSS ≥7) + **Trivy** | `ci.yml` | 🟡 (dep-check skips without `NVD_API_KEY`) |
| **A07** | Identification / Auth Failures | JWT auth filter, role gating, MFA (built, gated off), account lockout status | `AuthControllerSecurityTest`, JWT role-filter tests | backend tests | 🟡 |
| **A08** | Software / Data Integrity | Ledger SHA-256 hash chaining (immutable journal); CDC outbox | `LedgerServiceTest`, `OutboxEncryptionTest` | backend tests | 🟡 |
| **A09** | Logging / Monitoring Failures | OpenTelemetry tracing, Prometheus metrics, `security_anomaly` metric | `SecurityAnomalyIntegrationTest` | backend + observability | 🟡 |
| **A10** | SSRF | outbound calls constrained to configured hosts (Ollama/Groq/Letta) | *no explicit SSRF test* | — | ❌ |

---

## 2. Automated security gates (CI)

| Gate | Tool | Covers | Fail condition | Caveat |
|---|---|---|---|---|
| OWASP Dependency Security Check | `org.owasp:dependency-check-maven` | A06 CVEs | CVSS ≥ 7 | **Skipped when `NVD_API_KEY` unset** |
| FileSystem Vulnerability Audit | Trivy (`aquasecurity/trivy-action`) | A06, A05, secrets | configured severities | — |
| Audit Git History for Secrets | TruffleHog | A02/A07 leaked secrets | verified secret found | needs full history |
| Enforce Standards Gates | conventional commits / PR gate | process | non-conforming PR | **only required check** |

Run locally:
```bash
cd backend && export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
NVD_API_KEY=<key> mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
trivy fs .
./mvnw test -Dtest='*SecurityTest,SecurityHardeningIntegrationTest,SecurityAnomalyIntegrationTest'
```
On-demand review of pending changes: the **`/security-review`** skill.

---

## 3. PII filter — how it actually works

Two-stage, regex-based, with a single shared pattern source. This is the
AI-safety ∩ security control (also referenced in AI_EVALS.md guardrails).

**Pattern source of truth:** `governance/guardrails/pii_patterns.py` — 7 compiled
regexes (most-specific first): `CONNECTION_STRING`, `API_KEY`, `CREDIT_CARD`,
`SSN`, `EMAIL`, `PHONE_NUMBER`, `IP_ADDRESS`. Exposes `scan_pii()`,
`contains_pii()`, `redact_pii()` → replaces with `[REDACTED:<TYPE>]`.

**Stage 1 — pre-route scan (data sovereignty)** — `router/pii_scan.py`:
```
pre_route_pii_scan(text) → if PII detected: local_only = True
```
When PII is present, the query is **forced to a local Ollama agent and never
sent to a cloud API** (Groq/etc.). This is the primary control — PII physically
cannot leave the box to a third-party LLM.

**Stage 2 — guardrail enforcement (redaction)** — `guardrails/detectors.py` +
`enforcer.py`, orchestrated by `pipeline.py`. The unified pipeline:
1. Scans for PII → enforces local routing (stage 1)
6. Applies **input** guardrails (redact / block / strip / warn)
8. Applies **output** guardrails
`redact_matches()` rewrites matches to `[REDACTED:<TYPE>]` on both input and
output, so PII never reaches the model prompt *or* leaks in the response.

**Stage 3 — persistence / GDPR (Java side)** — `model/Customer.java`:
- `hashed_email` (SHA-256, 64-char, unique) — raw email is not stored in the clear for lookup keys.
- `is_anonymized` flag → GDPR right-to-deletion workflow (BRD NFR-07).

**Evaluated by:** `tests/test_router/test_pii_scan.py` (detection + local-only
enforcement) and `tests/test_guardrails/test_detectors.py` (redaction). Part of
the 74-test governance suite (`AI Governance Quality Gate` in CI).

**Known limits:** regex-based → catches structured PII (cards, SSN, email,
keys) but not free-form names/addresses; no ML NER. Non-US phone/ID formats are
partial. Worth documenting as accepted scope.

---

## 4. Security eval scenarios (test matrix)

| ID | OWASP | Scenario | Expected |
|---|---|---|---|
| SE-01 | A01 | customer reads/refunds another customer's order/payment | 403 (IDOR guard) |
| SE-02 | A01 | non-admin hits `/api/admin/**` or HITL approve | 403 |
| SE-03 | A01 | unauthenticated hits protected `/api/**` | 401 |
| SE-04 | A02 | password stored | BCrypt cost-12, never plaintext |
| SE-05 | A02/A07 | secret committed to a branch | TruffleHog blocks |
| SE-06 | A03 | injection payload in order/search fields | parameterized → no injection *(needs test)* |
| SE-07 | A06 | dependency with CVSS ≥7 | dep-check fails build *(only with NVD key)* |
| SE-08 | A07 | expired / tampered JWT | rejected |
| SE-09 | A08 | ledger entry tampered | hash-chain mismatch detected |
| SE-10 | A09 | anomalous access pattern | `security_anomaly` metric fires |
| SE-11 | PII | prompt contains email/card/SSN | routed local-only + redacted in/out |
| SE-12 | PII | cloud routing attempted with PII present | blocked — stays local |

---

## 5. Gaps & prioritized recommendations (architect view)

1. **A03 Injection — add SAST.** No dedicated injection tests or static
   analysis. Add **CodeQL** (free for the repo) or **Semgrep OSS** (free,
   consistent with the free/local policy) as a CI gate; add a few injection
   test cases (SE-06).
2. **A06 — make dep-check non-optional.** It silently skips without
   `NVD_API_KEY`, so CVE coverage isn't guaranteed. Provision the key (free
   from NVD) or add an offline mirror so it always runs.
3. **A10 SSRF & A04/A05/A08** — currently doc/tool-implicit; add explicit
   assertions (SSRF egress allow-list test; misconfig checks; ledger-tamper test SE-09).
4. **PII coverage** — accept regex scope explicitly, or add local NER (spaCy)
   for names/addresses if data-residency risk warrants it (stays local per policy).
5. **Keep this matrix living** — update the status column whenever a control or
   test changes; treat 🟡/❌ rows as the security backlog. Pair with
   `AI_EVALS.md` so agent-safety and app-security are reviewed together.
