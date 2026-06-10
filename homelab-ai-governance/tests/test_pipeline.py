"""End-to-end integration tests for the query governance pipeline."""

from __future__ import annotations

import pytest

from governance.agents.base import AgentResponse
from governance.config import load_shared_guardrails
from governance.pipeline import execute_pipeline
from governance.router.classifier import ClassificationResult


class DummyAgent:
    """Mock agent wrapper that yields a predefined sequence of response strings."""

    def __init__(self, agent_id: str, responses: list[str]):
        self.agent_id = agent_id
        self.responses = responses
        self.call_count = 0

    def generate(self, prompt: str) -> AgentResponse:
        idx = min(self.call_count, len(self.responses) - 1)
        self.call_count += 1
        return AgentResponse(
            text=self.responses[idx],
            model="mock-model",
            agent_id=self.agent_id,
            input_tokens=100,
            output_tokens=150,
            latency_ms=250.0,
        )

    def is_available(self) -> bool:
        return True


def test_pipeline_blocked_by_input_guardrail(monkeypatch):
    """Verify that queries matching critical input filters are blocked immediately."""
    # "badword" triggers the hate_speech_filter (since we configured profanity or keyword checks)
    # Let's mock load_guardrails to return a blocking rule
    monkeypatch.setattr(
        "governance.pipeline.load_guardrails",
        lambda agent_id: [
            {
                "id": "hate_speech_filter",
                "severity": "critical",
                "applies_to": ["input"],
                "default_action": "block",
                "detector": {
                    "type": "regex",
                    "patterns": [{"name": "slurs", "pattern": "badword"}],
                },
            }
        ],
    )

    res = execute_pipeline("You are a badword person.")
    assert res["status"] == "blocked"
    assert "safety guardrails" in res["message"]


def test_pipeline_successful_execution(monkeypatch):
    """Verify that a clean query proceeds and completes successfully."""
    # Mock classifier and decision table to select "gemma_reasoner"
    monkeypatch.setattr(
        "governance.pipeline.get_agent",
        lambda agent_id: DummyAgent(agent_id, ["This is a clean, helpful response."]),
    )

    res = execute_pipeline("What is the capital of Switzerland?")
    assert res["status"] == "success"
    assert "clean, helpful response" in res["response"]
    assert res["agent_id"] == "gemma_reasoner"


def test_pipeline_self_correction_success(monkeypatch):
    """Verify that the self-correction loop heals formatting failures."""
    # Agent returns invalid JSON on first attempt, then valid JSON on second
    responses = [
        "This is not valid JSON",
        '{"status": "success", "data": "healed"}',
    ]
    monkeypatch.setattr(
        "governance.pipeline.get_agent",
        lambda agent_id: DummyAgent(agent_id, responses),
    )

    # Force a dummy evaluation config that requires JSON
    res = execute_pipeline("Get system stats", expected_format="json")
    assert res["status"] == "success"
    # Verify it took 2 attempts (initial + 1 correction)
    assert res["loop_result"]["attempts"] == 2
    assert "healed" in res["response"]


def test_pipeline_fallback_escalation(monkeypatch):
    """Verify that the system escalates to the fallback agent after 3 failed correction retries."""
    # Mistral always returns invalid JSON
    mistral = DummyAgent("mistral_summarizer", ["invalid_json"] * 5)
    # Gemma fallback returns clean plaintext response
    gemma = DummyAgent("gemma_reasoner", ["Here is the clean text fallback answer."])

    def mock_get_agent(agent_id):
        if agent_id == "mistral_summarizer":
            return mistral
        return gemma

    monkeypatch.setattr("governance.pipeline.get_agent", mock_get_agent)

    # Mock classifier to route to mistral_summarizer (summarization + low)
    monkeypatch.setattr(
        "governance.pipeline.classify_intent",
        lambda query: ClassificationResult(
            intent="summarization",
            complexity="low",
            confidence=0.95,
            method="model",
        ),
    )

    # We expect JSON, but Mistral fails 3 retries, so we escalate to Gemma fallback
    res = execute_pipeline("Summarize this document", expected_format="json")
    
    assert res["status"] == "success"
    assert res["loop_result"]["fallback_used"] is True
    assert "fallback answer" in res["response"]
    assert res["agent_id"] == "gemma_reasoner"


def test_rate_limiter_logic():
    """Verify that the RateLimiter counts and blocks requests when limit is exceeded."""
    from governance.audit import RateLimiter
    
    # Create rate limiter with small limit of 2 requests
    limiter = RateLimiter(limit_per_hour=2)
    assert limiter.is_allowed() is True
    
    limiter.record_request()
    assert limiter.is_allowed() is True
    
    limiter.record_request()
    # Limit reached (2 requests recorded)
    assert limiter.is_allowed() is False


def test_pipeline_blocked_by_rate_limiter(monkeypatch):
    """Verify that the pipeline immediately blocks execution when rate limit is exceeded."""
    # Force RateLimiter to report limit exceeded
    class MockRateLimiter:
        def is_allowed(self):
            return False
        def get_limit(self):
            return 100
            
    monkeypatch.setattr("governance.pipeline.get_rate_limiter", lambda: MockRateLimiter())
    
    res = execute_pipeline("Hello")
    assert res["status"] == "blocked"
    assert "rate limit" in res["message"]
    assert res["triggered_rules"][0]["rule_id"] == "rate_limit"


