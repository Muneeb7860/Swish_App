from pydantic import BaseModel, Field, field_validator
from governance.guardrails.guardrails_ai import Guard


class MockPricingSchema(BaseModel):
    price: float = Field(..., gt=0.0)
    confidence: float = Field(..., ge=0.0, le=1.0)
    rationale: str

    @field_validator("rationale")
    @classmethod
    def validate_rationale(cls, v: str) -> str:
        if len(v) < 3:
            raise ValueError("Rationale must be at least 3 characters")
        return v


def test_guardrails_ai_valid():
    guard = Guard.from_pydantic(MockPricingSchema)

    # Valid JSON string
    raw_response = (
        '{"price": 4.5, "confidence": 0.9, "rationale": "Valid price based on competitor index"}'
    )
    res = guard.parse(raw_response)

    assert res.validation_passed is True
    assert res.validated_output["price"] == 4.5
    assert len(res.errors) == 0


def test_guardrails_ai_invalid():
    guard = Guard.from_pydantic(MockPricingSchema)

    # Invalid JSON string failing Pydantic constraints
    raw_response = '{"price": -1.2, "confidence": 1.5, "rationale": "ok"}'
    res = guard.parse(raw_response)

    assert res.validation_passed is False
    assert len(res.errors) == 3

    # Verify that descriptive validation errors are present
    assert any("price" in err for err in res.errors)
    assert any("confidence" in err for err in res.errors)
    assert any("rationale" in err for err in res.errors)


def test_guardrails_ai_malformed_json():
    guard = Guard.from_pydantic(MockPricingSchema)

    # Malformed JSON syntax
    raw_response = '{"price": 4.5, "confidence": 0.9, "rationale": "incomplete'
    res = guard.parse(raw_response)

    assert res.validation_passed is False
    assert any("syntax error" in err.lower() for err in res.errors)
