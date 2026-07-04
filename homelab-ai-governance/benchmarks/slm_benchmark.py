import argparse
import json
import logging
import math
import sys
import time
from pathlib import Path
from typing import Any

import httpx

# Ensure the parent src/ directory is in sys.path so we can import governance package
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from governance.router.classifier import classify_intent

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("slm_benchmark")


# ── Helpers ──────────────────────────────────────────────────────────────────

def load_dataset(dataset_path: Path) -> list[dict[str, Any]]:
    with open(dataset_path, "r", encoding="utf-8") as f:
        return json.load(f)


def calculate_percentile(data: list[float], pct: float) -> float:
    if not data:
        return 0.0
    sorted_data = sorted(data)
    n = len(sorted_data)
    idx = (n - 1) * pct / 100.0
    lo, hi = math.floor(idx), math.ceil(idx)
    if lo == hi:
        return sorted_data[int(idx)]
    return sorted_data[lo] * (hi - idx) + sorted_data[hi] * (idx - lo)


def get_available_models(ollama_url: str) -> list[str]:
    try:
        resp = httpx.get(f"{ollama_url.rstrip('/')}/api/tags", timeout=5.0)
        resp.raise_for_status()
        return [m["name"] for m in resp.json().get("models", [])]
    except Exception as e:
        logger.error("Failed to reach Ollama at %s: %s", ollama_url, e)
        return []


def warm_up_model(model_name: str, ollama_url: str) -> None:
    """Load the model into memory so first-query latency excludes model load time."""
    try:
        httpx.post(
            f"{ollama_url.rstrip('/')}/api/generate",
            json={"model": model_name, "prompt": "hi", "stream": False},
            timeout=600.0,
        )
    except Exception as e:
        logger.warning("Warm-up for '%s' failed (continuing): %s", model_name, e)


# ── Per-model benchmark ───────────────────────────────────────────────────────

def run_model_benchmark(
    model_name: str,
    dataset: list[dict[str, Any]],
    ollama_url: str,
) -> dict[str, Any]:
    logger.info("▶ Starting benchmark for '%s'", model_name)
    logger.info("  Warming up '%s' (loading into memory)...", model_name)
    warm_up_model(model_name, ollama_url)

    latencies: list[float] = []
    json_compliant = 0
    intent_correct = 0
    refusal_count = 0
    fallback_count = 0

    # per-intent: {intent -> {correct, total}}
    per_intent: dict[str, dict[str, int]] = {}
    safe_cases = [tc for tc in dataset if tc["expected_intent"] != "sensitive_query"]
    total_safe = len(safe_cases)

    for idx, tc in enumerate(dataset):
        q = tc["query"]
        exp_intent = tc["expected_intent"]
        exp_complexity = tc["expected_complexity"]

        if exp_intent not in per_intent:
            per_intent[exp_intent] = {"correct": 0, "total": 0}
        per_intent[exp_intent]["total"] += 1

        if idx > 0 and idx % 10 == 0:
            logger.info("  %s — %d/%d queries done", model_name, idx, len(dataset))

        t0 = time.perf_counter()
        try:
            result = classify_intent(q, model_override=model_name, timeout_override=30_000)
        except Exception as exc:
            logger.error("  Crash on query %d: %s", idx, exc)
            result = None
        latencies.append((time.perf_counter() - t0) * 1000)

        if result is None:
            fallback_count += 1
            if exp_intent != "sensitive_query":
                refusal_count += 1
            continue

        if result.method == "model":
            json_compliant += 1
            if result.intent == exp_intent:
                intent_correct += 1
                per_intent[exp_intent]["correct"] += 1
            # over-sensitive: model routes safe query to sensitive_query
            if exp_intent != "sensitive_query" and result.intent == "sensitive_query":
                refusal_count += 1
        else:
            # keyword fallback triggered
            fallback_count += 1
            if result.intent == exp_intent:
                per_intent[exp_intent]["correct"] += 1
            if exp_intent != "sensitive_query":
                refusal_count += 1

    n = len(dataset)
    json_compliance     = json_compliant / n if n else 0.0
    intent_accuracy     = intent_correct  / n if n else 0.0
    false_refusal_rate  = refusal_count   / total_safe if total_safe else 0.0
    keyword_fallback_rt = fallback_count  / n if n else 0.0

    p50 = calculate_percentile(latencies, 50)
    p95 = calculate_percentile(latencies, 95)
    p99 = calculate_percentile(latencies, 99)

    per_intent_pct = {
        k: round(v["correct"] / v["total"] * 100, 1) if v["total"] else 0.0
        for k, v in per_intent.items()
    }

    logger.info(
        "✔ '%s'  acc=%.1f%%  fallback=%.1f%%  compliance=%.1f%%  p95=%.0fms",
        model_name,
        intent_accuracy * 100,
        keyword_fallback_rt * 100,
        json_compliance * 100,
        p95,
    )
    return {
        "model": model_name,
        "json_compliance":      json_compliance,
        "intent_accuracy":      intent_accuracy,
        "false_refusal_rate":   false_refusal_rate,
        "keyword_fallback_rate": keyword_fallback_rt,
        "latency_p50": p50,
        "latency_p95": p95,
        "latency_p99": p99,
        "latencies":   latencies,
        "per_intent_accuracy_pct": per_intent_pct,
    }


