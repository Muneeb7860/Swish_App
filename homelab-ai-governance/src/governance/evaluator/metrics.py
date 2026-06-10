"""Quality evaluation metrics — CI, FIS, CCR scoring for the output evaluation layer.

Implements the three heuristic firewall metrics from the Recursive Validation design:
- Completeness Index (CI): Semantic coverage of requirements
- Format Integrity Score (FIS): Structural validation (JSON, Markdown)
- Context Conservation Ratio (CCR): Source grounding / anti-hallucination
"""

from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from typing import Any

logger = logging.getLogger(__name__)


@dataclass
class EvaluationScores:
    """Individual and aggregated quality scores."""

    ci: float  # Completeness Index
    fis: float  # Format Integrity Score
    ccr: float  # Context Conservation Ratio
    total: float  # Weighted aggregate
    passed: bool
    details: dict[str, Any]


def evaluate_output(
    candidate: str,
    original_prompt: str,
    context_docs: str = "",
    expected_format: str | None = None,
    weights: dict[str, float] | None = None,
    threshold: float = 0.75,
) -> EvaluationScores:
    """Run all three quality metrics and aggregate the total score.

    Args:
        candidate: The model's output to evaluate.
        original_prompt: The original user query.
        context_docs: Retrieved context documents (for CCR grounding).
        expected_format: "json" if JSON output is expected, None otherwise.
        weights: Override default weights {completeness, format_integrity, context_conservation}.
        threshold: Passing threshold for the total score.

    Returns:
        EvaluationScores with individual and aggregate scores.
    """
    w = weights or {"completeness": 0.3, "format_integrity": 0.4, "context_conservation": 0.3}
    w_ci = w.get("completeness", 0.3)
    w_fis = w.get("format_integrity", 0.4)
    w_ccr = w.get("context_conservation", 0.3)

    ci, ci_details = compute_completeness_index(candidate, original_prompt)
    fis, fis_details = compute_format_integrity(candidate, expected_format)
    ccr, ccr_details = compute_context_conservation(candidate, context_docs)

    # FIS critical failure clamp: corrupt JSON → total < threshold regardless
    if expected_format == "json" and fis == 0.0:
        total = 0.0
    else:
        total = w_ci * ci + w_fis * fis + w_ccr * ccr

    return EvaluationScores(
        ci=round(ci, 3),
        fis=round(fis, 3),
        ccr=round(ccr, 3),
        total=round(total, 3),
        passed=total >= threshold,
        details={
            "ci": ci_details,
            "fis": fis_details,
            "ccr": ccr_details,
        },
    )


# ── Completeness Index (CI) ─────────────────────────────────────────────────


def compute_completeness_index(
    candidate: str, prompt: str
) -> tuple[float, dict[str, Any]]:
    """Measure how completely the candidate addresses the prompt.

    MVP heuristic: Extract key directives from the prompt (questions, commands)
    and check if the candidate references them via token overlap.
    """
    directives = _extract_directives(prompt)

    if not directives:
        return 1.0, {"directives": [], "covered": [], "method": "no_directives"}

    candidate_lower = candidate.lower()
    covered = []
    for directive in directives:
        # Check if key tokens from the directive appear in the candidate
        tokens = set(directive.lower().split())
        # Require at least 40% of directive tokens to appear
        matched = sum(1 for t in tokens if t in candidate_lower)
        coverage = matched / max(len(tokens), 1)
        if coverage >= 0.4:
            covered.append(directive)

    score = len(covered) / len(directives) if directives else 1.0

    return score, {
        "directives": directives,
        "covered": covered,
        "total": len(directives),
        "method": "token_overlap",
    }


def _extract_directives(prompt: str) -> list[str]:
    """Extract key directives (questions, numbered items) from the prompt."""
    directives: list[str] = []

    # Extract questions
    questions = re.findall(r"[^.!?\n]*\?", prompt)
    directives.extend(q.strip() for q in questions if len(q.strip()) > 10)

    # Extract numbered/bulleted items
    items = re.findall(r"(?:^|\n)\s*(?:\d+[.)]\s*|[-*]\s+)(.+)", prompt)
    directives.extend(i.strip() for i in items if len(i.strip()) > 5)

    # If no structured directives found, use the whole prompt as one directive
    if not directives:
        directives = [prompt.strip()[:200]]

    return directives


# ── Format Integrity Score (FIS) ─────────────────────────────────────────────


def compute_format_integrity(
    candidate: str, expected_format: str | None
) -> tuple[float, dict[str, Any]]:
    """Validate structural requirements of the candidate output.

    - If expected_format == "json": parse with json.loads()
    - Otherwise: basic structural checks (non-empty, reasonable length)
    """
    if not candidate or not candidate.strip():
        return 0.0, {"error": "empty_response"}

    if expected_format == "json":
        try:
            # Try to extract JSON from response (may be wrapped in markdown)
            json_match = re.search(r"(\{[\s\S]*\}|\[[\s\S]*\])", candidate)
            if json_match:
                json.loads(json_match.group())
                return 1.0, {"valid_json": True, "extracted": True}
            else:
                json.loads(candidate)
                return 1.0, {"valid_json": True, "extracted": False}
        except json.JSONDecodeError as e:
            return 0.0, {"valid_json": False, "error": str(e)}

    # For non-JSON output, check basic structure
    checks = {
        "non_empty": bool(candidate.strip()),
        "reasonable_length": len(candidate.strip()) > 20,
        "not_truncated": not candidate.rstrip().endswith("..."),
    }
    score = sum(checks.values()) / len(checks)
    return score, checks


# ── Context Conservation Ratio (CCR) ────────────────────────────────────────


def compute_context_conservation(
    candidate: str, context_docs: str
) -> tuple[float, dict[str, Any]]:
    """Measure source grounding of the candidate against context documents.

    MVP: Token overlap ratio between candidate claims and context.
    Phase 5: Replace with NLI contradiction analysis.
    """
    if not context_docs or not context_docs.strip():
        # No context to check against — pass by default
        return 1.0, {"method": "no_context", "note": "No context docs provided"}

    # Tokenize both
    context_tokens = set(_normalize_tokens(context_docs))
    candidate_tokens = set(_normalize_tokens(candidate))

    if not candidate_tokens:
        return 0.0, {"method": "token_overlap", "error": "no_candidate_tokens"}

    # What fraction of candidate tokens appear in the context?
    grounded = candidate_tokens & context_tokens
    # Filter out stop words for a more meaningful ratio
    stop_words = {
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "shall",
        "should", "may", "might", "must", "can", "could", "to", "of", "in",
        "for", "on", "with", "at", "by", "from", "as", "into", "through",
        "during", "before", "after", "above", "below", "and", "but", "or",
        "not", "no", "nor", "so", "yet", "both", "either", "neither",
        "this", "that", "these", "those", "it", "its",
    }
    meaningful_candidate = candidate_tokens - stop_words
    meaningful_grounded = grounded - stop_words

    if not meaningful_candidate:
        return 1.0, {"method": "token_overlap", "note": "only_stop_words"}

    ratio = len(meaningful_grounded) / len(meaningful_candidate)

    return min(ratio, 1.0), {
        "method": "token_overlap",
        "candidate_tokens": len(meaningful_candidate),
        "grounded_tokens": len(meaningful_grounded),
        "ratio": round(ratio, 3),
    }


def _normalize_tokens(text: str) -> list[str]:
    """Lowercase, strip punctuation, and split into tokens."""
    cleaned = re.sub(r"[^\w\s]", " ", text.lower())
    return [t for t in cleaned.split() if len(t) > 2]
