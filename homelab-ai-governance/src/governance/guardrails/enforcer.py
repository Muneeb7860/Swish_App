"""Guardrail enforcement engine — applies resolved rules to content at input/output phases."""

from __future__ import annotations

import hashlib
import logging
from typing import Any

from governance.audit import get_audit_logger
from governance.guardrails.detectors import (
    redact_matches,
    run_detector,
    strip_matched_segments,
)

logger = logging.getLogger(__name__)


def compute_input_hash(content: str) -> str:
    """Produce a short SHA-256 hash of the input for audit log correlation."""
    return hashlib.sha256(content.encode("utf-8")).hexdigest()[:12]


def apply_rules(
    rules: list[dict[str, Any]],
    phase: str,
    content: str,
    agent_id: str,
    input_hash: str,
) -> dict[str, Any]:
    """Run all rules matching the given phase ('input' or 'output') against content.

    Each detector call is wrapped in try/except — a crashing detector skips
    that rule (logged as detector_error) rather than disabling all guardrails.

    Returns:
        {
            "allowed": bool,
            "content": str,       # possibly redacted/stripped
            "triggered_rules": list[dict],
            "warnings": list[dict],
        }
    """
    audit = get_audit_logger()
    result: dict[str, Any] = {
        "allowed": True,
        "content": content,
        "triggered_rules": [],
        "warnings": [],
    }

    for rule in rules:
        if phase not in rule.get("applies_to", []):
            continue

        # Run the detector with crash isolation
        try:
            matched = run_detector(rule["detector"], result["content"])
        except Exception as e:
            audit.log_event(
                "detector_error",
                agent_id=agent_id,
                rule_id=rule["id"],
                error=str(e),
                input_hash=input_hash,
            )
            logger.warning(
                "Detector crash for rule '%s', agent '%s': %s — skipping rule",
                rule["id"],
                agent_id,
                e,
            )
            continue

        if not matched:
            # Non-match: increment in-memory counter (flushed hourly)
            audit.increment_counter(rule["id"], agent_id, phase)
            continue

        # Match: log per-event
        action = rule["default_action"]
        audit.log_event(
            "guardrail_trigger",
            agent_id=agent_id,
            rule_id=rule["id"],
            action=action,
            phase=phase,
            input_hash=input_hash,
            matched=True,
        )

        trigger = {
            "rule_id": rule["id"],
            "action": action,
            "severity": rule["severity"],
        }
        result["triggered_rules"].append(trigger)

        if action == "block":
            result["allowed"] = False
            return result  # Hard stop

        elif action == "redact":
            result["content"] = redact_matches(rule["detector"], result["content"])

        elif action == "strip":
            result["content"] = strip_matched_segments(
                rule["detector"], result["content"]
            )

        elif action == "warn":
            result["warnings"].append(trigger)
            # Content passes through with warning flag

        # "log" and "pass" — recorded but no content modification

    return result


def blocked_response(triggered_rules: list[dict[str, Any]]) -> dict[str, Any]:
    """Construct a blocked response payload.

    AUDIT FIX F4: Added 'warnings' key for schema contract consistency —
    all response dicts from the pipeline must include the same keys so
    consumers don't need defensive .get() for every field.
    """
    return {
        "status": "blocked",
        "message": "Request blocked by safety guardrails.",
        "triggered_rules": triggered_rules,
        "warnings": [],
    }


def attach_warnings(
    content: str, warnings: list[dict[str, Any]]
) -> dict[str, Any]:
    """Wrap content with warning metadata for the caller."""
    return {
        "content": content,
        "warnings": warnings,
    }
