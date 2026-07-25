"""Conditional enforcement & concurrency guards — GOVERNANCE_SPEC.md §3b / Phase 3b.

Covers both product goals:
- goal 1: elevated requests always get full enforcement; critical/high rules
  can never be skipped for anyone.
- goal 2: normal requests never pay for the eval loop, extra retries, or
  advisory detectors; different models never queue behind each other.
"""

from __future__ import annotations

import threading

import pytest

from governance.agents.base import AgentResponse
from governance.concurrency import get_model_semaphore
from governance.evaluator.loop import LoopResult
from governance.pipeline import execute_pipeline
from governance.risk import (
    ALWAYS_ON_SEVERITIES,
    assess_risk,
    max_retries_for,
    select_output_rules,
)
from governance.router.classifier import ClassificationResult


# ── assess_risk ──────────────────────────────────────────────────────────────


def test_no_signals_is_normal():
    r = assess_risk(
        contains_pii=False, intent="general_knowledge", agent_id="gemma_reasoner"
    )
    assert r.elevated is False
    assert r.signals == ()


@pytest.mark.parametrize(
    "kwargs,expected_signal",
    [
        (
            {"contains_pii": True, "intent": "general_knowledge", "agent_id": "gemma_reasoner"},
            "pii_detected",
        ),
        (
            {"contains_pii": False, "intent": "sensitive_query", "agent_id": "gemma_reasoner"},
            "elevated_intent:sensitive_query",
        ),
        (
            {"contains_pii": False, "intent": "system_admin", "agent_id": "gemma_reasoner"},
            "elevated_intent:system_admin",
        ),
        (
            {"contains_pii": False, "intent": "general_knowledge", "agent_id": "cloud_frontier"},
            "cloud_route:cloud_frontier",
        ),
    ],
)
def test_single_signal_elevates(kwargs, expected_signal):
    r = assess_risk(**kwargs)
    assert r.elevated is True
    assert expected_signal in r.signals


def test_rule_of_two_lethal_trifecta():
    """Meta's Agents Rule of Two: Private Data + Untrusted Content + External Comms = Lethal Trifecta."""
    r = assess_risk(
        contains_pii=False,
        intent="general_knowledge",
        agent_id="gemma_reasoner",
        has_private_data=True,
        has_untrusted_content=True,
        has_external_comms=True,
    )
    assert r.elevated is True
    assert "rule_of_two_lethal_trifecta" in r.signals


def test_rule_of_two_partial_does_not_trigger():
    """If missing any leg of the trifecta, rule_of_two_lethal_trifecta is not signaled."""
    r = assess_risk(
        contains_pii=False,
        intent="general_knowledge",
        agent_id="gemma_reasoner",
        has_private_data=True,
        has_untrusted_content=True,
        has_external_comms=False,
    )
    assert r.elevated is False
    assert "rule_of_two_lethal_trifecta" not in r.signals



# ── select_output_rules: the goal-1 invariant ────────────────────────────────

_RULESET = [
    {"id": "hate", "severity": "critical"},
    {"id": "pii", "severity": "high"},
    {"id": "license", "severity": "medium"},
    {"id": "markdown", "severity": "low"},
    {"id": "mystery"},  # no severity declared
]


def test_elevated_gets_the_full_suite():
    assert select_output_rules(_RULESET, elevated=True) == _RULESET


def test_normal_keeps_every_critical_and_high_rule():
    """Goal 1: security rules never come off — for any request."""
    kept = {r["id"] for r in select_output_rules(_RULESET, elevated=False)}
    assert {"hate", "pii"} <= kept


def test_normal_drops_only_advisory_rules():
    kept = {r["id"] for r in select_output_rules(_RULESET, elevated=False)}
    assert "license" not in kept
    assert "markdown" not in kept


def test_unknown_severity_fails_toward_enforcement():
    kept = {r["id"] for r in select_output_rules(_RULESET, elevated=False)}
    assert "mystery" in kept


def test_retry_caps():
    assert max_retries_for(True) == 3
    assert max_retries_for(False) == 1
    assert ALWAYS_ON_SEVERITIES == {"critical", "high"}


# ── Pipeline wiring ──────────────────────────────────────────────────────────


class _OneShotAgent:
    def __init__(self, agent_id: str, text: str = "A clean helpful answer."):
        self.agent_id = agent_id
        self._text = text

    def generate(self, prompt: str) -> AgentResponse:
        return AgentResponse(
            text=self._text,
            model="mock-model",
            agent_id=self.agent_id,
            input_tokens=10,
            output_tokens=10,
            latency_ms=1.0,
        )

    def is_available(self) -> bool:
        return True


