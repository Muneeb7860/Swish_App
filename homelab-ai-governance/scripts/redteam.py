#!/usr/bin/env python3
"""Network-independent red-team runner for the governance service.

Replaces the fragile `npx promptfoo` path (its native deps — sharp,
transformers, onnxruntime — routinely fail to install on a flaky network, and
the previous CI loop ran each bare test-list file with no valid provider, so it
passed while testing nothing). This reads the SAME category YAML files, POSTs
each query to the live service, and evaluates each test's JS assertion via
`node` (eval's completion-value semantics match promptfoo's javascript assert).

Usage
-----
    python scripts/redteam.py                 # all categories, human summary
    python scripts/redteam.py jailbreak pii_leakage   # subset
    python scripts/redteam.py --ci            # exit non-zero if a CRITICAL
                                              # category has any failure

Env
---
    GOVERNANCE_URL   default http://localhost:8000/api/v1/govern
    REDTEAM_OUT      default <repo>/promptfoo/results/redteam_findings.json

CI note: run the service with GOVERNANCE_ALLOW_MOCK_FALLBACK=1 so responses are
deterministic without a live Ollama. That faithfully exercises the guardrail
GATES (which run before any model); model-COMPLIANCE red-teaming needs real
models and belongs in a local/staging run.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parent.parent
TESTS_DIR = REPO / "promptfoo" / "tests"
CATEGORIES = [
    "pii_leakage", "prompt_injection", "jailbreak",
    "code_safety", "schema_compliance", "clean_queries",
]
# Categories whose failure should fail CI (guardrail correctness, model-free gates).
CRITICAL = {"prompt_injection", "pii_leakage", "jailbreak"}
URL = os.environ.get("GOVERNANCE_URL", "http://localhost:8000/api/v1/govern")
OUT = Path(os.environ.get("REDTEAM_OUT", REPO / "promptfoo" / "results" / "redteam_findings.json"))


def call_service(query: str, timeout: float = 90.0) -> dict:
    data = json.dumps({"query": query}).encode()
    req = urllib.request.Request(URL, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"status": "http_error", "code": e.code}
    except Exception as e:
        return {"status": "transport_error", "error": str(e)}


def eval_assertion(output_obj: dict, js_body: str) -> bool | None:
    runner = (
        "const output = JSON.parse(process.argv[1]);\n"
        "const body = process.argv[2];\n"
        "try { process.stdout.write(eval(body) ? 'PASS' : 'FAIL'); }\n"
        "catch (e) { process.stdout.write('ERR:' + e.message); }\n"
    )
    try:
        p = subprocess.run(
            ["node", "-e", runner, json.dumps(output_obj), js_body],
            capture_output=True, text=True, timeout=15,
        )
        return {"PASS": True, "FAIL": False}.get(p.stdout.strip())
    except Exception:
        return None


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    ci = "--ci" in sys.argv
    cats = [c for c in CATEGORIES if c in args] if args else CATEGORIES

    summary, failures, t0 = {}, [], time.time()
    for cat in cats:
        tests = yaml.safe_load((TESTS_DIR / f"{cat}.yaml").read_text()) or []
        p = f = 0
        for t in tests:
            desc = t.get("description", "?")
            out = call_service(t.get("vars", {}).get("query", ""))
            ok = True
            for a in t.get("assert", []):
                if a.get("type") != "javascript":
                    continue
                if eval_assertion(out, a.get("value", "false")) is not True:
                    ok = False
            if ok:
                p += 1
            else:
                f += 1
                failures.append({"cat": cat, "desc": desc, "status": out.get("status"),
                                 "response": str(out.get("response", out.get("message", "")))[:160]})
            print(f"[{cat}] {'PASS' if ok else 'FAIL'}: {desc[:60]}", flush=True)
        summary[cat] = {"pass": p, "fail": f, "total": len(tests)}

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"summary": summary, "failures": failures,
                               "elapsed_s": round(time.time() - t0, 1)}, indent=2))

    print("\n=== SUMMARY ===")
    tp = tt = crit_fail = 0
    for cat, s in summary.items():
        tp += s["pass"]; tt += s["total"]
        if cat in CRITICAL:
            crit_fail += s["fail"]
        print(f"  {cat:20s} {s['pass']:2d}/{s['total']:2d} pass ({s['fail']} fail)")
    print(f"  {'TOTAL':20s} {tp:2d}/{tt:2d} pass")

    if ci and crit_fail:
        print(f"\n❌ {crit_fail} failure(s) in CRITICAL categories {sorted(CRITICAL)}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
