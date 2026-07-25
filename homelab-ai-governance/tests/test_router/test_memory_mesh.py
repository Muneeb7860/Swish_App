"""Tests for the MemoryMesh RAG retriever module."""

from __future__ import annotations

from unittest.mock import MagicMock, patch
import pytest

from governance.stubs.memory_mesh import MemoryMesh

@pytest.fixture(autouse=True)
def reset_circuit_breakers():
    """Reset circuit breaker and pool states on MemoryMesh class before every test."""
    MemoryMesh._db_failed_time = None
    MemoryMesh._embedding_failed_time = None
    MemoryMesh.db_breaker_trips = 0
    MemoryMesh.embedding_breaker_trips = 0
    if MemoryMesh._pool is not None:
        try:
            MemoryMesh._pool.closeall()
        except Exception:
            pass
        MemoryMesh._pool = None


def test_memory_mesh_disabled(monkeypatch):
    """Verify that when RAG is disabled, retriever returns local mock stubs."""
    with patch("governance.stubs.memory_mesh.load_routing_config") as mock_cfg:
        mock_cfg.return_value = {"rag": {"enabled": False}}
        
        mesh = MemoryMesh()
        assert mesh.enabled is False
        
        # Test weather query
        docs = mesh.retrieve("What is the weather like?")
        assert len(docs) == 1
        assert docs[0]["id"] == "doc_weather_1"
        assert "sunny skies" in docs[0]["content"]

        # Test pricing query
        docs = mesh.retrieve("What is the pricing limit?")
        assert len(docs) == 1
        assert docs[0]["id"] == "doc_pricing_1"
        assert "daily budget ceiling" in docs[0]["content"]

        # Test zurich query
        docs = mesh.retrieve("Zurich delivery info")
        assert len(docs) == 1
        assert docs[0]["id"] == "doc_zurich_1"
        assert "Zurich delivery hours" in docs[0]["content"]

        # Test default fallback query
        docs = mesh.retrieve("some unknown text")
        assert len(docs) == 1
        assert docs[0]["id"] == "doc_default_1"
        assert "Default context reference" in docs[0]["content"]


def test_memory_mesh_connection_failure_fallback():
    """Verify that when database connection fails, retriever falls back to stubs instead of crashing."""
    with patch("governance.stubs.memory_mesh.load_routing_config") as mock_cfg, \
         patch("psycopg2.connect") as mock_connect:
        mock_cfg.return_value = {
            "rag": {
                "enabled": True,
                "database_url": "postgresql://test:test@localhost:5432/test",
                "similarity_threshold": 0.60
            }
        }
        # Connection refuses
        mock_connect.side_effect = Exception("Connection refused")
        
        mesh = MemoryMesh()
        assert mesh.enabled is True
        
        # Should gracefully return fallbacks
        docs = mesh.retrieve("zurich delivery")
        assert len(docs) == 1
        assert docs[0]["id"] == "doc_zurich_1"
        assert "Zurich delivery hours" in docs[0]["content"]


def test_memory_mesh_successful_retrieval():
    """Verify that when database and Ollama are online, retriever queries pgvector and returns docs."""
    with patch("governance.stubs.memory_mesh.load_routing_config") as mock_cfg, \
         patch("psycopg2.connect") as mock_connect, \
         patch("httpx.Client") as mock_client:
        
        mock_cfg.return_value = {
            "rag": {
                "enabled": True,
                "database_url": "postgresql://test:test@localhost:5432/test",
                "embedding_model": "nomic-embed-text",
                "embedding_url": "http://localhost:11434/api/embeddings",
                "similarity_threshold": 0.60
            }
        }
        
        # 1. Mock Ollama response
        mock_res = MagicMock()
        mock_res.status_code = 200
        mock_res.json.return_value = {"embedding": [0.1] * 768}
        
        mock_client_instance = MagicMock()
        mock_client_instance.post.return_value = mock_res
        mock_client.return_value.__enter__.return_value = mock_client_instance

        # 2. Mock PG database cursor
        mock_conn = MagicMock()
        mock_cur = MagicMock()
        mock_cur.fetchall.return_value = [
            {"id": "doc_db_1", "content": "Db Grounding Document 1", "score": 0.85},
            {"id": "doc_db_2", "content": "Db Grounding Document 2", "score": 0.50} # Below threshold
        ]
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        mock_connect.return_value = mock_conn

        mesh = MemoryMesh()
        assert mesh.enabled is True
        
        # Execute retrieval
        docs = mesh.retrieve("find documents", limit=2)
        
        # Asserts
        assert len(docs) == 1
        assert docs[0]["id"] == "doc_db_1"
        assert docs[0]["content"] == "Db Grounding Document 1"
        assert docs[0]["score"] == 0.85

        # Verify SQL execution parameters
        mock_cur.execute.assert_any_call(
            """
                    SELECT doc_id AS id, content, (1.0 - (embedding <=> %s::vector)) AS score
                    FROM knowledge_base
                    ORDER BY embedding <=> %s::vector
                    LIMIT %s
                    """,
            ([0.1] * 768, [0.1] * 768, 2)
        )


