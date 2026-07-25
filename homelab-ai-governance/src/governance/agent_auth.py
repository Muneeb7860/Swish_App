"""Inter-agent HMAC identity & audit-proof signing (ASI07 — agentic-redteam
crypto_probes).

Two independent mechanisms, staged rollout (GOVERNANCE_SPEC §5):

1. Audit-proof headers (X-SwishOS-Audit-Proof/-Timestamp/-Nonce) — signed on
   every BLOCKED response, always on, additive-only (extra response headers
   can never break an existing caller). Proves a "blocked" response is
   genuine, not a hallucinated/fake error a compromised or misbehaving layer
   fabricated to look like a block.

2. Inter-agent request signing (X-Agent-ID/-Timestamp/-Nonce/-Signature) —
   verifies the CALLER's identity via a pre-shared secret. Gated behind
   GOVERNANCE_REQUIRE_AGENT_SIGNATURE (default OFF) because making this
   mandatory means EVERY caller (Java PythonGovernanceAdapter, the red-team
   harness's other 10 categories, promptfoo) must sign first — flipped on
   only after every caller does. See docs/GOVERNANCE_SPEC.md §5 rollout plan.

Algorithm mirrors agentic-redteam/agentic_redteam/crypto.py &
telemetry_verifier.py byte-for-byte (same canonical string, same HMAC-SHA256,
same constant) — the client and server implementations must never drift
independently; if you change one, change both and re-verify crypto_probes.
"""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import secrets
import threading
import time
from typing import Any

# Must match agentic_redteam/telemetry_verifier.py AUDIT_PROOF_SECRET exactly.
AUDIT_PROOF_SECRET = "swishos-audit-proof-signature-key-v4"

_MAX_CLOCK_SKEW_SECONDS = 300  # 5 minutes — matches agentic_redteam/crypto.py

# In-memory replay-nonce cache (per-process; fine for a single-instance
# governance service — matches the red-team client's own in-memory model).
_replay_nonce_cache: set[str] = set()
_replay_lock = threading.Lock()


# ── 1. Audit-proof headers (always on, additive) ─────────────────────────────


def sign_audit_proof(rule_triggered: str, client_ip: str = "127.0.0.1") -> dict[str, str]:
    """Sign a blocked-response audit proof. Returns headers to attach."""
    ts = str(int(time.time()))
    nonce = secrets.token_hex(16)
    string_to_sign = f"{rule_triggered}:{client_ip}:{ts}:{nonce}"
    sig = hmac.new(
        AUDIT_PROOF_SECRET.encode("utf-8"), string_to_sign.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    return {
        "X-SwishOS-Audit-Proof": sig,
        "X-SwishOS-Audit-Timestamp": ts,
        "X-SwishOS-Audit-Nonce": nonce,
    }


# ── 2. Inter-agent request signing (staged, flagged) ─────────────────────────


def agent_signature_required() -> bool:
    return os.environ.get("GOVERNANCE_REQUIRE_AGENT_SIGNATURE", "").lower() in ("1", "true")


def _shared_secret() -> str | None:
    return os.environ.get("SWISH_AGENT_SHARED_SECRET")


def verify_agent_signature(
    headers: dict[str, str], payload: dict[str, Any]
) -> tuple[bool, str]:
    """Verify inbound X-Agent-* headers against SWISH_AGENT_SHARED_SECRET.

    Mirrors agentic_redteam/crypto.py verify_payload_signature() exactly
    (same canonical string, same HMAC-SHA256), so any client using that
    module's sign_payload() with the correct shared secret verifies here.
    """
    secret = _shared_secret()
    if not secret:
        # Enforcement requested but no secret configured — a deploy/config
        # bug, not a caller's fault. Fail closed (goal 1): reject rather than
        # silently skip verification.
        return False, "Agent signature enforcement is enabled but no shared secret is configured."

    h = {k.lower(): v for k, v in headers.items()}
    agent_id = h.get("x-agent-id")
    ts_str = h.get("x-agent-timestamp")
    nonce = h.get("x-agent-nonce")
    sig = h.get("x-agent-signature")

    if not all([agent_id, ts_str, nonce, sig]):
        return False, "Missing required cryptographic identity headers (ASI07)."

    try:
        ts = int(ts_str)
    except ValueError:
        return False, "Invalid timestamp format."

    if abs(int(time.time()) - ts) > _MAX_CLOCK_SKEW_SECONDS:
        return False, f"Clock skew too large ({abs(int(time.time()) - ts)}s)."

    nonce_key = f"{agent_id}:{nonce}"
    with _replay_lock:
        if nonce_key in _replay_nonce_cache:
            return False, "Replay attack detected: Nonce already used."

    canonical_body = json.dumps(payload, sort_keys=True, separators=(",", ":"))
    string_to_sign = f"{agent_id}:{ts_str}:{nonce}:{canonical_body}"
    expected_sig = hmac.new(
        secret.encode("utf-8"), string_to_sign.encode("utf-8"), hashlib.sha256
    ).hexdigest()

    if not hmac.compare_digest(sig, expected_sig):
        return False, "Cryptographic signature mismatch: Unauthorized or spoofed agent payload."

    with _replay_lock:
        _replay_nonce_cache.add(nonce_key)
    return True, "Valid Signature"
