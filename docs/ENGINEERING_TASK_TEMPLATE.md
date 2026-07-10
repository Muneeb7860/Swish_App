# ENGINEERING_TASK_TEMPLATE.md
## Swish App — Engineering Task & QA Standard

> **Version**: 2.0 | **Maintainer**: Technical Lead  
> **Scope**: All features merged into `develop`, `main`, and `macbook_machine`

---

## 1. Task Overview

| Field | Value |
|---|---|
| **Task / Feature Name** | _[e.g., ESG Bag Return Toggle, VIP Membership Hub, HITL Console]_ |
| **Assigned Engineer** | _[Name]_ |
| **Sprint / Phase** | _[Sprint X / Phase Y]_ |
| **Entry Point Name** | _[e.g., "My Profile Hub" button, "Checkout Wallet" button]_ |
| **Interface Name** | _[e.g., Profile Hub, HITL Queue Console, Cart Drawer]_ |

---

## 2. Architectural Placement

> **Architect Mandate**: Declare where this feature lives. Spaghetti code is not acceptable.

- [ ] **State Location**: Where does the state live?
  - `[ ]` Client-side Zustand store (`frontend-host/src/store.ts`)
  - `[ ]` Server-side Spring Boot domain entity (persisted in PostgreSQL)
  - `[ ]` Temporal Workflow (long-running durable state)
  - `[ ]` Kafka Event (transient message)
  - `[ ]` Redis Cache (ephemeral)
- [ ] **Existing Service Reuse**: List the existing component/service this reuses:
  - e.g., _"Uses existing `GovernanceServiceImpl`, `CustomerSupportAgent`, `LettaMemoryService`"_
- [ ] **MFE Boundary**: Which Micro-Frontend owns this UI?
  - e.g., _"`frontend-customer` CustomerApp.tsx, exposed via Module Federation"_

---

## 3. Implementation Checklist

- [ ] Feature implemented according to design (LLD/HLD alignment)
- [ ] Unit tests written and passing (`mvn clean test` — 100% pass rate)
- [ ] Integration tests written (if applicable)
- [ ] TypeScript/Java types are correct and no `any` escapes added without justification
- [ ] Biome/ESLint/Spotless linting passes
- [ ] No merge conflicts with `develop`

---

## 4. Performance & Observability

> Verifying function is not enough — verify *health*.

- [ ] **Latency**: Does this implementation introduce latency or slow down page loads?
  - Run Lighthouse score before and after. Target: LCP < 2.5s, no regressions.
- [ ] **Telemetry**: Is there appropriate logging or telemetry to monitor this feature in production?
  - Check: Does a meaningful Kafka log event fire? (via `logKafka(source, event, meta)`)
  - Check: Is an OpenTelemetry span emitted for any backend call?
  - Check: Does this feature increment any Prometheus metric counter (if applicable)?
- [ ] **State Metrics**: If this affects shared state, are there metrics to track success rates?
  - e.g., Cart checkout success rate, ESG toggle engagement rate
- [ ] **Error Observability**: Are errors surfaced via toast notifications AND logged to Kafka?

---

## 5. Security & Privacy

> As an architect, ensure the feature does not inadvertently expose sensitive user data.

- [ ] **PII Handling**: Does this interact with sensitive data (PII, payment info)?
  - If YES: Is the data encrypted at rest (DB) and in transit (TLS)?
  - If YES: Is PII masked/redacted in logs and Kafka events?
- [ ] **Storage Security**: Is the storage mechanism secure?
  - `[ ]` JWT stored in `localStorage` (acceptable for this MVP — note risk in production)
  - `[ ]` Auth tokens never logged in plain text
  - `[ ]` No sensitive data in URL query params
- [ ] **CSRF Protection**: Is the entry point susceptible to CSRF?
  - Spring Boot: Verify CSRF token middleware is not accidentally disabled for this route.
- [ ] **XSS Prevention**: Are user inputs sanitized before rendering?
  - React: Avoid `dangerouslySetInnerHTML`. All user content must be escaped.
- [ ] **RBAC**: Is the endpoint protected by `@PreAuthorize("hasRole('...')")`?
  - Verified by `SecurityHardeningIntegrationTest.java`.

---

## 6. Browser Test Plan (Mandatory End-to-End Verification)

> **Unit and integration tests are NOT sufficient.**  
> You must verify the application through the browser like a real user.  
> Use `npx playwright test` from the `tests/` directory.

### Test 1 — New Feature Interaction

| Step | Action | Expected |
|---|---|---|
| 1 | Open a clean browser session → `http://localhost:3000` | Login portal appears |
| 2 | Select role, enter password, click "Verify Credentials" | App header visible, MFA portal disappears |
| 3 | [Trigger condition for feature — e.g., click ESG toggle] | Toggle changes state |
| 4 | [Interact with feature] | Cart/Invoice updates to reflect change |
| 5 | Verify application state in UI | State correctly stored (no stale values) |
| ✓ | **No UI/console errors** | Zero critical console errors |

### Test 2 — Entry Point Validation