class _LoopSpy:
    def __init__(self):
        self.calls: list[dict] = []

    def __call__(self, **kwargs):
        self.calls.append(kwargs)
        return LoopResult(
            final_response=kwargs["candidate"],
            scores=None,
            attempts=1,
            passed=True,
            fallback_used=False,
        )


def _wire(monkeypatch, intent: str, complexity: str = "low"):
    monkeypatch.setattr(
        "governance.pipeline.classify_intent",
        lambda q: ClassificationResult(
            intent=intent, complexity=complexity, confidence=0.99, method="model"
        ),
    )
    monkeypatch.setattr(
        "governance.pipeline.get_agent", lambda agent_id: _OneShotAgent(agent_id)
    )
    spy = _LoopSpy()
    monkeypatch.setattr(
        "governance.pipeline.run_self_correction_loop",
        lambda **kwargs: spy(**kwargs),
    )
    return spy


def test_normal_request_skips_eval_loop(monkeypatch):
    """Goal 2: a plain request never invokes quality-eval model compute."""
    spy = _wire(monkeypatch, "general_knowledge")
    res = execute_pipeline("What is the capital of Switzerland?")
    assert res["status"] == "success"
    assert res["risk"]["elevated"] is False
    assert spy.calls == []
    assert res["loop_result"]["attempts"] == 1


def test_elevated_intent_runs_eval_loop_with_full_retries(monkeypatch):
    """Goal 1: sensitive requests always get the eval loop at full retry cap."""
    spy = _wire(monkeypatch, "sensitive_query")
    res = execute_pipeline("How do I rotate the production credentials?")
    assert res["status"] == "success"
    assert res["risk"]["elevated"] is True
    assert "elevated_intent:sensitive_query" in res["risk"]["signals"]
    assert len(spy.calls) == 1
    assert spy.calls[0]["max_retries_override"] == 3


def test_pii_elevates_even_on_local_route(monkeypatch):
    spy = _wire(monkeypatch, "general_knowledge")
    res = execute_pipeline("Email jane.doe@example.com the report")
    assert res["risk"]["elevated"] is True
    assert "pii_detected" in res["risk"]["signals"]
    assert len(spy.calls) == 1


def test_cloud_route_elevates(monkeypatch):
    spy = _wire(monkeypatch, "general_knowledge", complexity="high")
    res = execute_pipeline("Compare four distributed consensus protocols in depth")
    assert res["risk"]["elevated"] is True
    assert "cloud_route:cloud_frontier" in res["risk"]["signals"]
    assert len(spy.calls) == 1


def test_expected_format_runs_loop_capped_at_one_retry(monkeypatch):
    """A caller-requested format check runs the loop, but at the normal cap."""
    spy = _wire(monkeypatch, "general_knowledge")
    res = execute_pipeline("Give me stats", expected_format="json")
    assert res["risk"]["elevated"] is False
    assert len(spy.calls) == 1
    assert spy.calls[0]["max_retries_override"] == 1


# ── Concurrency guards ───────────────────────────────────────────────────────


def test_same_model_gets_same_semaphore():
    assert get_model_semaphore("qwen2.5:3b") is get_model_semaphore("qwen2.5:3b")


def test_different_models_do_not_share_a_gate():
    """Goal 2: one model's in-flight generation must not queue another model."""
    sem_a = get_model_semaphore("model-a-test")
    sem_b = get_model_semaphore("model-b-test")
    assert sem_a is not sem_b

    with sem_a:  # model A busy
        # model B must be acquirable immediately
        acquired = sem_b.acquire(timeout=0.1)
        assert acquired
        sem_b.release()


def test_same_model_serializes():
    sem = get_model_semaphore("model-serial-test")
    with sem:
        assert sem.acquire(timeout=0.05) is False  # second caller must wait


def test_mock_fallback_is_opt_in(monkeypatch):
    """Goal 1 honesty: without the env opt-in, an unreachable model raises
    instead of fabricating a 'governed' response."""
    from governance.agents.ollama_agent import OllamaAgent

    agent = OllamaAgent(
        agent_id="gemma_reasoner",
        model="nonexistent-model-test",
        ollama_url="http://localhost:1",  # guaranteed refused
        timeout_ms=200,
    )

    monkeypatch.delenv("GOVERNANCE_ALLOW_MOCK_FALLBACK", raising=False)
    with pytest.raises(Exception):
        agent.generate("hello")

    monkeypatch.setenv("GOVERNANCE_ALLOW_MOCK_FALLBACK", "1")
    res = agent.generate("hello")
    assert res.metadata.get("mocked") is True


def test_semaphore_registry_is_thread_safe():
    results = []

    def grab():
        results.append(get_model_semaphore("model-race-test"))

    threads = [threading.Thread(target=grab) for _ in range(8)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert all(s is results[0] for s in results)
