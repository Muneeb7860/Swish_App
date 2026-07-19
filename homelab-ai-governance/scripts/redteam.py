#!/usr/bin/env python3
"""Swish governance red-team entry point — thin shim over the canonical tool.

The runner and payloads now live in the standalone, target-agnostic
`agentic-redteam` package (repo root), which is the single source of truth so
the Swish CI gate and the standalone audit/demo tool can never drift. This shim
keeps the historical `python scripts/redteam.py --ci` invocation working.

Install (CI does this): `pip install -e ../agentic-redteam`
Run:  python scripts/redteam.py [categories...] [--ci]
      GOVERNANCE_URL overrides the target (default http://localhost:8000/api/v1/govern).
"""
import sys

from agentic_redteam.cli import main

if __name__ == "__main__":
    sys.exit(main())
