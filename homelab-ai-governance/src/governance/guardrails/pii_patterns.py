"""PII regex patterns shared between the pre-routing PII scan and the pii_filter guardrail.

These patterns are defined once here and imported by both:
- governance.router.pii_scan (pre-routing)
- governance.guardrails.detectors (guardrail enforcement)
"""

from __future__ import annotations

import re
from typing import NamedTuple


class PIIPattern(NamedTuple):
    name: str
    pattern: re.Pattern[str]


# Compiled PII patterns — order matters for redaction (most specific first)
PII_PATTERNS: list[PIIPattern] = [
    PIIPattern("CONNECTION_STRING", re.compile(r"(?i)(postgres|mysql|mongodb|redis)://[^\s]+")),
    PIIPattern("API_KEY", re.compile(
        r"(?i)(api[_-]?key|secret|token|password)\s*[:=]\s*[\"']?[a-zA-Z0-9_\-]{16,}"
    )),
    PIIPattern("CREDIT_CARD", re.compile(r"\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b")),
    PIIPattern("SSN", re.compile(r"\b\d{3}-\d{2}-\d{4}\b")),
    PIIPattern("EMAIL", re.compile(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")),
    PIIPattern("PHONE_NUMBER", re.compile(
        r"\+?[-.\s]?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{2,4}[-.\s]?\d{2,4}|\b\d{3}-\d{3}-\d{4}\b"
    )),
    PIIPattern("IP_ADDRESS", re.compile(
        r"\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b"
    )),
]


def scan_pii(text: str) -> list[dict[str, str]]:
    """Scan text for PII matches. Returns a list of dicts with 'name' and 'match'."""
    matches = []
    for pp in PII_PATTERNS:
        for m in pp.pattern.finditer(text):
            matches.append({"name": pp.name, "match": m.group()})
    return matches


def contains_pii(text: str) -> bool:
    """Quick check: does the text contain any PII?"""
    return any(pp.pattern.search(text) for pp in PII_PATTERNS)


def redact_pii(text: str) -> str:
    """Redact all PII matches in the text, replacing with [REDACTED:<type>]."""
    result = text
    for pp in PII_PATTERNS:
        result = pp.pattern.sub(f"[REDACTED:{pp.name.upper()}]", result)
    return result
