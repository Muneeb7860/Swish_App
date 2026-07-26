"""Tests for governance.agent_auth — the ASI07 inter-agent HMAC identity
layer (agentic-redteam crypto_probes).

Includes true cross-package interop tests against the actual client-side
agentic_redteam.crypto / telemetry_verifier modules (not just "looks similar"
— byte-for-byte compatibility is the whole point of this feature, so a test
that only exercises our own re-implementation in isolation would miss a
canonical-string or constant drift between the two).
"""

from __future__ import annotations

import os
import time

import pytest

from governance.agent_auth import (
    AUDIT_PROOF_SECRET,
    agent_signature_required,
    sign_audit_proof,
    verify_agent_signature,
)


# ── Audit-proof headers (always-on) ──────────────────────────────────────────


def test_sign_audit_proof_produces_all_three_headers():
    headers = sign_audit_proof("prompt_injection_filter")
    assert set(headers) == {
        "X-SwishOS-Audit-Proof",
        "X-SwishOS-Audit-Timestamp",
        "X-SwishOS-Audit-Nonce",
    }
    assert all(headers.values())  # none blank


def test_audit_proof_verifies_against_the_real_client_verifier():
    """Cross-package interop: agentic_redteam.telemetry_verifier is the
    ACTUAL code crypto_probes.py uses to check our response."""
    from agentic_redteam.telemetry_verifier import verify_audit_proof_header

    headers = sign_audit_proof("prompt_injection_filter")
    ok, msg = verify_audit_proof_header(headers)
    assert ok is True, msg


def test_audit_proof_signature_matches_when_rule_triggered_is_known():
    """With rule_triggered supplied, the client verifier checks the actual
    HMAC, not just header presence — confirms the canonical string matches
    exactly, not just that 3 headers happen to exist."""
    from agentic_redteam.telemetry_verifier import verify_audit_proof_header

    headers = sign_audit_proof("prompt_injection_filter", client_ip="127.0.0.1")
    ok, msg = verify_audit_proof_header(
        headers, client_ip="127.0.0.1", rule_triggered="prompt_injection_filter"
    )
    assert ok is True, msg


# ── Agent signature verification (feature-flagged) ──────────────────────────


@pytest.fixture(autouse=True)
def _clean_env(monkeypatch):
    monkeypatch.delenv("GOVERNANCE_REQUIRE_AGENT_SIGNATURE", raising=False)
    monkeypatch.delenv("SWISH_AGENT_SHARED_SECRET", raising=False)
    yield


def test_agent_signature_required_off_by_default():
    assert agent_signature_required() is False


def test_agent_signature_required_on_when_flagged(monkeypatch):
    monkeypatch.setenv("GOVERNANCE_REQUIRE_AGENT_SIGNATURE", "1")
    assert agent_signature_required() is True


def test_verify_valid_signature_from_the_real_client_signer(monkeypatch):
    """Cross-package interop: sign with the ACTUAL client library
    (agentic_redteam.crypto.sign_payload), verify with ours."""
    from agentic_redteam.crypto import sign_payload

    monkeypatch.setenv("SWISH_AGENT_SHARED_SECRET", "test-shared-secret")
    payload = {"query": "hello"}
    headers = sign_payload("agent-test-1", "test-shared-secret", payload)

    ok, msg = verify_agent_signature(headers, payload)
    assert ok is True, msg


def test_verify_rejects_wrong_secret(monkeypatch):
    from agentic_redteam.crypto import sign_payload

    monkeypatch.setenv("SWISH_AGENT_SHARED_SECRET", "server-secret")
    payload = {"query": "hello"}
    headers = sign_payload("agent-test-1", "attacker-guessed-secret", payload)

    ok, msg = verify_agent_signature(headers, payload)
    assert ok is False


def test_verify_rejects_missing_headers(monkeypatch):
    monkeypatch.setenv("SWISH_AGENT_SHARED_SECRET", "test-shared-secret")
    ok, msg = verify_agent_signature({}, {"query": "hello"})
    assert ok is False
    assert "Missing" in msg


def test_verify_rejects_forged_signature(monkeypatch):
    from agentic_redteam.crypto import sign_payload

    monkeypatch.setenv("SWISH_AGENT_SHARED_SECRET", "test-shared-secret")
    payload = {"query": "hello"}
    headers = sign_payload("agent-test-1", "test-shared-secret", payload)
    headers["X-Agent-Signature"] = "0" * 64

    ok, msg = verify_agent_signature(headers, payload)
    assert ok is False


def test_verify_rejects_stale_timestamp(monkeypatch):
    from agentic_redteam.crypto import sign_payload
    import time

    monkeypatch.setenv("SWISH_AGENT_SHARED_SECRET", "test-shared-secret")
    payload = {"query": "hello"}
    headers = sign_payload(
        "agent-test-1", "test-shared-secret", payload, timestamp=time.time() - 600
    )

    ok, msg = verify_agent_signature(headers, payload)
    assert ok is False
    assert "skew" in msg.lower()


def test_verify_rejects_replayed_nonce(monkeypatch):
    from agentic_redteam.crypto import sign_payload

    monkeypatch.setenv("SWISH_AGENT_SHARED_SECRET", "test-shared-secret")
    payload = {"query": "hello"}
    headers = sign_payload("agent-test-1", "test-shared-secret", payload, nonce="fixed-nonce-1")

    ok1, _ = verify_agent_signature(headers, payload)
    assert ok1 is True

    ok2, msg2 = verify_agent_signature(headers, payload)  # same nonce again
    assert ok2 is False
    assert "Replay" in msg2


def test_verify_fails_closed_when_enforcement_enabled_but_no_secret_configured(monkeypatch):
    """A deploy/config bug (flag on, secret unset) must reject, never
    silently skip verification — goal 1."""
    ok, msg = verify_agent_signature(
        {"X-Agent-ID": "x", "X-Agent-Timestamp": "1", "X-Agent-Nonce": "n", "X-Agent-Signature": "s"},
        {},
    )
    assert ok is False
    assert "shared secret" in msg.lower()


def test_replay_cache_eviction_never_forgets_a_recent_nonce_under_load(monkeypatch):
    """Regression test: the cache used to do a blanket `.clear()` once it
    exceeded 10,000 entries, wiping recently-inserted (still-valid) nonces
    along with genuinely stale ones — briefly reopening the replay window at
    exactly the moment (high request volume) an attacker would most likely
    be probing. Age-based eviction must never forget an entry inserted
    within the clock-skew window, no matter how many entries pile up."""
    from agentic_redteam.crypto import sign_payload
    import governance.agent_auth as agent_auth_module

    monkeypatch.setenv("SWISH_AGENT_SHARED_SECRET", "test-shared-secret")
    monkeypatch.setattr(agent_auth_module, "_replay_nonce_cache", {})

    payload = {"query": "hello"}
    headers = sign_payload("agent-load-test", "test-shared-secret", payload, nonce="the-nonce-to-protect")
    ok1, _ = verify_agent_signature(headers, payload)
    assert ok1 is True

    # Flood the cache well past the old 10,000-entry clear threshold with
    # OTHER nonces, all inserted "now" (well within the clock-skew window).
    now = time.time()
    for i in range(10_500):
        agent_auth_module._replay_nonce_cache[f"agent-flood:{i}"] = now

    # The original nonce, inserted before the flood, must still be
    # remembered — replaying it must still be rejected.
    ok2, msg2 = verify_agent_signature(headers, payload)
    assert ok2 is False
    assert "Replay" in msg2
