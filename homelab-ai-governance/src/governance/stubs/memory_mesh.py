"""Memory Mesh Stub — mock semantic memory retriever."""

from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)


class MemoryMesh:
    """Stub implementation of Memory Mesh context retriever.

    In production, this queries a vector database (e.g., Qdrant/Chroma)
    or database to retrieve context documents relevant to the query.
    """

    def __init__(self, database_url: str = "mock://memory_mesh"):
        self.database_url = database_url

    def retrieve(self, query: str, limit: int = 3) -> list[dict[str, Any]]:
        """Retrieve mock documents matching the query."""
        logger.info("MemoryMesh: retrieving context docs for query: %r", query)
        
        # Simple rule-based mock responses for testing purposes
        query_lower = query.lower()
        if "weather" in query_lower:
            return [
                {
                    "id": "doc_weather_1",
                    "content": "The weather forecast calls for sunny skies with a high of 22C.",
                    "score": 0.89,
                }
            ]
        elif "pricing" in query_lower or "budget" in query_lower:
            return [
                {
                    "id": "doc_pricing_1",
                    "content": "Our dynamic pricing engine maintains a daily budget ceiling of 5.00 USD.",
                    "score": 0.92,
                }
            ]
        
        # Default mock document
        return [
            {
                "id": "doc_default_1",
                "content": f"Default context reference document for query: {query}",
                "score": 0.70,
            }
        ]


def retrieve_context(query: str, limit: int = 3) -> list[dict[str, Any]]:
    """Retrieve helper function to call the MemoryMesh retriever."""
    mesh = MemoryMesh()
    return mesh.retrieve(query, limit)
