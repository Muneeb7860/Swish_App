# AI Governance — Canonical Spec & Implementation Plan

**Version:** 1.0 · **Status:** ACTIVE — single source of truth
**Supersedes:** all chat-drafted governance specs (v3.x / v4.0 drafts were never committed and described features that do not exist)

> **Doc-as-code rule:** any PR that changes enforcement behavior (blocking, fail-mode,
> routing constraints) MUST update this file in the same PR. If the code and this
> document disagree, that is a bug — file it.

---

## 0. Product goals (owner-set, non-negotiable)

1. **Security is never compromised.** Guardrails and evals exist for exactly this;
   a request must never receive weaker safety treatment because a component failed
   or because we wanted speed.
2. **Latency & concurrency are never compromised by default.** Guardrails and evals
   are extra compute — invoke the expensive ones **only when needed**.

**Resolution rule (how both hold at once):** every enforcement layer must be either
- **(a) always-on AND ~free** (< 5 ms, no model call, no lock) — these never come off, or
- **(b) risk-triggered** — heavy compute (model calls, retry loops, full detector
  suites) fires only on an explicit risk signal (§3b), never unconditionally.

A layer that is neither free nor risk-triggered does not ship. Fail-closed applies to
the *always-on* gates — which costs zero latency, so the two goals never conflict there.

---

## 1. Principles (the four hats)

| Hat | Rule |
| --- | --- |
| **Architect** | Safety gates fail **closed**. Latency budgets come from measured benchmarks, never aspiration. |
| **SDE** | Small, verifiable diffs. No new subsystem when a config/constant change achieves the same guarantee. |
| **SRE** | Measure first, alert second. An SLO without a measurement source does not go in this doc. |
| **TPO** | Any change that makes a request *fail that previously succeeded* needs explicit product sign-off (§6). |

---

## 2. As-built pipeline (what actually runs)

```
POST /api/v1/govern
  │
  ├─ [G1] Input guardrail gate ..... BLOCKING   pattern/flow matcher (Colang-style,
  │        nemo_guardrails.py                   NO model call) — cost ≈ microseconds
  │
  ├─ [G2] PII scan ................. BLOCKING   regex (pii_patterns.py); on hit →
  │        router/pii_scan.py                   local_only=True (data sovereignty)
  │
  ├─ [R1] Intent classifier ........ BLOCKING   qwen2.5:3b via Ollama, CPU mode
  │        router/classifier.py                 p50 1063ms / p95 1234ms / p99 2396ms *
  │
  ├─ [R2] Decision table ........... BLOCKING   13-rule intent×complexity map +
  │        router/decision_table.py             local_only & daily-budget downgrade
  │
  ├─ [A]  Agent execution .......... BLOCKING   gemma3:4b / mistral / deepseek /
  │        agents/*                             groq cloud / letta (see routing_config)
  │
  ├─ [G3] Output enforcer .......... BLOCKING   detector rules + ≤3 self-correction
  │        guardrails/enforcer.py               retries; PII placeholders re-checked
  │
  └─ [X]  Audit .................... ASYNC-ish  append-only JSONL (data/logs/audit.jsonl)
           audit.py                             DuckDB on demand for analytics
```

\* Measured on this hardware, 63-query dataset — see `benchmarks/results/benchmark_report.md`
(2026-07-02). Re-run `benchmarks/slm_benchmark.py` after any model or hardware change and
update this table.

### Latency budget (measured, not aspirational)

| Stage | Budget (p95) | Source |
| --- | --- | --- |
| G1 pattern gate | < 5 ms | pure string matching |
| G2 PII regex | < 5 ms | pure regex |
| R1 classifier | 1300 ms | benchmark p95 = 1234 ms |
| R2 table | < 1 ms | in-memory dict scan |
| A agent (local) | 2.5–45 s | per-agent `timeout_ms` in routing_config.yaml |
| G3 enforcer | < 50 ms/attempt | regex detectors; ×(1+retries) |
| **End-to-end SLO (local path, excl. agent)** | **≤ 2.5 s** | classifier p99 + overhead |

