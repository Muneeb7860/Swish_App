"""Shared deterministic mock-fallback for local agents (Ollama, vLLM).

Goal-1 honesty gate (GOVERNANCE_SPEC.md §3): a fabricated "governed" response
that no model produced must never reach production. The mock path is opt-in via
GOVERNANCE_ALLOW_MOCK_FALLBACK (set in tests/CI conftest); otherwise the agent
error propagates and the pipeline reports an honest failure.

Extracted from OllamaAgent/VllmAgent so the gate and the canned responses can
never drift between the two backends.
"""

from __future__ import annotations

import os
from typing import Any


def mock_fallback_enabled() -> bool:
    """True only when the test/CI opt-in env flag is set."""
    return os.environ.get("GOVERNANCE_ALLOW_MOCK_FALLBACK", "").lower() in ("1", "true")


def build_mock_response(
    prompt: str, agent_id: str, source: str | None = None
) -> tuple[str, dict[str, Any]]:
    """Deterministic offline stand-in. Returns (response_text, metadata).

    `source` (e.g. "vLLM") is woven into the human-readable strings so each
    backend's mock text is distinguishable; omit for the plain (Ollama) form.
    """
    tag = f"{source} " if source else ""  # "vLLM " / ""
    via = f" to {source}" if source else ""  # " to vLLM" / ""
    reply_tail = f" from {source}" if source else ""
    p = prompt.lower()

    if "valid JSON" in prompt or "ClassificationSchema" in prompt or "intent" in prompt:
        text = '{"intent": "general_knowledge", "complexity": "low", "confidence": 0.95}'
    elif "customer support agent" in p or "CustomerSupportSchema" in prompt:
        text = (
            f'{{"reply": "This is a simulated customer support reply{reply_tail}.", '
            f'"confidence": 0.9, "tool": null}}'
        )
    elif "dynamic pricing agent" in p or "DynamicPricingSchema" in prompt:
        text = (
            '{"surgeMultiplier": 1.0, "discountPercent": 0.0, '
            f'"confidence": 0.95, "rationale": "{tag}Base price"}}'
        )
    elif "quick test sentence" in p or "verifying" in p:
        text = f"Homelab AI Governance connection{via} verified successfully!"
    else:
        text = f"Simulated {tag}response from agent {agent_id} for prompt: {prompt[:100]}..."

    return text, {"mocked": True}
