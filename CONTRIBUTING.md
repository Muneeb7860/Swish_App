# Contributing to SwissQ Commerce

First off, thank you for considering contributing to SwissQ Commerce! It's people like you that make this tool such a great project.

## Where do I go from here?

If you've noticed a bug or have a feature request, make sure to check our Issues to see if someone else has already created a ticket. If not, go ahead and make one!

## Branching Strategy (MANDATED — do not deviate)

This project uses a fixed, per-machine branching model. There are **exactly four branches** and **no others are ever created**:

```
mac-machine ─────┐
                 ├──> develop ──> master
macbook_machine ─┘
```

- **`master`** — single source of truth for the whole project.
- **`develop`** — the integration branch. It is the **ONLY** branch permitted to merge into `master`.
- **`mac-machine`** — all work done on the primary Mac machine commits here.
- **`macbook_machine`** — all work done on the secondary Macbook machine commits here.

### Rules
1. **Commit only to your machine's branch.** On the primary Mac machine, every agent commits to `mac-machine`. On the secondary Macbook machine, every agent commits to `macbook_machine`. Never cross machine branches.
2. **Machine branches pull from and push to `develop` only.** `develop` is where the two machines converge.
3. **A machine branch must NEVER open a PR directly to `master`.** The only PR that targets `master` is `develop → master`.
4. **Never create new branches.** No `feature/*`, no `agent/feat/*`, no `fix/*`. The four branches above are the complete set.

### PR base rule
| Head branch | PR base |
|-------------|---------|
| `mac-machine` | `develop` |
| `macbook_machine` | `develop` |
| `develop` | `master` |

## Pull Request Guidelines
- Ensure your code adheres to our coding standards.
- Write tests for your changes.
- Update the documentation if necessary.
- Your PR must pass all CI checks before it can be merged.

## Agent Git & Development Strategy
Autonomous Agents working on this repository MUST strictly follow this operational protocol:
1. **Branching Model**: Follow the mandated strategy above. Commit to your machine branch (`mac-machine` here), never create task branches, never PR a machine branch to `master`.
2. **Conventional Commits**: Use granular, atomic commits adhering to conventional commits (e.g. `feat(backend):`, `fix(frontend):`, `chore(docs):`). Use `security:` not `sec:`.
3. **Stage only your own files**: Never `git add -A`. Stage explicit paths and verify with `git diff --cached --name-only` before committing — another agent may have unstaged work in the tree.
4. **Correct JDK**: Backend builds require `JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home` (Homebrew JDK 26 breaks Lombok).
5. **Lightweight Handovers**: Do NOT append massive logs to root handover documents. Update only the "Active Epic" section in `AGENT_HANDOVER.md`.
