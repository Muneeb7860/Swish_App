# ADR-008: Multi-Agent Operating Model & Ownership Boundaries

- **Status:** Accepted 2026-06-14; **amended 2026-06-15** — CI reclassified to Shared, worktree-isolation gap acknowledged (see [Amendment](#amendment-2026-06-15--validity-review--realignment))
- **Date:** 2026-06-14 (amended 2026-06-15)
- **Author:** Agentic OS architect
- **Relates:** [BRANCH_STRATEGY.md](../../BRANCH_STRATEGY.md), [ADR-001 Hexagonal](./adr_001_hexagonal_architecture.md), [ADR-007 Agentic Governance](./adr_007_agentic_governance_layering.md)

## Context

Multiple AI agents work this repository concurrently. The branch mandate is fixed:
**exactly four branches** — `master` ← `develop` ← {`Mac_Machine`, `Windows_Machine`}.
Agents on a machine commit to that machine's branch; machine branches PR into
`develop`; `develop` is the sole branch that PRs into `master`. No other branches
are created.

**Observed incident (2026-06-14).** Two agents on the Mac shared **one working
tree and one git index**. While one agent staged its change, the *other* agent's
unrelated modified files (`RedisCacheConfig.java`, `AuthEventPublisherAdapter.java`,
`TransactionPersistenceAdapter.java`) plus a stray screenshot were already staged
in the shared index and would have been committed under the wrong scope/author.
It was caught only by a manual `git diff --cached` check before committing.
`git worktree list` confirms a single shared worktree, and the repo carries many
` 2`/` 3`-suffixed duplicate files — scar tissue from prior copy-based "isolation".

Root causes:
1. **Shared working tree + index** across same-machine agents — `git add`/`reset`/
   `commit` are global, so one agent's staging captures another's edits.
2. **No domain ownership** — agents edit overlapping files, causing both index
   races and `develop` merge conflicts.
3. **Reliance on per-commit human-style vigilance** as the only safety net.

`BRANCH_STRATEGY.md` additionally documents a `feat/*`-branch workflow that
**conflicts** with the four-branch mandate; this ADR supersedes that section for
AI agents.

## Decision

1. **Index isolation is structural, not behavioural.** Concurrent agents on the
   same machine MUST each operate in their own `git worktree` (separate working
   directory, index, and HEAD). A single working tree is **single-writer**. This
   removes the shared-index class of collision entirely.
   - To preserve the four-branch mandate: a per-agent worktree may hold a
     **short-lived, auto-deleted task branch that only fast-forwards into the
     machine branch** (`Mac_Machine`/`Windows_Machine`) and is never pushed — it
     is scratch, not a persistent fifth branch. Commits onto the machine branch
     itself are serialized (single-writer).

2. **Atomic commit protocol (mandatory).** Every commit is one uninterrupted
   sequence: `git reset` (start from a clean index) → `git add <explicit paths>`
   → **verify `git diff --cached --name-only` matches intent** → commit → push.
   Never `git add -A` / `git add .`; never leave the index dirty between commits.
   Always `SKIP_E2E=true` and `JAVA_HOME` pinned to Java 17.

3. **Ownership map (ratified — by layer, 2026-06-14).** Owners edit their zone
   freely; any cross-zone edit requires a claim (Decision 4).

   | Zone | Paths | Owner |
   | --- | --- | --- |
   | Backend core + AI spine + infra | `backend/`, `homelab-ai-governance/`, `platform-gateway/`, `.githooks/`, `docker-compose*` | **Mac_Machine** |
   | Micro-frontends | `frontend-customer/`, `frontend-rider/`, `frontend-admin/`, `frontend-b2b/`, `frontend-host/` + their Cypress specs | **Windows_Machine** |
   | CI pipeline *(amended 2026-06-15)* | `.github/**` — each machine owns its own jobs (Windows → `frontend-*`; Mac → `backend`/`microservices`/`governance`); cross-cutting parts claim-first | **Shared — per-job** |
   | Architecture & docs | `docs/`, `ROADMAP.md`, root `*.md`, ADRs | **Shared — claim required** |
   | >1 agent on one machine | partition by Java domain package / subproject, per task | per-task claim |

4. **Claim-before-cross-edit.** To touch a path outside your zone, leave a claim
   / hand-off note and confirm no other agent holds it. Shared zones always
   require a claim. This prevents two agents editing the same file on either side
   of a machine boundary (where the conflict surfaces at the `develop` merge).

5. **Promotion is unchanged:** machine branch → PR → `develop` → PR → `master`.

## Consequences

- **+** Shared-index collisions become structurally impossible (worktrees); the
  manual `git diff --cached` check becomes a backstop, not the primary defence.
- **+** Disjoint domain ownership minimizes `develop` merge conflicts.
- **+** Mechanism-first: the protocol holds regardless of the exact ownership
  map, so the map can be retuned without re-litigating the model.
- **−** Setup overhead: a worktree per concurrent same-machine agent; claim notes
  for cross-domain work.
- **−** The ` 2`/` 3` duplicate files should be cleaned up separately — they are
  the failure mode this ADR replaces, and they currently pollute search/builds.

## Ratification

**Accepted 2026-06-14** with the **layer-based** ownership map (Decision 3):
Mac_Machine owns backend + AI spine + gateway + infra; Windows_Machine owns the
micro-frontends; docs are shared/claim-first. The *mechanism* (Decisions 1, 2, 4)
applies immediately and is what prevents a repeat of the 2026-06-14 incident.

## Amendment (2026-06-15) — validity review & realignment

A validity review after one week of operation found two decisions out of step
with practice; this amendment realigns them. The model's spirit holds — the
specifics are corrected.

**Finding 1 — "all CI = Mac" (Decision 3) is impractical and was violated.**
Commits `ed97128` (CI path-sharding), `640968d` (PR-checklist template), and
`3402000` (branch-protection) changed `.github/**` from the Windows side, bundled
with frontend work. The changes were **legitimate and good** (per-path job
sharding so frontend edits skip backend gates; PR standards). The lesson: CI is
**inherently cross-cutting** — Windows must evolve the frontend jobs, Mac the
backend/governance jobs. A single owner bottlenecks both.

**Finding 2 — worktree isolation (Decision 1) was never adopted.** The 2026-06-15
session had repeated **shared-working-tree collisions** (orphaned `git stash pop`
conflicts in `frontend-host`/`handover.md`, stale cross-agent edits, an index race
that nearly committed a peer's files). No `git worktree` was ever created.
Decision 1 is therefore **aspirational, not operative**.

**Realignment:**

1. **CI is now Shared (claim-light).** `.github/workflows/*`, `branch-protection`,
   and `pull_request_template.md` move from Mac-only to **Shared**. Within the
   sharded pipeline each machine owns *its own jobs* (Windows → `frontend-*`
   gates; Mac → `backend`/`microservices`/`governance` gates) and may edit them
   freely; changes to the **cross-cutting parts** (the `changes`/path-filter job,
   branch-protection rules, shared templates) require a claim. `.githooks/`,
   `docker-compose*`, `backend/`, and the AI spine **remain Mac** (the hook runs
   the backend suite + JDK pin).
2. **No mixed-zone commits.** Don't bundle CI/infra changes with frontend feature
   work in one commit — that made the boundary crossing invisible until the
   `develop` merge. One commit, one zone.
3. **Shared-tree survival (until worktrees are real).** Because Decision 1 isn't
   adopted, the **shared working tree is the de-facto mode**, so its defences are
   mandatory, not optional: (i) `git fetch` + fast-forward sync at session start
   **and before every commit**; (ii) the atomic commit protocol (Decision 2);
   (iii) the pre-commit hook's formatter re-stage + Biome error-gate (commits
   `680618e`, `f1844d2`). Worktrees remain the recommended upgrade, but the model
   must not assume an isolation that isn't there.

The ownership table in Decision 3 is updated accordingly: move the `.github/`
entry out of the Mac "infra" row into **Shared — claim required (per-job ownership)**.
