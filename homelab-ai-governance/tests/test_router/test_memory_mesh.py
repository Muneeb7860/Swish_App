"""Tests for the MemoryMesh RAG retriever module."""

from __future__ import annotations

from unittest.mock import MagicMock, patch
import pytest

from governance.stubs.memory_mesh import MemoryMesh, retrieve_context

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
