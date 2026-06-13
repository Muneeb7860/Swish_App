"""Tests for the /metrics Prometheus endpoint in server.py."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from governance.server import app
from governance.stubs.memory_mesh import MemoryMesh


@pytest.fixture(autouse=True)
def reset_counters():
    """Reset MemoryMesh counters before each test."""
    MemoryMesh.db_breaker_trips = 0
    MemoryMesh.embedding_breaker_trips = 0
    yield
    MemoryMesh.db_breaker_trips = 0
    MemoryMesh.embedding_breaker_trips = 0


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


def test_metrics_output_ends_with_newline(client):
    """Prometheus exposition format requires a trailing newline."""
    response = client.get("/metrics")
    assert response.text.endswith("\n")
