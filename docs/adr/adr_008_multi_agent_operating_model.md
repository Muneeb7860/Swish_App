# ADR-008: Multi-Agent Operating Model & Ownership Boundaries

- **Status:** Proposed (awaiting ratification)
- **Date:** 2026-06-14
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

3. **Ownership map (proposed — ratify/adjust).** Owners edit their zone freely;
   any cross-zone edit requires a claim (Decision 4).

   | Zone | Paths | Owner |
   | --- | --- | --- |
   | Backend core + AI spine + infra | `backend/`, `homelab-ai-governance/`, `platform-gateway/`, `.github/`, `.githooks/`, `docker-compose*` | **Mac_Machine** |
   | Micro-frontends | `frontend-customer/`, `frontend-rider/`, `frontend-admin/`, `frontend-b2b/`, `frontend-host/` + their Cypress specs | **Windows_Machine** |
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

Flip **Status → Accepted** once the ownership map (Decision 3) is confirmed or
adjusted. Until then the *mechanism* (Decisions 1, 2, 4) should be adopted
immediately — it is what prevents a repeat of the 2026-06-14 incident.
