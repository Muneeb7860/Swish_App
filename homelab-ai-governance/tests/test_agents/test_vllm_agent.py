"""Unit tests for the vLLM Agent implementation."""

from __future__ import annotations

import pytest
import httpx
from unittest.mock import MagicMock, patch

from governance.agents.vllm_agent import VllmAgent


class TestVllmAgent:
    def test_initialization(self):
        agent = VllmAgent(
            agent_id="test_vllm",
            model="qwen-coder-7b",
            vllm_url="http://vllm-host:8001",
            timeout_ms=5000,
        )
        assert agent.agent_id == "test_vllm"
        assert agent.model == "qwen-coder-7b"
        assert agent.vllm_url == "http://vllm-host:8001"
        assert agent.timeout_ms == 5000

    @patch("httpx.Client.post")
    def test_generate_success(self, mock_post):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "id": "chatcmpl-123",
            "object": "chat.completion",
            "created": 1677652288,
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "This is a response from vLLM model.",
                    },
                    "finish_reason": "stop",
                }
            ],
            "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 20,
                "total_tokens": 30,
            }
        }
        mock_post.return_value = mock_response

        agent = VllmAgent(agent_id="test_vllm", model="qwen-coder-7b")
        response = agent.generate("Hello world")

        assert response.text == "This is a response from vLLM model."
        assert response.model == "qwen-coder-7b"
        assert response.input_tokens == 10
        assert response.output_tokens == 20
        assert response.metadata["id"] == "chatcmpl-123"
        assert response.metadata.get("mocked") is None

    @patch("httpx.Client.post")
    def test_generate_failure_fallback_pricing(self, mock_post):
        mock_post.side_effect = httpx.ConnectError("Connection refused")
        
        agent = VllmAgent(agent_id="test_vllm", model="qwen-coder-7b")
        response = agent.generate("Generate dynamic pricing agent results.")

        assert response.metadata.get("mocked") is True
        assert "vLLM Base price" in response.text
        assert response.input_tokens > 0
        assert response.output_tokens > 0

    @patch("httpx.Client.get")
    def test_is_available_true(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "data": [
                {"id": "qwen-coder-7b", "object": "model"},
                {"id": "another-model", "object": "model"}
            ]
        }
        mock_get.return_value = mock_response

        agent = VllmAgent(agent_id="test_vllm", model="qwen-coder-7b")
        assert agent.is_available() is True

    @patch("httpx.Client.get")
    def test_is_available_false(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "data": [
                {"id": "some-other-model", "object": "model"}
            ]
        }
        mock_get.return_value = mock_response

        agent = VllmAgent(agent_id="test_vllm", model="qwen-coder-7b")
        assert agent.is_available() is False

    @patch("httpx.Client.get")
    def test_is_available_exception_returns_false(self, mock_get):
        mock_get.side_effect = httpx.RequestError("Network down")
        agent = VllmAgent(agent_id="test_vllm", model="qwen-coder-7b")
        assert agent.is_available() is False
