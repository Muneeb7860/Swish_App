"""Pydantic output schema validation — RAIL (Reliable AI Layer) for Swish OS agents.

Each agent response type has a typed schema. The `validate_output()` function
attempts to parse the LLM's text as JSON and validate it against the named schema.

If validation fails, the caller injects the error list into the self-correction
loop prompt so the model can fix its own output.

Schemas
-------
CustomerSupportSchema   — for customer-facing support agent responses
DynamicPricingSchema    — for surge/discount pricing decisions
ClassificationSchema    — for intent/complexity classification outputs
GenericTextSchema       — fallback: any non-empty string is valid

Usage
-----
    is_valid, errors, parsed = validate_output(text, "CustomerSupportSchema")
    if not is_valid:
        # re-trigger self-correction with errors injected into prompt
"""

from __future__ import annotations

import json
import logging
from typing import Any, ClassVar

from pydantic import BaseModel, Field, ValidationError, field_validator

logger = logging.getLogger(__name__)


# ── Schema definitions ────────────────────────────────────────────────────────


class CustomerSupportSchema(BaseModel):
    """Response from the customer support agent."""

    reply: str = Field(..., min_length=1, description="The agent's reply to the customer.")
    confidence: float = Field(
        ..., ge=0.0, le=1.0, description="Agent confidence score (0.0–1.0)."
    )
    tool: str | None = Field(
        None, description="Optional tool invoked to resolve the query."
    )
    tool_argument: str | None = Field(
        None, description="Optional argument passed to the tool."
    )

    @field_validator("reply")
    @classmethod
    def reply_not_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("reply must not be blank")
        return v


class DynamicPricingSchema(BaseModel):
    """Response from the dynamic pricing agent."""

    surgeMultiplier: float = Field(  # noqa: N815 — matches existing API contract
        ..., ge=0.5, le=3.0, description="Surge price multiplier (0.5x–3.0x)."
    )
    discountPercent: float = Field(  # noqa: N815
        ..., ge=0.0, le=50.0, description="Discount percentage to apply (0–50)."
    )
    confidence: float = Field(
        ..., ge=0.0, le=1.0, description="Pricing decision confidence."
    )
    rationale: str = Field(
        ..., min_length=1, description="Human-readable rationale for the pricing decision."
    )

    @field_validator("rationale")
    @classmethod
    def rationale_not_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("rationale must not be blank")
        return v


class ClassificationSchema(BaseModel):
    """Response from the intent classifier."""

    # Source of truth is the live classifier's Literal (router/classifier.py,
    # ClassificationSchema.intent) mirrored in routing_config.yaml `intents`.
    # These MUST stay in sync — test_schema_gate.py enforces it — otherwise the
    # "classification" RAIL schema rejects every real classifier output.
    VALID_INTENTS: ClassVar[set[str]] = {
        "general_knowledge",
        "code_generation",
        "code_debugging",
        "code_review",
        "summarization",
        "creative_writing",
        "data_analysis",
        "system_admin",
        "sensitive_query",
    }
    VALID_COMPLEXITIES: ClassVar[set[str]] = {"low", "medium", "high"}

    intent: str = Field(..., description="Classified intent category.")
    complexity: str = Field(..., description="Estimated query complexity.")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Classification confidence.")

    @field_validator("intent")
    @classmethod
    def intent_valid(cls, v: str) -> str:
        if v not in cls.VALID_INTENTS:
            raise ValueError(
                f"intent '{v}' is not recognised. "
                f"Valid intents: {sorted(cls.VALID_INTENTS)}"
            )
        return v

    @field_validator("complexity")
    @classmethod
    def complexity_valid(cls, v: str) -> str:
        if v not in cls.VALID_COMPLEXITIES:
            raise ValueError(
                f"complexity '{v}' is not recognised. "
                f"Valid values: {sorted(cls.VALID_COMPLEXITIES)}"
            )
        return v


class GenericTextSchema(BaseModel):
    """Fallback schema: any non-empty string response."""

    response: str = Field(..., min_length=1, description="The agent's text response.")

    @field_validator("response")
    @classmethod
    def response_not_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("response must not be blank")
        return v


# ── Schema registry ───────────────────────────────────────────────────────────

_SCHEMA_REGISTRY: dict[str, type[BaseModel]] = {
    "CustomerSupportSchema": CustomerSupportSchema,
    "DynamicPricingSchema": DynamicPricingSchema,
    "ClassificationSchema": ClassificationSchema,
    "GenericTextSchema": GenericTextSchema,
    # Convenient short aliases used by routing_config.yaml expected_format values
    "customer_support": CustomerSupportSchema,
    "dynamic_pricing": DynamicPricingSchema,
    "classification": ClassificationSchema,
    "generic": GenericTextSchema,
}


# ── Public API ────────────────────────────────────────────────────────────────


def validate_output(
    text: str,
    schema_name: str,
) -> tuple[bool, list[str], dict[str, Any]]:
    """Validate LLM output text against a named Pydantic schema.

    Attempts to parse `text` as JSON (required for all typed schemas except
    GenericTextSchema / 'generic'). If parsing or validation fails, returns
    a structured list of human-readable error strings suitable for injection
    into the self-correction prompt.

    Args:
        text:        Raw LLM output text.
        schema_name: Name of the schema (see _SCHEMA_REGISTRY for valid names).

    Returns:
        (is_valid, errors, parsed_dict)
        - is_valid:   True if the text fully conforms to the schema.
        - errors:     List of human-readable error messages (empty if is_valid).
        - parsed_dict: Validated model data as a dict (empty dict on failure).
    """
    schema_cls = _SCHEMA_REGISTRY.get(schema_name)
    if schema_cls is None:
        known = sorted(_SCHEMA_REGISTRY.keys())
        logger.warning("Unknown schema '%s'. Known schemas: %s", schema_name, known)
        return False, [f"Unknown schema '{schema_name}'. Known: {known}"], {}

    # GenericTextSchema / 'generic' — just check the raw text is non-empty
    if schema_cls is GenericTextSchema:
        stripped = text.strip()
        if stripped:
            return True, [], {"response": stripped}
        return False, ["Response is empty — a non-empty reply is required."], {}

    # All other schemas require valid JSON
    stripped = text.strip()

    # Extract JSON from markdown code fences if the model wrapped it
    if stripped.startswith("```"):
        lines = stripped.splitlines()
        inner = [l for l in lines if not l.startswith("```")]
        stripped = "\n".join(inner).strip()

    try:
        data = json.loads(stripped)
    except json.JSONDecodeError as e:
        return (
            False,
            [
                f"Output is not valid JSON. Parser error: {e}. "
                f"Ensure your response is a single valid JSON object with no extra text."
            ],
            {},
        )

    try:
        instance = schema_cls.model_validate(data)
        return True, [], instance.model_dump()
    except ValidationError as e:
        errors: list[str] = []
        for err in e.errors():
            field_path = " → ".join(str(p) for p in err["loc"])
            errors.append(f"Field '{field_path}': {err['msg']} (type: {err['type']})")
        return False, errors, {}


def list_schemas() -> list[str]:
    """Return canonical (non-alias) schema names."""
    return [k for k in _SCHEMA_REGISTRY if k[0].isupper()]


def is_rail_schema(name: str | None) -> bool:
    """True if `name` is a registered RAIL schema (not the legacy "json" flag
    or None). Call sites must guard with this before invoking validate_output —
    "json" selects metrics.py's raw-JSON format check, a different mechanism,
    and is deliberately NOT a registry key."""
    return name is not None and name in _SCHEMA_REGISTRY
