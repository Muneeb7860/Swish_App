"""Ollama agent — wraps the Ollama REST API for local model inference."""

from __future__ import annotations

import logging
import time
from typing import Any

import httpx

from governance.agents.base import AgentResponse, BaseAgent

logger = logging.getLogger(__name__)


class OllamaAgent(BaseAgent):
    """Agent backed by a locally-running Ollama instance.

    Uses the /api/generate endpoint for synchronous, non-streaming inference.
    """

    def __init__(
        self,
        agent_id: str,
        model: str,
        ollama_url: str = "http://localhost:11434",
        timeout_ms: int = 30000,
    ):
        super().__init__(agent_id, model, timeout_ms)
        self.ollama_url = ollama_url.rstrip("/")
        self._client = httpx.Client(timeout=timeout_ms / 1000)

    def generate(self, prompt: str) -> AgentResponse:
        """Send a prompt to Ollama and return the response."""
        url = f"{self.ollama_url}/api/generate"
        payload = {
            "model": self.model,
            "prompt": prompt,
            "stream": False,
        }

        start = time.perf_counter()
        try:
            resp = self._client.post(url, json=payload)
            resp.raise_for_status()
        except httpx.HTTPStatusError as e:
            logger.error("Ollama HTTP error for %s: %s", self.agent_id, e)
            raise
        except httpx.ConnectError as e:
            logger.error(
                "Ollama connection failed at %s for agent %s: %s",
                self.ollama_url,
                self.agent_id,
                e,
            )
            raise

        elapsed_ms = (time.perf_counter() - start) * 1000
        data: dict[str, Any] = resp.json()

        return AgentResponse(
            text=data.get("response", ""),
            model=self.model,
            agent_id=self.agent_id,
            input_tokens=data.get("prompt_eval_count", 0),
            output_tokens=data.get("eval_count", 0),
            latency_ms=elapsed_ms,
            metadata={
                "total_duration_ns": data.get("total_duration"),
                "load_duration_ns": data.get("load_duration"),
            },
        )

    def is_available(self) -> bool:
        """Check if Ollama is reachable and the model is loaded."""
        try:
            resp = self._client.get(f"{self.ollama_url}/api/tags")
            if resp.status_code != 200:
                return False
            models = [m["name"] for m in resp.json().get("models", [])]
            # Match model name with or without tag suffix
            return any(
                m == self.model or m.startswith(f"{self.model}:")
                for m in models
            )
        except Exception:
            return False
