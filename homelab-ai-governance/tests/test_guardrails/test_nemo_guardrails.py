from governance.guardrails.nemo_guardrails import NemoGuardrailsEngine


def test_nemo_guardrails_parsing(tmp_path):
    # Setup temporary configurations and flows
    config_dir = tmp_path / "nemo_guardrails"
    config_dir.mkdir()

    config_yml = config_dir / "config.yml"
    config_yml.write_text("""
rails:
  input:
    flows:
      - competitor pricing check
""")

    flows_co = config_dir / "flows.co"
    flows_co.write_text("""
define user ask about competitor prices
  "what does cadbury cost elsewhere"
  "check competitor price"

define bot refuse competitor check
  "I cannot share competitor pricing details."

define flow competitor pricing check
  user ask about competitor prices
  bot refuse competitor check
""")

    engine = NemoGuardrailsEngine(str(config_dir))

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
