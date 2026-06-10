"""Context Constructor Stub — formats retrieved context documents for prompt insertion."""

from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)


class ContextConstructor:
    """Stub implementation of Context Constructor.

    Formats retrieved raw search results into a clean text block
    suitable for appending to LLM system instructions or user prompts.
    """

    def construct_context(self, docs: list[dict[str, Any]]) -> str:
        """Construct a formatted context string from a list of documents."""
        if not docs:
            logger.debug("ContextConstructor: no documents to construct context from")
            return ""

        formatted_blocks = []
        for doc in docs:
            doc_id = doc.get("id", "unknown_doc")
            content = doc.get("content", "").strip()
            score = doc.get("score", 0.0)
            
            block = (
                f"--- DOCUMENT REFERENCE: {doc_id} (Relevance Score: {score:.2f}) ---\n"
                f"{content}"
            )
            formatted_blocks.append(block)

        constructed = "\n\n".join(formatted_blocks)
        logger.info("ContextConstructor: formatted %d context document(s)", len(docs))
        return constructed


def construct_context(docs: list[dict[str, Any]]) -> str:
    """Helper function to call ContextConstructor formatting."""
    constructor = ContextConstructor()
    return constructor.construct_context(docs)
