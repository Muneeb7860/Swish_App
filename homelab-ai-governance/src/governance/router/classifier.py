"""Intent classifier — classifies the inbound query into one of 9 intent categories.

Uses Gemma 4B via Ollama for classification, with a static keyword fallback
if the model is unavailable or returns unparseable output.
"""

from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Any

from governance.agents.ollama_agent import OllamaAgent
from governance.config import load_routing_config

logger = logging.getLogger(__name__)


@dataclass
class ClassificationResult:
    """Result of intent classification."""

    intent: str
    complexity: str  # "low", "medium", "high"
    confidence: float
    method: str  # "model" or "fallback"


# ── Static keyword fallback ──────────────────────────────────────────────────

_KEYWORD_RULES: list[tuple[str, list[str]]] = [
    ("code_generation", ["write", "create", "implement", "build", "generate code", "function"]),
    ("code_debugging", ["debug", "fix", "error", "traceback", "exception", "bug", "stacktrace"]),
    ("code_review", ["review", "refactor", "optimize", "improve code", "code quality"]),
    ("summarization", ["summarize", "summary", "tldr", "brief", "overview", "recap"]),
    ("creative_writing", ["write a story", "poem", "creative", "fiction", "narrative"]),
    ("data_analysis", ["analyze", "data", "csv", "chart", "statistics", "plot", "graph"]),
    ("system_admin", ["deploy", "docker", "kubernetes", "server", "nginx", "ssh", "systemctl"]),
    ("sensitive_query", ["medical", "legal", "financial advice", "health", "diagnosis"]),
    ("general_knowledge", ["what is", "explain", "how does", "why", "define"]),
]


def _classify_by_keywords(query: str) -> ClassificationResult:
    """Keyword-based fallback classification."""
    query_lower = query.lower()
    for intent, keywords in _KEYWORD_RULES:
        if any(kw in query_lower for kw in keywords):
            return ClassificationResult(
                intent=intent,
                complexity="medium",
                confidence=0.55,
                method="fallback",
            )
    return ClassificationResult(
        intent="general_knowledge",
        complexity="low",
        confidence=0.40,
        method="fallback",
    )


# ── Model-based classification ───────────────────────────────────────────────

_CLASSIFICATION_PROMPT = """Classify the following user query into exactly one intent and complexity level.

INTENTS: general_knowledge, code_generation, code_debugging, code_review, summarization, creative_writing, data_analysis, system_admin, sensitive_query

COMPLEXITY: low, medium, high
- low: simple factual question, single-step task
- medium: multi-step reasoning, moderate context needed
- high: expert-level, multi-domain, requires extensive context

Respond ONLY with valid JSON (no markdown, no explanation):
{{"intent": "<intent>", "complexity": "<complexity>", "confidence": <0.0-1.0>}}

USER QUERY:
{query}"""


_VALID_INTENTS = {
    "general_knowledge", "code_generation", "code_debugging", "code_review",
    "summarization", "creative_writing", "data_analysis", "system_admin",
    "sensitive_query",
}
_VALID_COMPLEXITIES = {"low", "medium", "high"}


def classify_intent(query: str) -> ClassificationResult:
    """Classify the query intent using Gemma 4B, falling back to keywords.

    The classifier prompt asks for structured JSON output. If the model
    returns unparseable output or is unavailable, we fall back to static
    keyword matching.
    """
    routing_cfg = load_routing_config()
    classifier_cfg = routing_cfg.get("classifier", {})

    try:
        agent = OllamaAgent(
            agent_id="_classifier",
            model=classifier_cfg.get("model", "gemma3:4b"),
            ollama_url=classifier_cfg.get("ollama_url", "http://localhost:11434"),
            timeout_ms=classifier_cfg.get("timeout_ms", 2000),
        )

        prompt = _CLASSIFICATION_PROMPT.format(query=query)
        response = agent.generate(prompt)

        # Parse JSON from model response (may contain markdown fencing)
        text = response.text.strip()
        # Strip markdown code fences if present
        json_match = re.search(r"\{[^{}]+\}", text)
        if not json_match:
            logger.warning("Classifier returned non-JSON: %s — using fallback", text[:100])
            return _classify_by_keywords(query)

        data: dict[str, Any] = json.loads(json_match.group())

        intent = data.get("intent", "general_knowledge")
        complexity = data.get("complexity", "medium")
        confidence = float(data.get("confidence", 0.5))

        # Validate parsed values
        if intent not in _VALID_INTENTS:
            logger.warning("Invalid intent '%s' from classifier — fallback", intent)
            return _classify_by_keywords(query)
        if complexity not in _VALID_COMPLEXITIES:
            complexity = "medium"

        return ClassificationResult(
            intent=intent,
            complexity=complexity,
            confidence=max(0.0, min(1.0, confidence)),
            method="model",
        )

    except Exception as e:
        logger.warning("Classifier model unavailable: %s — using keyword fallback", e)
        return _classify_by_keywords(query)
