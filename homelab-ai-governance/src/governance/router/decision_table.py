"""Decision table — 13-rule routing engine mapping intent × complexity × constraints to agents.

Routes queries to the optimal agent based on:
1. Intent classification
2. Complexity level
3. local_only constraint (PII)
4. Budget constraint (daily cost ceiling)
5. Confidence threshold
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from governance.audit import get_audit_logger, get_cost_tracker
from governance.config import load_routing_config
from governance.router.classifier import ClassificationResult

logger = logging.getLogger(__name__)


@dataclass
class RoutingDecision:
    """The final routing decision for a query."""

    agent_id: str
    intent: str
    complexity: str
    confidence: float
    matched_rule: int
    escalated: bool
    local_only: bool
    budget_constrained: bool


# ── Decision Table Rules ─────────────────────────────────────────────────────
#
# Each rule: (intent, complexity, local_only_override) -> agent_id
# Rules are evaluated in order — first match wins.
# Rule 0 is the low-confidence fallback.

_RULES: list[dict[str, Any]] = [
    # Rule 0: Low-confidence fallback → Gemma (cheap, local, safe)
    {"rule": 0, "condition": "confidence < threshold", "agent": "gemma_reasoner"},

    # Rule 1: general_knowledge / low
    {"rule": 1, "intent": "general_knowledge", "complexity": "low", "agent": "gemma_reasoner"},
    # Rule 2: general_knowledge / medium
    {"rule": 2, "intent": "general_knowledge", "complexity": "medium", "agent": "mistral_summarizer"},
    # Rule 3: general_knowledge / high
    {"rule": 3, "intent": "general_knowledge", "complexity": "high", "agent": "cloud_frontier"},

    # Rule 4: code_generation / low-medium
    {"rule": 4, "intent": "code_generation", "complexity": "low", "agent": "deepseek_coder"},
    {"rule": 5, "intent": "code_generation", "complexity": "medium", "agent": "deepseek_coder"},
    # Rule 6: code_generation / high → cloud
    {"rule": 6, "intent": "code_generation", "complexity": "high", "agent": "cloud_frontier"},

    # Rule 7: code_debugging → deepseek always
    {"rule": 7, "intent": "code_debugging", "complexity": "low", "agent": "deepseek_coder"},
    {"rule": 8, "intent": "code_debugging", "complexity": "medium", "agent": "deepseek_coder"},
    {"rule": 9, "intent": "code_debugging", "complexity": "high", "agent": "cloud_frontier"},

    # Rule 10: code_review → deepseek or cloud
    {"rule": 10, "intent": "code_review", "complexity": "low", "agent": "deepseek_coder"},
    {"rule": 11, "intent": "code_review", "complexity": "medium", "agent": "deepseek_coder"},
    {"rule": 12, "intent": "code_review", "complexity": "high", "agent": "cloud_frontier"},

    # Rule 13: summarization → mistral (specialist)
    {"rule": 13, "intent": "summarization", "complexity": "low", "agent": "mistral_summarizer"},
    {"rule": 14, "intent": "summarization", "complexity": "medium", "agent": "mistral_summarizer"},
    {"rule": 15, "intent": "summarization", "complexity": "high", "agent": "cloud_frontier"},

    # Rule 16: creative_writing → cloud (nuance matters)
    {"rule": 16, "intent": "creative_writing", "complexity": "low", "agent": "gemma_reasoner"},
    {"rule": 17, "intent": "creative_writing", "complexity": "medium", "agent": "cloud_frontier"},
    {"rule": 18, "intent": "creative_writing", "complexity": "high", "agent": "cloud_frontier"},

    # Rule 19: data_analysis
    {"rule": 19, "intent": "data_analysis", "complexity": "low", "agent": "deepseek_coder"},
    {"rule": 20, "intent": "data_analysis", "complexity": "medium", "agent": "deepseek_coder"},
    {"rule": 21, "intent": "data_analysis", "complexity": "high", "agent": "cloud_frontier"},

    # Rule 22: system_admin → local only (security sensitive)
    {"rule": 22, "intent": "system_admin", "complexity": "low", "agent": "gemma_reasoner"},
    {"rule": 23, "intent": "system_admin", "complexity": "medium", "agent": "deepseek_coder"},
    {"rule": 24, "intent": "system_admin", "complexity": "high", "agent": "deepseek_coder"},

    # Rule 25: sensitive_query → local only (data sovereignty)
    {"rule": 25, "intent": "sensitive_query", "complexity": "low", "agent": "gemma_reasoner"},
    {"rule": 26, "intent": "sensitive_query", "complexity": "medium", "agent": "gemma_reasoner"},
    {"rule": 27, "intent": "sensitive_query", "complexity": "high", "agent": "gemma_reasoner"},
]

# Agents that are local (Ollama/vLLM-backed) vs cloud.
# NOTE: vllm_reasoner is listed as local (so budget/local_only logic treats it
# correctly IF ever selected) but NO _RULES entry routes to it — it is staged
# and requires a remote vLLM host (see routing_config.yaml). Add a rule only
# when a real vLLM endpoint exists.
_LOCAL_AGENTS = {"gemma_reasoner", "mistral_summarizer", "deepseek_coder", "vllm_reasoner"}


# Local fallback map: when a cloud agent is selected but can't be used
_LOCAL_FALLBACK = {
    "cloud_frontier": "deepseek_coder",  # best local alternative
}


def route_query(
    classification: ClassificationResult,
    local_only: bool = False,
    input_hash: str = "",
) -> RoutingDecision:
    """Apply the decision table to select the target agent.

    Handles:
    - Low confidence fallback (Rule 0)
    - PII-based local_only constraint
    - Daily budget constraint
    - Intent × complexity matching
    """
    routing_cfg = load_routing_config()
    confidence_threshold = routing_cfg["routing"]["confidence_threshold"]
    budget_cfg = routing_cfg["budget"]

    audit = get_audit_logger()
    cost_tracker = get_cost_tracker()

    # Check budget constraint
    daily_cost = cost_tracker.get_daily_cost()
    budget_constrained = daily_cost >= budget_cfg["daily_limit_usd"]

    # Rule 0: Low confidence → fallback to Gemma
    if classification.confidence < confidence_threshold:
        decision = RoutingDecision(
            agent_id="gemma_reasoner",
            intent=classification.intent,
            complexity=classification.complexity,
            confidence=classification.confidence,
            matched_rule=0,
            escalated=False,
            local_only=local_only,
            budget_constrained=budget_constrained,
        )
        _log_routing_decision(audit, decision, classification, input_hash)
        return decision

    # Match intent × complexity
    matched_agent = "gemma_reasoner"  # ultimate fallback
    matched_rule = -1

    for rule in _RULES[1:]:  # skip rule 0 (handled above)
        if (
            rule.get("intent") == classification.intent
            and rule.get("complexity") == classification.complexity
        ):
            matched_agent = rule["agent"]
            matched_rule = rule["rule"]
            break

    # Apply constraints
    escalated = False

    if matched_agent not in _LOCAL_AGENTS:
        # Cloud agent selected
        if local_only or budget_constrained:
            # Can't use cloud — fall back to local
            original = matched_agent
            matched_agent = _LOCAL_FALLBACK.get(matched_agent, "gemma_reasoner")
            escalated = False
            logger.info(
                "Downgraded %s → %s (local_only=%s, budget=%s)",
                original,
                matched_agent,
                local_only,
                budget_constrained,
            )

    decision = RoutingDecision(
        agent_id=matched_agent,
        intent=classification.intent,
        complexity=classification.complexity,
        confidence=classification.confidence,
        matched_rule=matched_rule,
        escalated=escalated,
        local_only=local_only,
        budget_constrained=budget_constrained,
    )

    _log_routing_decision(audit, decision, classification, input_hash)
    return decision


def _log_routing_decision(
    audit: Any,
    decision: RoutingDecision,
    classification: ClassificationResult,
    input_hash: str,
) -> None:
    """Write the routing decision to the audit log."""
    audit.log_event(
        "routing_decision",
        input_hash=input_hash,
        classification={
            "intent": classification.intent,
            "complexity": classification.complexity,
            "confidence": classification.confidence,
        },
        matched_rule=decision.matched_rule,
        route=decision.agent_id,
        escalated=decision.escalated,
        local_only=decision.local_only,
        budget_constrained=decision.budget_constrained,
    )
