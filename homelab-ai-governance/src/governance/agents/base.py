"""Abstract base agent — common interface for all model backends."""

from __future__ import annotations

import abc
from dataclasses import dataclass
from typing import Any


@dataclass
class AgentResponse:
    """Standardized response from any agent backend."""

    text: str
    model: str
    agent_id: str
    input_tokens: int = 0
    output_tokens: int = 0
    latency_ms: float = 0.0
    metadata: dict[str, Any] | None = None


class BaseAgent(abc.ABC):
    """Abstract agent interface. All backends (Ollama, cloud) implement this."""

    def __init__(self, agent_id: str, model: str, timeout_ms: int = 30000):
        self.agent_id = agent_id
        self.model = model
        self.timeout_ms = timeout_ms

    @abc.abstractmethod
    def generate(self, prompt: str) -> AgentResponse:
        """Send a prompt and return the agent's response."""

    @abc.abstractmethod
    def is_available(self) -> bool:
        """Health check: can this agent accept requests right now?"""

    def __repr__(self) -> str:
        return f"{self.__class__.__name__}(id={self.agent_id!r}, model={self.model!r})"
