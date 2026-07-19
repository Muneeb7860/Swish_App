"""Pytest conftest configuration and shared fixtures."""

from __future__ import annotations

import os

import pytest

# Must be set before any test module imports app code that initializes the
# OTel TracerProvider (fixtures run too late). Without this, spans try to
# export to the otel-collector container on localhost:4318; when it's not
# running, each export blocks on retry backoff before timing out, turning a
# <5s suite into 13+ minutes.
os.environ["OTEL_SDK_DISABLED"] = "true"

# Tests may run without a live Ollama; the deterministic mock fallback is
# opt-in (production fails honestly instead — GOVERNANCE_SPEC.md §3).
os.environ["GOVERNANCE_ALLOW_MOCK_FALLBACK"] = "1"


@pytest.fixture(autouse=True)
def setup_test_env():
    """Ensure test environment variables are set."""
    os.environ["GROQ_API_KEY"] = "mock-groq-api-key"
    os.environ["OPENAI_API_KEY"] = "mock-openai-api-key"
    yield
    # Cleanup if needed
