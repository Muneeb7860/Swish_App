# Agent Handover

**Active Epic:** Redis Caching for Product Catalog complete. Next: Per user/system task.

**Active Branch:** `Mac_Machine` (always — never create new branches)

---

## ⚡ MANDATED BRANCHING — NON-NEGOTIABLE, NEVER ASK AGAIN

```
Mac_Machine ─────┐
                 ├──> develop ──> master
Windows_Machine ─┘
```

- **This is the Mac machine → every agent here commits ONLY to `Mac_Machine`.** The Windows machine commits ONLY to `Windows_Machine`. Never cross them.
- **Machine branches pull from / push to `develop` ONLY.** `develop` is where both machines integrate.
- **`develop` is the ONLY branch that may merge into `master`.** A machine branch must NEVER open a PR to `master`.
- **`master` is the single source of truth.**
- **PR base rule:** `Mac_Machine → develop`, `Windows_Machine → develop`, `develop → master`. A machine branch targeting `master` is always wrong.
- **EXACTLY four branches exist:** `master`, `develop`, `Mac_Machine`, `Windows_Machine`. Never create others. Never recreate lowercase `mac-machine`.

---

## ⚡ MANDATORY SESSION START — DO THIS FIRST, NO EXCEPTIONS

```bash
git fetch origin
git stash                          # stash ANY local changes before pulling
git pull origin Mac_Machine        # fast-forward only
git stash pop                      # reapply; resolve conflicts immediately if any
git status --short                 # verify clean before touching files
```

**If stash pop conflicts:** keep BOTH sides (upstream + stash), remove markers, `git add` the file, done. Never discard either side without reading it.

---

## ⚡ MANDATORY COMMIT PROTOCOL

```bash
# Stage ONLY the files YOU touched — never git add -A or git add .
git add path/to/file1 path/to/file2

# Verify staged set before committing
git diff --cached --name-only

# Commit with correct JDK + skip E2E (CI runs E2E; local hook is backend tests only)
JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home \
  SKIP_E2E=true git commit -m "type(scope): description"

git push origin Mac_Machine
```

**Rules:**
- Never stage files you didn't explicitly modify
- Always run `git diff --cached --name-only` and read the list before committing
- Another agent may have unstaged working-tree changes — ignore them, never stage them
- Homebrew JDK 26 breaks Lombok → always set `JAVA_HOME` to `microsoft-17`
- `SKIP_E2E=true` always locally — CI handles E2E

---

## ⚡ KNOWN GOTCHAS (save yourself the pain)

| Symptom | Fix |
|---------|-----|
| `mvn test` hangs with Kafka broker errors | `spring.kafka.listener.auto-startup=false` in `test/application.properties` — already set |
| `ExceptionInInitializerError: TypeTag::UNKNOWN` | Wrong JDK — use `JAVA_HOME=.../microsoft-17.jdk/Contents/Home` |
| Staged 50+ files accidentally | `git reset HEAD` then `git add` only your files |
| Merge conflict in handover/docs | Keep both sides, remove markers, commit — never discard history |
| Pre-commit hook fails on first try | Check the hook output for the real error; don't retry blindly |

---

## Current State (2026-06-14)

| Phase / Feature | Status |
|-----------------|--------|
| Phase 8A — Unified HITL queue | ✅ Done |
| Phase 8B — Admin console frontend | ✅ Done |
| Phase 8C — Temporal signals + Adjust Bid | ✅ Done (`a9155cd`, `fc4061d`) |
| Phase 6 — RAG / pgvector | ✅ Done |
| Phase 7 — Kafka consumers + n8n webhook | ✅ Done (`87d2cb0`) |
| ArchUnit hexagonal enforcement | ✅ Done (`690d81f`) |
| WebSocket Reconnect Guard (max 5) | ✅ Done (`4e025b6`) |
| Redis Catalog Caching | ✅ Done (`527c895`) |
| **Next** | Per task requirements / product roadmap |

---

## Historical Documentation

Deep context in `docs/handovers/archived/` — only read if you hit something genuinely unexplained by the code.
