"""Unit tests for Cryptographic Human-in-the-Loop (HITL) token verification module."""

from __future__ import annotations

import time

import pytest

from governance.hitl import generate_hitl_token, hitl_configured, verify_hitl_token


@pytest.fixture(autouse=True)
def _hitl_secret(monkeypatch):
    """Default a real secret for the happy-path tests; the fail-closed tests
    below explicitly delete it to test the unconfigured case."""
    monkeypatch.setenv("SWISHOS_HITL_SECRET", "test-hitl-secret-fixed")


def test_hitl_token_valid():
    input_hash = "abc123hash"
    token, ts = generate_hitl_token(input_hash)
    assert len(token) == 16
    assert verify_hitl_token(input_hash, token) is True


def test_hitl_token_invalid_forged():
    input_hash = "abc123hash"
    generate_hitl_token(input_hash)
    forged_token = "1234567890abcdef"
    assert verify_hitl_token(input_hash, forged_token) is False


def test_hitl_token_mismatched_hash():
    token, _ = generate_hitl_token("hash_one")
    assert verify_hitl_token("hash_two", token) is False


def test_hitl_token_expired():
    input_hash = "expiring_hash"
    past_ts = int(time.time()) - 400  # 400s ago (> 300s TTL)
    token, _ = generate_hitl_token(input_hash, timestamp=past_ts)
    assert verify_hitl_token(input_hash, token, max_age_seconds=300) is False


class TestHitlFailsClosedWithoutSecret:
    """Regression coverage: the original implementation hardcoded a fallback
    secret (`swishos_hitl_secret_default_key_2026`) directly in source, so
    anyone who read the code could compute a valid approval token themselves
    -- a step-up gate that verifies nothing. Must fail closed instead: no
    configured secret means no token can be minted or accepted, full stop."""

    def test_hitl_configured_false_without_env_var(self, monkeypatch):
        monkeypatch.delenv("SWISHOS_HITL_SECRET", raising=False)
        assert hitl_configured() is False

    def test_hitl_configured_true_with_env_var(self, monkeypatch):
        monkeypatch.setenv("SWISHOS_HITL_SECRET", "some-secret")
        assert hitl_configured() is True

    def test_generate_returns_none_without_secret(self, monkeypatch):
        monkeypatch.delenv("SWISHOS_HITL_SECRET", raising=False)
        assert generate_hitl_token("any-hash") is None

    def test_verify_rejects_everything_without_secret(self, monkeypatch):
        monkeypatch.delenv("SWISHOS_HITL_SECRET", raising=False)
        # Even the exact string that used to be the hardcoded fallback secret's
        # own computed token must not verify -- there is no secret to check
        # against, so every token is rejected, not just forged-looking ones.
        import hashlib
        import hmac

        old_hardcoded_secret = b"swishos_hitl_secret_default_key_2026"
        ts = int(time.time())
        nonce = f"any-hash:{ts}".encode("utf-8")
        legacy_token = hmac.new(old_hardcoded_secret, nonce, hashlib.sha256).hexdigest()[:16]
        assert verify_hitl_token("any-hash", legacy_token) is False

    def test_verify_rejects_old_hardcoded_secret_even_when_configured(self, monkeypatch):
        """Even if SWISHOS_HITL_SECRET happens to be configured, a token
        forged with the OLD hardcoded fallback secret must not verify --
        proves the fix isn't just gating on presence but on the actual
        configured value."""
        monkeypatch.setenv("SWISHOS_HITL_SECRET", "a-real-operator-configured-secret")
        import hashlib
        import hmac

        old_hardcoded_secret = b"swishos_hitl_secret_default_key_2026"
        ts = int(time.time())
        nonce = f"any-hash:{ts}".encode("utf-8")
        legacy_token = hmac.new(old_hardcoded_secret, nonce, hashlib.sha256).hexdigest()[:16]
        assert verify_hitl_token("any-hash", legacy_token) is False
