"""REST API server exposing the governance pipeline."""

from __future__ import annotations

import logging
from typing import Any
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from governance.pipeline import execute_pipeline

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Homelab AI Governance Service", version="0.1.0")


class GovernRequest(BaseModel):
    query: str
    expected_format: str | None = None
    local_only_override: bool = False


@app.post("/api/v1/govern")
def govern(req: GovernRequest) -> dict[str, Any]:
    """Execute the query governance pipeline."""
    try:
        logger.info("Governing query: %s", req.query[:100])
        res = execute_pipeline(
            query=req.query,
            expected_format=req.expected_format,
            local_only_override=req.local_only_override,
        )
        return res
    except Exception as e:
        logger.exception("Governance pipeline execution failed")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health() -> dict[str, str]:
    """Check service health."""
    return {"status": "UP"}
