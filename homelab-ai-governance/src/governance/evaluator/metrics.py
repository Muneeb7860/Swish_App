"""Quality evaluation metrics — CI, FIS, CCR scoring for the output evaluation layer.

Implements the three heuristic firewall metrics from the Recursive Validation design:
- Completeness Index (CI): Semantic coverage of requirements
- Format Integrity Score (FIS): Structural validation (JSON, Markdown)
- Context Conservation Ratio (CCR): Source grounding / anti-hallucination
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any

from governance.guardrails.guardrails_ai import Guard
from governance.guardrails.schemas import CustomerSupportSchema, DynamicPricingSchema


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
    fis, fis_details = compute_format_integrity(candidate, expected_format, original_prompt)
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


def compute_completeness_index(candidate: str, prompt: str) -> tuple[float, dict[str, Any]]:
    """Measure how completely the candidate addresses the prompt.

    MVP heuristic: Extract key directives from the prompt (questions, commands)
    and check if the candidate references them via token overlap.
    """
    directives = _extract_directives(prompt)

    if not directives:
        return 1.0, {"directives": [], "covered": [], "method": "no_directives"}

    candidate_lower = candidate.lower()
    covered = []
    candidate_tokens = set(re.sub(r"[^\w\s]", "", candidate_lower).split())
    for directive in directives:
        # Check if key tokens from the directive appear in the candidate
        tokens = set(re.sub(r"[^\w\s]", "", directive.lower()).split())
        # Require at least 40% of directive tokens to appear
        matched = sum(1 for t in tokens if t in candidate_tokens)
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


def _check_nesting_depth(s: str, max_depth: int = 20) -> bool:
    """Validate that the nesting depth of braces/brackets does not exceed max_depth.

    Prevents RecursionError/Denial-of-Service when parsing malicious nested structures.
    """
    depth = 0
    for char in s:
        if char in "{[":
            depth += 1
            if depth > max_depth:
                return False
        elif char in "}]":
            depth -= 1
    return True


def compute_format_integrity(
    candidate: str, expected_format: str | None, original_prompt: str = ""
) -> tuple[float, dict[str, Any]]:
    """Validate structural requirements of the candidate output.

    - If expected_format == "json": parse with json.loads() and validate against Pydantic schemas if applicable
    - Otherwise: basic structural checks (non-empty, reasonable length)
    """
    if not candidate or not candidate.strip():
        return 0.0, {"error": "empty_response"}

    if expected_format == "json":
        try:
            # Try to extract JSON from response (may be wrapped in markdown)
            json_match = re.search(r"(\{[\s\S]*\}|\[[\s\S]*\])", candidate)
            target_str = json_match.group() if json_match else candidate

            # Enforce nesting depth ceiling to prevent stack-overflow / ReDoS
            if not _check_nesting_depth(target_str, max_depth=20):
                return 0.0, {
                    "valid_json": False,
                    "error": "Exceeded maximum JSON nesting depth of 20",
                }

            json.loads(target_str)

            # Pydantic schema validation based on the original prompt context using Guardrails AI
            prompt_lower = original_prompt.lower()
            schema_name = "generic"
            if "customer support agent" in prompt_lower:
                schema_name = "customer_support"
                guard = Guard.from_pydantic(CustomerSupportSchema)
                res = guard.parse(candidate)
                if not res.validation_passed:
                    return 0.0, {
                        "valid_json": True,
                        "valid_schema": False,
                        "schema": schema_name,
                        "error": f"Schema validation failed: {'; '.join(res.errors)}",
                    }
            elif "dynamic pricing agent" in prompt_lower:
                schema_name = "dynamic_pricing"
                guard = Guard.from_pydantic(DynamicPricingSchema)
                res = guard.parse(candidate)
                if not res.validation_passed:
                    return 0.0, {
                        "valid_json": True,
                        "valid_schema": False,
                        "schema": schema_name,
                        "error": f"Schema validation failed: {'; '.join(res.errors)}",
                    }

            return 1.0, {
                "valid_json": True,
                "valid_schema": True,
                "schema": schema_name,
                "extracted": bool(json_match),
            }
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


def compute_context_conservation(candidate: str, context_docs: str) -> tuple[float, dict[str, Any]]:
    """Measure source grounding of the candidate against context documents.

    Includes a numeric entity guard to penalize scores heavily when new/hallucinated
    numbers appear in the response that are not grounded in the context.
    """
    if not context_docs or not context_docs.strip():
        # No context to check against — pass by default
        return 1.0, {"method": "no_context", "note": "No context docs provided"}

    # Extract numbers (integers or decimals) from candidate and context
    candidate_numbers = set(re.findall(r"\b\d+(?:[.,]\d+)?\b", candidate))
    context_numbers = set(re.findall(r"\b\d+(?:[.,]\d+)?\b", context_docs))

    # Strip formatting like commas/dots from numbers for exact comparison
    normalized_candidate_nums = {num.replace(",", "").replace(".", "") for num in candidate_numbers}
    normalized_context_nums = {num.replace(",", "").replace(".", "") for num in context_numbers}

    # Filter out single digits 0-9 to avoid list indices triggering violations
    unsupported_numbers = {
        num
        for num in (normalized_candidate_nums - normalized_context_nums)
        if len(num) > 1 or num not in {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"}
    }

    # Tokenize both
    context_tokens = set(_normalize_tokens(context_docs))
    candidate_tokens = set(_normalize_tokens(candidate))

    if not candidate_tokens:
        return 0.0, {"method": "token_overlap", "error": "no_candidate_tokens"}

    # What fraction of candidate tokens appear in the context?
    grounded = candidate_tokens & context_tokens
    # Filter out stop words for a more meaningful ratio
    stop_words = {
        "the",
        "a",
        "an",
        "is",
        "are",
        "was",
        "were",
        "be",
        "been",
        "being",
        "have",
        "has",
        "had",
        "do",
        "does",
        "did",
        "will",
        "would",
        "shall",
        "should",
        "may",
        "might",
        "must",
        "can",
        "could",
        "to",
        "of",
        "in",
        "for",
        "on",
        "with",
        "at",
        "by",
        "from",
        "as",
        "into",
        "through",
        "during",
        "before",
        "after",
        "above",
        "below",
        "and",
        "but",
        "or",
        "not",
        "no",
        "nor",
        "so",
        "yet",
        "both",
        "either",
        "neither",
        "this",
        "that",
        "these",
        "those",
        "it",
        "its",
    }
    redaction_tokens = {
        "redacted",
        "email",
        "ssn",
        "phone",
        "card",
        "ip",
        "address",
        "connection",
        "string",
        "api",
        "key",
        "us",
    }
    meaningful_candidate = candidate_tokens - stop_words - redaction_tokens
    meaningful_grounded = grounded - stop_words - redaction_tokens

    if not meaningful_candidate:
        return 1.0, {"method": "token_overlap", "note": "only_stop_words"}

    ratio = len(meaningful_grounded) / len(meaningful_candidate)

    # Apply penalty for hallucinated numeric sequences
    penalty = 0.2 * len(unsupported_numbers)
    final_ratio = max(0.0, ratio - penalty)

    return min(final_ratio, 1.0), {
        "method": "token_overlap_with_numeric_guard",
        "candidate_tokens": len(meaningful_candidate),
        "grounded_tokens": len(meaningful_grounded),
        "unsupported_numbers": list(unsupported_numbers),
        "penalty": round(penalty, 3),
        "ratio": round(final_ratio, 3),
    }


def _normalize_tokens(text: str) -> list[str]:
    """Lowercase, strip punctuation, and split into tokens."""
    cleaned = re.sub(r"[^\w\s]", " ", text.lower())
    return [t for t in cleaned.split() if len(t) > 2 or t.isdigit()]
