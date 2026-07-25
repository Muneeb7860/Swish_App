"""Ollama agent — wraps the Ollama REST API for local model inference."""

from __future__ import annotations

import logging
import time
from typing import Any

import httpx

from governance.agents._mock import build_mock_response, mock_fallback_enabled
from governance.agents.base import AgentResponse, BaseAgent
from governance.concurrency import get_model_semaphore

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
        return self.generate_chat(prompt, system_prompt=None)

    def generate_chat(self, prompt: str, system_prompt: str | None = None) -> AgentResponse:
        """Send a structured chat prompt to Ollama and return the response."""
        url = f"{self.ollama_url}/api/chat"
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})

        payload = {
            "model": self.model,
            "messages": messages,
            "stream": False,
        }

        start = time.perf_counter()
        try:
            # Per-model gate (GOVERNANCE_SPEC.md §3b): same-model generations
            # queue; different models may overlap. Held only for the HTTP call.
            with get_model_semaphore(self.model):
                resp = self._client.post(url, json=payload)
            resp.raise_for_status()
            elapsed_ms = (time.perf_counter() - start) * 1000
            data: dict[str, Any] = resp.json()
            message = data.get("message", {})
            response_text = message.get("content", "")
            input_tokens = data.get("prompt_eval_count", 0)
            output_tokens = data.get("eval_count", 0)
            metadata = {
                "total_duration_ns": data.get("total_duration"),
                "load_duration_ns": data.get("load_duration"),
            }
        except Exception as e:
            # Goal 1 honesty gate (GOVERNANCE_SPEC.md §3): opt-in mock only for
            # tests/CI; otherwise propagate so the pipeline fails honestly.
            if not mock_fallback_enabled():
                raise
            logger.warning(
                "Ollama chat inference failed for agent %s (model: %s): %s. Falling back to mock generation.",
                self.agent_id,
                self.model,
                e
            )
            elapsed_ms = (time.perf_counter() - start) * 1000
            response_text, metadata = build_mock_response(prompt, self.agent_id)
            metadata["original_error"] = str(e)
            input_tokens = len(prompt) // 4
            output_tokens = len(response_text) // 4

        return AgentResponse(
            text=response_text,
            model=self.model,
            agent_id=self.agent_id,
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            latency_ms=elapsed_ms,
            metadata=metadata,
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
