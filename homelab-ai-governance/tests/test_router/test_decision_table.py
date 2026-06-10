"""Tests for the Semantic Router Decision Table routing engine."""

from __future__ import annotations

import pytest

from governance.audit import get_cost_tracker
from governance.router.classifier import ClassificationResult
from governance.router.decision_table import route_query


@pytest.fixture(autouse=True)
def reset_cost_tracker():
    """Reset the cost tracker before each test to ensure predictable daily budgets."""
    tracker = get_cost_tracker()
    with tracker._lock:
        tracker._daily_cost = 0.0


def test_route_low_confidence():
    """Verify that low confidence scores route to gemma_reasoner fallback (Rule 0)."""
    res = ClassificationResult(
        intent="code_generation",
        complexity="high",
        confidence=0.20,  # below 0.60 threshold
        method="model",
    )
    decision = route_query(res, local_only=False, input_hash="test")
    assert decision.agent_id == "gemma_reasoner"
    assert decision.matched_rule == 0


def test_route_intent_complexity_match():
    """Verify that intent and complexity levels route to the correct agent."""
    # code_generation + low -> deepseek_coder
    res = ClassificationResult(
        intent="code_generation",
        complexity="low",
        confidence=0.95,
        method="model",
    )
    decision = route_query(res, local_only=False, input_hash="test")
    assert decision.agent_id == "deepseek_coder"
    assert decision.matched_rule == 4

    # general_knowledge + high -> cloud_frontier (if budget OK)
    res_high = ClassificationResult(
        intent="general_knowledge",
        complexity="high",
        confidence=0.90,
        method="model",
    )
    decision_high = route_query(res_high, local_only=False, input_hash="test")
    assert decision_high.agent_id == "cloud_frontier"
    assert decision_high.matched_rule == 3


def test_route_pii_local_only():
    """Verify that cloud routes are downgraded to local alternatives when local_only is True."""
    res = ClassificationResult(
        intent="general_knowledge",
        complexity="high",
        confidence=0.90,
        method="model",
    )
    # With local_only=True, cloud_frontier should fall back to deepseek_coder
    decision = route_query(res, local_only=True, input_hash="test")
    assert decision.agent_id == "deepseek_coder"
    assert decision.local_only is True


def test_route_budget_exceeded():
    """Verify that cloud routes are downgraded to local alternatives when budget is exceeded."""
    tracker = get_cost_tracker()
    
    # Force cost to exceed daily limit of 5.00 USD
    with tracker._lock:
        tracker._daily_cost = 6.00

    res = ClassificationResult(
        intent="general_knowledge",
        complexity="high",
        confidence=0.90,
        method="model",
    )
    
    decision = route_query(res, local_only=False, input_hash="test")
    assert decision.agent_id == "deepseek_coder"
    assert decision.budget_constrained is True
