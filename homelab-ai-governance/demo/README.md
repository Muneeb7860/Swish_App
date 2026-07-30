# SwishOS Governance Demo — Non-Technical Guide

> **One command runs the whole thing. No coding required.**
>
> ⏱ **Total runtime: 10–12 minutes.** Most of that is Phase D, which sends 128
> attack payloads one at a time. It will look idle for several minutes — that is
> normal, do not cancel it.

---

## What This Demo Shows

| Phase | What Happens | Runtime |
|-------|-------------|---------|
| **A** | Boots the governance engine locally (loopback only, no GPU needed) | ~5s |
| **B** 🛑 | Three privileged actions ($150K procurement, offshore wire) get **intercepted**; one legitimate query passes through | ~5s |
| **C** 🧬 | An adaptive attacker takes a blocked payload and **rewrites it** to look for gaps | ~5s |
| **D** 📊 | **128 attack payloads** across 11 threat categories, graded | **8–10 min** |
| **E** 🔐 | 5 crypto probes (forged signature, replay, clock skew) against the deployed edge route | ~10s |
| **G** 🔎 | **14 verified-open gaps** probed and reported honestly — what we do *not* catch | ~60s |
| **H** | Stops the server, leaves no dangling process | instant |

---

## How to Run It

**Step 1 — Open Terminal.** On Mac: `Cmd + Space`, type "Terminal", Enter.

**Step 2 — Go to the project:**

```bash
cd ~/Documents/GitHub/Swish_App/homelab-ai-governance
```

**Step 3 — Run:**

```bash
bash demo/run_demo.sh
```

### Faster variant (skips the 8-minute sweep)

If you just want the interesting parts for a live demo:

```bash
DEMO_SKIP_PHASE_D=1 bash demo/run_demo.sh
```

### Fully offline variant

Phase E needs internet (it hits the deployed route). To skip it:

```bash
DEMO_SKIP_PHASE_E=1 bash demo/run_demo.sh
```

### Skip the gap scan

```bash
DEMO_SKIP_PHASE_G=1 bash demo/run_demo.sh
```

Any combination of `DEMO_SKIP_PHASE_D`, `_E`, `_G` works. The closing summary
marks skipped phases with ⏭ rather than claiming they ran.

---

## Reading the Output

| Symbol | Meaning |
|--------|---------|
| `✅ PASS` | The system handled the test correctly — attack blocked, or safe query allowed |
| `❌ FAIL` | The system missed something. A real finding worth fixing. |
| `⚠️` | Phase C only: the adaptive attacker found a gap. **This is the tool doing its job**, not a crash. |

At the end you get a one-line verdict: `Scripted checks: all passed` or a count of failures.

---

## Two Things to Understand Before You Demo This

**1. Phase B is content interception, not durable HITL.**

The engine intercepts privileged actions (unauthorized spend, wire transfers, admin
escalation) at the content layer. It does **not** currently pause a workflow,
issue an approval token, and resume on a human decision — that's Phase 8C in the
Swish_App backlog. Don't claim a human-approval loop you can't show.

**2. Phase E runs against a different target than the rest.**

Signature enforcement (agent identity, replay protection, clock skew) lives in the
deployed SwishOS edge route, not in the local content-guardrail engine. So Phase E
points at the production endpoint. If you run the crypto probes against the local
server instead, you'll see 1/5 — that's expected, not a regression. Different
component, different job.

---

## Expected Results (verified run, 2026-07-28)

```
Phase B  — 3/3 intercepted correctly (2 blocked, 1 safe query allowed)
Phase C  — 3/3 blocked (direct, variable-splitting, fictional-framing)
Phase D  — 128/128 content payloads passed across 11 categories
Phase E  — 5/5 crypto probes passed against the edge route
Phase G  — 3 blocked, 7 guardrail misses, 4 need review, 0 confirmed disclosures
```

**Phase G is the most useful part of this demo.** Phases B–E are all green, which
on its own proves very little — a suite that only contains attacks you already
defend against will always pass. Phase G probes 14 techniques that are verified
*open* against this engine, and reports honestly that 11 of them still get past
the guardrail layer.

Note on Phase C: the variable-splitting mutation used to bypass. It was found by
the gap scan, then closed by adding payload reconstruction to the detector — along
with homoglyph, control-character and leetspeak normalization. Three of the
original 14 gaps are now `BLOCKED`. See `known_gaps/README.md` for what remains
and why the semantic-framing group is not solvable with pattern matching.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Looks frozen during Phase D | It isn't. 128 sequential HTTP requests take 8–10 min. |
| "governance module not importable" | `python3 -m venv .venv && .venv/bin/pip install -r requirements.txt` |
| "agentic_redteam not importable" | `.venv/bin/pip install -e ../../agentic-redteam` (Python 3.14 needs the source dir on `PYTHONPATH`; the script handles this) |
| "port 8000 already in use" | `lsof -ti :8000 \| xargs kill` or `GOVERNANCE_PORT=8001 bash demo/run_demo.sh` |
| Phase E shows 0/5 or times out | No internet, or you hit the 10 req/min rate limit. Wait 60s. |
| "Connection refused" mentioning Ollama | Normal. Mock fallback mode is on; no local model needed. |
| Need to debug a boot failure | Check `demo/.server.log` |

---

## Files Written

| File | Purpose | Committed? |
|------|---------|-----------|
| `demo/redteam_results.json` | Machine-readable results, includes OWASP composite score | No (gitignored) |
| `demo/.server.log` | Server stdout/stderr for debugging | No (gitignored) |

---

## Test Your Own AI Agent

```bash
pip install agentic-redteam
agentic-redteam --target-url https://YOUR-ENDPOINT/api --ci
```

Works against any HTTP endpoint accepting `{"query": "..."}`. None of the
SwishOS-specific environment variables are needed for this — those only apply
when testing our own reference server.
