"""PII pattern matrix — GOVERNANCE_SPEC.md Phase 2.

Every pattern in ``pii_patterns.PII_PATTERNS`` must have positive and
near-miss cases here, enforced by a sync test: deleting a pattern from the
module (or adding one without cases) fails CI. This is the guarantee behind
goal 1 — PII detection coverage cannot silently shrink.
"""

from __future__ import annotations

import pytest

from governance.guardrails.pii_patterns import (
    PII_PATTERNS,
    contains_pii,
    redact_pii,
    scan_pii,
)

# ── Case matrix ──────────────────────────────────────────────────────────────
# name → texts that MUST be detected as that PII type
POSITIVE_CASES: dict[str, list[str]] = {
    "CONNECTION_STRING": [
        "connect via postgres://admin:secret@db.internal:5432/orders",
        "MONGODB://root:hunter2@10.0.0.5/logs is the archive",
        "cache lives at redis://:p4ssw0rd@localhost:6379/0",
    ],
    "API_KEY": [
        "api_key = 'sk_live_abcdef1234567890ABCD'",
        "password: supersecretvalue1234",
        "export API-KEY=A1b2C3d4E5f6G7h8I9j0",
    ],
    "CREDIT_CARD": [
        "card 4111 1111 1111 1111 please",
        "pay with 4111-2222-3333-4444 today",
        "pan 4111222233334444 on file",
    ],
    "SSN": [
        "my ssn is 123-45-6789",
    ],
    "EMAIL": [
        "reach me at jane.doe+test@example.co.uk",
        "support@example.ch handles tickets",
    ],
    "OBFUSCATED_EMAIL": [
        "reach me at jane (at) example (dot) com",
        "contact info: john [at] test [dot] org",
    ],
    "PHONE_NUMBER": [
        "call +41-44-123-4567 tomorrow",
        "hotline 555-123-4567 is open",
    ],
    "IP_ADDRESS": [
        "server 192.168.1.100 is down",
        "ping 8.8.8.8 first",
    ],
}

# name → near-misses that MUST NOT be detected as that PII type
# (they may legitimately match a *different* pattern — asserted per-pattern)
NEAR_MISS_CASES: dict[str, list[str]] = {
    "CONNECTION_STRING": ["see https://example.com/docs for the schema"],
    "API_KEY": ["api_key = 'short'", "the word password appears alone"],
    "CREDIT_CARD": ["invoice 1234-5678-9012 (12 digits)"],
    "SSN": ["ref 123-45-678 is not an ssn", "date 2026-07-13 either"],
    "EMAIL": ["mention @channel in slack", "price is 5@10% margin"],
    "OBFUSCATED_EMAIL": ["look at that cat over there", "no email pattern here"],
    "PHONE_NUMBER": ["room 42", "order #1234"],
    "IP_ADDRESS": ["version 2.0.0 shipped", "999.999.999.999 is not routable"],
}

# Whole strings that must contain NO PII of any type
CLEAN_CASES = [
    "What is the capital of Switzerland?",
    "The build finished in 42 seconds",
    "Meeting at 10.30 am in room 7",
    "v2.0.0 released with 15 fixes",
    "Summarize my meeting notes please",
]

_PATTERNS_BY_NAME = {pp.name: pp.pattern for pp in PII_PATTERNS}


# ── Sync guarantee (the Phase 2 acceptance criterion) ────────────────────────


def test_case_matrix_covers_every_pattern_exactly():
    """Deleting a pattern from pii_patterns.py — or adding one without test
    cases — must fail CI."""
    pattern_names = {pp.name for pp in PII_PATTERNS}
    assert set(POSITIVE_CASES) == pattern_names, (
        "POSITIVE_CASES out of sync with PII_PATTERNS"
    )
    assert set(NEAR_MISS_CASES) == pattern_names, (
        "NEAR_MISS_CASES out of sync with PII_PATTERNS"
    )


# ── Positives ────────────────────────────────────────────────────────────────


@pytest.mark.parametrize(
    "name,text",
    [(n, t) for n, texts in POSITIVE_CASES.items() for t in texts],
)
def test_positive_detected_as_correct_type(name, text):
    assert contains_pii(text), f"{name} positive not detected at all: {text!r}"
    detected_types = {m["name"] for m in scan_pii(text)}
    assert name in detected_types, (
        f"expected {name} in {detected_types} for {text!r}"
    )


# ── Near-misses ──────────────────────────────────────────────────────────────


@pytest.mark.parametrize(
    "name,text",
    [(n, t) for n, texts in NEAR_MISS_CASES.items() for t in texts],
)
def test_near_miss_does_not_match_its_pattern(name, text):
    assert not _PATTERNS_BY_NAME[name].search(text), (
        f"{name} false-positive on {text!r}"
    )


@pytest.mark.parametrize("text", CLEAN_CASES)
def test_clean_text_matches_nothing(text):
    assert not contains_pii(text), f"false positive on clean text: {text!r}"
    assert scan_pii(text) == []


# ── Redaction ────────────────────────────────────────────────────────────────


def test_redaction_replaces_all_types_in_mixed_text():
    text = (
        "email jane@example.com, card 4111 1111 1111 1111, "
        "host 10.0.0.7, ssn 123-45-6789"
    )
    redacted = redact_pii(text)
    for token in (
        "[REDACTED:EMAIL]",
        "[REDACTED:CREDIT_CARD]",
        "[REDACTED:IP_ADDRESS]",
        "[REDACTED:SSN]",
    ):
        assert token in redacted
    assert not contains_pii(redacted), f"PII survived redaction: {redacted!r}"


def test_redacted_output_is_stable():
    """Redacting twice must be a no-op (placeholders are not re-matched)."""
    text = "reach jane@example.com at 192.168.0.1"
    once = redact_pii(text)
    assert redact_pii(once) == once


# ── Known gaps (documented, strict-xfail so a fix flips them loudly) ─────────


def test_obfuscated_email_gap():
    assert contains_pii("reach me at jane (at) example (dot) com")
