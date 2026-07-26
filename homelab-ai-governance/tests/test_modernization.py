"""Tests for the modernized AI governance components (lean stack).

Covers what was actually adopted:
  - instructor/Pydantic guided decoding schema for the classifier,
  - pgvector-only MemoryMesh with a class-level connection-pool lock,
  - DuckDB-over-JSONL on-demand audit analytics.

Qdrant and ClickHouse were intentionally not adopted (deferred until a measured
need), so there are no tests for them here.
"""

from __future__ import annotations

import pytest
import threading
from unittest.mock import patch

from governance.audit import AuditLogger
from governance.router.classifier import ClassificationSchema
from governance.stubs.memory_mesh import MemoryMesh


def test_classification_schema_validation():
    """ClassificationSchema validates fields and clamps confidence to [0, 1]."""
    schema = ClassificationSchema(intent="general_knowledge", complexity="low", confidence=0.85)
    assert schema.intent == "general_knowledge"
    assert schema.complexity == "low"
    assert schema.confidence == 0.85

    assert ClassificationSchema(intent="code_generation", complexity="high", confidence=1.5).confidence == 1.0
    assert ClassificationSchema(intent="sensitive_query", complexity="medium", confidence=-0.5).confidence == 0.0


def test_memory_mesh_has_pool_lock_and_no_qdrant():
    """MemoryMesh exposes a class-level pool lock and no longer references Qdrant."""
    assert isinstance(MemoryMesh._pool_lock, type(threading.Lock()))
    assert not hasattr(MemoryMesh, "_qdrant_client")
    assert not hasattr(MemoryMesh, "qdrant_breaker_trips")


def test_memory_mesh_falls_back_to_stubs_when_disabled():
    """With RAG disabled, retrieval returns in-memory stubs (no DB required)."""
    with patch("governance.stubs.memory_mesh.load_routing_config") as mock_cfg:
        mock_cfg.return_value = {"rag": {"enabled": False}}
        mesh = MemoryMesh()
        assert mesh.enabled is False
        docs = mesh.retrieve("What are the Zurich delivery hours?")
        assert len(docs) >= 1
        assert "Zurich" in docs[0]["content"]


def test_duckdb_audit_analytics(tmp_path):
    """AuditLogger writes JSONL and DuckDB analytics read it back by event type."""
    pytest.importorskip("duckdb")
    audit = AuditLogger(log_dir=tmp_path)
    audit.log_event("classification", agent_id="a1", intent="code_generation")
    audit.log_event("classification", agent_id="a2", intent="summarization")
    audit.log_event("guardrail_block", agent_id="a1")

    # Flush the rotating handler to disk before querying the files.
    for h in audit._logger.handlers:
        h.flush()

    counts = audit.event_type_counts()
    assert counts.get("classification") == 2
    assert counts.get("guardrail_block") == 1

    rows = audit.analytics_query(
        "SELECT agent_id, COUNT(*) AS n FROM pipeline_events "
        "WHERE agent_id = 'a1' GROUP BY agent_id"
    )
    assert rows and rows[0]["n"] == 2


def test_duckdb_analytics_degrades_when_no_events(tmp_path):
    """Analytics return empty (never crash) when no events have been logged yet."""
    audit = AuditLogger(log_dir=tmp_path / "fresh")
    assert audit.event_type_counts() == {}
