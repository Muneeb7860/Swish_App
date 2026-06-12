"""REST API server exposing the governance pipeline."""

from __future__ import annotations

import logging
from typing import Any
from fastapi import FastAPI, HTTPException
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel

from governance.pipeline import execute_pipeline
from governance.stubs.memory_mesh import MemoryMesh

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

import os

app = FastAPI(title="Homelab AI Governance Service", version="0.1.0")

# Instrument with OpenTelemetry
if os.environ.get("SWISH_TRACING_ENABLED", "true").lower() == "true":
    try:
        from opentelemetry import trace
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

        # Initialize OpenTelemetry Trace Provider
        resource = Resource.create(attributes={"service.name": "homelab-ai-governance"})
        provider = TracerProvider(resource=resource)

        # OTLP collector HTTP endpoint
        otlp_endpoint = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318/v1/traces")
        processor = BatchSpanProcessor(OTLPSpanExporter(endpoint=otlp_endpoint))
        provider.add_span_processor(processor)
        trace.set_tracer_provider(provider)

        # Instrument the app
        FastAPIInstrumentor.instrument_app(app)
        logger.info("OpenTelemetry instrumentation active exporting to %s", otlp_endpoint)
    except Exception as e:
        logger.warning("Failed to initialize OpenTelemetry instrumentation: %s", e)



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


@app.get("/metrics", response_class=PlainTextResponse)
def metrics() -> str:
    """Expose Prometheus-formatted metrics."""
    lines = [
        "# HELP rag_circuit_breaker_tripped_total Total number of RAG circuit breaker trips.",
        "# TYPE rag_circuit_breaker_tripped_total counter",
        f'rag_circuit_breaker_tripped_total{{type="db"}} {MemoryMesh.db_breaker_trips}',
        f'rag_circuit_breaker_tripped_total{{type="embedding"}} {MemoryMesh.embedding_breaker_trips}',
    ]
    return "\n".join(lines) + "\n"
