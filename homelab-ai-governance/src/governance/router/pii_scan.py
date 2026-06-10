"""Pre-routing PII scan — determines if the query must stay local-only.

Uses the shared PII patterns from governance.guardrails.pii_patterns
(defined once, used everywhere).
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from governance.guardrails.pii_patterns import contains_pii, scan_pii

logger = logging.getLogger(__name__)


@dataclass
class PIIScanResult:
    """Result of the pre-routing PII scan."""

    contains_pii: bool
    local_only: bool
    pii_types: list[str]


def pre_route_pii_scan(text: str) -> PIIScanResult:
    """Scan the input for PII and determine routing constraint.

    If PII is detected, the query MUST route to a local agent only —
    it cannot be sent to cloud APIs (data sovereignty).
    """
    if not contains_pii(text):
        return PIIScanResult(contains_pii=False, local_only=False, pii_types=[])

    matches = scan_pii(text)
    pii_types = list({m["name"] for m in matches})

    logger.info(
        "PII detected in input: types=%s — enforcing local_only routing",
        pii_types,
    )

    return PIIScanResult(
        contains_pii=True,
        local_only=True,
        pii_types=pii_types,
    )