# ── Scoring & reporting ───────────────────────────────────────────────────────

def compute_score(r: dict[str, Any]) -> float:
    """Weighted composite score (higher = better)."""
    return (
        r["intent_accuracy"]      * 0.60
        + r["json_compliance"]    * 0.30
        - r["keyword_fallback_rate"] * 0.10
    )


def generate_report(
    results: list[dict[str, Any]],
    dataset_size: int,
    ollama_url: str,
    output_dir: Path,
) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    md_path   = output_dir / "benchmark_report.md"
    json_path = output_dir / "benchmark_raw.json"

    # Raw JSON
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "dataset_size": dataset_size,
                "ollama_url":   ollama_url,
                "results": [
                    {k: v for k, v in r.items() if k != "latencies"}
                    for r in results
                ],
            },
            f,
            indent=2,
        )
    logger.info("Raw JSON → %s", json_path)

    # Find best model
    best = max(results, key=compute_score)

    # All intents union
    all_intents = sorted({
        intent
        for r in results
        for intent in r.get("per_intent_accuracy_pct", {})
    })

    with open(md_path, "w", encoding="utf-8") as f:

        f.write("# SLM Intent Classifier Benchmark Performance Report\n\n")
        f.write(f"**Date/Time:** {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"**Dataset size:** {dataset_size} queries\n")
        f.write(f"**Ollama URL:** `{ollama_url}`\n\n")
        f.write("> **Target**: Intent Accuracy ≥ 85% | Keyword Fallback Rate ≤ 5%\n\n")

        # ── Summary matrix ────────────────────────────────────────────────
        f.write("## Performance Summary Matrix\n\n")
        f.write("| Model | JSON Compliance | Intent Accuracy | Keyword Fallback | False Refusal Rate | Latency (p50) | Latency (p95) | Latency (p99) | Score |\n")
        f.write("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n")
        for r in results:
            score    = compute_score(r)
            acc_flag = "✅" if r["intent_accuracy"]      >= 0.85 else "❌"
            fb_flag  = "✅" if r["keyword_fallback_rate"] <= 0.05 else "❌"
            star     = " ⭐" if r["model"] == best["model"] else ""
            f.write(
                f"| **{r['model']}{star}** "
                f"| {r['json_compliance']*100:.1f}% "
                f"| {acc_flag} {r['intent_accuracy']*100:.1f}% "
                f"| {fb_flag} {r['keyword_fallback_rate']*100:.1f}% "
                f"| {r['false_refusal_rate']*100:.1f}% "
                f"| {r['latency_p50']:.1f}ms "
                f"| {r['latency_p95']:.1f}ms "
                f"| {r['latency_p99']:.1f}ms "
                f"| {score:.3f} |\n"
            )

        # ── Per-intent breakdown ──────────────────────────────────────────
        if all_intents:
            f.write("\n## Per-Intent Accuracy Breakdown (%)\n\n")
            f.write("| Intent |" + "".join(f" {r['model']} |" for r in results) + "\n")
            f.write("| --- |"    + "".join( " --- |"          for _  in results) + "\n")
            for intent in all_intents:
                row = f"| `{intent}` |"
                for r in results:
                    pct  = r.get("per_intent_accuracy_pct", {}).get(intent, 0.0)
                    flag = "✅" if pct >= 85 else ("🟡" if pct >= 60 else "❌")
                    row += f" {flag} {pct:.0f}% |"
                f.write(row + "\n")

        # ── Recommendation ────────────────────────────────────────────────
        f.write("\n## Evaluation & Recommendations\n\n")
        meets_acc = best["intent_accuracy"]      >= 0.85
        meets_fb  = best["keyword_fallback_rate"] <= 0.05
        if meets_acc and meets_fb:
            f.write(f"✅ **Recommended model**: `{best['model']}` — meets BOTH accuracy (≥85%) and fallback (≤5%) targets.\n\n")
        else:
            f.write(f"⚠️ **Best available model**: `{best['model']}` — targets not fully met. Consider pulling larger 7B models.\n\n")
            if not meets_acc:
                f.write(f"  - Intent accuracy: **{best['intent_accuracy']*100:.1f}%** (need ≥85%)\n")
            if not meets_fb:
                f.write(f"  - Keyword fallback rate: **{best['keyword_fallback_rate']*100:.1f}%** (need ≤5%)\n")
            f.write("\n")

        f.write("### Model Highlights & Details\n\n")
        for r in sorted(results, key=compute_score, reverse=True):
            score = compute_score(r)
            star  = " ⭐ **RECOMMENDED**" if r["model"] == best["model"] else ""
            f.write(f"#### {r['model']}{star}\n")
            f.write(f"- **Score (weighted):** {score:.3f}\n")
            f.write(f"- **JSON Compliance:** {r['json_compliance']*100:.1f}%\n")
            f.write(f"- **Intent Accuracy:** {r['intent_accuracy']*100:.1f}%\n")
            f.write(f"- **Keyword Fallback Rate:** {r['keyword_fallback_rate']*100:.1f}%\n")
            f.write(f"- **False Refusal Rate:** {r['false_refusal_rate']*100:.1f}%\n")
            f.write(f"- **Inference Latency:**\n")
            f.write(f"  - p50: {r['latency_p50']:.1f}ms\n")
            f.write(f"  - p95: {r['latency_p95']:.1f}ms\n")
            f.write(f"  - p99: {r['latency_p99']:.1f}ms\n\n")

    logger.info("Report → %s", md_path)
    return md_path


# ── Entry point ───────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Offline SLM Intent Classifier Benchmark")
    parser.add_argument("--ollama-url",  default="http://localhost:11434")
    parser.add_argument("--output",      default="benchmarks/results")
    parser.add_argument("--dataset",     default="benchmarks/dataset.json")
    parser.add_argument(
        "--models",
        default=None,
        help="Comma-separated model names to benchmark. Defaults to all recognised candidates.",
    )
    args = parser.parse_args()

    dataset_path = PROJECT_ROOT / args.dataset
    if not dataset_path.exists():
        logger.error("Dataset not found: %s", dataset_path)
        sys.exit(1)

    dataset = load_dataset(dataset_path)
    logger.info("Loaded %d test cases", len(dataset))

    available = get_available_models(args.ollama_url)
    if not available:
        logger.error("No Ollama models available at %s", args.ollama_url)
        sys.exit(1)
    logger.info("Ollama models: %s", available)

    if args.models:
        models_to_run = [m.strip() for m in args.models.split(",")]
        missing = [m for m in models_to_run if m not in available]
        if missing:
            logger.error("Not found in Ollama: %s — pull them first", missing)
            sys.exit(1)
    else:
        candidates = {
            "gemma3", "phi3", "llama3.2", "llama3.1", "qwen2.5",
            "mistral", "deepseek-coder",
        }
        models_to_run = [m for m in available if any(m.startswith(c) for c in candidates)]
        if not models_to_run:
            logger.warning("No standard candidates found — benchmarking all: %s", available)
            models_to_run = available

    logger.info("Benchmarking: %s", models_to_run)

    results = [run_model_benchmark(m, dataset, args.ollama_url) for m in models_to_run]

    report = generate_report(results, len(dataset), args.ollama_url, PROJECT_ROOT / args.output)
    logger.info("All done. Report: %s", report)


if __name__ == "__main__":
    main()