There is **no** 15–20 ms or 70–80 ms guardrail budget. Any doc claiming a synchronous
SLM stage under 100 ms on this hardware contradicts our own benchmark by ~15×.

---

## 3. Enforcement matrix (current truth, including defects)

| Layer | On violation | On engine error | Verdict |
| --- | --- | --- | --- |
| G1 input gate | Block + audit `pipeline_blocked` | **BLOCK (fail-closed)** + audit `guardrail_engine_error`; unloadable config refuses startup and `/health` → 503 DEGRADED | ✅ fixed in Phase 1 |
| G2 PII scan | Force `local_only` routing | Regex can't "error"; missing pattern = silent miss | ⚠️ coverage-tested in Phase 2 |
| R2 budget/local_only | Downgrade cloud→local, audit | n/a (in-memory) | ✅ correct |
| G3 output enforcer | Retry ≤3, then `blocked_response` | attach warnings, fail toward block | ✅ acceptable |
| A agent execution | fallback → gemma, else honest `failed` status | mock responses are **opt-in** (`GOVERNANCE_ALLOW_MOCK_FALLBACK=1`, tests/CI only) — production never fabricates a governed answer | ✅ gated 2026-07-13 |
| G3b RAIL schema (`guardrails/schemas.py`, Epic 5) | Errors feed the correction prompt only — never the response text; re-checked on **every** loop attempt incl. fallback; persistent failure → `warnings` + `schema_validation.valid=false`, still `status: success` (format gate, not a safety gate) | `"json"` is not a registry key — `is_rail_schema()` guards every call site so the legacy raw-JSON flag never hits this path | ✅ fixed 2026-07-14 (was: one-shot pre-loop check, error text spliced into the response) |

### 3b. Conditional enforcement policy (goal 2 operationalized)

**Risk signals** (any one ⇒ request is *elevated*):
- G2 PII hit (`contains_pii = true`)
- Intent ∈ {`sensitive_query`, `system_admin`}
- Route resolves to a **cloud** agent (data leaves the box)
- G1 near-miss / engine-error path (anything other than a clean pass)

| Layer | Normal request | Elevated request |
| --- | --- | --- |
| G1 pattern gate (free) | always on | always on |
| G2 PII regex (free) | always on | always on |
| G3 detector suite | all **critical/high** rules (injection, PII/secrets, hate speech, catastrophic code); advisory medium/low skipped | **full suite** |
| Self-correction retries | max **1** | max **3** |
| Eval loop (evaluator/loop.py) | skip — unless caller passes `expected_format`, then run at the normal retry cap | run |
| Audit detail | `risk_assessed` + `eval_loop_skipped` events | `risk_assessed` with signal list |

*Implementation: `risk.py` (signals + severity-based rule selection — unknown
severity fails toward enforcement), `concurrency.py` (per-model gates),
wired in `pipeline.py` steps 6b/9/10/11. The `/govern` response now carries
`risk.elevated` + `risk.signals`.*

**Concurrency guards (single Ollama host, 16 GB):**
- One in-flight generation per model — a per-model semaphore, not a global lock, so
  the classifier and an agent model can overlap but two agent calls queue.
- `keep_alive` pinned for the classifier model (it is on the floor of every request);
  agent models load on demand.
- Never trigger two model loads simultaneously — memory pressure on 16 GB stalls
  everything (goal 2 failure mode).
- Retries and eval loops count against the same semaphore — an elevated request may
  be slower, but it must never starve normal traffic.

---

## 4. Explicit non-features (do NOT reference these as if they exist)

- **Presidio** — PII is homegrown regex, not Presidio.
- **GUARD_LEVEL flag / RiskRouter / LOW-MED-HIGH risk tiers** — routing is intent ×
  complexity; there is no risk-tier concept in code.
- **503 shedding during guardrail incidents** — deferred design (§5, Phase 4).
- **Async advisory guardrail pipeline** — not built; no consumer currently needs
  sub-second governed responses (homelab profile).
- **WORM object storage** — audit is append-only local JSONL. Object-lock WORM arrives
  only with the Swish backend `onprem` MinIO profile.
- **Multi-window burn-rate alerts** — Prometheus rules are single-window thresholds.

---

