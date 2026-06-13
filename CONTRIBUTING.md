# Contributing to SwissQ Commerce

First off, thank you for considering contributing to SwissQ Commerce! It's people like you that make this tool such a great project.

## Where do I go from here?

If you've noticed a bug or have a feature request, make sure to check our Issues to see if someone else has already created a ticket. If not, go ahead and make one!

## Branching Strategy
We use a standard branching strategy. 
1. Create a `feature/<feature-name>` branch off `develop`.
2. Commit your changes and open a Pull Request against `develop`.
3. Wait for reviews.

## Pull Request Guidelines
- Ensure your code adheres to our coding standards.
- Write tests for your changes.
- Update the documentation if necessary.
- Your PR must pass all CI checks before it can be merged.

## Agent Git & Development Strategy
Autonomous Agents working on this repository MUST strictly follow this operational protocol to prevent messy Git trees and merge conflicts:
1. **Branching Model**: Do not commit directly to `develop` or `Mac_Machine` for complex features. Create a task-specific branch first:
   ```bash
   git checkout -b agent/feat/<task-name> Mac_Machine
   ```
2. **Conventional Commits**: Agents must use granular, atomic commits adhering to conventional commits (e.g. `feat(backend):`, `fix(frontend):`, `chore(docs):`).
3. **Rebase Over Merge**: Always execute `git pull --rebase` to integrate upstream changes smoothly before pushing.
4. **Lightweight Handovers**: When finishing a task, do NOT append massive logs to the root-level handover documents. Only update the "Active Epic" section in `AGENT_HANDOVER.md`. Preserve tokens!
