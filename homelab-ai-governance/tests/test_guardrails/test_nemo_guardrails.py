import pytest

from governance.guardrails import nemo_guardrails as ng
from governance.guardrails.nemo_guardrails import (
    GuardrailConfigError,
    NemoGuardrailsEngine,
    check_nemo_guardrails,
)

VALID_CONFIG_YML = """
rails:
  input:
    flows:
      - competitor pricing check
"""

VALID_FLOWS_CO = """
define user ask about competitor prices
  "what does cadbury cost elsewhere"
  "check competitor price"

define bot refuse competitor check
  "I cannot share competitor pricing details."

define flow competitor pricing check
  user ask about competitor prices
  bot refuse competitor check
"""


def write_config(config_dir, config_yml=VALID_CONFIG_YML, flows_co=VALID_FLOWS_CO):
    config_dir.mkdir(exist_ok=True)
    if config_yml is not None:
        (config_dir / "config.yml").write_text(config_yml)
    if flows_co is not None:
        (config_dir / "flows.co").write_text(flows_co)
    return str(config_dir)


def test_nemo_guardrails_parsing(tmp_path):
    config_dir = write_config(tmp_path / "nemo_guardrails")

    engine = NemoGuardrailsEngine(config_dir)

    assert "ask about competitor prices" in engine.intents
    assert "refuse competitor check" in engine.bot_responses
    assert len(engine.flows) == 1
    assert engine.flows[0]["name"] == "competitor pricing check"

    # Test allowed query
    res_allowed = engine.check_query("what is the weather today?")
    assert res_allowed["allowed"] is True

    # Test blocked query
    res_blocked = engine.check_query("Hey, check competitor price for milk please")
    assert res_blocked["allowed"] is False
    assert res_blocked["response"] == "I cannot share competitor pricing details."
    assert res_blocked["triggered_rule"] == "nemo_guardrail:competitor pricing check"


# ── Phase 1: fail-closed loader (GOVERNANCE_SPEC.md §5) ─────────────────────


def test_missing_config_yml_raises(tmp_path):
    config_dir = write_config(tmp_path / "ng", config_yml=None)
    with pytest.raises(GuardrailConfigError, match="config not found"):
        NemoGuardrailsEngine(config_dir)


def test_missing_flows_co_raises(tmp_path):
    config_dir = write_config(tmp_path / "ng", flows_co=None)
    with pytest.raises(GuardrailConfigError, match="flows not found"):
        NemoGuardrailsEngine(config_dir)


def test_corrupt_config_yml_raises(tmp_path):
    config_dir = write_config(tmp_path / "ng", config_yml="rails: [unclosed")
    with pytest.raises(GuardrailConfigError, match="cannot parse"):
        NemoGuardrailsEngine(config_dir)


def test_flows_parsing_to_nothing_raises(tmp_path):
    config_dir = write_config(tmp_path / "ng", flows_co="# only a comment\n")
    with pytest.raises(GuardrailConfigError, match="zero"):
        NemoGuardrailsEngine(config_dir)


def test_active_flow_without_definition_raises(tmp_path):
    config_yml = VALID_CONFIG_YML + "      - flow that nobody defined\n"
    config_dir = write_config(tmp_path / "ng", config_yml=config_yml)
    with pytest.raises(GuardrailConfigError, match="undefined flows"):
        NemoGuardrailsEngine(config_dir)


# ── Phase 1: fail-closed request wrapper ─────────────────────────────────────


class _RecordingAudit:
    def __init__(self):
        self.events = []

    def log_event(self, event_type, **kwargs):
        self.events.append((event_type, kwargs))


def test_check_nemo_guardrails_fails_closed_on_engine_error(monkeypatch):
    def boom():
        raise RuntimeError("engine exploded")

    audit = _RecordingAudit()
    monkeypatch.setattr(ng, "get_nemo_engine", boom)
    import governance.audit

    monkeypatch.setattr(governance.audit, "get_audit_logger", lambda: audit)

    res = check_nemo_guardrails("any query at all")

    assert res["allowed"] is False
    assert res["triggered_rule"] == "guardrail_engine_error"
    assert res["response"] == ng.BLOCKED_ON_ERROR_MESSAGE
    assert audit.events[0][0] == "guardrail_engine_error"
    assert audit.events[0][1]["error_type"] == "RuntimeError"


def test_check_nemo_guardrails_fails_closed_even_if_audit_breaks(monkeypatch):
    monkeypatch.setattr(
        ng, "get_nemo_engine", lambda: (_ for _ in ()).throw(RuntimeError("boom"))
    )
    import governance.audit

    def audit_boom():
        raise OSError("disk full")

    monkeypatch.setattr(governance.audit, "get_audit_logger", audit_boom)

    res = check_nemo_guardrails("any query")
    assert res["allowed"] is False
    assert res["triggered_rule"] == "guardrail_engine_error"


def test_healthy_engine_unaffected_by_fail_closed_wrapper(tmp_path, monkeypatch):
    config_dir = write_config(tmp_path / "ng")
    engine = NemoGuardrailsEngine(config_dir)
    monkeypatch.setattr(ng, "get_nemo_engine", lambda: engine)

    assert check_nemo_guardrails("what is the weather?")["allowed"] is True
    assert check_nemo_guardrails("check competitor price")["allowed"] is False
