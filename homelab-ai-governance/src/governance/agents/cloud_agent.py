"""Cloud agent — wraps any OpenAI-compatible API (Groq, OpenAI, OpenRouter, etc.)."""

from __future__ import annotations

import logging
import os
import time
from typing import Any

import httpx

from governance.agents.base import AgentResponse, BaseAgent
from governance.config import ConfigError

logger = logging.getLogger(__name__)


class CloudAgent(BaseAgent):
    """Agent backed by an OpenAI-compatible cloud API.

    Supports Groq, OpenAI, OpenRouter, and any provider that implements
    the /v1/chat/completions endpoint. The provider is selected by
    configuring base_url and api_key_env in routing_config.yaml.
    """

    def __init__(
        self,
        agent_id: str,
        model: str,
        base_url: str,
        api_key_env: str,
        timeout_ms: int = 30000,
    ):
        super().__init__(agent_id, model, timeout_ms)
        self.base_url = base_url.rstrip("/")
        self.api_key_env = api_key_env
        self._api_key: str | None = None
        self._client = httpx.Client(timeout=timeout_ms / 1000)

    def _get_api_key(self) -> str:
        """Lazily resolve API key from environment."""
        if self._api_key is None:
            self._api_key = os.environ.get(self.api_key_env)
            if not self._api_key:
                raise ConfigError(
                    f"Cloud agent '{self.agent_id}' requires env var "
                    f"'{self.api_key_env}' to be set"
                )
        return self._api_key

    def generate(self, prompt: str) -> AgentResponse:
        """Send a prompt to the cloud API using chat completions."""
        return self.generate_chat(prompt, system_prompt=None)

    def generate_chat(self, prompt: str, system_prompt: str | None = None) -> AgentResponse:
        """Send a structured chat prompt to the cloud API using chat completions."""
        url = f"{self.base_url}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self._get_api_key()}",
            "Content-Type": "application/json",
        }
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})

        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.3,
        }

        start = time.perf_counter()
        try:
            resp = self._client.post(url, json=payload, headers=headers)
            resp.raise_for_status()
        except httpx.HTTPStatusError as e:
            logger.error("Cloud API HTTP error for %s: %s", self.agent_id, e)
            raise
        except httpx.ConnectError as e:
            logger.error(
                "Cloud API connection failed at %s for agent %s: %s",
                self.base_url,
                self.agent_id,
                e,
            )
            raise

        elapsed_ms = (time.perf_counter() - start) * 1000
        data: dict[str, Any] = resp.json()

        # Extract from OpenAI-compatible response format
        choices = data.get("choices", [])
        text = choices[0]["message"]["content"] if choices else ""
        usage = data.get("usage", {})

        # Record cloud call in CostTracker to prevent budget leakage during pipeline/retries
        try:
            from governance.audit import get_cost_tracker
            from governance.config import load_routing_config
            routing = load_routing_config()
            budget = routing.get("budget", {})
            cost_in = budget.get("cost_per_1k_input_tokens", 0.005)
            cost_out = budget.get("cost_per_1k_output_tokens", 0.015)
            get_cost_tracker().record_cloud_call(
                agent_id=self.agent_id,
                input_tokens=usage.get("prompt_tokens", 0),
                output_tokens=usage.get("completion_tokens", 0),
                cost_per_1k_input=cost_in,
                cost_per_1k_output=cost_out,
            )
        except Exception as e:
            logger.warning("Failed to record cloud call in CostTracker: %s", e)

        return AgentResponse(
            text=text,
            model=self.model,
            agent_id=self.agent_id,
            input_tokens=usage.get("prompt_tokens", 0),
            output_tokens=usage.get("completion_tokens", 0),
            latency_ms=elapsed_ms,
            metadata={
                "provider_model": data.get("model"),
                "finish_reason": choices[0].get("finish_reason") if choices else None,
            },
        )

    def is_available(self) -> bool:
        """Check if the API key is configured and endpoint is reachable."""
        try:
            api_key = os.environ.get(self.api_key_env)
            if not api_key:
                return False
            resp = self._client.get(
                f"{self.base_url}/models",
                headers={"Authorization": f"Bearer {api_key}"},
            )
            return resp.status_code == 200
        except Exception:
            return False
