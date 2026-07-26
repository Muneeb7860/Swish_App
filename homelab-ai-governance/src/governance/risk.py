"""Risk signals & conditional enforcement — GOVERNANCE_SPEC.md §3b.

Goal 1: security is never compromised — every `critical`/`high` severity rule
runs on every request, unconditionally.
Goal 2: latency is never spent by default — advisory rules (`medium`/`low`),
self-correction retries, and the eval loop are reserved for *elevated*
requests, identified by explicit risk signals.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

from governance.config import load_routing_config

# Intents whose subject matter always warrants full enforcement.
ELEVATED_INTENTS = frozenset({"sensitive_query", "system_admin"})

# HIGH-risk intents that are SHED with a fast 503 when the guardrail engine is
# degraded (GOVERNANCE_SPEC Phase 4; owner decision 2026-07-18: fail-fast rather
# than serve a sensitive action without full safety coverage). Same set as
# ELEVATED_INTENTS today — system_admin (admin overrides) and sensitive_query —
# kept as a distinct name so the shed policy can diverge from the enforcement
# tier later without touching every call site.
HIGH_RISK_INTENTS = ELEVATED_INTENTS


def is_high_risk(intent: str) -> bool:
    return intent in HIGH_RISK_INTENTS


# Prompt substrings marking a privileged / high-stakes directive (spend
# approvals, admin scripts, audit tampering) that the classifier may not tag as
# system_admin. These ELEVATE risk (full enforcement, §3b) and are SHED during
# guardrail degradation (Phase 4). Outright-destructive commands (DROP TABLE,
# rm -rf) are blocked earlier by shared_guardrails.destructive_command_filter.
_PRIVILEGED_DIRECTIVE_TERMS = (
    "system_admin",
    "root privileges",
    "wipe_audit",
    "procurement",
    # Authorization / limit / verification bypass on financial actions
    # (e.g. AL-05 refund-threshold bypass). Anchored on unambiguous bypass
    # phrasing so legitimate refund/payment queries are not over-elevated.
    "override refund",
    "without verification",
    "without approval",
    "without supervisor",
)


def is_privileged_directive(prompt: str | None) -> bool:
    """Fast (keyword, no-model) check for a privileged/high-stakes directive.

    Deliberately broad (bare "procurement" included) — used for the soft
    risk.elevated signal and Phase 4's conservative shed-when-degraded path,
    where over-flagging just means "run full guardrails" or "503, retry,"
    never a hard denial. Do NOT reuse this for a hard block/pause gate (see
    requires_hitl_stepup) — an ordinary "I need to place a procurement
    request for..." B2B query would trip it and get denied outright, which
    is exactly the false positive found via CQ-06 in the red-team suite."""
    if not prompt:
        return False
    p = prompt.lower()
    return any(term in p for term in _PRIVILEGED_DIRECTIVE_TERMS)


# Terms/patterns precise enough to justify a HARD stop (HITL pause/deny) --
# narrower than _PRIVILEGED_DIRECTIVE_TERMS above on purpose. Excludes the
# bare "procurement" term; adds a proximity-anchored pattern for the actual
# bypass-of-oversight phrasing procurement abuse looks like (e.g. AL-01's
# "...without requiring supervisor override" — "without supervisor" isn't an
# exact substring there since "requiring" breaks the adjacency, so a plain
# term list can't express it without either missing AL-01 or over-matching
# CQ-06 by keying off "procurement" alone).
_HARD_STOP_TERMS = (
    "system_admin",
    "root privileges",
    "wipe_audit",
    "override refund",
    "without verification",
    "without approval",
    "without supervisor",
)
_HARD_STOP_PATTERNS = (
    re.compile(
        r"\bprocurement\b.{0,60}\b(without|bypass(?:ing)?|skip(?:ping)?|no)\b.{0,20}"
        r"\b(approval|verification|supervisor|review|sign.?off|authoriz\w*)\b"
    ),
)


def requires_hitl_stepup(prompt: str | None) -> bool:
    """Fast (keyword/regex, no-model) check for directives that justify
    pausing for human step-up authorization -- a real bypass/override signal,
    not merely "mentions procurement" (see is_privileged_directive's broader,
    softer use for risk elevation / Phase 4 shed)."""
    if not prompt:
        return False
    p = prompt.lower()
    if any(term in p for term in _HARD_STOP_TERMS):
        return True
    return any(pattern.search(p) for pattern in _HARD_STOP_PATTERNS)


# Raw function/tool-call syntax with keyword arguments, e.g.
# `buy_units(symbol='TSLA', qty=50000)` or `aws_s3_delete_bucket(bucket_name=...)`.
# A legitimate end user does not type keyword-argument function calls into a
# chat/support endpoint; this shape is a vocabulary-INDEPENDENT signal of an
# attempted direct tool invocation. Unlike a keyword list (which must
# enumerate every synonym for "delete"/"grant"/"trade" and is defeated by the
# next one not on the list), this doesn't care what the function or resource
# is named — it flags the act of invoking a tool through chat input at all.
_TOOL_CALL_SYNTAX_RE = re.compile(r"\b[a-zA-Z_][a-zA-Z0-9_]*\s*\(\s*[a-zA-Z_][a-zA-Z0-9_]*\s*=")


def contains_tool_call_syntax(prompt: str | None) -> bool:
    """Fast (regex, no-model) structural check for embedded tool-call syntax."""
    if not prompt:
        return False
    return bool(_TOOL_CALL_SYNTAX_RE.search(prompt))

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
    prompt: str | None = None,
    has_private_data: bool = False,
    has_untrusted_content: bool = True,
    has_external_comms: bool = False,
) -> RiskAssessment:
    """Compute the request's risk signals (GOVERNANCE_SPEC.md §3b).

    Any single signal elevates the request to full enforcement.
    Meta's Agents Rule of Two (Oct 2025): If an agent possesses all 3 properties
    (private data + untrusted content + external comms), the Lethal Trifecta is satisfied,
    forcing elevated risk enforcement and mandatory HITL approval.
    """
    signals: list[str] = []
    if contains_pii:
        signals.append("pii_detected")
    if intent in ELEVATED_INTENTS:
        signals.append(f"elevated_intent:{intent}")
    if _is_cloud_agent(agent_id):
        signals.append(f"cloud_route:{agent_id}")
    if is_privileged_directive(prompt):
        signals.append("admin_or_privileged_directive")
    if contains_tool_call_syntax(prompt):
        signals.append("raw_tool_call_syntax")

    # Meta's Agents Rule of Two — Lethal Trifecta evaluation
    if has_private_data and has_untrusted_content and has_external_comms:
        signals.append("rule_of_two_lethal_trifecta")

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
