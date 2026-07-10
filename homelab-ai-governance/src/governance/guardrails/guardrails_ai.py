import json
import re
from typing import Any, List, Optional, Type
from pydantic import BaseModel, ValidationError


class GuardrailsAIResult:
    def __init__(
        self,
        validation_passed: bool,
        validated_output: Optional[Any],
        errors: List[str],
        raw_error: Optional[str] = None,
    ):
        self.validation_passed = validation_passed
        self.validated_output = validated_output
        self.errors = errors
        self.raw_error = raw_error


class Guard:
    def __init__(self, schema_class: Type[BaseModel]):
        self.schema_class = schema_class

    @classmethod
    def from_pydantic(cls, output_class: Type[BaseModel]) -> "Guard":
        return cls(output_class)

    def parse(self, content: str) -> GuardrailsAIResult:
        """Parse content and validate it against the registered schema, returning structured errors."""
        if not content or not content.strip():
            return GuardrailsAIResult(
                validation_passed=False,
                validated_output=None,
                errors=["Empty response received"],
                raw_error="Empty response",
            )

        # Extract JSON (matching Guardrails AI extraction capabilities)
        json_match = re.search(r"(\{[\s\S]*\}|\[[\s\S]*\])", content)
        target_str = json_match.group() if json_match else content

        try:
            parsed = json.loads(target_str)
        except json.JSONDecodeError as e:
            return GuardrailsAIResult(
                validation_passed=False,
                validated_output=None,
                errors=[f"JSON syntax error: {str(e)}"],
                raw_error=str(e),
            )

        try:
            # Validate schema
            validated_obj = self.schema_class(**parsed)
            return GuardrailsAIResult(
                validation_passed=True,
                validated_output=validated_obj.model_dump()
                if hasattr(validated_obj, "model_dump")
                else validated_obj.dict(),
                errors=[],
            )
        except ValidationError as val_err:
            # Extract structured field errors
            errors = []
            for err in val_err.errors():
                loc = " -> ".join(str(part) for part in err["loc"])
                msg = err["msg"]
                errors.append(f"Field '{loc}': {msg}")

            return GuardrailsAIResult(
                validation_passed=False,
                validated_output=None,
                errors=errors,
                raw_error=str(val_err),
            )
        except Exception as e:
            return GuardrailsAIResult(
                validation_passed=False,
                validated_output=None,
                errors=[f"Validation error: {str(e)}"],
                raw_error=str(e),
            )
