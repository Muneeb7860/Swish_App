import logging
import os
import re
import yaml
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

BLOCKED_ON_ERROR_MESSAGE = (
    "Request blocked: the safety guardrail engine is unavailable. "
    "This is a fail-closed response — see GOVERNANCE_SPEC.md §3."
)


class GuardrailConfigError(RuntimeError):
    """Guardrail configuration is missing, unparseable, or inconsistent.

    Raised at engine construction so a governance service with unloadable
    guardrails refuses to serve traffic (GOVERNANCE_SPEC.md Phase 1).
    """


class NemoGuardrailsEngine:
    def __init__(self, config_dir: str):
        self.config_dir = config_dir
        self.intents: Dict[str, List[str]] = {}
        self.bot_responses: Dict[str, str] = {}
        self.flows: List[Dict[str, Any]] = []
        self.active_flows: List[str] = []
        self.load_config()
        self.load_flows()
        self._validate_loaded_rules()

    def load_config(self):
        config_path = os.path.join(self.config_dir, "config.yml")
        if not os.path.exists(config_path):
            raise GuardrailConfigError(f"guardrail config not found: {config_path}")
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                config_data = yaml.safe_load(f)
        except Exception as exc:
            raise GuardrailConfigError(f"cannot parse {config_path}: {exc}") from exc
        if config_data and "rails" in config_data:
            input_rails = config_data["rails"].get("input", {})
            self.active_flows = input_rails.get("flows", [])

    def load_flows(self):
        flows_path = os.path.join(self.config_dir, "flows.co")
        if not os.path.exists(flows_path):
            raise GuardrailConfigError(f"guardrail flows not found: {flows_path}")
        try:
            with open(flows_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.parse_colang(content)
        except Exception as exc:
            raise GuardrailConfigError(f"cannot parse {flows_path}: {exc}") from exc

    def _validate_loaded_rules(self):
        """A gate that parsed to nothing is a silently-disabled gate — refuse it."""
        if not self.intents or not self.flows:
            raise GuardrailConfigError(
                f"guardrail config in {self.config_dir} parsed to zero "
                f"intents/flows — the input gate would allow everything"
            )
        flow_names = {flow["name"] for flow in self.flows}
        missing = [name for name in self.active_flows if name not in flow_names]
        if missing:
            raise GuardrailConfigError(
                f"config.yml activates undefined flows: {missing} "
                f"(defined: {sorted(flow_names)})"
            )

    def parse_colang(self, content: str):
        lines = content.splitlines()
        current_type: Optional[str] = None
        current_name: Optional[str] = None
        current_items: List[str] = []

        def save_current():
            if not current_type or not current_name:
                return
            if current_type == "user":
                self.intents[current_name] = [item.strip('"') for item in current_items]
            elif current_type == "bot":
                self.bot_responses[current_name] = "\n".join(
                    [item.strip('"') for item in current_items]
                )
            elif current_type == "flow":
                # Parse simple flows into user/bot sequence
                flow_steps = []
                for item in current_items:
                    parts = item.split(None, 1)
                    if len(parts) == 2:
                        flow_steps.append({"type": parts[0], "name": parts[1]})
                self.flows.append({"name": current_name, "steps": flow_steps})

        for line in lines:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue

            # Start of a definition block
            match = re.match(r"^define\s+(user|bot|flow)\s+(.+)$", stripped)
            if match:
                save_current()
                current_type = match.group(1)
                current_name = match.group(2).strip()
                current_items = []
            elif line.startswith("  ") or line.startswith("\t"):
                current_items.append(stripped)

        save_current()

    def check_query(self, query: str) -> Dict[str, Any]:
        """Check user query against parsed Colang safety flows."""
        query_lower = query.lower()

        # Step 1: Match query against registered safety intents
        matched_intent: Optional[str] = None
        for intent_name, phrases in self.intents.items():
            for phrase in phrases:
                # Use regex word boundaries or substring containment for robust matching
                phrase_esc = re.escape(phrase.lower())
                if (
                    re.search(r"\b" + phrase_esc + r"\b", query_lower)
                    or phrase.lower() in query_lower
                ):
                    matched_intent = intent_name
                    break
            if matched_intent:
                break

        if not matched_intent:
            return {"allowed": True}

        # Step 2: Traverse flows to find a bot refusal path matching the intent
        for flow in self.flows:
            # Check if this flow is declared active in config.yml
            if self.active_flows and flow["name"] not in self.active_flows:
                continue

            steps = flow["steps"]
            if (
                len(steps) >= 2
                and steps[0]["type"] == "user"
                and steps[0]["name"] == matched_intent
            ):
                # Flow matches! Get the bot response from the next step
                bot_action = steps[1]
                if bot_action["type"] == "bot":
                    bot_name = bot_action["name"]
                    response_text = self.bot_responses.get(
                        bot_name, "Request blocked by safety guardrails."
                    )
                    return {
                        "allowed": False,
                        "response": response_text,
                        "triggered_rule": f"nemo_guardrail:{flow['name']}",
                    }

        return {"allowed": True}


# Singleton instance helper
_engine_instance: Optional[NemoGuardrailsEngine] = None


def get_nemo_engine() -> NemoGuardrailsEngine:
    global _engine_instance
    if _engine_instance is None:
        # Default config path relative to the root
        base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(__file__))))
        config_dir = os.path.join(base_dir, "config", "nemo_guardrails")
        _engine_instance = NemoGuardrailsEngine(config_dir)
    return _engine_instance


def check_nemo_guardrails(query: str) -> Dict[str, Any]:
    """Input gate wrapper — FAILS CLOSED (GOVERNANCE_SPEC.md §0 goal 1).

    An engine error must never grant weaker safety treatment: the request is
    blocked and a `guardrail_engine_error` audit event is written. Because the
    gate is a pure pattern matcher, failing closed costs no latency (goal 2).

    Test/staging hook: GOVERNANCE_FORCE_DEGRADED=1 forces the fail-closed
    (`guardrail_engine_error`) path without actually breaking the engine, so the
    Phase 4 shed-503 can be exercised live end-to-end. Off by default; logs
    loudly when active. Never set in production.
    """
    if os.environ.get("GOVERNANCE_FORCE_DEGRADED", "").lower() in ("1", "true"):
        logger.warning("GOVERNANCE_FORCE_DEGRADED active — forcing guardrail_engine_error path")
        return {
            "allowed": False,
            "response": BLOCKED_ON_ERROR_MESSAGE,
            "triggered_rule": "guardrail_engine_error",
        }
    try:
        return get_nemo_engine().check_query(query)
    except Exception as exc:
        logger.exception("Guardrail engine error — failing closed")
        try:
            from governance.audit import get_audit_logger

            get_audit_logger().log_event(
                "guardrail_engine_error",
                error=str(exc),
                error_type=type(exc).__name__,
            )
        except Exception:
            logger.exception("Could not write guardrail_engine_error audit event")
        return {
            "allowed": False,
            "response": BLOCKED_ON_ERROR_MESSAGE,
            "triggered_rule": "guardrail_engine_error",
        }
