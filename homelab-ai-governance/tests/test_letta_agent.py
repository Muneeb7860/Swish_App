"""Tests for LettaAgent backend and Letta API connection handling."""

from __future__ import annotations

import httpx
import pytest
from unittest.mock import MagicMock

from governance.agents.letta_agent import LettaAgent
from governance.agents.base import AgentResponse


class MockResponse:
    def __init__(self, status_code: int, json_data: dict | list):
        self.status_code = status_code
        self.json_data = json_data

    def json(self) -> dict | list:
        return self.json_data

    def raise_for_status(self) -> None:
        if self.status_code >= 400:
            raise httpx.HTTPStatusError(
                "Error", request=MagicMock(), response=MagicMock()
            )


def test_letta_agent_initialization():
    """Verify LettaAgent registers configuration settings correctly."""
    agent = LettaAgent(
        agent_id="test_letta",
        model="ollama/qwen2.5:7b",
        letta_url="http://localhost:8283",
        api_token="my-token",
        timeout_ms=10000,
    )
    assert agent.agent_id == "test_letta"
    assert agent.model == "ollama/qwen2.5:7b"
    assert agent.letta_url == "http://localhost:8283"
    assert agent.api_token == "my-token"
    assert agent.timeout_ms == 10000


def test_letta_agent_is_available_success(monkeypatch):
    """Verify is_available returns True when Letta lists agents successfully."""
    agent = LettaAgent("test_letta", "ollama/qwen2.5:7b")

    mock_get = MagicMock(return_value=MockResponse(200, []))
    monkeypatch.setattr(agent._client, "get", mock_get)

    assert agent.is_available() is True
    mock_get.assert_called_once_with("http://localhost:8283/v1/agents")


def test_letta_agent_is_available_failure(monkeypatch):
    """Verify is_available returns False when Letta connection fails."""
    agent = LettaAgent("test_letta", "ollama/qwen2.5:7b")

    def mock_get_error(*args, **kwargs):
        raise httpx.ConnectError("Connection refused")

    monkeypatch.setattr(agent._client, "get", mock_get_error)

    assert agent.is_available() is False


def test_letta_agent_generate_success(monkeypatch):
    """Verify LettaAgent creates/retrieves session agent and sends message successfully."""
    agent = LettaAgent("test_letta", "ollama/qwen2.5:7b")

    # Mock list: returns no existing agents
    mock_list_resp = MockResponse(200, {"items": []})
    # Mock create: returns a new agent with ID "agent-uuid-123"
    mock_create_resp = MockResponse(201, {"id": "agent-uuid-123"})
    # Mock message: returns assistant response
    mock_msg_resp = MockResponse(
        200,
        {
            "messages": [
                {"role": "user", "content": "Hello"},
                {"role": "assistant", "content": "Hi! I am your stateful Letta assistant."},
            ]
        },
    )

    call_counts = {"get": 0, "post": 0}

    def mock_get(url, *args, **kwargs):
        call_counts["get"] += 1
        if "/v1/agents" in url:
            return mock_list_resp
        return MockResponse(404, {})

    def mock_post(url, json=None, *args, **kwargs):
        call_counts["post"] += 1
        if url.endswith("/v1/agents"):
            return mock_create_resp
        elif "/v1/agents/agent-uuid-123/messages" in url:
            return mock_msg_resp
        return MockResponse(404, {})

    monkeypatch.setattr(agent._client, "get", mock_get)
    monkeypatch.setattr(agent._client, "post", mock_post)

    response = agent.generate_chat("Hello", session_id="my-session-xyz")

    assert isinstance(response, AgentResponse)
    assert response.text == "Hi! I am your stateful Letta assistant."
    assert response.metadata.get("letta_agent_id") == "agent-uuid-123"
    assert call_counts["get"] == 1
    assert call_counts["post"] == 2


def test_letta_agent_fallback_on_exception(monkeypatch):
    """Verify LettaAgent falls back to simulated/mock responses when Letta is unreachable."""
    agent = LettaAgent("test_letta", "ollama/qwen2.5:7b")

    def mock_post_error(*args, **kwargs):
        raise httpx.ConnectError("Letta is offline")

    monkeypatch.setattr(agent._client, "post", mock_post_error)

    response = agent.generate_chat("verifying connection details", session_id="my-session-xyz")

    assert isinstance(response, AgentResponse)
    assert "Simulated Letta response for session" in response.text or "verified successfully" in response.text
    assert response.metadata.get("mocked") is True
    assert "Could not get or create Letta agent" in response.metadata.get("original_error")
