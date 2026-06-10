"""Unified audit logger — JSONL append-only log with counter aggregation and cost tracking."""

from __future__ import annotations

import json
import logging
import sys
import threading
import time
from collections import defaultdict
from datetime import datetime, timezone
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Any

from governance.config import load_audit_config, load_shared_guardrails

logger = logging.getLogger("governance.audit")


class AuditLogger:
    """Append-only JSONL audit logger with non-match counter aggregation.

    - Match events: logged per-event with full context.
    - Non-match events: aggregated as hourly counters (rule_eval_counters).
    - Disk-full safety: falls back to stderr, sets logging_degraded flag.
    """

    def __init__(self, log_dir: str | Path | None = None):
        config = load_audit_config()
        guardrails = load_shared_guardrails()
        self._schema_version = guardrails.get("schema_version", "2.0")

        rotation = config.get("rotation", {})
        paths = config.get("paths", {})
        counter_cfg = config.get("counters", {})

        if log_dir is None:
            log_dir = Path(paths.get("log_dir", "data/logs"))
        else:
            log_dir = Path(log_dir)

        log_dir.mkdir(parents=True, exist_ok=True)
        log_file = log_dir / paths.get("log_file", "audit.jsonl")

        # Set up rotating file handler
        max_bytes = rotation.get("max_file_size_mb", 100) * 1024 * 1024
        max_files = rotation.get("max_files", 12)

        self._handler = RotatingFileHandler(
            str(log_file),
            maxBytes=max_bytes,
            backupCount=max_files,
            encoding="utf-8",
        )
        self._handler.setFormatter(logging.Formatter("%(message)s"))

        self._logger = logging.getLogger("governance.audit.file")
        self._logger.setLevel(logging.INFO)
        self._logger.addHandler(self._handler)
        self._logger.propagate = False

        # In-memory non-match counters
        # Structure: counters[rule_id][agent_id][phase] = count
        self._counters: dict[str, dict[str, dict[str, int]]] = defaultdict(
            lambda: defaultdict(lambda: defaultdict(int))
        )
        self._counter_lock = threading.Lock()
        self._flush_interval = counter_cfg.get("flush_interval_seconds", 3600)
        self._last_flush = time.time()

        # Degraded mode flag
        self.logging_degraded = False

    def log_event(self, event_type: str, **kwargs: Any) -> None:
        """Write a single JSON line to the audit log."""
        entry = {
            "schema_version": self._schema_version,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "type": event_type,
            **kwargs,
        }
        try:
            self._logger.info(json.dumps(entry, default=str))
            self.logging_degraded = False
        except Exception as e:
            # Disk full or I/O error — degrade gracefully
            self.logging_degraded = True
            print(
                f"[AUDIT DEGRADED] Failed to write audit log: {e}. Entry: {entry}",
                file=sys.stderr,
            )

    def increment_counter(self, rule_id: str, agent_id: str, phase: str) -> None:
        """Increment the in-memory non-match counter for a rule evaluation."""
        with self._counter_lock:
            self._counters[rule_id][agent_id][phase] += 1

    def flush_counters(self) -> None:
        """Write aggregated non-match counters to the audit log and reset."""
        with self._counter_lock:
            if not self._counters:
                return

            now = datetime.now(timezone.utc)
            period_start = datetime.fromtimestamp(self._last_flush, tz=timezone.utc)

            self.log_event(
                "rule_eval_counters",
                period=f"{period_start.isoformat()}/{now.isoformat()}",
                counters={
                    rule_id: {
                        agent_id: dict(phases)
                        for agent_id, phases in agents.items()
                    }
                    for rule_id, agents in self._counters.items()
                },
            )

            self._counters.clear()
            self._last_flush = time.time()

    def maybe_flush_counters(self) -> None:
        """Flush counters if the flush interval has elapsed."""
        if time.time() - self._last_flush >= self._flush_interval:
            self.flush_counters()


class CostTracker:
    """Tracks daily cloud API spend. Resets at midnight UTC."""

    def __init__(self, audit_logger: AuditLogger):
        self._audit = audit_logger
        self._daily_cost: float = 0.0
        self._reset_date: str = self._today()
        self._lock = threading.Lock()

    @staticmethod
    def _today() -> str:
        return datetime.now(timezone.utc).strftime("%Y-%m-%d")

    def _maybe_reset(self) -> None:
        today = self._today()
        if today != self._reset_date:
            self._daily_cost = 0.0
            self._reset_date = today

    def record_cloud_call(
        self,
        agent_id: str,
        input_tokens: int,
        output_tokens: int,
        cost_per_1k_input: float = 0.005,
        cost_per_1k_output: float = 0.015,
    ) -> float:
        """Record a cloud API call and return the estimated cost."""
        cost = (input_tokens / 1000) * cost_per_1k_input + (
            output_tokens / 1000
        ) * cost_per_1k_output

        with self._lock:
            self._maybe_reset()
            self._daily_cost += cost

        self._audit.log_event(
            "cloud_api_call",
            agent_id=agent_id,
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            estimated_cost_usd=round(cost, 6),
            daily_cumulative_usd=round(self.get_daily_cost(), 4),
        )
        return cost

    def get_daily_cost(self) -> float:
        """Return the current cumulative daily cost."""
        with self._lock:
            self._maybe_reset()
            return self._daily_cost


# Module-level singleton (lazy init)
_audit_logger: AuditLogger | None = None
_cost_tracker: CostTracker | None = None


def get_audit_logger(log_dir: str | Path | None = None) -> AuditLogger:
    """Get or create the global AuditLogger singleton."""
    global _audit_logger
    if _audit_logger is None:
        _audit_logger = AuditLogger(log_dir=log_dir)
    return _audit_logger


def get_cost_tracker() -> CostTracker:
    """Get or create the global CostTracker singleton."""
    global _cost_tracker
    if _cost_tracker is None:
        _cost_tracker = CostTracker(get_audit_logger())
    return _cost_tracker
