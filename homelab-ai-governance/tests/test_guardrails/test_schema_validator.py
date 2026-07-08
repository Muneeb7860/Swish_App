"""Unit tests for the Pydantic RAIL schema validator.

Tests:
- CustomerSupportSchema: valid, invalid, missing field, wrong type
- DynamicPricingSchema: valid, out-of-range, missing rationale
- ClassificationSchema: valid, invalid intent, invalid complexity
- GenericTextSchema: non-empty, empty, whitespace-only
- validate_output():
    - markdown code fence extraction
    - not-JSON text → failure
    - unknown schema name → failure
    - schema alias resolution
"""

from __future__ import annotations

import json
import pytest
from governance.guardrails.schemas import validate_output, list_schemas


# ── CustomerSupportSchema ─────────────────────────────────────────────────────

class TestCustomerSupportSchema:
    def test_valid_full(self):
        text = json.dumps({"reply": "Your order is on its way.", "confidence": 0.92, "tool": None})
        valid, errors, parsed = validate_output(text, "CustomerSupportSchema")
        assert valid is True
        assert errors == []
        assert parsed["reply"] == "Your order is on its way."
        assert parsed["confidence"] == 0.92
        assert parsed["tool"] is None

    def test_valid_with_tool(self):
        text = json.dumps({"reply": "Checked the tracker.", "confidence": 0.8, "tool": "order_lookup"})
        valid, errors, parsed = validate_output(text, "CustomerSupportSchema")
        assert valid is True
        assert parsed["tool"] == "order_lookup"

    def test_missing_reply_field(self):
        text = json.dumps({"confidence": 0.7, "tool": None})
        valid, errors, _ = validate_output(text, "CustomerSupportSchema")
        assert valid is False
        assert any("reply" in e for e in errors)

    def test_confidence_out_of_range_high(self):
        text = json.dumps({"reply": "Hi", "confidence": 1.5, "tool": None})
        valid, errors, _ = validate_output(text, "CustomerSupportSchema")
        assert valid is False
        assert any("confidence" in e for e in errors)

    def test_confidence_out_of_range_low(self):
        text = json.dumps({"reply": "Hi", "confidence": -0.1, "tool": None})
        valid, errors, _ = validate_output(text, "CustomerSupportSchema")
        assert valid is False

    def test_empty_reply_fails(self):
        text = json.dumps({"reply": "   ", "confidence": 0.5, "tool": None})
        valid, errors, _ = validate_output(text, "CustomerSupportSchema")
        assert valid is False

    def test_alias_customer_support(self):
        text = json.dumps({"reply": "Hello!", "confidence": 0.75, "tool": None})
        valid, errors, _ = validate_output(text, "customer_support")
        assert valid is True


# ── DynamicPricingSchema ──────────────────────────────────────────────────────

class TestDynamicPricingSchema:
    def test_valid(self):
        text = json.dumps({
            "surgeMultiplier": 1.2,
            "discountPercent": 5.0,
            "confidence": 0.88,
            "rationale": "Moderate demand, slight rain.",
        })
        valid, errors, parsed = validate_output(text, "DynamicPricingSchema")
        assert valid is True
        assert parsed["surgeMultiplier"] == 1.2

    def test_surge_too_high(self):
        text = json.dumps({
            "surgeMultiplier": 6.0,
            "discountPercent": 0.0,
            "confidence": 0.9,
            "rationale": "Extreme surge.",
        })
        valid, errors, _ = validate_output(text, "DynamicPricingSchema")
        assert valid is False
        assert any("surgeMultiplier" in e for e in errors)

    def test_surge_too_low(self):
        text = json.dumps({
            "surgeMultiplier": 0.4,
            "discountPercent": 0.0,
            "confidence": 0.9,
            "rationale": "Under minimum.",
        })
        valid, errors, _ = validate_output(text, "DynamicPricingSchema")
        assert valid is False

    def test_missing_rationale(self):
        text = json.dumps({"surgeMultiplier": 1.0, "discountPercent": 0.0, "confidence": 0.9})
        valid, errors, _ = validate_output(text, "DynamicPricingSchema")
        assert valid is False
        assert any("rationale" in e for e in errors)

    def test_empty_rationale(self):
        text = json.dumps({
            "surgeMultiplier": 1.0,
            "discountPercent": 0.0,
            "confidence": 0.9,
            "rationale": "",
        })
        valid, errors, _ = validate_output(text, "DynamicPricingSchema")
        assert valid is False

    def test_discount_over_100(self):
        text = json.dumps({
            "surgeMultiplier": 1.0,
            "discountPercent": 101.0,
            "confidence": 0.9,
            "rationale": "Over max discount.",
        })
        valid, errors, _ = validate_output(text, "DynamicPricingSchema")
        assert valid is False

    def test_alias_dynamic_pricing(self):
        text = json.dumps({
            "surgeMultiplier": 1.0,
            "discountPercent": 0.0,
            "confidence": 0.9,
            "rationale": "Baseline pricing.",
        })
        valid, _, _ = validate_output(text, "dynamic_pricing")
        assert valid is True


