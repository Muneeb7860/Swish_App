"""Tests for the guardrails loader."""

from __future__ import annotations

import pytest

from governance.config import ConfigError
from governance.guardrails.loader import load_guardrails


def test_load_guardrails_default_agent():
    """Verify that default agent overrides are loaded and validated correctly."""
    rules = load_guardrails("gemma_reasoner")
    assert isinstance(rules, list)
    assert len(rules) > 0

    # Look for a specific rule like hate_speech_filter and verify it is enabled
    hate_speech_rule = next((r for r in rules if r["id"] == "hate_speech_filter"), None)
    assert hate_speech_rule is not None
    assert hate_speech_rule["enabled"] is True


def test_load_guardrails_nonexistent_agent():
    """Verify that a nonexistent agent resolves to base guardrails without errors."""
    rules = load_guardrails("nonexistent_agent_id_xyz")
    assert isinstance(rules, list)
    assert len(rules) > 0


def test_override_policy_constraints(monkeypatch):
    """Test that violating the override policies raises a ConfigError."""
    # Mock load_agent_config to return an invalid override: disabling a critical rule
    monkeypatch.setattr(
        "governance.guardrails.loader.load_agent_config",
        lambda agent_id: {
            "overrides": {
                "hate_speech_filter": {
                    "enabled": False  # Crucial rule with allow_disable: false
                }
            }
        },
    )
    with pytest.raises(ConfigError) as exc_info:
        load_guardrails("gemma_reasoner")
    assert "cannot disable critical-severity rule" in str(exc_info.value)


def test_override_policy_action_constraints(monkeypatch):
    """Test that violating allowed actions policy raises ConfigError."""
    # Mock load_agent_config to override critical rule action to something not allowed (e.g. warn)
    monkeypatch.setattr(
        "governance.guardrails.loader.load_agent_config",
        lambda agent_id: {
            "overrides": {
                "hate_speech_filter": {
                    "default_action": "warn"  # Critical rule allows only [block]
                }
            }
        },
    )
    with pytest.raises(ConfigError) as exc_info:
        load_guardrails("gemma_reasoner")
    assert "not allowed for critical severity" in str(exc_info.value)


def test_invalid_override_keys(monkeypatch):
    """Verify that using invalid override keys raises ConfigError."""
    monkeypatch.setattr(
        "governance.guardrails.loader.load_agent_config",
        lambda agent_id: {
            "overrides": {
                "hate_speech_filter": {
                    "defualt_action": "block"  # Typo in key name
                }
            }
        },
    )
    with pytest.raises(ConfigError) as exc_info:
        load_guardrails("gemma_reasoner")
    assert "invalid override keys" in str(exc_info.value)
