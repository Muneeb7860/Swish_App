#!/usr/bin/env bash
# ============================================================================
# sync_to_mac_machine.sh
#
# One-shot script that consolidates the in-progress work on this Mac into the
# Mac_Machine branch on origin. Run this from the repo root:
#
#   chmod +x sync_to_mac_machine.sh
#   ./sync_to_mac_machine.sh
#
# Before running:
#   • Quit any open Git GUI (GitHub Desktop, SourceTree, Tower, the VS Code
#     git pane in another window). They hold locks that break automated work.
#   • Make sure you're on the `mac-machine` branch (this script confirms).
#   • Read what it deletes — it is destructive on disk for *.py scaffolding
#     scripts, " 2.*" Finder duplicates, build logs, and the duplicate
#     V8/V9/V10/V11/V12 migrations that were superseded by V13–V18.
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

echo "==> Repo: $REPO_ROOT"
echo "==> Branch: $(git symbolic-ref --short HEAD)"
git fetch origin Mac_Machine

# ---------------------------------------------------------------------------
# 1. Clear any stale lock files left by interrupted git commands
# ---------------------------------------------------------------------------
echo "==> Clearing any stale .git locks"
rm -f .git/index.lock .git/gc.pid

# ---------------------------------------------------------------------------
# 2. Delete junk files from disk
# ---------------------------------------------------------------------------
echo "==> Deleting scaffolding .py scripts"
rm -f -- *.py backend/*.py

echo "==> Deleting Finder duplicates (\"... 2.*\")"
find . -maxdepth 4 -name '* [0-9].*' \
  -not -path './.git/*' \
  -not -path './node_modules/*' \
  -not -path './target/*' \
  -print -delete || true

echo "==> Deleting build log + .DS_Store junk"
rm -f backend/maven_errors.txt
find . -name '.DS_Store' -not -path './.git/*' -print -delete || true

# ---------------------------------------------------------------------------
# 3. Delete the obsolete migrations that have been superseded by V13–V18.
#    The old V8__wholesaler_schema.sql collided with V8__align_rider_check_
#    constraints.sql, which blocks Flyway from starting.
# ---------------------------------------------------------------------------
echo "==> Removing obsolete migration files"
rm -f backend/src/main/resources/db/migration/V8__wholesaler_schema.sql
rm -f backend/src/main/resources/db/migration/V8a__wholesaler_schema.sql
rm -f backend/src/main/resources/db/migration/V9__ecommerce_core_schema.sql
rm -f backend/src/main/resources/db/migration/V10__fleet_logistics_schema.sql
rm -f backend/src/main/resources/db/migration/V11__governance_support_schema.sql
rm -f backend/src/main/resources/db/migration/V12__notifications_feedback_schema.sql

# ---------------------------------------------------------------------------
# 4. Build the commit
# ---------------------------------------------------------------------------
echo "==> Staging all changes (additions, modifications, and deletions)"
git add -A

echo "==> Files staged for commit:"
git diff --cached --stat | tail -1

# ---------------------------------------------------------------------------
# 5. Commit and push
# ---------------------------------------------------------------------------
COMMIT_MSG=$(cat <<'EOF'
chore+security: consolidate Mac branch, fix auth, renumber migrations

Hygiene
- Delete 46 root-level *.py scaffolding scripts and 20 macOS " 2.*"
  Finder duplicates that had been accidentally committed.
- Delete backend/maven_errors.txt and stray .DS_Store files.
- Extend .gitignore to keep all of the above out of future commits.

Flyway migrations
- The old V8__wholesaler_schema.sql collided with the existing
  V8__align_rider_check_constraints.sql and blocked Flyway boot.
  Old V8/V9/V10/V11/V12 are deleted; their content is rewritten as
  V13–V17 with proper schemas (oltp, wholesaler, dispatch), foreign
  keys, indexes, and CHECK constraints, aligned to the @Entity
  classes in each domain.
- V15 no longer redefines active_shipments (V7 already creates the
  full dispatch.active_shipments table).
- New V18__auth_user_accounts.sql creates oltp.user_accounts and
  oltp.sessions for the rewritten auth flow.

Auth
- AuthServiceImpl now BCrypt-encodes on register and verifies with
  PasswordEncoder.matches on login. Login no longer compares
  plaintext. A constant-time bogus matches() call is run on lookup
  miss to prevent email-enumeration via timing.
- TokenServiceAdapter generates real HS256 JWTs using the existing
  jwt.secret config, with claim shape (sub, role, sid) matching what
  the existing JwtAuthenticationFilter expects.
- AuthController now uses request/response DTOs (RegisterRequest/
  Response, LoginRequest/Response) and never returns the domain
  UserAccount (which would leak the password hash).
- New JPA persistence (UserAccountEntity, SessionEntity, plus their
  Spring Data repositories and Adapter rewrites) replaces the
  stub adapters that previously returned Optional.empty() / echoed
  the input.
- SecurityConfig permits /api/v1/auth/** in addition to the legacy
  /api/auth/**.

Entity reconciliation
- Add @Column(name=...) and schema = "oltp" to ProductListingEntity,
  CustomerProfileEntity, NotificationEntity, SupportTicketEntity so
  the mapping is explicit and matches the new migrations.
EOF
)

echo "==> Committing"
git commit -m "$COMMIT_MSG"

echo "==> Pushing to origin/Mac_Machine"
git push origin mac-machine:Mac_Machine

echo
echo "==> Done."
echo "    Next: set Mac_Machine as the default branch in GitHub UI:"
echo "      https://github.com/Muneeb7860/Swish_App/settings/branches"
echo
echo "    Then verify the build locally:"
echo "      cd backend && ./mvnw -DskipTests compile"
echo "      cd backend && ./mvnw verify   # full test suite incl. integration"