## 5. Implementation plan (simplified, phased, each phase shippable alone)

### Phase 1 — Fail-closed input gate ✅ *DONE (2026-07-13)*
- `check_nemo_guardrails()` exception path → `{"allowed": False, "response": <safe msg>,
  "triggered_rule": "guardrail_engine_error"}` + audit event `guardrail_engine_error`.
- Loader (`config.yml` / `flows.co` parse) failures raise at startup — a governance
  service with unloadable guardrails must not serve traffic (`/health` reports it).
- Tests: corrupt config dir → requests blocked; healthy path unchanged.
- **Acceptance:** kill the flows file on a running stack → `/api/v1/govern` blocks,
  audit log shows `guardrail_engine_error`, `/health` degrades.

### Phase 2 — PII test hardening ✅ *DONE (2026-07-13)*
- Parametrized matrix: one positive + one negative case for **every** pattern in
  `pii_patterns.py` (email, credit card, phone, + all others present).
- Obfuscation cases (spaces/dashes in card numbers, `(at)` emails).
- **Acceptance:** deleting any single pattern from `pii_patterns.py` fails CI.

### Phase 3 — Latency truth & SLO wiring ✅ *DONE (2026-07-13)*
- §2 table is canonical; `/api/v1/stats` already exposes classifier latency — add p95
  gauge for end-to-end `/govern` to the existing metrics recorder.
- Prometheus: single-window alert `GovernanceLatencyHigh: p95 > 2.5s for 5m`. That's it.
- **Acceptance:** Grafana (localhost:3300) shows the p95 panel.

### Phase 3b — Conditional enforcement + concurrency guards ✅ *DONE (2026-07-13)*
- Implement §3b: elevated-vs-normal detector sets, retry caps (1 vs 3), eval loop
  only on elevated requests.
- Per-model asyncio semaphores around Ollama calls; `keep_alive` for the classifier.
- Tests: a normal request never invokes the eval loop; an elevated request always
  runs the full suite (goal 1 check); two concurrent normal requests don't serialize
  behind each other's enforcement (goal 2 check).
- **Acceptance:** p95 for normal requests unchanged vs Phase 3 baseline while an
  elevated request is in flight.

### Phase 4 — Risk tiers & shed-vs-downgrade ⚪ *deferred; requires §6 sign-off*
- Only if a real consumer needs it. Design sketch: map intents → risk tier in
  `routing_config.yaml`; on guardrail degradation, HIGH-tier intents get fast-fail
  (503-equivalent) instead of downgraded checks.
- **Blocked on TPO answer:** "during a guardrail incident, is blocking sensitive
  actions entirely acceptable, or must they degrade-but-serve?"

### Phase 5 — Optional hardening ⚪ *deferred*
- Multi-window burn-rate alerts (Prometheus-only).
- WORM audit sink (rides the backend `onprem` MinIO work — do not build separately).
- Async advisory guardrail split — revisit only if Phase 3 SLO is breached or a
  <500 ms consumer appears. Until then it is scope creep.

---

## 6. Change control (TPO)

Sign-off required before merging anything that: blocks a request class that previously
succeeded (Phase 1 qualifies — approved by owner on adoption of this doc), changes
`local_only` semantics, raises any latency SLO, or sends data to a new cloud endpoint.
Sign-off = a line in the PR description naming the approver and this doc's section.

## 7. Verification quick-reference

```bash
uv run pytest                                   # unit + contract tests
bash scripts/smoke_governance.sh                # end-to-end guardrail smoke
python benchmarks/slm_benchmark.py              # refresh §2 latency table
```

**Required local models** (agents fail honestly when missing — no mocks in prod):

```bash
ollama pull qwen2.5:3b gemma3:4b mistral:latest deepseek-coder:latest qwen2.5:7b
```

The classifier model (`qwen2.5:3b`) is pre-warmed and pinned at server startup;
the others load on first routed request. Verified live 2026-07-13: guardrail
blocks < 0.5 s, `/metrics` histogram scraped by Prometheus (job `governance`),
`GovernanceLatencyHigh` loaded, Grafana panel on `:3300`, live p95 ≈ 0.47 s.
