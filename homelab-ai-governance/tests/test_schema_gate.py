"""RAIL schema gate — regression tests for two bugs found in PR #148 review:

1. Internal schema-error text leaking into the user-facing response when a
   named schema (e.g. "customer_support") failed validation on attempt 0.
2. The gate never re-validating after a self-correction attempt, so a
   corrected-but-still-invalid output could ship as status="success".

Also covers the pre-existing "json" flag, which is NOT a RAIL schema name
(guardrails.schemas.is_rail_schema must reject it) — it selects a different,
older format-integrity check in evaluator/metrics.py.
"""

from __future__ import annotations

from governance.agents.base import AgentResponse
from governance.evaluator.loop import run_self_correction_loop
from governance.evaluator.metrics import EvaluationScores
from governance.guardrails.schemas import is_rail_schema, validate_output
from governance.pipeline import execute_pipeline
from governance.router.classifier import ClassificationResult


def test_classification_schema_taxonomy_matches_live_classifier():
    """The 'classification' RAIL schema must accept exactly what the live
    classifier emits. If these drift, validate_output rejects every real
    classification (the Epic-5 fork: schemas had Swish business domains
    {inventory,rider,order,…} while the classifier emits {code_generation,
    sensitive_query,…})."""
    from typing import get_args

    from governance.guardrails.schemas import ClassificationSchema as RailSchema
    from governance.router.classifier import ClassificationSchema as LiveSchema

    live_intents = set(get_args(LiveSchema.model_fields["intent"].annotation))
    assert RailSchema.VALID_INTENTS == live_intents, (
        f"taxonomy drift: RAIL {RailSchema.VALID_INTENTS ^ live_intents} "
        f"differs from live classifier"
    )


def test_json_flag_is_not_a_rail_schema():
    """'json' must never be looked up in the RAIL schema registry — it isn't
    one, and treating it as one previously injected an 'Unknown schema' error
    into every single JSON-format request."""
    assert is_rail_schema("json") is False
    assert is_rail_schema(None) is False
    assert is_rail_schema("customer_support") is True


def test_validate_output_unknown_schema_is_reported_not_silently_true():
    ok, errors, _ = validate_output('{"a": 1}', "json")
    assert ok is False
    assert "Unknown schema" in errors[0]


class _ScriptedAgent:
    """Returns a fixed sequence of responses; last one repeats if exhausted."""

    def __init__(self, agent_id: str, responses: list[str]):
        self.agent_id = agent_id
        self._responses = responses
        self._i = 0

    def generate(self, prompt: str) -> AgentResponse:
        text = self._responses[min(self._i, len(self._responses) - 1)]
        self._i += 1
        return AgentResponse(
            text=text,
            model="mock-model",
            agent_id=self.agent_id,
            input_tokens=10,
            output_tokens=10,
            latency_ms=1.0,
        )

    def is_available(self) -> bool:
        return True


def _wire_agent(monkeypatch, responses: list[str], intent: str = "general_knowledge"):
    monkeypatch.setattr(
        "governance.pipeline.classify_intent",
        lambda q: ClassificationResult(
            intent=intent, complexity="low", confidence=0.99, method="model"
        ),
    )
    monkeypatch.setattr(
        "governance.pipeline.get_agent",
        lambda agent_id: _ScriptedAgent(agent_id, responses),
    )


# ── Bug 1: no leak into the response ─────────────────────────────────────────


def test_invalid_named_schema_response_never_leaks_error_text(monkeypatch):
    """A response that fails CustomerSupportSchema validation (confidence out
    of range) must never surface 'Field ... confidence' text to the user —
    that belongs in the correction prompt, not the shipped response."""
    bad = '{"reply": "hi there", "confidence": 1.5}'  # confidence > 1.0
    _wire_agent(monkeypatch, [bad, bad, bad, bad])  # never self-corrects

    res = execute_pipeline("Help me with my order", expected_format="customer_support")

    assert res["status"] == "success"
    assert "confidence" not in res["response"] or "Field" not in res["response"]
    assert "Unknown schema" not in res["response"]
    assert res["schema_validation"]["valid"] is False
    assert any("confidence" in e for e in res["schema_validation"]["errors"])
    assert any("schema" in w.lower() for w in res["warnings"])


def test_json_format_response_is_never_tainted_with_unknown_schema_text(monkeypatch):
    """Regression for the specific bug: expected_format='json' used to always
    fail RAIL lookup and splice 'Unknown schema' into the candidate text."""
    clean_json = '{"status": "ok", "value": 42}'
    _wire_agent(monkeypatch, [clean_json])

    res = execute_pipeline("Get system stats", expected_format="json")

    assert res["status"] == "success"
    assert "Unknown schema" not in res["response"]
    assert res["response"] == clean_json


# ── Bug 2: re-validated after correction, not just once pre-loop ────────────
# The "revalidates after each correction" scenario is verified at the loop level
# (test_schema_gate_revalidates_after_each_correction_isolated below), where
# quality scoring is stubbed so only the schema gate drives retries. An earlier
# pipeline-level duplicate asserted an exact attempt count with LIVE quality
# scoring, which flaps (CI/CCR on short mock JSON forces an extra retry) — it was
# removed to fix an F811 name collision that silently shadowed the isolated test.


