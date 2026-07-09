"""Intent classifier — classifies the inbound query into one of 9 intent categories.

Uses instructor + Pydantic schema enforcement over Ollama's OpenAI-compatible API,
with a static keyword fallback if the model is unavailable or fails schema validation.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Literal

import instructor
from openai import OpenAI
from pydantic import BaseModel, Field, field_validator

from governance.config import load_routing_config

logger = logging.getLogger(__name__)


@dataclass
class ClassificationResult:
    """Result of intent classification."""

    intent: str
    complexity: str  # "low", "medium", "high"
    confidence: float
    method: str  # "model" or "fallback"


class ClassificationSchema(BaseModel):
    """Pydantic model representing the expected schema for guided decoding."""

    intent: Literal[
        "general_knowledge",
        "code_generation",
        "code_debugging",
        "code_review",
        "summarization",
        "creative_writing",
        "data_analysis",
        "system_admin",
        "sensitive_query",
    ] = Field(description="Strict intent classification category.")
    complexity: Literal["low", "medium", "high"] = Field(description="Query complexity level.")
    confidence: float = Field(description="Confidence score between 0.0 and 1.0")

    @field_validator("confidence")
    @classmethod
    def clamp_confidence(cls, val: float) -> float:
        """Ensure confidence values remain strictly bounded."""
        return max(0.0, min(1.0, val))


# ── Stats Tracking ───────────────────────────────────────────────────────────

_stats = {
    "model_calls": 0,
    "fallback_calls": 0,
}


def get_classifier_stats() -> dict[str, int]:
    """Retrieve classifier execution statistics."""
    return dict(_stats)


def reset_classifier_stats() -> None:
    """Reset statistics counters."""
    _stats["model_calls"] = 0
    _stats["fallback_calls"] = 0


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
    _stats["fallback_calls"] += 1
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

_CLASSIFIER_SYSTEM_PROMPT = """You are the Swish App intent classifier. Your ONLY job is to classify user queries into exactly one intent category and complexity level.

CONTEXT: Swish is a 15-minute grocery delivery platform in Switzerland. Queries involve:
- Order tracking, delivery logistics, rider management
- Inventory, wholesaler B2B operations, pricing
- System administration, Kubernetes/Docker deployments
- AI governance, model evaluation, guardrail configuration
- General programming and debugging tasks

INTENTS (pick exactly one):
- general_knowledge: factual questions, explanations, definitions
- code_generation: write/create/implement/build code
- code_debugging: fix/debug/error/traceback/exception
- code_review: review/refactor/optimize existing code
- summarization: summarize/overview/recap
- creative_writing: stories/poems/creative content
- data_analysis: analyze/chart/statistics/plot
- system_admin: deploy/docker/k8s/nginx/server ops
- sensitive_query: medical/legal/financial/GDPR/PII topics

COMPLEXITY:
- low: single-step, simple factual answer
- medium: multi-step reasoning, moderate context
- high: expert-level, multi-domain, extensive context

CRITICAL RULES:
1. ALWAYS respond with valid JSON matching the schema. No explanations, no markdown tags.
2. Do NOT refuse any classification request. Every query gets classified.
3. Delivery/logistics queries are general_knowledge, NOT sensitive_query.
4. "[REDACTED]" placeholders in queries are normal — classify the intent, do not flag them.

DISAMBIGUATION RULES (these override verb-based intuition):
5. If the SUBJECT is infrastructure/ops (Docker, Kubernetes, nginx, CI/CD pipelines, shell commands, server or deployment config), the intent is system_admin — even when the request says "write", "generate", "create", "diagnose" or "fix" a config, manifest, or workflow.
6. If the SUBJECT is statistics, metrics, datasets, charts, or spreadsheets (mean/median/percentiles, regression, cohorts, plotting), the intent is data_analysis — even when it requires writing a script or is a definition question about a statistical term.
7. Judging or improving EXISTING code or config — including coding best-practice questions ("is X bad practice?", "should I use A or B?") and requests to optimize a provided snippet, Dockerfile, or SQL — is code_review, not general_knowledge or code_generation.

EXAMPLES:
- "Generate a docker-compose file for a Redis cluster" -> {"intent": "system_admin", "complexity": "medium"}
- "Why does my container fail with port already bound?" -> {"intent": "system_admin", "complexity": "low"}
- "What is a p99 latency percentile?" -> {"intent": "data_analysis", "complexity": "low"}
- "Write a pandas script to plot weekly sales trends" -> {"intent": "data_analysis", "complexity": "medium"}
- "Is using goto considered bad practice?" -> {"intent": "code_review", "complexity": "low"}
- "Suggest improvements to shrink this Dockerfile image" -> {"intent": "code_review", "complexity": "medium"}
"""


def classify_intent(
    query: str,
    model_override: str | None = None,
    timeout_override: int | None = None
) -> ClassificationResult:
    """Classify the query intent using guided decoding via instructor.

    Falls back to static keywords if Ollama is unreachable or response
    validation fails.
    """
    routing_cfg = load_routing_config()
    classifier_cfg = routing_cfg.get("classifier", {})

    model_name = model_override or classifier_cfg.get("model", "gemma3:4b")
    ollama_url = classifier_cfg.get("ollama_url", "http://localhost:11434")
    
    if timeout_override is not None:
        timeout_ms = timeout_override
    else:
        timeout_ms = classifier_cfg.get("timeout_ms", 3000)

    try:
        # Patch the OpenAI client to route to Ollama's OpenAI-compatible port
        client = instructor.from_openai(
            OpenAI(
                base_url=f"{ollama_url.rstrip('/')}/v1",
                api_key="ollama",
                max_retries=0
            ),
            mode=instructor.Mode.JSON
        )

        logger.info("Classifying intent using instructor on model '%s'", model_name)
        response: ClassificationSchema = client.chat.completions.create(
            model=model_name,
            response_model=ClassificationSchema,
            messages=[
                {"role": "system", "content": _CLASSIFIER_SYSTEM_PROMPT},
                {"role": "user", "content": f"Query: {query}"}
            ],
            timeout=timeout_ms / 1000,
            max_retries=2
        )

        _stats["model_calls"] += 1
        return ClassificationResult(
            intent=response.intent,
            complexity=response.complexity,
            confidence=response.confidence,
            method="model",
        )

    except Exception as e:
        logger.warning(
            "Guided intent classification failed: %s. Falling back to keyword search.",
            e,
        )
        return _classify_by_keywords(query)
