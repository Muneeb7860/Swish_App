"""Contract tests for the /api/v1/govern HTTP seam.

This is the wire contract the Java backend depends on
(`domain/agent/adapter/out/governance/PythonGovernanceAdapter`): it POSTs
``{"query": ...}`` and reads ``status`` / ``response`` / ``message`` off the
JSON body. These tests pin that exact shape at the FastAPI layer so the
Java↔Python bridge cannot silently drift — the integration seam no prior test
exercised.

Deterministic and offline: the classifier, agents, and rate limiter are
monkeypatched (mirroring test_pipeline), so no Ollama is required.
"""

from __future__ import annotations

import os

# Disable OTLP exporter spin-up before importing the app.
os.environ.setdefault("SWISH_TRACING_ENABLED", "false")

from fastapi.testclient import TestClient  # noqa: E402

from governance.agents.base import AgentResponse  # noqa: E402
from governance.router.classifier import ClassificationResult  # noqa: E402
from governance.server import app  # noqa: E402

client = TestClient(app)


class _DummyAgent:
    """Offline agent stub returning a fixed completion."""

    def __init__(self, agent_id: str, text: str):
        self.agent_id = agent_id
        self._text = text

    def generate(self, prompt: str) -> AgentResponse:
        return AgentResponse(
            text=self._text,
            model="mock-model",
            agent_id=self.agent_id,
            input_tokens=10,
            output_tokens=20,
            latency_ms=5.0,
        )

    def is_available(self) -> bool:
        return True


def test_health_contract():
    """Java/ops health probe: GET /health returns {"status": "UP"}."""
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "UP"}


def test_health_degrades_when_guardrail_engine_unloadable(monkeypatch):
    """Phase 1 (GOVERNANCE_SPEC.md): broken guardrail config → 503 DEGRADED,
    so orchestrators restart us instead of routing traffic to a service whose
    input gate can only fail-closed everything."""
    import governance.server as server_mod

    def broken_engine():
        raise RuntimeError("flows.co unreadable")

    monkeypatch.setattr(server_mod, "get_nemo_engine", broken_engine)
    r = client.get("/health")
    assert r.status_code == 503
    body = r.json()
    assert body["status"] == "DEGRADED"
    assert "guardrail engine" in body["reason"]


def test_govern_success_contract(monkeypatch):
    """On success the body carries status=='success' and a string `response`
    (the field PythonGovernanceAdapter reads). Also exercises optional-field binding."""
    monkeypatch.setattr(
        "governance.pipeline.classify_intent",
        lambda query: ClassificationResult(
            intent="general_knowledge", complexity="low", confidence=0.95, method="model"
        ),
    )
    monkeypatch.setattr(
        "governance.pipeline.get_agent",
        lambda agent_id: _DummyAgent(agent_id, "Bern is the capital of Switzerland."),
    )

    r = client.post(
        "/api/v1/govern",
        json={"query": "What is the capital of Switzerland?", "local_only_override": False},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "success"
    assert isinstance(body.get("response"), str) and body["response"]


def test_govern_blocked_contract(monkeypatch):
    """On a governed block the body carries status=='blocked' and a string `message`,
    and must NOT smuggle a model `response` — Java treats any non-success as a definitive
    governed decision, never bypassed to an ungoverned model."""

    class _BlockedRateLimiter:
        def is_allowed(self):
            return False

        def get_limit(self):
            return 100

    monkeypatch.setattr(
        "governance.pipeline.get_rate_limiter", lambda: _BlockedRateLimiter()
    )

    r = client.post("/api/v1/govern", json={"query": "anything"})
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "blocked"
    assert isinstance(body.get("message"), str) and body["message"]
    assert "response" not in body