def test_schema_gate_flags_persistent_failure_after_exhausting_retries(monkeypatch):
    """If every attempt AND the fallback fail schema validation, the pipeline
    must say so — not silently report success with a broken payload."""
    always_bad = '{"reply": "hi", "confidence": 1.5}'
    _wire_agent(monkeypatch, [always_bad] * 6, intent="sensitive_query")  # elevated: 3 retries

    res = execute_pipeline("Handle this sensitive request", expected_format="customer_support")

    assert res["status"] == "success"  # format gate warns, does not block
    assert res["schema_validation"]["valid"] is False
    assert res["loop_result"]["passed"] is False
    assert any("did not conform" in w for w in res["warnings"])


# The two tests above already prove the end-to-end pipeline shape (leak-free,
# flags persistent failure). The next three isolate the schema gate itself at
# run_self_correction_loop, with quality scoring (CI/FIS/CCR) stubbed to
# always "pass" — otherwise the pipeline-level completeness/context
# heuristics on short mock strings confound what's under test here.


def _stub_passing_quality(monkeypatch):
    def _always_passes(
        candidate,
        original_prompt,
        context_docs="",
        expected_format=None,
        weights=None,
        threshold=0.75,
    ):
        return EvaluationScores(ci=1.0, fis=1.0, ccr=1.0, total=1.0, passed=True, details={})

    monkeypatch.setattr("governance.evaluator.loop.evaluate_output", _always_passes)


def test_schema_gate_revalidates_after_each_correction_isolated(monkeypatch):
    """attempt 0 invalid → correction → attempt 1 still invalid (different
    error) → correction → attempt 2 finally valid. Quality scoring alone
    would pass every attempt; only the schema gate should force retries, and
    it must not report success until the ACTUAL last response conforms.

    Loop-level isolation (quality stubbed) — complements the pipeline-level
    test of the same scenario above."""
    _stub_passing_quality(monkeypatch)
    responses = [
        '{"reply": "", "confidence": 0.9}',  # attempt 1 correction: empty reply
        '{"reply": "Your order ships tomorrow.", "confidence": 0.9}',  # attempt 2: valid
    ]
    agent = _ScriptedAgent("gemma_reasoner", responses)

    result = run_self_correction_loop(
        agent=agent,
        candidate='{"reply": "hi", "confidence": 1.5}',  # attempt 0: out of range
        original_prompt="Where is my order?",
        max_retries_override=3,
        schema_name="customer_support",
    )

    assert result.passed is True
    assert result.final_response == responses[1]
    assert result.attempts == 3  # attempt 0 + 2 corrections


def test_schema_gate_persistent_failure_reports_unpassed_not_success(monkeypatch):
    """If every attempt fails schema validation, the loop must report
    passed=False even though quality scoring alone would pass every one —
    a broken payload must never be indistinguishable from a good one."""
    _stub_passing_quality(monkeypatch)
    always_bad = '{"reply": "", "confidence": 1.5}'  # empty reply AND out-of-range
    agent = _ScriptedAgent("gemma_reasoner", [always_bad] * 5)

    result = run_self_correction_loop(
        agent=agent,
        candidate=always_bad,
        original_prompt="Where is my order?",
        max_retries_override=1,
        schema_name="customer_support",
    )

    assert result.passed is False
    assert result.attempts == 2  # attempt 0 + 1 retry, no fallback_agent given


def test_schema_gate_respects_the_retry_cap_passed_in(monkeypatch):
    """A schema request does not grant extra attempts beyond whatever cap the
    caller (pipeline, per §3b risk tier) decided on."""
    _stub_passing_quality(monkeypatch)
    responses = ["would have been valid but never reached"]
    agent = _ScriptedAgent("gemma_reasoner", responses)

    result = run_self_correction_loop(
        agent=agent,
        candidate='{"reply": "hi", "confidence": 1.5}',
        original_prompt="Where is my order?",
        max_retries_override=1,
        schema_name="customer_support",
    )

    assert result.attempts == 2  # attempt 0 + the single permitted retry


def test_no_schema_requested_reports_trivially_valid(monkeypatch):
    _wire_agent(monkeypatch, ["A clean helpful answer."])
    res = execute_pipeline("What is the capital of Switzerland?")
    assert res["schema_validation"] == {"schema": None, "valid": True, "errors": []}


def test_metrics_and_schemas_share_canonical_definitions():
    """Verify that evaluator/metrics.py imports its schemas directly from
    guardrails/schemas.py to prevent schema drift."""
    import governance.evaluator.metrics as metrics_mod
    import governance.guardrails.schemas as schemas_mod

    assert metrics_mod.DynamicPricingSchema is schemas_mod.DynamicPricingSchema
    assert metrics_mod.CustomerSupportSchema is schemas_mod.CustomerSupportSchema
