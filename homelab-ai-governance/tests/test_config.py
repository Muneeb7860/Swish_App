"""Tests for the config loading and validation module."""

from __future__ import annotations

import pytest

from governance.config import (
    ConfigError,
    get_config_dir,
    get_detectors_dir,
    get_env_or_raise,
    load_agent_config,
    load_audit_config,
    load_routing_config,
    load_shared_guardrails,
    load_terms,
    validate_all_configs,
)


def test_paths():
    """Verify that config and detectors directories are resolved properly."""
    assert get_config_dir().exists()
    assert get_config_dir().is_dir()
    assert get_detectors_dir().exists()
    assert get_detectors_dir().is_dir()


def test_load_routing_config():
    """Test loading the routing configuration."""
    config = load_routing_config()
    assert isinstance(config, dict)
    assert "classifier" in config
    assert "agents" in config


def test_load_audit_config():
    """Test loading the audit log configuration."""
    config = load_audit_config()
    assert isinstance(config, dict)
    assert "paths" in config


def test_load_shared_guardrails():
    """Test loading the shared guardrails base policy library."""
    config = load_shared_guardrails()
    assert isinstance(config, dict)
    assert "rules" in config
    assert "override_policy" in config


def test_load_agent_config():
    """Test loading the agent configs."""
    gemma = load_agent_config("gemma_reasoner")
    assert isinstance(gemma, dict)
    assert gemma.get("agent_id") == "gemma_reasoner"


def test_load_terms():
    """Test loading terms files."""
    terms = load_terms("profanity_terms.txt")
    assert isinstance(terms, list)
    # Check fallback / non-existent file path
    non_existent = load_terms("does-not-exist.txt")
    assert non_existent == []


def test_get_env_or_raise():
    """Test environment variable retrieval."""
    assert get_env_or_raise("GROQ_API_KEY") == "mock-groq-api-key"
    with pytest.raises(ConfigError):
        get_env_or_raise("NON_EXISTENT_VAR_XYZ")


def test_validate_all_configs():
    """Verify that boot-time validation returns no errors for the default workspace config."""
    errors = validate_all_configs()
    assert isinstance(errors, list)
    assert len(errors) == 0, f"Config validation errors found: {errors}"
