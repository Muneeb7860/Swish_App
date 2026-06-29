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
    """Verify that custom governance metrics are exposed correctly."""
    from governance.server import metrics_tracker
    
    with metrics_tracker.lock:
        metrics_tracker.requests_total = 10
        metrics_tracker.failures_total = 2
        metrics_tracker.latency_sum = 4.5
        metrics_tracker.pii_redacted_total = 3
        metrics_tracker.attempts_total = 12
        metrics_tracker.fallback_total = 1
        metrics_tracker.intent_counts["pricing"] = 5
        metrics_tracker.intent_counts["support"] = 3
    
    response = client.get("/metrics")
    body = response.text
    assert 'governance_requests_total 10' in body
    assert 'governance_exceptions_total 2' in body
    assert 'governance_pipeline_latency_seconds_sum 4.5' in body
    assert 'governance_pipeline_latency_seconds_count 10' in body
    assert 'governance_pii_redactions_total 3' in body
    assert 'governance_self_correction_attempts_total 12' in body
    assert 'governance_self_correction_fallback_total 1' in body
    assert 'governed_requests_by_intent_total{intent="pricing"} 5' in body
    assert 'governed_requests_by_intent_total{intent="support"} 3' in body


def test_metrics_output_ends_with_newline(client):
    """Prometheus exposition format requires a trailing newline."""
    response = client.get("/metrics")
    assert response.text.endswith("\n")
