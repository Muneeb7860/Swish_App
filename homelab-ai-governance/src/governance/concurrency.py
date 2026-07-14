"""Single-host model concurrency guards — GOVERNANCE_SPEC.md §3b.

One Ollama host, 16 GB: two generations on the SAME model must queue (KV-cache
and compute contention), but the classifier and an agent model may overlap.
Hence one semaphore per model name, never a global lock. Elevated-request
retries and eval passes acquire the same semaphore, so heavy requests cannot
starve normal traffic on other models.
"""

from __future__ import annotations

import threading

_registry_lock = threading.Lock()
_model_semaphores: dict[str, threading.BoundedSemaphore] = {}

# The classifier model is on the floor of every request (GOVERNANCE_SPEC.md
# §2), so Ollama is told to keep it resident instead of reloading it — a
# reload on a 16 GB host stalls every in-flight request.
CLASSIFIER_KEEP_ALIVE = -1


def get_model_semaphore(model: str) -> threading.BoundedSemaphore:
    """One BoundedSemaphore(1) per model name, created on first use."""
    with _registry_lock:
        sem = _model_semaphores.get(model)
        if sem is None:
            sem = threading.BoundedSemaphore(1)
            _model_semaphores[model] = sem
        return sem
