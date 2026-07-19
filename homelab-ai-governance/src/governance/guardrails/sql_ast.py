"""SQL AST validator module for inspecting generated SQL blocks in model outputs."""

from __future__ import annotations

import logging
import re
from typing import Any

logger = logging.getLogger(__name__)

# Try importing sqlglot if available, else fall back to regex AST tokenization
try:
    import sqlglot
    from sqlglot import exp
    HAS_SQLGLOT = True
except ImportError:
    HAS_SQLGLOT = False
    logger.info("sqlglot not installed; using fallback SQL AST tokenizer")


def extract_sql_blocks(content: str) -> list[str]:
    """Extract code blocks tagged as sql or inline SQL query statements."""
    blocks = re.findall(r"```sql\s*([\s\S]*?)\s*```", content, re.IGNORECASE)
    if not blocks:
        # Check for inline SQL statements if keywords are present
        if re.search(r"\b(SELECT|INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER)\b", content, re.IGNORECASE):
            blocks = [content]
    return blocks


def is_safe_sql(sql_code: str) -> bool:
    """Validate if a SQL code block is safe (no DDL, no unconstrained DELETE/TRUNCATE)."""
    if HAS_SQLGLOT:
        try:
            parsed = sqlglot.parse(sql_code)
            for stmt in parsed:
                if stmt is None:
                    continue
                # Block DDL statements (DROP, TRUNCATE, ALTER)
                if isinstance(stmt, (exp.Drop, exp.TruncateTable, exp.Alter)):
                    logger.warning("SQL AST: Disqualified DDL statement %s", type(stmt).__name__)
                    return False
                # Block DELETE statements without a WHERE clause
                if isinstance(stmt, exp.Delete):
                    if not stmt.this or not stmt.args.get("where"):
                        logger.warning("SQL AST: Disqualified unconstrained DELETE statement without WHERE clause")
                        return False
            return True
        except Exception as e:
            logger.debug("sqlglot parse error: %s; falling back to token analysis", e)

    # Heuristic fallback if sqlglot parse fails or is unavailable
    if re.search(r"\b(DROP\s+TABLE|DROP\s+DATABASE|TRUNCATE\s+TABLE)\b", sql_code, re.IGNORECASE):
        return False
    if re.search(r"\bDELETE\s+FROM\s+\w+\s*(;|\s*$)", sql_code, re.IGNORECASE):
        return False

    return True


def detect_unsafe_sql(detector_config: dict[str, Any], content: str) -> bool:
    """Detector entry point for shared_guardrails dispatch table.

    Returns True if unsafe SQL (DDL or unconstrained DML) is detected.
    """
    blocks = extract_sql_blocks(content)
    for b in blocks:
        if not is_safe_sql(b):
            return True
    return False
