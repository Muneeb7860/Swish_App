"""Risk signals & conditional enforcement — GOVERNANCE_SPEC.md §3b.

Goal 1: security is never compromised — every `critical`/`high` severity rule
runs on every request, unconditionally.
Goal 2: latency is never spent by default — advisory rules (`medium`/`low`),
self-correction retries, and the eval loop are reserved for *elevated*
requests, identified by explicit risk signals.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from governance.config import load_routing_config

# Intents whose subject matter always warrants full enforcement.
ELEVATED_INTENTS = frozenset({"sensitive_query", "system_admin"})

# Agent backends where the response leaves this machine.
CLOUD_BACKENDS = frozenset({"openai", "openai_compatible"})

# Severities that must NEVER be skipped (goal 1). Everything in the shared
# rule set that can block or redact meaningfully lives here: prompt injection
# (critical), hate speech (critical), catastrophic code (critical),
# PII/secrets (high), cautionary code (high).
ALWAYS_ON_SEVERITIES = frozenset({"critical", "high"})


@dataclass(frozen=True)
class RiskAssessment:
    elevated: bool
    signals: tuple[str, ...]


def _is_cloud_agent(agent_id: str) -> bool:
    agents = load_routing_config().get("agents", {})
    backend = agents.get(agent_id, {}).get("backend", "")
    return backend in CLOUD_BACKENDS


def assess_risk(
    *,
    contains_pii: bool,
    intent: str,
    agent_id: str,
) -> RiskAssessment:
    """Compute the request's risk signals (GOVERNANCE_SPEC.md §3b).

    Any single signal elevates the request to full enforcement.
    """
    signals: list[str] = []
    if contains_pii:
        signals.append("pii_detected")
    if intent in ELEVATED_INTENTS:
        signals.append(f"elevated_intent:{intent}")
    if _is_cloud_agent(agent_id):
        signals.append(f"cloud_route:{agent_id}")
    return RiskAssessment(elevated=bool(signals), signals=tuple(signals))


def select_output_rules(
    rules: list[dict[str, Any]], elevated: bool
) -> list[dict[str, Any]]:
    """Choose the G3 output detector set for this request.

    Elevated → the full suite. Normal → every always-on rule (critical/high);
    only advisory medium/low rules (license fingerprint, profanity, markdown
    cosmetics) are skipped. A rule with an unknown/missing severity is treated
    as always-on — unknown must fail toward more enforcement, never less.
    """
    if elevated:
        return rules
    return [
        r
        for r in rules
        if r.get("severity", "critical") in ALWAYS_ON_SEVERITIES
    ]


def max_retries_for(elevated: bool) -> int:
    """Self-correction retry cap: elevated requests may burn model calls on
    quality; normal requests get at most one correction pass."""
    return 3 if elevated else 1
