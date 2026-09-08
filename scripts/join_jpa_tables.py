#!/usr/bin/env python3
"""Connect graphify's Java subgraph to its SQL subgraph via JPA @Table mappings.

graphify extracts Java and SQL independently and, on this repo, produced zero edges between
them: `OrderRepository` had no path to `oltp.orders`. That is not a graphify defect — the
entity-to-table mapping lives in an annotation (`@Table(name = "orders", schema = "oltp")`),
and an annotation argument is not a syntactic reference to anything tree-sitter can resolve.

But the mapping is stated explicitly in the source, so it can be recovered exactly rather than
guessed. This reads every @Entity class, pairs it with the table its @Table names, and writes
one `maps_to` edge per pair. That closes the loop: Controller -> UseCase -> ServiceImpl ->
Repository -> Entity -> table, so "what touches oltp.orders" becomes answerable.

Run it AFTER every `graphify extract` / `graphify update` — those rewrite graph.json and drop
these edges. Re-running is safe: edges are keyed by `_origin` and replaced, never duplicated.

    python3 scripts/join_jpa_tables.py --dry-run     # report only
    python3 scripts/join_jpa_tables.py               # write edges into graph.json

Two matching rules exist because two things collide in this repo, both found by inspection,
not anticipated:

  Java class labels are NOT unique (`Order`, `OrderItem` and `PolicyDecision` each name more
  than one class across modules), so entities are matched on source_file path, never on label.

  SQL table labels are NOT unique either (`oltp.wholesalers` and `oltp.onboarding_applications`
  are each CREATE-d in two migrations, producing two nodes for one logical table). Every
  matching node is linked, since they are the same table; the count is reported so a genuine
  duplicate-table bug would still be visible rather than silently absorbed.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

# An annotation block sitting directly above a class declaration. `[^)]*` spans newlines, so a
# @Table(...) broken across lines is captured whole.
ENTITY_BLOCK = re.compile(
    r"((?:[ \t]*@\w+(?:\([^)]*\))?[ \t]*\r?\n)+)"
    r"[ \t]*(?:public\s+|final\s+|abstract\s+)*class\s+(\w+)",
    re.MULTILINE,
)
TABLE_ARGS = re.compile(r"@Table\s*\(([^)]*)\)", re.S)
ARG_NAME = re.compile(r'name\s*=\s*"([^"]+)"')
ARG_SCHEMA = re.compile(r'schema\s*=\s*"([^"]+)"')

ORIGIN = "jpa_table_join"
RELATION = "maps_to"


def find_entities(root: Path) -> list[dict]:
    """Every @Entity class with the table it declares."""
    found = []
    for path in sorted(root.rglob("*.java")):
        rel = path.relative_to(root).as_posix()
        if "/target/" in f"/{rel}" or rel.startswith("target/"):
            continue
        try:
            src = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if "@Entity" not in src:
            continue
        for block, cls in ENTITY_BLOCK.findall(src):
            if "@Entity" not in block:
                continue
            args = TABLE_ARGS.search(block)
            if not args:
                found.append({"cls": cls, "file": rel, "table": None, "schema": None, "line": None})
                continue
            name = ARG_NAME.search(args.group(1))
            schema = ARG_SCHEMA.search(args.group(1))
            line = src[: args.start()].count("\n") + 1
            found.append({
                "cls": cls,
                "file": rel,
                "table": name.group(1) if name else None,
                "schema": schema.group(1) if schema else None,
                "line": line,
            })
    return found


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--graph", default="graphify-out/graph.json")
    ap.add_argument("--root", default=".", help="repository root the graph was built from")
    ap.add_argument("--dry-run", action="store_true", help="report what would change, write nothing")
    ap.add_argument("--strict", action="store_true", help="exit 1 if any entity could not be matched")
    args = ap.parse_args()

    root = Path(args.root).resolve()
    graph_path = Path(args.graph)
    if not graph_path.exists():
        print(f"error: {graph_path} not found — run `graphify extract .` first", file=sys.stderr)
        return 2

    graph = json.loads(graph_path.read_text(encoding="utf-8"))
    nodes, links = graph["nodes"], graph["links"]

    # Entity classes are looked up by (source_file, label): label alone is ambiguous here.
    java_by_file_label: dict[tuple[str, str], dict] = {}
    sql_by_label: dict[str, list[dict]] = {}
    for n in nodes:
        src = str(n.get("source_file", ""))
        if src.endswith(".java") and n.get("_callable_class"):
            java_by_file_label[(src, n.get("label", ""))] = n
        elif src.endswith(".sql"):
            sql_by_label.setdefault(n.get("label", ""), []).append(n)

    entities = find_entities(root)
    existing = sum(1 for e in links if e.get("_origin") == ORIGIN)
    kept = [e for e in links if e.get("_origin") != ORIGIN]

    new_edges: list[dict] = []
    no_table, no_java_node, no_sql_node, dup_targets = [], [], [], []

    for ent in entities:
        if not ent["table"]:
            no_table.append(f"{ent['cls']} ({ent['file']})")
            continue
        jnode = java_by_file_label.get((ent["file"], ent["cls"]))
        if jnode is None:
            no_java_node.append(f"{ent['cls']} ({ent['file']})")
            continue
        label = f"{ent['schema']}.{ent['table']}" if ent["schema"] else ent["table"]
        targets = sql_by_label.get(label, [])
        if not targets:
            no_sql_node.append(f"{ent['cls']} -> {label}")
            continue
        if len(targets) > 1:
            dup_targets.append(f"{label} ({len(targets)} nodes)")
        for t in targets:
            new_edges.append({
                "source": jnode["id"],
                "target": t["id"],
                "relation": RELATION,
                "_origin": ORIGIN,
                "confidence": "EXTRACTED",
                "confidence_score": 1.0,
                "context": "jpa @Table",
                "source_file": ent["file"],
                "source_location": f"L{ent['line']}",
                "weight": 1.0,
            })

    matched = len(entities) - len(no_table) - len(no_java_node) - len(no_sql_node)
    print(f"@Entity classes found:      {len(entities)}")
    print(f"matched to a SQL table:     {matched}")
    print(f"edges to write:             {len(new_edges)}  (replacing {existing} from a previous run)")
    for title, items in (
        ("no @Table(name=)", no_table),
        ("no Java class node in graph", no_java_node),
        ("no SQL table node in graph", no_sql_node),
    ):
        if items:
            print(f"\nUNMATCHED — {title} ({len(items)}):")
            for i in items:
                print(f"  {i}")
    if dup_targets:
        print(f"\nnote: table defined by more than one migration, all linked ({len(dup_targets)}):")
        for d in sorted(set(dup_targets)):
            print(f"  {d}")

    if args.dry_run:
        print("\n--dry-run: graph.json not modified")
    else:
        graph["links"] = kept + new_edges
        tmp = graph_path.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(graph), encoding="utf-8")
        os.replace(tmp, graph_path)
        print(f"\nwrote {graph_path}: {len(kept)} existing + {len(new_edges)} {RELATION} edges")

    if args.strict and (no_table or no_java_node or no_sql_node):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