def test_memory_mesh_circuit_breaker():
    """Verify that circuit breakers trip under failure and bypass subsequent calls during cooldown."""
    # Reset circuit breaker states first
    MemoryMesh._db_failed_time = None
    MemoryMesh._embedding_failed_time = None
    MemoryMesh.db_breaker_trips = 0
    MemoryMesh.embedding_breaker_trips = 0
    
    with patch("governance.stubs.memory_mesh.load_routing_config") as mock_cfg, \
         patch("psycopg2.connect") as mock_connect, \
         patch("httpx.Client") as mock_client:
        
        mock_cfg.return_value = {
            "rag": {
                "enabled": True,
                "database_url": "postgresql://test:test@localhost:5432/test",
                "embedding_model": "nomic-embed-text",
                "embedding_url": "http://localhost:11434/api/embeddings",
                "similarity_threshold": 0.60
            }
        }
        
        # 1. DB connection failure
        mock_connect.side_effect = Exception("DB offline")
        
        mesh = MemoryMesh()
        assert MemoryMesh.db_breaker_trips == 0
        # First query should fail and trip DB circuit breaker
        docs1 = mesh.retrieve("weather query")
        assert len(docs1) == 1
        assert docs1[0]["id"] == "doc_weather_1"
        assert MemoryMesh._db_failed_time is not None
        assert MemoryMesh.db_breaker_trips == 1  # Counter incremented on DB failure
        
        # Reset mock_connect call history
        mock_connect.reset_mock()
        
        # Second query should immediately bypass DB connection check without calling psycopg2.connect
        docs2 = mesh.retrieve("weather query")
        assert len(docs2) == 1
        assert docs2[0]["id"] == "doc_weather_1"
        mock_connect.assert_not_called()
        # Counter should NOT increment on circuit-breaker bypass (no new failure)
        assert MemoryMesh.db_breaker_trips == 1
        
        # Reset DB breaker, trip embedding breaker
        MemoryMesh._db_failed_time = None
        # Mock connection success, but embedding generation raises HTTP error
        mock_connect.side_effect = None
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        
        mock_client_instance = MagicMock()
        mock_client_instance.post.side_effect = Exception("Embedding service down")
        mock_client.return_value.__enter__.return_value = mock_client_instance
        
        assert MemoryMesh.embedding_breaker_trips == 0
        # First query with DB online but embedding failing
        docs3 = mesh.retrieve("pricing query")
        assert len(docs3) == 1
        assert docs3[0]["id"] == "doc_pricing_1"
        assert MemoryMesh._embedding_failed_time is not None
        assert MemoryMesh.embedding_breaker_trips == 1  # Counter incremented on embedding failure
        
        # Reset mock_client call history
        mock_client_instance.post.reset_mock()
        
        # Second query should bypass embedding generation
        docs4 = mesh.retrieve("pricing query")
        assert len(docs4) == 1
        assert docs4[0]["id"] == "doc_pricing_1"
        mock_client_instance.post.assert_not_called()
        # Counter should NOT increment on bypass
        assert MemoryMesh.embedding_breaker_trips == 1
        
    # Reset circuit breaker states after test
    MemoryMesh._db_failed_time = None
    MemoryMesh._embedding_failed_time = None
    MemoryMesh.db_breaker_trips = 0
    MemoryMesh.embedding_breaker_trips = 0

