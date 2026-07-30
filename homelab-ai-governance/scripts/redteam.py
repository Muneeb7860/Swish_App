#!/usr/bin/env python3
"""Swish governance red-team entry point — thin shim over the canonical tool.

The runner and payloads live in the standalone `agentic-redteam` package
(github.com/Muneeb7860/agentic-redteam), which is the single source of truth.
This shim keeps the historical `python scripts/redteam.py --ci` invocation working.

Install: `pip install agentic-redteam` (PyPI) or set PYTHONPATH to the source dir.
Run:  python scripts/redteam.py [categories...] [--ci]
      GOVERNANCE_URL overrides the target (default http://localhost:8000/api/v1/govern).
"""
import sys

from agentic_redteam.cli import main

if __name__ == "__main__":
    sys.exit(main())
