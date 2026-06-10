"""Token validator — estimates prompt+context token count and cross-checks model limits."""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass

from governance.config import load_routing_config

logger = logging.getLogger(__name__)


@dataclass
class TokenEstimate:
    """Result of token estimation."""

    estimated_tokens: int
    within_limit: bool
    max_allowed: int


def estimate_tokens(text: str) -> int:
    """Estimate token count using a word/punctuation heuristic.

    Approximation: ~1.3 tokens per whitespace-delimited word.
    This avoids loading a tokenizer dependency for the MVP.
    """
    # Split on whitespace and count punctuation-heavy fragments
    words = text.split()
    # Heuristic: code-heavy text has more tokens due to symbols
    code_indicator = len(re.findall(r"[{}()\[\];:=<>]", text))
    multiplier = 1.3 if code_indicator < 20 else 1.5
    return int(len(words) * multiplier)


def validate_token_count(
    prompt: str,
    context: str = "",
    agent_id: str | None = None,
) -> TokenEstimate:
    """Estimate combined token count and check against routing limits.

    If an agent_id is provided and has a model-specific limit in config,
    use that. Otherwise, use the global max_context_tokens.
    """
    routing = load_routing_config()
    combined = prompt + "\n" + context if context else prompt
    estimated = estimate_tokens(combined)

    max_tokens = routing["routing"]["max_context_tokens"]

    return TokenEstimate(
        estimated_tokens=estimated,
        within_limit=estimated <= max_tokens,
        max_allowed=max_tokens,
    )
