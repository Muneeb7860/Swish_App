"""Recursive self-correction loop — the core of Phase 4 Output Evaluation Layer.

When a candidate output fails the quality threshold (< 0.75), this loop:
1. Constructs a feedback prompt with specific validation errors
2. Re-submits to the same agent for correction
3. Re-evaluates the corrected output
4. Repeats up to max_retries (default 3) times
5. On exhaustion, drops the payload and routes to Gemma 4B fallback
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

from governance.agents.base import AgentResponse, BaseAgent
from governance.audit import get_audit_logger
from governance.config import load_routing_config
from governance.evaluator.metrics import EvaluationScores, evaluate_output

logger = logging.getLogger(__name__)


# ── Feedback prompt template ─────────────────────────────────────────────────

_FEEDBACK_TEMPLATE = """[SYSTEM INSTRUCTION: SELF-CORRECTION]
Your previous response failed our quality and compliance validation checks.
Review the original request, your previous attempt, and the specific validation errors detailed below.
Generate a corrected output that resolves all listed failures.
Ensure you adhere strictly to any requested formats (e.g., JSON schemas) and source context constraints.

---

### ORIGINAL REQUEST
{original_prompt}

---

### CONTEXT DOCUMENTS
{context_docs}

---

### PREVIOUS RESPONSE (FAILED ATTEMPT)
{candidate_response}

---

### VALIDATION ERRORS DETECTED
{validation_error_summary}

---

### INSTRUCTIONS TO RECTIFY
1. Correct format syntax issues (ensure valid matching braces/quotes).
2. Remove any claims or facts not explicitly supported by the context documents.
3. Address all sub-questions from the original prompt.

