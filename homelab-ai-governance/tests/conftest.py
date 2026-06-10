"""Pytest conftest configuration and shared fixtures."""

from __future__ import annotations

import os
from pathlib import Path

import pytest


@pytest.fixture(autouse=True)
def setup_test_env():
    """Ensure test environment variables are set."""
    os.environ["GROQ_API_KEY"] = "mock-groq-api-key"
    os.environ["OPENAI_API_KEY"] = "mock-openai-api-key"
    yield
    # Cleanup if needed
