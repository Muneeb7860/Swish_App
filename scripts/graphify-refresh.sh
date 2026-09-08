#!/bin/sh
# Rebuild the graphify graph, then re-apply the JPA @Table -> SQL joins.
#
# Measured, not assumed: `graphify update` MERGES into the existing graph, so the
# maps_to edges from a previous join usually survive it. The join still has to run
# after every rebuild for the cases where they don't -- a newly added @Entity has
# nothing linking it until the join runs, and a re-extracted file can take its own
# edges with it. The join is idempotent and sub-second, so running it every time is
# cheaper than working out when it is needed.
#
# The ORDER is the part that matters, and it is why this is one script rather than
# a line appended to graphify's own git hook: that hook spawns its rebuild DETACHED
# and returns immediately, so anything chained after it would run against the
# pre-rebuild graph. One sequential process, launched detached once, has no race.
#
# Known divergence: the graph was built with `extract --code-only`, but `update`
# has no such flag (it accepts only --force and --no-cluster), so an update widens
# the corpus to include markdown -- 1,360 document nodes on this repo. They are
# structurally extracted (_origin: ast), so there is no API cost, but the corpus
# shape drifts from the original build. Swap `update .` for `extract . --code-only`
# below if holding that shape matters more than update's incremental speed.
#
# Safe to run by hand:  sh scripts/graphify-refresh.sh
# Opt out per-command:  GRAPHIFY_SKIP_HOOK=1 git commit ...
set -u

[ "${GRAPHIFY_SKIP_HOOK:-0}" = "1" ] && exit 0

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$ROOT" || exit 0

# Skip inside a linked worktree: graphify-out/ belongs to the primary checkout,
# and rebuilding from a worktree writes a delta-only graph nobody asked for.
# (Same reasoning as graphify's own hook, upstream #1809/#1806.)
if [ "$(git rev-parse --git-dir 2>/dev/null)" != "$(git rev-parse --git-common-dir 2>/dev/null)" ]; then
    exit 0
fi

GRAPHIFY="${GRAPHIFY_BIN:-$HOME/.local/bin/graphify}"
[ -x "$GRAPHIFY" ] || exit 0
[ -f graphify-out/graph.json ] || exit 0   # nothing built yet; a full extract is a manual step

LOG="${GRAPHIFY_REFRESH_LOG:-$HOME/.cache/graphify-refresh.log}"
mkdir -p "$(dirname "$LOG")" 2>/dev/null || true

{
    echo "--- $(date '+%Y-%m-%d %H:%M:%S') refresh in $ROOT"
    "$GRAPHIFY" update . 2>&1 && python3 scripts/join_jpa_tables.py 2>&1
    echo "--- exit=$?"
} >> "$LOG" 2>&1
