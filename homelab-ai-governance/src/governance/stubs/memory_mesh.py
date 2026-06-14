"""Memory Mesh — semantic memory retriever using pgvector and Ollama."""

from __future__ import annotations

import logging
import os
import threading
import time
from typing import Any

import httpx
import psycopg2
from psycopg2.extras import RealDictCursor
from psycopg2.pool import ThreadedConnectionPool

from governance.config import load_routing_config

logger = logging.getLogger(__name__)

# Fallback in-memory mock documents
DEFAULT_DOCUMENTS = [
    {
        "id": "doc_weather_1",
        "content": "The weather forecast calls for sunny skies with a high of 22C.",
        "score": 0.89,
    },
    {
        "id": "doc_pricing_1",
        "content": "Our dynamic pricing engine maintains a daily budget ceiling of 5.00 USD.",
        "score": 0.92,
    },
    {
        "id": "doc_zurich_1",
        "content": "The Zurich delivery hours are from 08:00 to 22:00, Monday through Saturday.",
        "score": 0.95,
    },
]


class MemoryMesh:
    """Memory Mesh context retriever.

    Queries a PostgreSQL database with the pgvector extension, using embeddings
    generated dynamically via the local Ollama nomic-embed-text:latest model.
    Falls back gracefully to in-memory mock stubs if any errors occur.

    The connection pool is created exactly once at class level under a lock
    (double-checked) so concurrent first requests cannot race on initialization.
    """

    _pool: ThreadedConnectionPool | None = None
    _pool_lock = threading.Lock()

    _db_failed_time: float | None = None
    _embedding_failed_time: float | None = None
    _cooldown_duration: float = 30.0

    # Expose counters for circuit breaker trips
    db_breaker_trips: int = 0
    embedding_breaker_trips: int = 0

    def __init__(self):
        try:
            config = load_routing_config()
            rag_config = config.get("rag", {})
            self.enabled = rag_config.get("enabled", False)
            self.database_url = os.getenv(
                "SWISH_RAG_DATABASE_URL",
                rag_config.get("database_url", "postgresql://letta:lettapassword@localhost:5434/letta"),
            )
            self.embedding_model = rag_config.get("embedding_model", "nomic-embed-text:latest")
            self.embedding_url = rag_config.get("embedding_url", "http://localhost:11434/api/embeddings")
            self.similarity_threshold = rag_config.get("similarity_threshold", 0.60)

            # Initialize the pool once at class level under a lock (double-checked).
            if self.enabled and MemoryMesh._pool is None:
                with MemoryMesh._pool_lock:
                    if MemoryMesh._pool is None:
                        now = time.time()
                        if MemoryMesh._db_failed_time is None or (now - MemoryMesh._db_failed_time) >= self._cooldown_duration:
                            try:
                                MemoryMesh._pool = ThreadedConnectionPool(
                                    minconn=1,
                                    maxconn=10,
                                    dsn=self.database_url,
                                    connect_timeout=2,
                                )
                                logger.info("MemoryMesh: database connection pool initialized successfully (maxconn=10).")
                            except Exception as pool_err:
                                logger.error("MemoryMesh: failed to initialize database connection pool: %s", pool_err)
        except Exception as e:
            logger.warning("MemoryMesh: failed to load RAG config: %s. RAG disabled.", e)
            self.enabled = False

    def retrieve(self, query: str, limit: int = 3) -> list[dict[str, Any]]:
        """Retrieve documents matching the query."""
        if not self.enabled:
            logger.info("MemoryMesh: RAG is disabled. Returning in-memory stubs.")
            return self._retrieve_fallbacks(query)

        # Check circuit breakers
        now = time.time()
        if MemoryMesh._db_failed_time is not None and (now - MemoryMesh._db_failed_time) < self._cooldown_duration:
            logger.warning("MemoryMesh: DB circuit breaker is active (cooldown). Bypassing RAG database check.")
            return self._retrieve_fallbacks(query)

        if self._embedding_failed_time is not None and (now - self._embedding_failed_time) < self._cooldown_duration:
            logger.warning("MemoryMesh: Embedding circuit breaker is active (cooldown). Bypassing RAG embedding check.")
            return self._retrieve_fallbacks(query)

        logger.info("MemoryMesh: retrieving context docs for query: %r", query)
        return self._retrieve_pgvector(query, limit)

    def _retrieve_pgvector(self, query: str, limit: int) -> list[dict[str, Any]]:
        """Retrieve documents from pgvector."""
        conn = None
        try:
            if MemoryMesh._pool is None:
                self.__init__()
                if MemoryMesh._pool is None:
                    logger.warning("MemoryMesh: pgvector connection pool unavailable. Falling back to stubs.")
                    MemoryMesh._db_failed_time = time.time()
                    MemoryMesh.db_breaker_trips += 1
                    return self._retrieve_fallbacks(query)

            conn = MemoryMesh._pool.getconn()
            self._init_db(conn)

            # Generate embedding AFTER database connection success (matches original flow)
            embedding = self._generate_embedding(query)
            if not embedding:
                logger.warning("MemoryMesh: embedding generation failed. Falling back to in-memory stubs.")
                return self._retrieve_fallbacks(query)

            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                cur.execute(
                    """
                    SELECT doc_id AS id, content, (1.0 - (embedding <=> %s::vector)) AS score
                    FROM knowledge_base
                    ORDER BY embedding <=> %s::vector
                    LIMIT %s
                    """,
                    (embedding, embedding, limit),
                )
                results = cur.fetchall()

            docs = []
            for r in results:
                score = float(r["score"]) if r["score"] is not None else 0.0
                if score >= self.similarity_threshold:
                    docs.append({"id": r["id"], "content": r["content"], "score": score})

            if not docs:
                logger.info("MemoryMesh: no documents matched similarity threshold in pgvector. Returning default fallback.")
                return [self._get_default_document(query)]

            return docs
        except Exception as e:
            logger.warning("MemoryMesh: pgvector query execution failed (%s). Tripping DB circuit breaker.", e)
            MemoryMesh._db_failed_time = time.time()
            MemoryMesh.db_breaker_trips += 1
            return self._retrieve_fallbacks(query)
        finally:
            if conn and MemoryMesh._pool:
                try:
                    MemoryMesh._pool.putconn(conn)
                except Exception as put_err:
                    logger.error("MemoryMesh: failed to put connection back to pool: %s", put_err)

    def _retrieve_fallbacks(self, query: str) -> list[dict[str, Any]]:
        """Simple rule-based mock responses for testing / fallback purposes."""
        query_lower = query.lower()
        if "weather" in query_lower:
            return [DEFAULT_DOCUMENTS[0]]
        elif "pricing" in query_lower or "budget" in query_lower:
            return [DEFAULT_DOCUMENTS[1]]
        elif "zurich" in query_lower:
            return [DEFAULT_DOCUMENTS[2]]

        # Default mock document
        return [self._get_default_document(query)]

    def _get_default_document(self, query: str) -> dict[str, Any]:
        from governance.guardrails.pii_patterns import redact_pii

        redacted_query = redact_pii(query)
        return {
            "id": "doc_default_1",
            "content": f"Default context reference document for query: {redacted_query}",
            "score": 0.70,
        }

    def _generate_embedding(self, text: str) -> list[float] | None:
        """Fetch embedding vector from Ollama API."""
        now = time.time()
        if self._embedding_failed_time is not None and (now - self._embedding_failed_time) < self._cooldown_duration:
            logger.warning("MemoryMesh: Embedding circuit breaker is active (cooldown). Bypassing embedding generation.")
            return None

        try:
            with httpx.Client(timeout=3.0) as client:
                res = client.post(
                    self.embedding_url,
                    json={"model": self.embedding_model, "prompt": text},
                )
                if res.status_code == 200:
                    return res.json().get("embedding")
                else:
                    logger.error("MemoryMesh: embedding generation response error status %s. Tripping embedding circuit breaker.", res.status_code)
                    MemoryMesh._embedding_failed_time = time.time()
                    MemoryMesh.embedding_breaker_trips += 1
        except Exception as e:
            logger.error("MemoryMesh: embedding generation error: %s. Tripping embedding circuit breaker.", e)
            MemoryMesh._embedding_failed_time = time.time()
            MemoryMesh.embedding_breaker_trips += 1
        return None

    def _init_db(self, conn: psycopg2.extensions.connection) -> None:
        """Verify/create pgvector table and seed initial documents if empty."""
        with conn.cursor() as cur:
            # 1. Enable extension
            cur.execute("CREATE EXTENSION IF NOT EXISTS vector;")
            # 2. Create table
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS knowledge_base (
                    id SERIAL PRIMARY KEY,
                    doc_id VARCHAR(100) UNIQUE,
                    content TEXT,
                    embedding vector(768)
                );
                """
            )
            # 3. Check if empty
            cur.execute("SELECT COUNT(*) FROM knowledge_base;")
            count = cur.fetchone()[0]

            if count == 0:
                logger.info("MemoryMesh: seeding pgvector knowledge base table with default documents.")
                for doc in DEFAULT_DOCUMENTS:
                    content = doc["content"]
                    doc_id = doc["id"]
                    embedding = self._generate_embedding(content)
                    if embedding:
                        cur.execute(
                            """
                            INSERT INTO knowledge_base (doc_id, content, embedding)
                            VALUES (%s, %s, %s::vector)
                            ON CONFLICT (doc_id) DO NOTHING;
                            """,
                            (doc_id, content, embedding),
                        )
                conn.commit()


def retrieve_context(query: str, limit: int = 3) -> list[dict[str, Any]]:
    """Retrieve helper function to call the MemoryMesh retriever."""
    mesh = MemoryMesh()
    return mesh.retrieve(query, limit)
