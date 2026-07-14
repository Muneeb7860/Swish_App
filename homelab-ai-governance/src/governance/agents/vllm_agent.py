"""vLLM agent — wraps the OpenAI compatible API exposed by local vLLM instances."""

from __future__ import annotations

import logging
import os
import time
from typing import Any

import httpx

from governance.agents.base import AgentResponse, BaseAgent

logger = logging.getLogger(__name__)


class VllmAgent(BaseAgent):
    """Agent backed by a locally-running vLLM server exposing an OpenAI-compatible API.

    Uses the /v1/chat/completions endpoint for chat-based inference.
    """

    def __init__(
        self,
        agent_id: str,
        model: str,
        vllm_url: str = "http://localhost:8000",
        timeout_ms: int = 30000,
    ):
        super().__init__(agent_id, model, timeout_ms)
        self.vllm_url = vllm_url.rstrip("/")
        # Initialize HTTP client with reasonable timeout
        self._client = httpx.Client(timeout=timeout_ms / 1000)

    def generate(self, prompt: str) -> AgentResponse:
        """Send a prompt to vLLM using the chat interface."""
        return self.generate_chat(prompt, system_prompt=None)

    def generate_chat(self, prompt: str, system_prompt: str | None = None) -> AgentResponse:
        """Send a chat-structured request to the vLLM server."""
        url = f"{self.vllm_url}/v1/chat/completions"
        messages = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": prompt})

        payload = {
            "model": self.model,
            "messages": messages,
            "stream": False,
            "temperature": 0.0,  # Deterministic output for governance checks
        }

        start = time.perf_counter()
        try:
            resp = self._client.post(url, json=payload)
            resp.raise_for_status()
            elapsed_ms = (time.perf_counter() - start) * 1000
            data: dict[str, Any] = resp.json()
            
            choices = data.get("choices", [])
            if not choices:
                raise ValueError("vLLM response returned empty choices")
                
            response_text = choices[0].get("message", {}).get("content", "")
            
            # Extract token counts if provided by vLLM
            usage = data.get("usage", {})
            input_tokens = usage.get("prompt_tokens", 0)
            output_tokens = usage.get("completion_tokens", 0)
            
            metadata = {
                "id": data.get("id"),
                "object": data.get("object"),
                "created": data.get("created"),
                "usage": usage,
            }
        except Exception as e:
            # Goal 1 honesty gate: a mocked "governed" response that no model
            # produced must never reach production. Mock fallback is opt-in for
            # tests/CI only; otherwise the error propagates and the pipeline
            # reports an honest failure (GOVERNANCE_SPEC.md §3).
            if os.environ.get("GOVERNANCE_ALLOW_MOCK_FALLBACK", "").lower() not in (
                "1",
                "true",
            ):
                raise
            logger.warning(
                "vLLM chat completions failed for agent %s (model: %s) at %s: %s. Falling back to mock generation.",
                self.agent_id,
                self.model,
                self.vllm_url,
                e
            )
            elapsed_ms = (time.perf_counter() - start) * 1000
            
            # Simple mock fallback strategy matching OllamaAgent pattern
            if "valid JSON" in prompt or "ClassificationSchema" in prompt or "intent" in prompt:
                response_text = '{"intent": "general_knowledge", "complexity": "low", "confidence": 0.95}'
            elif "customer support agent" in prompt.lower() or "CustomerSupportSchema" in prompt:
                response_text = '{"reply": "This is a simulated customer support reply from vLLM.", "confidence": 0.9, "tool": null}'
            elif "dynamic pricing agent" in prompt.lower() or "DynamicPricingSchema" in prompt:
                response_text = '{"surgeMultiplier": 1.0, "discountPercent": 0.0, "confidence": 0.95, "rationale": "vLLM Base price"}'
            else:
                if "quick test sentence" in prompt.lower() or "verifying" in prompt.lower():
                    response_text = "Homelab AI Governance connection to vLLM verified successfully!"
                else:
                    response_text = f"Simulated vLLM response from agent {self.agent_id} for prompt: {prompt[:100]}..."
            
            input_tokens = len(prompt) // 4
            output_tokens = len(response_text) // 4
            metadata = {"mocked": True, "original_error": str(e)}

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
        """Check if the vLLM server is responsive and listing models."""
        try:
            # Query the standard OpenAI-compatible /v1/models endpoint
            resp = self._client.get(f"{self.vllm_url}/v1/models")
            if resp.status_code != 200:
                return False
            models_data = resp.json().get("data", [])
            # Return true if any model listed matches the expected model identifier
            return any(m.get("id") == self.model for m in models_data)
        except Exception:
            return False
