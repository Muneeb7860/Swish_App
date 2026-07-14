"""Tests for the /metrics Prometheus endpoint in server.py."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from governance.server import app
from governance.stubs.memory_mesh import MemoryMesh


@pytest.fixture(autouse=True)
def reset_counters():
    """Reset MemoryMesh and metrics_tracker counters before each test."""
    MemoryMesh.db_breaker_trips = 0
    MemoryMesh.embedding_breaker_trips = 0
    from governance.server import metrics_tracker
    with metrics_tracker.lock:
        metrics_tracker.requests_total = 0
        metrics_tracker.failures_total = 0
        metrics_tracker.latency_sum = 0.0
        metrics_tracker.pii_redacted_total = 0
        metrics_tracker.attempts_total = 0
        metrics_tracker.fallback_total = 0
        metrics_tracker.latency_bucket_counts = [0] * len(
            metrics_tracker.latency_bucket_counts
        )
        for intent in metrics_tracker.intent_counts:
            metrics_tracker.intent_counts[intent] = 0
    yield
    MemoryMesh.db_breaker_trips = 0
    MemoryMesh.embedding_breaker_trips = 0
    with metrics_tracker.lock:
        metrics_tracker.requests_total = 0
        metrics_tracker.failures_total = 0
        metrics_tracker.latency_sum = 0.0
        metrics_tracker.pii_redacted_total = 0
        metrics_tracker.attempts_total = 0
        metrics_tracker.fallback_total = 0
        metrics_tracker.latency_bucket_counts = [0] * len(
            metrics_tracker.latency_bucket_counts
        )
        for intent in metrics_tracker.intent_counts:
            metrics_tracker.intent_counts[intent] = 0


@pytest.fixture
def client():
    """Provide a FastAPI TestClient instance."""
    return TestClient(app)


def test_metrics_endpoint_returns_200(client):
    """Verify that /metrics returns 200 with text/plain content type."""
    response = client.get("/metrics")
    assert response.status_code == 200
    assert "text/plain" in response.headers["content-type"]


def test_metrics_contains_prometheus_headers(client):
    """Verify that /metrics output contains HELP and TYPE declarations."""
    response = client.get("/metrics")
    body = response.text
    assert "# HELP rag_circuit_breaker_tripped_total" in body
    assert "# TYPE rag_circuit_breaker_tripped_total counter" in body


def test_metrics_reports_zero_counters(client):
    """Verify that /metrics reports 0 for both counters when no breakers have tripped."""
    response = client.get("/metrics")
    body = response.text
    assert 'rag_circuit_breaker_tripped_total{type="db"} 0' in body
    assert 'rag_circuit_breaker_tripped_total{type="embedding"} 0' in body


def test_metrics_reflects_counter_increments(client):
    """Verify that /metrics dynamically reflects counter mutations."""
    # Simulate breaker trips
    MemoryMesh.db_breaker_trips = 5
    MemoryMesh.embedding_breaker_trips = 3

    response = client.get("/metrics")
    body = response.text
    assert 'rag_circuit_breaker_tripped_total{type="db"} 5' in body
    assert 'rag_circuit_breaker_tripped_total{type="embedding"} 3' in body


def test_metrics_reflects_governance_metrics(client):
    """Verify that custom governance metrics are exposed correctly, recorded
    through the real tracker API (record_request / record_failure)."""
    from governance.server import metrics_tracker

    result_pricing = {
        "routing_decision": {"intent": "pricing", "local_only": True},
        "loop_result": {"attempts": 2, "fallback_used": True},
    }
    result_support = {
        "routing_decision": {"intent": "support", "local_only": False},
        "loop_result": {"attempts": 1, "fallback_used": False},
    }
    metrics_tracker.record_request(1.5, result_pricing)
    metrics_tracker.record_request(3.0, result_support)
    metrics_tracker.record_failure(0.25)

    response = client.get("/metrics")
    body = response.text
    assert "governance_requests_total 3" in body
    assert "governance_exceptions_total 1" in body
    assert "governance_pipeline_latency_seconds_sum 4.75" in body
    assert "governance_pipeline_latency_seconds_count 3" in body
    assert "governance_pii_redactions_total 1" in body
    assert "governance_self_correction_attempts_total 3" in body
    assert "governance_self_correction_fallback_total 1" in body
    assert 'governed_requests_by_intent_total{intent="pricing"} 1' in body
    assert 'governed_requests_by_intent_total{intent="support"} 1' in body


def test_latency_histogram_buckets_are_cumulative(client):
    """GOVERNANCE_SPEC.md Phase 3: p95 needs a real histogram. Buckets must be
    cumulative and the 2.5s SLO edge must exist."""
    from governance.server import metrics_tracker

    result = {"routing_decision": {}, "loop_result": {}}
    metrics_tracker.record_request(0.4, result)   # → le=0.5
    metrics_tracker.record_request(1.2, result)   # → le=1.5
    metrics_tracker.record_request(2.4, result)   # → le=2.5
    metrics_tracker.record_request(60.0, result)  # → +Inf only

    body = client.get("/metrics").text
    assert "# TYPE governance_pipeline_latency_seconds histogram" in body
    assert 'governance_pipeline_latency_seconds_bucket{le="0.5"} 1' in body
    assert 'governance_pipeline_latency_seconds_bucket{le="1.5"} 2' in body
    assert 'governance_pipeline_latency_seconds_bucket{le="2.5"} 3' in body
    assert 'governance_pipeline_latency_seconds_bucket{le="30.0"} 3' in body
    assert 'governance_pipeline_latency_seconds_bucket{le="+Inf"} 4' in body
    assert "governance_pipeline_latency_seconds_count 4" in body


def test_slo_bucket_edge_matches_spec():
    """The 2.5s SLO edge in GOVERNANCE_SPEC.md §2 must exist as a bucket bound,
    or the GovernanceLatencyHigh alert quantile turns into interpolation noise."""
    from governance.server import LATENCY_BUCKET_BOUNDS

    assert 2.5 in LATENCY_BUCKET_BOUNDS


def test_metrics_output_ends_with_newline(client):
    """Prometheus exposition format requires a trailing newline."""
    response = client.get("/metrics")
    assert response.text.endswith("\n")