Corrected Response:"""


@dataclass
class LoopResult:
    """Result of the self-correction loop."""

    final_response: str
    scores: EvaluationScores
    attempts: int
    passed: bool
    fallback_used: bool
    attempt_history: list[dict[str, Any]] = field(default_factory=list)


def run_self_correction_loop(
    agent: BaseAgent,
    candidate: str,
    original_prompt: str,
    context_docs: str = "",
    expected_format: str | None = None,
    fallback_agent: BaseAgent | None = None,
) -> LoopResult:
    """Execute the recursive validation and self-correction loop.

    Args:
        agent: The primary agent that produced the candidate output.
        candidate: The initial candidate output to evaluate.
        original_prompt: The original user prompt.
        context_docs: Retrieved context documents for CCR grounding.
        expected_format: Expected output format ("json" or None).
        fallback_agent: Agent to use if all retries are exhausted.

    Returns:
        LoopResult with the final response, scores, and attempt history.
    """
    routing_cfg = load_routing_config()
    eval_cfg = routing_cfg.get("evaluation_rules", {})
    max_retries = eval_cfg.get("max_self_correction_retries", 3)
    threshold = eval_cfg.get("passing_threshold", 0.75)
    weights = eval_cfg.get("weights")
    audit = get_audit_logger()

    current_candidate = candidate
    attempt_history: list[dict[str, Any]] = []

    for attempt in range(max_retries + 1):  # 0 = initial, 1-3 = retries
        # Evaluate the current candidate
        scores = evaluate_output(
            candidate=current_candidate,
            original_prompt=original_prompt,
            context_docs=context_docs,
            expected_format=expected_format,
            weights=weights,
            threshold=threshold,
        )

        attempt_record = {
            "attempt": attempt,
            "scores": {
                "ci": scores.ci,
                "fis": scores.fis,
                "ccr": scores.ccr,
                "total": scores.total,
            },
            "passed": scores.passed,
        }
        attempt_history.append(attempt_record)

        logger.info(
            "Evaluation attempt %d/%d: total=%.3f (threshold=%.2f) — %s",
            attempt,
            max_retries,
            scores.total,
            threshold,
            "PASS" if scores.passed else "FAIL",
        )

        if scores.passed:
            return LoopResult(
                final_response=current_candidate,
                scores=scores,
                attempts=attempt + 1,
                passed=True,
                fallback_used=False,
                attempt_history=attempt_history,
            )

        # If this was the last retry, don't try to correct again
        if attempt >= max_retries:
            break

        # Build feedback prompt and re-submit
        error_summary = _build_error_summary(scores)
        feedback_prompt = _FEEDBACK_TEMPLATE.format(
            original_prompt=original_prompt,
            context_docs=context_docs,
            candidate_response=current_candidate,
            validation_error_summary=error_summary,
        )

        try:
            correction: AgentResponse = agent.generate(feedback_prompt)
            current_candidate = correction.text
        except Exception as e:
            logger.error(
                "Self-correction attempt %d failed (agent error): %s",
                attempt + 1,
                e,
            )
            break

    # All retries exhausted — escalate to fallback
    logger.warning(
        "Self-correction exhausted (%d attempts). "
        "Final score: %.3f. Escalating to fallback.",
        max_retries + 1,
        scores.total,
    )

    # Log escalation event
    audit.log_event(
        "validation_failure_escalated",
        agent_id=agent.agent_id,
        retry_count=max_retries,
        final_score=scores.total,
        scores={"ci": scores.ci, "fis": scores.fis, "ccr": scores.ccr},
        fallback_agent=fallback_agent.agent_id if fallback_agent else "none",
    )

    if fallback_agent is not None:
        try:
            # Strip complex formatting from the fallback prompt for reliability
            simplified_prompt = (
                f"Please answer the following question clearly and concisely:\n\n"
                f"{original_prompt}"
            )
            fallback_response = fallback_agent.generate(simplified_prompt)
            fallback_scores = evaluate_output(
                candidate=fallback_response.text,
                original_prompt=original_prompt,
                context_docs=context_docs,
                weights=weights,
                threshold=threshold,
            )

            return LoopResult(
                final_response=fallback_response.text,
                scores=fallback_scores,
                attempts=max_retries + 2,  # +1 for fallback attempt
                passed=fallback_scores.passed,
                fallback_used=True,
                attempt_history=attempt_history,
            )
        except Exception as e:
            logger.error("Fallback agent also failed: %s", e)

    # Complete failure — return the best attempt with failure flag
    return LoopResult(
        final_response=current_candidate,
        scores=scores,
        attempts=max_retries + 1,
        passed=False,
        fallback_used=False,
        attempt_history=attempt_history,
    )


def _build_error_summary(scores: EvaluationScores) -> str:
    """Build a human-readable error summary from evaluation scores."""
    errors: list[str] = []

    if scores.ci < 0.75:
        details = scores.details.get("ci", {})
        covered = details.get("covered", [])
        total = details.get("total", 0)
        errors.append(
            f"- COMPLETENESS (CI={scores.ci:.2f}): Only addressed {len(covered)}/{total} "
            f"requirements from the original prompt."
        )

    if scores.fis < 1.0:
        details = scores.details.get("fis", {})
        if details.get("valid_json") is False:
            errors.append(
                f"- FORMAT INTEGRITY (FIS={scores.fis:.2f}): Invalid JSON output. "
                f"Error: {details.get('error', 'unknown')}"
            )
        elif details.get("error") == "empty_response":
            errors.append(
                f"- FORMAT INTEGRITY (FIS={scores.fis:.2f}): Response is empty."
            )
        else:
            errors.append(
                f"- FORMAT INTEGRITY (FIS={scores.fis:.2f}): Structural issues detected."
            )

    if scores.ccr < 0.75:
        details = scores.details.get("ccr", {})
        errors.append(
            f"- CONTEXT CONSERVATION (CCR={scores.ccr:.2f}): Response contains "
            f"claims not grounded in the provided context. "
            f"Grounded tokens: {details.get('grounded_tokens', '?')}"
            f"/{details.get('candidate_tokens', '?')}"
        )

    if not errors:
        errors.append("- Total quality score below threshold.")

    return "\n".join(errors)
