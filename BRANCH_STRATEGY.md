# Enterprise Git Branching Strategy

This project follows a custom Multi-Environment flow to ensure code stability across our core pipeline and cross-OS development environments.

## Core Branches

1. **`master`**
   - The absolute source of truth.
   - Contains strictly production-ready, highly-tested code.
   - **Rule**: Never push directly to `master`. All code flows into `master` strictly via pull requests from `dev`.

2. **`develop`** (Integration Branch)
   - The primary integration and development branch.
   - New features are merged here for integration testing before heading to `master`.
   - Should run cleanly via Docker regardless of underlying OS.

## Environment-Specific Testing Branches

Because the underlying OS environments (Mac with APFS/ARM64 and Windows with WSL2/NTFS) may require specific edge-case Docker or Shell configurations, we maintain two persistent environment branches:

3. **`mac-machine`**
   - Strictly tracks `develop`. 
   - Used for Apple Silicon / APFS local environment testing or `docker.sock` volume overrides.
   - **Workflow**: If a fix is needed for macOS, branch off `mac-machine`, apply the fix, test, and then PR the agnostic parts of the fix back into `develop`.

4. **`macbook_machine`**
   - Strictly tracks `develop`.
   - Used for secondary Macbook environment testing or specific configurations.
   - **Workflow**: Similar to `mac-machine`, keep Macbook-specific hacks isolated here, while ensuring core agnostic business logic flows into `develop`.

## Standard Developer Workflow

1. Start all new agnostic feature work from `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feat/my-new-feature
   ```
2. Commit and test locally on your specific OS using your local environment branches (`mac-machine` or `macbook_machine`) if needed.
3. Push `feat/my-new-feature` and open a Pull Request into `develop`.
4. Once tested, `develop` is eventually merged into `master` for release.
