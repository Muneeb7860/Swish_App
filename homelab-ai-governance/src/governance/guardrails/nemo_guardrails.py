import os
import re
import yaml
from typing import Any, Dict, List, Optional


class NemoGuardrailsEngine:
    def __init__(self, config_dir: str):
        self.config_dir = config_dir
        self.intents: Dict[str, List[str]] = {}
        self.bot_responses: Dict[str, str] = {}
        self.flows: List[Dict[str, Any]] = []
        self.active_flows: List[str] = []
        self.load_config()
        self.load_flows()

    def load_config(self):
        config_path = os.path.join(self.config_dir, "config.yml")
        if not os.path.exists(config_path):
            return
        try:
            with open(config_path, "r", encoding="utf-8") as f:
                config_data = yaml.safe_load(f)
                if config_data and "rails" in config_data:
                    input_rails = config_data["rails"].get("input", {})
                    self.active_flows = input_rails.get("flows", [])
        except Exception:
            pass

    def load_flows(self):
        flows_path = os.path.join(self.config_dir, "flows.co")
        if not os.path.exists(flows_path):
            return
        try:
            with open(flows_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.parse_colang(content)
        except Exception:
            pass

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
    try:
        return get_nemo_engine().check_query(query)
    except Exception:
        # Resilient fallback: allow query if engine has internal errors
        return {"allowed": True}
