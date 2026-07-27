"""Cryptographic Human-in-the-Loop (HITL) token generation and verification.

GOVERNANCE_SPEC.md §3c: Step-up authorization requires constant-time HMAC-SHA256
token validation with a 5-minute replay window to prevent unauthorized token forging.

Fail-closed by design (GOVERNANCE_SPEC goal 1, same principle as
agent_auth.py's verify_agent_signature): there is deliberately NO hardcoded
fallback secret. A HITL approval token is supposed to prove a human took an
action; a secret checked into source control is readable by anyone who reads
the code, so a fallback default would let anyone compute a "valid" approval
token themselves without any human ever approving anything — a step-up gate
that can't be forged only if the operator actually configures a secret.
"""

from __future__ import annotations

import hashlib
import hmac
import os
import threading
import time

_TOKEN_TTL_SECONDS = 300  # 5 minute approval window

# AUDIT FIX F6: Single-use token cache — prevents replay of valid HITL tokens
# within the 300s window.  Keyed by (input_hash, token) with timestamp for
# time-based eviction.  Same pattern as agent_auth.py's nonce replay cache.
_used_tokens: dict[str, float] = {}
_used_tokens_lock = threading.Lock()
_EVICTION_INTERVAL = 100  # evict stale entries every N verifications
_eviction_counter = 0


def _evict_stale_tokens() -> None:
    """Remove expired tokens from the replay cache (called periodically)."""
    cutoff = time.time() - _TOKEN_TTL_SECONDS - 60  # 1 min grace
    stale = [k for k, ts in _used_tokens.items() if ts < cutoff]
    for k in stale:
        del _used_tokens[k]


def _hitl_secret() -> bytes | None:
    secret = os.getenv("SWISHOS_HITL_SECRET")
    return secret.encode("utf-8") if secret else None


def hitl_configured() -> bool:
    """Whether a real HITL secret is configured. Callers MUST hard-block
    (not silently allow) high-impact directives when this is False — see
    pipeline.py's HITL step-up interceptor and server.py's approve_hitl."""
    return _hitl_secret() is not None


def generate_hitl_token(input_hash: str, timestamp: int | None = None) -> tuple[str, int] | None:
    """Generate a constant-time HMAC-SHA256 step-up token for a given input_hash.

    Returns None if SWISHOS_HITL_SECRET isn't configured — callers must treat
    that as "step-up approval is unavailable," never as "approval granted."
    """
    secret = _hitl_secret()
    if secret is None:
        return None
    ts = timestamp if timestamp is not None else int(time.time())
    nonce = f"{input_hash}:{ts}".encode("utf-8")
    token = hmac.new(secret, nonce, hashlib.sha256).hexdigest()[:16]
    return token, ts


def verify_hitl_token(input_hash: str, token: str, max_age_seconds: int = _TOKEN_TTL_SECONDS) -> bool:
    """Verify an approval token against input_hash within the valid time-to-live window.

    AUDIT FIX F6: Tokens are now single-use — after successful verification,
    the (input_hash, token) pair is stored in a replay cache.  A second
    submission of the same token is rejected.  This prevents an attacker who
    intercepts a valid pending token from replaying it across sessions.
    """
    global _eviction_counter

    secret = _hitl_secret()
    if secret is None or not token or len(token) != 16:
        return False

    # Check replay cache BEFORE expensive HMAC loop
    cache_key = f"{input_hash}:{token.lower()}"
    with _used_tokens_lock:
        if cache_key in _used_tokens:
            return False  # Already used — reject replay

    current_ts = int(time.time())
    # Search backwards through the TTL window for a valid matching HMAC signature
    for age in range(max_age_seconds + 1):
        test_ts = current_ts - age
        nonce = f"{input_hash}:{test_ts}".encode("utf-8")
        expected_token = hmac.new(secret, nonce, hashlib.sha256).hexdigest()[:16]
        if hmac.compare_digest(token.lower(), expected_token.lower()):
            # Valid — record in replay cache so it can't be reused
            with _used_tokens_lock:
                # Double-check under lock (another thread may have used it)
                if cache_key in _used_tokens:
                    return False
                _used_tokens[cache_key] = current_ts
                _eviction_counter += 1
                if _eviction_counter >= _EVICTION_INTERVAL:
                    _evict_stale_tokens()
                    _eviction_counter = 0
            return True

    return False

