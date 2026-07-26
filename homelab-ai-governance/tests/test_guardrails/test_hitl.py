"""Unit tests for Cryptographic Human-in-the-Loop (HITL) token verification module."""

from __future__ import annotations

import time
import pytest
from governance.hitl import generate_hitl_token, verify_hitl_token


def test_hitl_token_valid():
    input_hash = "abc123hash"
    token, ts = generate_hitl_token(input_hash)
    assert len(token) == 16
    assert verify_hitl_token(input_hash, token) is True


def test_hitl_token_invalid_forged():
    input_hash = "abc123hash"
    _, _ = generate_hitl_token(input_hash)
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
