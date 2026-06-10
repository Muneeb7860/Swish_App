"""Tests for the output evaluation heuristics engine."""

from __future__ import annotations

from governance.evaluator.metrics import (
    compute_completeness_index,
    compute_context_conservation,
    compute_format_integrity,
    evaluate_output,
)


def test_completeness_index():
    """Verify that CI accurately measures directive coverage."""
    # Heuristic matches questions
    prompt = "How is weather? What is your budget limit?"
    
    # 2 directives: "How is weather?", "What is your budget limit?"
    # Answer covers both
    ans_full = "The weather is nice. The budget limit is 5.00 USD."
    score, details = compute_completeness_index(ans_full, prompt)
    assert score == 1.0
    assert len(details["covered"]) == 2

    # Answer covers only one
    ans_partial = "The weather is very sunny today!"
    score_p, details_p = compute_completeness_index(ans_partial, prompt)
    assert score_p == 0.5
    assert len(details_p["covered"]) == 1


def test_format_integrity_json():
    """Verify that FIS validates JSON output format correctly."""
    # Valid JSON
    valid_json = '{"weather": "sunny", "temp": 22}'
    score, details = compute_format_integrity(valid_json, expected_format="json")
    assert score == 1.0
    assert details["valid_json"] is True

    # Valid JSON wrapped in markdown
    wrapped_json = '```json\n{"weather": "sunny", "temp": 22}\n```'
    score_w, details_w = compute_format_integrity(wrapped_json, expected_format="json")
    assert score_w == 1.0

    # Invalid JSON
    invalid_json = '{"weather": "sunny", "temp": 22'  # missing brace
    score_i, details_i = compute_format_integrity(invalid_json, expected_format="json")
    assert score_i == 0.0
    assert details_i["valid_json"] is False


def test_context_conservation_ratio():
    """Verify that CCR accurately checks grounding against source context documents."""
    context = "The primary server is running Qwen on port 11434 in the Zurich hub."
    
    # Fully grounded
    grounded = " Zurich hub has a server running Qwen."
    score, details = compute_context_conservation(grounded, context)
    assert score > 0.8  # high overlap

    # Hallucination (contains lots of external words not in context)
    hallucinated = "The server is running Gemini 1.5 Pro on port 8080 in the Geneva office with AWS cloud backup."
    score_h, details_h = compute_context_conservation(hallucinated, context)
    assert score_h < 0.6  # lower overlap


def test_evaluate_output_aggregation():
    """Verify score aggregation and the JSON failure clamp."""
    prompt = "What is the capital of Switzerland?"
    context = "Switzerland has Bern as its capital city."
    
    # Passing response
    res_pass = "Bern is the capital city of Switzerland."
    scores = evaluate_output(
        candidate=res_pass,
        original_prompt=prompt,
        context_docs=context,
        expected_format=None,
    )
    assert scores.passed is True
    assert scores.total >= 0.75

    # FIS JSON clamp test: even with perfect CI/CCR, invalid JSON must yield total = 0.0
    scores_clamp = evaluate_output(
        candidate="Bern is the capital city of Switzerland (but not valid JSON)",
        original_prompt=prompt,
        context_docs=context,
        expected_format="json",
    )
    assert scores_clamp.passed is False
    assert scores_clamp.total == 0.0
