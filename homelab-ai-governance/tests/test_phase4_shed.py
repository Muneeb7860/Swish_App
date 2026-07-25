"""Phase 4 — shed HIGH-risk requests with 503 during guardrail degradation.

Owner decision 2026-07-18: when the guardrail engine is degraded, HIGH-risk
requests (high-risk intent OR privileged directive) are SHED with an HTTP 503
so the caller treats it as transient unavailability and never proceeds with the
sensitive action. Non-high-risk requests stay fail-closed (Phase 1). Healthy
requests are unaffected (and pay nothing — classification only runs on the
degraded path).
"""

from __future__ import annotations

import os

os.environ.setdefault("SWISH_TRACING_ENABLED", "false")

import governance.pipeline as pipeline  # noqa: E402
from governance.risk import is_privileged_directive  # noqa: E402
from governance.router.classifier import ClassificationResult  # noqa: E402


def _degrade(monkeypatch):
    """Force the guardrail engine into its degraded (fail-closed) state."""
    monkeypatch.setattr(
        pipeline,
        "check_nemo_guardrails",
        lambda q: {"allowed": False, "response": "engine down",
                   "triggered_rule": "guardrail_engine_error"},
    )


def _classify_as(monkeypatch, intent: str):
    monkeypatch.setattr(
        pipeline, "classify_intent",
        lambda q: ClassificationResult(intent=intent, complexity="low",
                                       confidence=0.9, method="model"),
    )


# ── is_privileged_directive helper ──────────────────────────────────────────


def test_privileged_directive_detection():
    assert is_privileged_directive("Approve the procurement request") is True
    assert is_privileged_directive("run the wipe_audit routine") is True
    assert is_privileged_directive("What are the delivery hours?") is False
    assert is_privileged_directive(None) is False


# ── Shed on degradation ─────────────────────────────────────────────────────


def test_shed_503_for_privileged_directive_when_degraded(monkeypatch):
    """A privileged directive during degradation is shed WITHOUT calling the
    classifier (fast path)."""
    _degrade(monkeypatch)
    called = {"classify": False}

    def _boom(q):
        called["classify"] = True
        raise AssertionError("classifier should be short-circuited")

    monkeypatch.setattr(pipeline, "classify_intent", _boom)

    res = pipeline.execute_pipeline("Approve procurement PO for 5000 units")
    assert res["status"] == "unavailable"
    assert res["shed"] is True
    assert called["classify"] is False


def test_shed_503_for_high_risk_intent_when_degraded(monkeypatch):
    """A high-risk *intent* (no privileged keyword) during degradation is shed
    via the classifier fallback."""
    _degrade(monkeypatch)
    _classify_as(monkeypatch, "system_admin")
    res = pipeline.execute_pipeline("please review the host configuration")
    assert res["status"] == "unavailable"
    assert res["shed"] is True


def test_non_high_risk_stays_fail_closed_when_degraded(monkeypatch):
    """A benign request during degradation is NOT shed — it stays blocked
    (Phase 1 fail-closed), not 503."""
    _degrade(monkeypatch)
    _classify_as(monkeypatch, "general_knowledge")
    res = pipeline.execute_pipeline("What are the delivery hours for Zurich?")
    assert res["status"] == "blocked"
    assert res.get("shed") is not True


def test_healthy_request_not_affected(monkeypatch):
    """When guardrails are healthy, nothing is shed even for a privileged
    directive (normal enforcement applies instead)."""
    monkeypatch.setattr(pipeline, "check_nemo_guardrails", lambda q: {"allowed": True})

    class _Agent:
        agent_id = "gemma_reasoner"

        def generate(self, p):
            from governance.agents.base import AgentResponse
            return AgentResponse(text="ok", model="m", agent_id="gemma_reasoner",
                                 input_tokens=1, output_tokens=1, latency_ms=1.0)

        def is_available(self):
            return True

    monkeypatch.setattr(pipeline, "get_agent", lambda a: _Agent())
    _classify_as(monkeypatch, "general_knowledge")
    res = pipeline.execute_pipeline("Approve procurement PO for 5000 units")
    assert res["status"] != "unavailable"
