"""Detector implementations dispatched by type from shared_guardrails.yaml.

Each detector_config['type'] maps to a function that returns True if the
content triggers the rule. The dispatch table is extensible.
"""

from __future__ import annotations

import logging
import re
from typing import Any

from governance.config import ConfigError, load_terms
from governance.guardrails.sql_ast import detect_unsafe_sql

logger = logging.getLogger(__name__)


def run_detector(detector_config: dict[str, Any], content: str) -> bool:
    """Dispatch to the appropriate detector implementation.

    Raises ConfigError for unknown detector types.
    """
    dtype = detector_config["type"]
    handler = _DETECTOR_DISPATCH.get(dtype)
    if handler is None:
        raise ConfigError(f"Unknown detector type: {dtype}")
    return handler(detector_config, content)


# ── Individual detector implementations ──────────────────────────────────────


def _build_obfuscation_pattern(term: str) -> str:
    """Build a regex pattern that matches the term even with common leetspeak and symbol insertion."""
    char_maps = {
        "a": "[aA@4]",
        "b": "[bB]",
        "c": "[cC]",
        "d": "[dD]",
        "e": "[eE3@]",
        "f": "[fF]",
        "g": "[gG]",
        "h": "[hH]",
        "i": "[iI!1]",
        "j": "[jJ]",
        "k": "[kK]",
        "l": "[lL1]",
        "m": "[mM]",
        "n": "[nN]",
        "o": "[oO0]",
        "p": "[pP]",
        "q": "[qQ]",
        "r": "[rR]",
        "s": "[sS$5]",
        "t": "[tT7]",
        "u": "[uU]",
        "v": "[vV]",
        "w": "[wW]",
        "x": "[xX]",
        "y": "[yY]",
        "z": "[zZ]"
    }
    
    pattern_parts = []
    for char in term.lower():
        mapped = char_maps.get(char, re.escape(char))
        pattern_parts.append(mapped)
        
    # Allow any amount of non-alphanumeric symbols/spaces between characters (e.g., s.w.e.a.r, s_w_e_a_r)
    return r"[\W_]*".join(pattern_parts)


def _detect_regex(config: dict[str, Any], content: str) -> bool:
    """Match any pattern in the detector's pattern list."""
    for p in config.get("patterns", []):
        if re.search(p["pattern"], content):
            return True
    return False


def _detect_keyword_list(config: dict[str, Any], content: str) -> bool:
    """Match terms from a file-based keyword list with obfuscation resistance."""
    terms = load_terms(config["source"])
    mode = config.get("match_mode", "substring")

    for term in terms:
        pattern = _build_obfuscation_pattern(term)
        if mode == "word_boundary":
            pattern = r"\b" + pattern + r"\b"
            
        if re.search(pattern, content, re.IGNORECASE):
            return True
    return False


def _detect_heuristic(config: dict[str, Any], content: str) -> bool:
    """Match heuristic rules — used for prompt injection detection."""
    for rule in config.get("rules", []):
        if re.search(rule["pattern"], content):
            return True
    return False


def _detect_fingerprint(config: dict[str, Any], content: str) -> bool:
    """MinHash/LSH fingerprint match against a license corpus.

    Stub for MVP — returns False. Phase 5 will integrate datasketch.
    """
    # TODO(phase5): Integrate datasketch MinHash/LSH
    logger.debug(
        "fingerprint detector is a stub — returning False. "
        "corpus=%s, threshold=%s",
        config.get("corpus"),
        config.get("similarity_threshold"),
    )
    return False


def _detect_toxicity(config: dict[str, Any], content: str) -> bool:
    """ONNX toxicity scorer with keyword-list fallback.

    MVP: goes straight to the fallback keyword list since the ONNX model
    requires Phase 5 setup (onnxruntime + model download).
    """
    # Phase 5: load ONNX model and run inference
    # score = run_toxicity_model(config["model"], content[:config.get("max_input_chars", 2000)])
    # if score >= config["threshold"]:
    #     return True

    fallback = config.get("fallback")
    if fallback:
        return run_detector(fallback, content)

    logger.warning("toxicity_scorer: no ONNX model and no fallback configured")
    return False


# ── Redaction helpers ────────────────────────────────────────────────────────


def redact_matches(detector_config: dict[str, Any], content: str) -> str:
    """Replace all detector matches with [REDACTED] markers."""
    dtype = detector_config["type"]

    if dtype == "regex":
        result = content
        for p in detector_config.get("patterns", []):
            result = re.sub(p["pattern"], f"[REDACTED:{p['name'].upper()}]", result)
        return result

    if dtype == "keyword_list":
        terms = load_terms(detector_config["source"])
        result = content
        mode = detector_config.get("match_mode", "substring")
        for term in terms:
            pattern = _build_obfuscation_pattern(term)
            if mode == "word_boundary":
                pattern = r"\b" + pattern + r"\b"
            
            result = re.sub(pattern, "[REDACTED]", result, flags=re.IGNORECASE)
        return result

    return content


def strip_matched_segments(detector_config: dict[str, Any], content: str) -> str:
    """Remove (strip) matched segments but keep the rest of the content.

    Used by the prompt_injection_filter's 'strip' action.
    """
    dtype = detector_config["type"]

    if dtype == "heuristic":
        result = content
        for rule in detector_config.get("rules", []):
            result = re.sub(rule["pattern"], "", result)
        return result.strip()

    if dtype == "regex":
        result = content
        for p in detector_config.get("patterns", []):
            result = re.sub(p["pattern"], "", result)
        return result.strip()

    return content


# ── Dispatch table ───────────────────────────────────────────────────────────


_DETECTOR_DISPATCH: dict[str, Any] = {
    "regex": _detect_regex,
    "keyword_list": _detect_keyword_list,
    "heuristic": _detect_heuristic,
    "fingerprint": _detect_fingerprint,
    "toxicity_scorer": _detect_toxicity,
    "sql_ast": detect_unsafe_sql,
}