| Step | Action | Expected |
|---|---|---|
| 1 | Log in with existing data loaded | Dashboard visible |
| 2 | Find "**[Entry Point Name]**" button | Button visible and enabled |
| 3 | Click the button | "**[Interface Name]**" opens successfully |
| 4 | Change a setting (e.g., apply voucher, toggle option) | Settings reflected in UI immediately |
| 5 | Verify updated state | Updated settings are saved |
| ✓ | **Interface opens, settings saved** | No errors during interaction |

### Test 3 — Persistence Validation

| Step | Action | Expected |
|---|---|---|
| 1 | With state set, click other role tabs | In-session state survives tab navigation |
| 2 | Click `#tab-rider`, `#tab-admin`, `#tab-customer` | App header visible throughout |
| 3 | Refresh the browser (`page.reload()`) | MFA portal reappears cleanly |
| 4 | Re-login as same role | Dashboard loads without errors |
| 5 | Reopen "**[Interface Name]**" | Entry point continues working |
| ✓ | **Profile Hub accessible post-reload** | Vouchers/settings visible |

---

## 7. Automated Test Execution

> **Run from the `tests/` directory.**

```bash
# Run all Playwright E2E tests (headless)
npm test

# Run in headed mode (watch browser)
npm run test:headed

# Run with trace recording (debug)
npm run test:trace

# View HTML report after run
npm run test:report
```

**Test files:**
- `tests/e2e/test1-esg-feature.spec.ts` — Feature interaction (ESG toggle)
- `tests/e2e/test2-profile-hub.spec.ts` — Entry point validation (My Profile Hub)
- `tests/e2e/test3-persistence.spec.ts` — Persistence validation (tab nav + refresh)
- `tests/e2e/test4-10-additional.spec.ts` — Additional flows (checkout, RBAC, bot, etc.)

**Results output:**
- `tests/test-results.json` — Machine-readable JSON for CI
- `tests/playwright-report/` — HTML report with screenshots/traces

---

## 8. Definition of Done ✅

A task is **only** considered complete when ALL of the following are true:

- [ ] All existing tests pass (`mvn clean test` — 0 failures)
- [ ] All Playwright E2E tests pass (`npm test` in `tests/` — 0 failures)
- [ ] **The agent must attach `test-results.json` or `summary.md` generated by the automation run to the PR.** This ensures a permanent audit record.
- [ ] No new Biome/ESLint/Spotless violations introduced
- [ ] OWASP dependency check passes (no CVE ≥ 7)
- [ ] Kafka event logs for the feature appear in the SystemEngineRoom panel
- [ ] Architecture Alignment checklist above is completed

---

## 9. Flakiness Policy

> As a Lead, flakiness is a blocker, not an excuse.

- **Rule**: If a Playwright test fails, rerun it **once** (`npm test -- --retries=1`).
- **If it fails again**: Log it immediately as a **Bug/Defect** issue in the repository with:
  - Test name and file
  - Screenshot/video from `playwright-report/`
  - Trace file from `test-results/`
  - Reproduction steps
- **Do NOT** commit code with known flaky tests without a filed issue.

---

## 10. Test Sheet (Manual Observation Record)

> Fill this out during manual/automation runs. Attach to PR.

| Test # | Description | Status | Errors Found | Notes |
|---|---|---|---|---|
| T1-A | ESG toggle updates cart invoice | ⬜ PASS / ❌ FAIL | | |
| T1-B | ESG state persists with multiple cart items | ⬜ PASS / ❌ FAIL | | |
| T2-A | Profile Hub opens and shows vouchers | ⬜ PASS / ❌ FAIL | | |
| T2-B | Profile Hub — saved addresses section renders | ⬜ PASS / ❌ FAIL | | |
| T2-C | Navigate away and back preserves profile state | ⬜ PASS / ❌ FAIL | | |
| T3-A | Cart + ESG persists during tab navigation | ⬜ PASS / ❌ FAIL | | |
| T3-B | After refresh → portal reappears → re-login works | ⬜ PASS / ❌ FAIL | | |
| T3-C | Environment profile switch doesn't break app | ⬜ PASS / ❌ FAIL | | |
| T4 | E2E checkout + SLA countdown banner | ⬜ PASS / ❌ FAIL | | |
| T5 | Admin Panel / HITL queue access | ⬜ PASS / ❌ FAIL | | |
| T6 | Low-stock substitution modal triggers + Swap works | ⬜ PASS / ❌ FAIL | | |
| T7 | RBAC blocker shown for wrong-role tab | ⬜ PASS / ❌ FAIL | | |
| T8 | Env profile switching (dev → staging → prod) | ⬜ PASS / ❌ FAIL | | |
| T9-A | Empty password validation | ⬜ PASS / ❌ FAIL | | |
| T9-B | Valid credentials login | ⬜ PASS / ❌ FAIL | | |
| T10 | Support bot opens and accepts message | ⬜ PASS / ❌ FAIL | | |

---

*Generated by Swish App Technical Lead — v2.0*  
*Follows architectural standards from ADR-007, handover.md, and ROADMAP.md*
