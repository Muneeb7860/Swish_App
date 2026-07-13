from __future__ import annotations

import logging
import os
import threading
import time
from typing import Any
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse, PlainTextResponse
from pydantic import BaseModel

from governance.guardrails.nemo_guardrails import GuardrailConfigError, get_nemo_engine
from governance.pipeline import execute_pipeline
from governance.router.classifier import get_classifier_stats
from governance.stubs.memory_mesh import MemoryMesh

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

from contextlib import asynccontextmanager


@asynccontextmanager
async def _lifespan(_: FastAPI):
    # Fail fast: a governance service with unloadable guardrails must not serve
    # traffic (GOVERNANCE_SPEC.md Phase 1). Requests that arrive anyway are
    # still fail-closed by check_nemo_guardrails.
    try:
        engine = get_nemo_engine()
        logger.info(
            "Guardrail engine loaded: %d intents, %d flows, %d active",
            len(engine.intents),
            len(engine.flows),
            len(engine.active_flows),
        )
    except GuardrailConfigError:
        logger.critical("Guardrail config invalid — refusing to start")
        raise
    yield


app = FastAPI(title="Homelab AI Governance Service", version="0.1.0", lifespan=_lifespan)

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
        otlp_endpoint = os.environ.get(
            "OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318/v1/traces"
        )
        processor = BatchSpanProcessor(OTLPSpanExporter(endpoint=otlp_endpoint))
        provider.add_span_processor(processor)
        trace.set_tracer_provider(provider)

        # Instrument the app
        FastAPIInstrumentor.instrument_app(app)
        logger.info("OpenTelemetry instrumentation active exporting to %s", otlp_endpoint)
    except Exception as e:
        logger.warning("Failed to initialize OpenTelemetry instrumentation: %s", e)



class MetricsTracker:
    def __init__(self):
        self.lock = threading.Lock()
        self.requests_total = 0
        self.failures_total = 0
        self.latency_sum = 0.0
        self.pii_redacted_total = 0
        self.fallback_total = 0
        self.attempts_total = 0
        self.intent_counts = {
            "general_knowledge": 0,
            "inventory": 0,
            "rider": 0,
            "order": 0,
            "support": 0,
            "pricing": 0,
            "system_admin": 0,
            "logistics": 0,
            "procurement": 0,
        }

    def record_request(self, latency: float, result: dict[str, Any]):
        with self.lock:
            self.requests_total += 1
            self.latency_sum += latency

            # Check if request contained PII or override triggered local only routing
            routing = result.get("routing_decision", {})
            if routing.get("local_only", False):
                self.pii_redacted_total += 1

            # Extract intent
            intent = routing.get("intent", "unknown")
            if intent in self.intent_counts:
                self.intent_counts[intent] += 1
            else:
                self.intent_counts[intent] = 1

            # Extract self-correction stats
            loop = result.get("loop_result", {})
            self.attempts_total += loop.get("attempts", 1)
            if loop.get("fallback_used", False):
                self.fallback_total += 1

    def record_failure(self):
        with self.lock:
            self.requests_total += 1
            self.failures_total += 1


metrics_tracker = MetricsTracker()


class GovernRequest(BaseModel):
    query: str
    expected_format: str | None = None
    local_only_override: bool = False
    session_id: str | None = None


@app.post("/api/v1/govern")
def govern(req: GovernRequest) -> dict[str, Any]:
    """Execute the query governance pipeline."""
    start = time.perf_counter()
    try:
        logger.info("Governing query: %s", req.query[:100])
        res = execute_pipeline(
            query=req.query,
            expected_format=req.expected_format,
            local_only_override=req.local_only_override,
            session_id=req.session_id,
        )
        latency = time.perf_counter() - start
        metrics_tracker.record_request(latency, res)
        return res
    except Exception as e:
        metrics_tracker.record_failure()
        logger.exception("Governance pipeline execution failed")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/v1/stats")
def stats() -> dict[str, Any]:
    """Get internal classifier routing stats."""
    return get_classifier_stats()


@app.get("/health")
def health() -> Any:
    """Check service health, including the input guardrail gate.

    Returns 503 DEGRADED if the guardrail engine cannot load — traffic is
    still fail-closed in that state, but orchestrators should restart us.
    """
    try:
        get_nemo_engine()
        # Body shape is a contract with PythonGovernanceAdapter — do not extend.
        return {"status": "UP"}
    except Exception as e:
        return JSONResponse(
            status_code=503,
            content={"status": "DEGRADED", "reason": f"guardrail engine: {e}"},
        )


@app.get("/metrics", response_class=PlainTextResponse)
def metrics() -> str:
    """Expose Prometheus-formatted metrics."""
    lines = [
        "# HELP rag_circuit_breaker_tripped_total Total number of RAG circuit breaker trips.",
        "# TYPE rag_circuit_breaker_tripped_total counter",
        f'rag_circuit_breaker_tripped_total{{type="db"}} {MemoryMesh.db_breaker_trips}',
        f'rag_circuit_breaker_tripped_total{{type="embedding"}} {MemoryMesh.embedding_breaker_trips}',
        "# HELP governance_requests_total Total number of queries governed.",
        "# TYPE governance_requests_total counter",
        f"governance_requests_total {metrics_tracker.requests_total}",
        "# HELP governance_exceptions_total Total number of governance pipeline failures.",
        "# TYPE governance_exceptions_total counter",
        f"governance_exceptions_total {metrics_tracker.failures_total}",
        "# HELP governance_pipeline_latency_seconds_sum Sum of governance pipeline latencies.",
        "# TYPE governance_pipeline_latency_seconds_sum counter",
        f"governance_pipeline_latency_seconds_sum {metrics_tracker.latency_sum}",
        "# HELP governance_pipeline_latency_seconds_count Count of requests recorded for latency.",
        "# TYPE governance_pipeline_latency_seconds_count counter",
        f"governance_pipeline_latency_seconds_count {metrics_tracker.requests_total}",
        "# HELP governance_pii_redactions_total Total requests triggering PII redaction or local routing.",
        "# TYPE governance_pii_redactions_total counter",
        f"governance_pii_redactions_total {metrics_tracker.pii_redacted_total}",
        "# HELP governance_self_correction_attempts_total Total self-correction attempts made.",
        "# TYPE governance_self_correction_attempts_total counter",
        f"governance_self_correction_attempts_total {metrics_tracker.attempts_total}",
        "# HELP governance_self_correction_fallback_total Total fallbacks to local Gemma Reasoner.",
        "# TYPE governance_self_correction_fallback_total counter",
        f"governance_self_correction_fallback_total {metrics_tracker.fallback_total}",
        "# HELP governed_requests_by_intent_total Total requests governed categorized by intent class.",
        "# TYPE governed_requests_by_intent_total counter",
    ]
    for intent, count in metrics_tracker.intent_counts.items():
        lines.append(f'governed_requests_by_intent_total{{intent="{intent}"}} {count}')

    return "\n".join(lines) + "\n"
