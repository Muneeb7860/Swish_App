"""Guardrail loader — merges base rules with per-agent overrides, enforcing severity constraints."""

from __future__ import annotations

from typing import Any

from governance.config import ConfigError, load_agent_config, load_shared_guardrails

# Valid keys that may appear in an agent override block.
VALID_OVERRIDE_KEYS = {"enabled", "default_action"}

# All valid enforcement actions across the system.
VALID_ACTIONS = {"block", "redact", "warn", "log", "pass", "strip"}


def load_guardrails(agent_id: str) -> list[dict[str, Any]]:
    """Load shared base rules, apply per-agent overrides with strict validation.

    Validates:
    - Override key names (catches typos like 'defualt_action')
    - Severity-based disable constraints (critical rules cannot be disabled)
    - Severity-based action constraints (critical rules can only be 'block')

    Returns the resolved list of enabled rules for the given agent.
    """
    config = load_shared_guardrails()
    base_rules: list[dict[str, Any]] = config["rules"]
    override_policy: dict[str, Any] = config["override_policy"]

    try:
        agent_cfg = load_agent_config(agent_id)
    except ConfigError:
        # No agent-specific config — return base rules as-is
        return [dict(rule) for rule in base_rules if rule.get("enabled", True)]

    overrides: dict[str, Any] = agent_cfg.get("overrides", {})
    resolved: list[dict[str, Any]] = []

    for rule in base_rules:
        cfg = dict(rule)
        rule_id = rule["id"]

        if rule_id in overrides:
            agent_override = overrides[rule_id]

            # 1. Validate override keys — catch typos
            invalid_keys = set(agent_override.keys()) - VALID_OVERRIDE_KEYS
            if invalid_keys:
                raise ConfigError(
                    f"Agent '{agent_id}', rule '{rule_id}': "
                    f"invalid override keys: {invalid_keys}. "
                    f"Valid keys: {VALID_OVERRIDE_KEYS}"
                )

            severity = rule["severity"]
            policy = override_policy.get(severity, {})

            # 2. Enforce disable constraints
            if not agent_override.get("enabled", True) and not policy.get(
                "allow_disable", False
            ):
                raise ConfigError(
                    f"Agent '{agent_id}' cannot disable {severity}-severity "
                    f"rule '{rule_id}'"
                )

            # 3. Enforce action constraints
            if "default_action" in agent_override:
                action = agent_override["default_action"]
                allowed = policy.get("allowed_actions", [])
                if action not in allowed:
                    raise ConfigError(
                        f"Agent '{agent_id}', rule '{rule_id}': "
                        f"action '{action}' not allowed for {severity} severity. "
                        f"Allowed: {allowed}"
                    )

            cfg.update(agent_override)

        if "enabled" not in cfg:
            cfg["enabled"] = True

        if cfg["enabled"]:
            resolved.append(cfg)

    return resolved