# ── ClassificationSchema ──────────────────────────────────────────────────────

class TestClassificationSchema:
    def test_valid(self):
        text = json.dumps({"intent": "order", "complexity": "low", "confidence": 0.95})
        valid, errors, parsed = validate_output(text, "ClassificationSchema")
        assert valid is True
        assert parsed["intent"] == "order"

    def test_invalid_intent(self):
        text = json.dumps({"intent": "garbage_intent", "complexity": "low", "confidence": 0.8})
        valid, errors, _ = validate_output(text, "ClassificationSchema")
        assert valid is False
        assert any("intent" in e for e in errors)

    def test_invalid_complexity(self):
        text = json.dumps({"intent": "order", "complexity": "extreme", "confidence": 0.8})
        valid, errors, _ = validate_output(text, "ClassificationSchema")
        assert valid is False
        assert any("complexity" in e for e in errors)

    def test_confidence_boundary_zero(self):
        text = json.dumps({"intent": "support", "complexity": "medium", "confidence": 0.0})
        valid, _, _ = validate_output(text, "ClassificationSchema")
        assert valid is True

    def test_confidence_boundary_one(self):
        text = json.dumps({"intent": "pricing", "complexity": "high", "confidence": 1.0})
        valid, _, _ = validate_output(text, "ClassificationSchema")
        assert valid is True

    def test_all_valid_intents(self):
        intents = [
            "general_knowledge", "inventory", "rider", "order",
            "support", "pricing", "system_admin", "logistics", "procurement"
        ]
        for intent in intents:
            text = json.dumps({"intent": intent, "complexity": "low", "confidence": 0.9})
            valid, errors, _ = validate_output(text, "ClassificationSchema")
            assert valid is True, f"Intent '{intent}' should be valid but got errors: {errors}"


# ── GenericTextSchema ─────────────────────────────────────────────────────────

class TestGenericTextSchema:
    def test_non_empty_string(self):
        valid, errors, parsed = validate_output("Hello, this is a response.", "GenericTextSchema")
        assert valid is True
        assert errors == []
        assert parsed["response"] == "Hello, this is a response."

    def test_empty_string_fails(self):
        valid, errors, _ = validate_output("", "GenericTextSchema")
        assert valid is False

    def test_whitespace_only_fails(self):
        valid, errors, _ = validate_output("   \n   ", "GenericTextSchema")
        assert valid is False

    def test_alias_generic(self):
        valid, _, _ = validate_output("Some text response.", "generic")
        assert valid is True


# ── validate_output() edge cases ─────────────────────────────────────────────

class TestValidateOutputEdgeCases:
    def test_unknown_schema_name(self):
        valid, errors, _ = validate_output('{"reply": "hi"}', "NonExistentSchema")
        assert valid is False
        assert any("Unknown schema" in e for e in errors)

    def test_not_json_text_fails_typed_schema(self):
        valid, errors, _ = validate_output(
            "This is not JSON at all, just plain text.", "CustomerSupportSchema"
        )
        assert valid is False
        assert any("not valid JSON" in e for e in errors)

    def test_markdown_code_fence_extraction(self):
        """Model sometimes wraps JSON in triple backticks — should still parse."""
        text = '```json\n{"reply": "Hi!", "confidence": 0.9, "tool": null}\n```'
        valid, errors, parsed = validate_output(text, "CustomerSupportSchema")
        assert valid is True, f"Should strip code fences but got: {errors}"
        assert parsed["reply"] == "Hi!"

    def test_markdown_code_fence_no_lang(self):
        text = '```\n{"reply": "Yo!", "confidence": 0.85, "tool": null}\n```'
        valid, _, parsed = validate_output(text, "CustomerSupportSchema")
        assert valid is True
        assert parsed["reply"] == "Yo!"

    def test_extra_json_fields_are_ignored(self):
        """Pydantic by default strips extra fields — should still be valid."""
        text = json.dumps({
            "reply": "Hello",
            "confidence": 0.9,
            "tool": None,
            "extra_field": "should be ignored",
        })
        valid, errors, parsed = validate_output(text, "CustomerSupportSchema")
        assert valid is True

    def test_list_schemas_returns_canonical_names(self):
        schemas = list_schemas()
        assert "CustomerSupportSchema" in schemas
        assert "DynamicPricingSchema" in schemas
        assert "ClassificationSchema" in schemas
        assert "GenericTextSchema" in schemas
        # Aliases should NOT appear in list_schemas()
        assert "customer_support" not in schemas
        assert "generic" not in schemas
