"""Cryptographic Human-in-the-Loop (HITL) token generation and verification.

GOVERNANCE_SPEC.md §3c: Step-up authorization requires constant-time HMAC-SHA256
token validation with a 5-minute replay window to prevent unauthorized token forging.
"""

from __future__ import annotations

import hashlib
import hmac
import os
import time

_HITL_SECRET = os.getenv("SWISHOS_HITL_SECRET", "swishos_hitl_secret_default_key_2026").encode("utf-8")
_TOKEN_TTL_SECONDS = 300  # 5 minute approval window


def generate_hitl_token(input_hash: str, timestamp: int | None = None) -> tuple[str, int]:
    """Generate a constant-time HMAC-SHA256 step-up token for a given input_hash."""
    ts = timestamp if timestamp is not None else int(time.time())
    nonce = f"{input_hash}:{ts}".encode("utf-8")
    token = hmac.new(_HITL_SECRET, nonce, hashlib.sha256).hexdigest()[:16]
    return token, ts


def verify_hitl_token(input_hash: str, token: str, max_age_seconds: int = _TOKEN_TTL_SECONDS) -> bool:
    """Verify an approval token against input_hash within the valid time-to-live window."""
    if not token or len(token) != 16:
        return False

    current_ts = int(time.time())
    # Search backwards through the TTL window for a valid matching HMAC signature
    for age in range(max_age_seconds + 1):
        test_ts = current_ts - age
        expected_token, _ = generate_hitl_token(input_hash, test_ts)
        if hmac.compare_digest(token.lower(), expected_token.lower()):
            return True

    return False
