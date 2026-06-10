"""Configuration loading, validation, and caching for the governance system."""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from typing import Any

import yaml


class ConfigError(Exception):
    """Raised when configuration is invalid or cannot be loaded."""


# Resolve config root relative to this package
_PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
_CONFIG_DIR = _PROJECT_ROOT / "config"
_DETECTORS_DIR = _PROJECT_ROOT / "detectors"


def get_config_dir() -> Path:
    """Return the resolved path to the config/ directory."""
    return _CONFIG_DIR


def get_detectors_dir() -> Path:
    """Return the resolved path to the detectors/ directory."""
    return _DETECTORS_DIR


@lru_cache(maxsize=32)
def load_yaml(path: str | Path) -> dict[str, Any]:
    """Load and cache a YAML file. Raises ConfigError on failure."""
    resolved = Path(path)
    if not resolved.is_absolute():
        resolved = _CONFIG_DIR / resolved

    if not resolved.exists():
        raise ConfigError(f"Config file not found: {resolved}")

    try:
        with open(resolved, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
    except yaml.YAMLError as e:
        raise ConfigError(f"YAML parse error in {resolved}: {e}") from e

    if not isinstance(data, dict):
        raise ConfigError(f"Expected a YAML mapping in {resolved}, got {type(data).__name__}")

    return data


def load_routing_config() -> dict[str, Any]:
    """Load the routing configuration."""
    return load_yaml("routing_config.yaml")


def load_audit_config() -> dict[str, Any]:
    """Load the audit log configuration."""
    return load_yaml("audit_log_config.yaml")


def load_shared_guardrails() -> dict[str, Any]:
    """Load the shared guardrails base policy library."""
    return load_yaml("shared_guardrails.yaml")


def load_agent_config(agent_id: str) -> dict[str, Any]:
    """Load a per-agent override config file."""
    return load_yaml(f"agents/{agent_id}.yaml")


def load_terms(source: str) -> list[str]:
    """Load a newline-delimited term list from the detectors directory.

    Returns an empty list if the file does not exist (graceful degradation).
    """
    resolved = Path(source)
    if not resolved.is_absolute():
        resolved = _DETECTORS_DIR / resolved.name

    if not resolved.exists():
        return []

    with open(resolved, "r", encoding="utf-8") as f:
        return [line.strip() for line in f if line.strip() and not line.startswith("#")]


def get_env_or_raise(var_name: str) -> str:
    """Get an environment variable or raise ConfigError."""
    value = os.environ.get(var_name)
    if not value:
        raise ConfigError(f"Required environment variable '{var_name}' is not set")
    return value


def validate_all_configs() -> list[str]:
    """Boot-time validation: load all configs and return a list of errors (empty = OK)."""
    errors: list[str] = []

    # Validate shared guardrails
    try:
        config = load_shared_guardrails()
        if "rules" not in config:
            errors.append("shared_guardrails.yaml: missing 'rules' key")
        if "override_policy" not in config:
            errors.append("shared_guardrails.yaml: missing 'override_policy' key")
    except ConfigError as e:
        errors.append(str(e))

    # Validate routing config
    try:
        routing = load_routing_config()
        for key in ("classifier", "agents", "budget", "evaluation_rules"):
            if key not in routing:
                errors.append(f"routing_config.yaml: missing '{key}' key")
    except ConfigError as e:
        errors.append(str(e))

    # Validate audit config
    try:
        load_audit_config()
    except ConfigError as e:
        errors.append(str(e))

    # Validate agent configs
    try:
        routing = load_routing_config()
        for agent_id in routing.get("agents", {}):
            try:
                load_agent_config(agent_id)
            except ConfigError as e:
                errors.append(str(e))
    except ConfigError:
        pass  # Already captured above

    return errors
