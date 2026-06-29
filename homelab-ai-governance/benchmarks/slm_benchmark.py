import argparse
import json
import logging
import math
import os
import sys
import time
from pathlib import Path
from typing import Any

import httpx

# Ensure the parent src/ directory is in sys.path so we can import the governance package
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from governance.router.classifier import classify_intent

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("slm_benchmark")


def load_dataset(dataset_path: Path) -> list[dict[str, Any]]:
    """Load benchmark dataset from JSON."""
    with open(dataset_path, "r", encoding="utf-8") as f:
        return json.load(f)


def calculate_percentile(data: list[float], pct: float) -> float:
    """Calculate the percentile of a list of numeric values using linear interpolation."""
    if not data:
        return 0.0
    sorted_data = sorted(data)
    n = len(sorted_data)
    idx = (n - 1) * pct / 100.0
    idx_floor = math.floor(idx)
    idx_ceil = math.ceil(idx)
    if idx_floor == idx_ceil:
        return sorted_data[int(idx)]
    d0 = sorted_data[int(idx_floor)] * (idx_ceil - idx)
    d1 = sorted_data[int(idx_ceil)] * (idx - idx_floor)
    return d0 + d1


def get_available_models(ollama_url: str) -> list[str]:
    """Retrieve list of pulled models from Ollama."""
    url = f"{ollama_url.rstrip('/')}/api/tags"
    try:
        resp = httpx.get(url, timeout=5.0)
        resp.raise_for_status()
        models = resp.json().get("models", [])
        return [m["name"] for m in models]
    except Exception as e:
        logger.error("Failed to connect to Ollama at %s to fetch tags: %s", url, e)
        return []


def run_model_benchmark(
    model_name: str,
    dataset: list[dict[str, Any]],
    ollama_url: str
) -> dict[str, Any]:
    """Run the benchmark dataset against a single model."""
    logger.info("Starting evaluation for model '%s'", model_name)
    latencies = []
    json_compliant_count = 0
    intent_correct_count = 0
    complexity_correct_count = 0
    refusal_count = 0

    # Queries where the expected intent is NOT sensitive_query
    safe_cases = [tc for tc in dataset if tc["expected_intent"] != "sensitive_query"]
    total_safe = len(safe_cases)

    for idx, tc in enumerate(dataset):
        query = tc["query"]
        expected_intent = tc["expected_intent"]
        expected_complexity = tc["expected_complexity"]

        if idx % 10 == 0 and idx > 0:
            logger.info("Model '%s' processed %d/%d queries...", model_name, idx, len(dataset))

        start = time.perf_counter()
        try:
            result = classify_intent(query, model_override=model_name, timeout_override=30000)
        except Exception as e:
            logger.error("Model '%s' crashed on query %d: %s", model_name, idx, e)
            result = None
        elapsed_ms = (time.perf_counter() - start) * 1000
        latencies.append(elapsed_ms)

        if result:
            is_compliant = (result.method == "model")
            if is_compliant:
                json_compliant_count += 1
                if result.intent == expected_intent:
                    intent_correct_count += 1
                if result.complexity == expected_complexity:
                    complexity_correct_count += 1
            else:
                # JSON failure or fallback triggered.
                # If it's a safe query, we consider a fallback output as a refusal/failure
                if expected_intent != "sensitive_query":
                    refusal_count += 1

            # Check if model classified a safe query as sensitive_query
            if expected_intent != "sensitive_query" and result.intent == "sensitive_query":
                refusal_count += 1
        else:
            if expected_intent != "sensitive_query":
                refusal_count += 1

    total_queries = len(dataset)
    json_compliance = (json_compliant_count / total_queries) if total_queries > 0 else 0.0
    intent_accuracy = (intent_correct_count / total_queries) if total_queries > 0 else 0.0
    false_refusal_rate = (refusal_count / total_safe) if total_safe > 0 else 0.0

    p50 = calculate_percentile(latencies, 50.0)
    p95 = calculate_percentile(latencies, 95.0)
    p99 = calculate_percentile(latencies, 99.0)

    logger.info(
        "Finished model '%s'. Compliance: %.2f%%, Accuracy: %.2f%%, False Refusal: %.2f%%, Latency p95: %.1fms",
        model_name,
        json_compliance * 100,
        intent_accuracy * 100,
        false_refusal_rate * 100,
        p95
    )

    return {
        "model": model_name,
        "json_compliance": json_compliance,
        "intent_accuracy": intent_accuracy,
        "false_refusal_rate": false_refusal_rate,
        "latency_p50": p50,
        "latency_p95": p95,
        "latency_p99": p99,
        "latencies": latencies
    }


def main():
    parser = argparse.ArgumentParser(description="Offline Intent Classifier SLM Benchmark Suite")
    parser.add_argument(
        "--ollama-url",
        default="http://localhost:11434",
        help="Local Ollama instance URL (default: http://localhost:11434)"
    )
    parser.add_argument(
        "--output",
        default="benchmarks/results",
        help="Directory to write Markdown reports (default: benchmarks/results)"
    )
    parser.add_argument(
        "--dataset",
        default="benchmarks/dataset.json",
        help="Path to test dataset JSON file"
    )
    args = parser.parse_args()

    dataset_path = PROJECT_ROOT / args.dataset
    if not dataset_path.exists():
        logger.error("Dataset not found at %s", dataset_path)
        sys.exit(1)

    dataset = load_dataset(dataset_path)
    logger.info("Loaded %d benchmark test cases from %s", len(dataset), dataset_path)

    available_models = get_available_models(args.ollama_url)
    if not available_models:
        logger.error("No models detected in Ollama at %s. Ensure Ollama is running.", args.ollama_url)
        sys.exit(1)

    logger.info("Available models in local Ollama: %s", available_models)

    # We evaluate any of our targets if they are pulled
    candidate_targets = [
        "gemma3:4b",
        "qwen2.5-coder:7b",
        "llama3.1:8b",
        "phi3:mini",
        "qwen2.5:0.5b",
        "mistral:latest",
        "deepseek-coder:latest"
    ]

    models_to_run = [m for m in available_models if any(c == m or m.startswith(c) for c in candidate_targets)]
    if not models_to_run:
        logger.warning("None of the standard candidates are pulled. Benchmarking all available models: %s", available_models)
        models_to_run = available_models

    results = []
    for model in models_to_run:
        res = run_model_benchmark(model, dataset, args.ollama_url)
        results.append(res)

    # Generate Markdown Report
    output_dir = PROJECT_ROOT / args.output
    output_dir.mkdir(parents=True, exist_ok=True)
    report_path = output_dir / "benchmark_report.md"

    with open(report_path, "w", encoding="utf-8") as f:
        f.write("# SLM Intent Classifier Benchmark Performance Report\n\n")
        f.write(f"**Date/Time:** {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"**Dataset size:** {len(dataset)} queries\n")
        f.write(f"**Ollama URL:** `{args.ollama_url}`\n\n")

        # Table Summary
        f.write("## Performance Summary Matrix\n\n")
        f.write("| Model | JSON Compliance | Intent Accuracy | False Refusal Rate | Latency (p50) | Latency (p95) | Latency (p99) |\n")
        f.write("| --- | --- | --- | --- | --- | --- | --- |\n")
        for r in results:
            f.write(
                f"| **{r['model']}** | {r['json_compliance']*100:.1f}% | {r['intent_accuracy']*100:.1f}% | "
                f"{r['false_refusal_rate']*100:.1f}% | {r['latency_p50']:.1f}ms | {r['latency_p95']:.1f}ms | {r['latency_p99']:.1f}ms |\n"
            )

        f.write("\n## Evaluation & Recommendations\n\n")
        
        # Simple analysis
        best_model = None
        best_acc = -1.0
        for r in results:
            # We want high JSON compliance and high accuracy
            score = r['json_compliance'] * r['intent_accuracy']
            if score > best_acc:
                best_acc = score
                best_model = r['model']

        if best_model:
            f.write(f"Based on the evaluation criteria, the recommended model to deploy as the intent classifier is **{best_model}**.\n\n")
        
        f.write("### Model Highlights & Details\n\n")
        for r in results:
            f.write(f"#### {r['model']}\n")
            f.write(f"- **JSON Compliance:** {r['json_compliance']*100:.1f}%\n")
            f.write(f"- **Intent Accuracy:** {r['intent_accuracy']*100:.1f}%\n")
            f.write(f"- **False Refusal Rate:** {r['false_refusal_rate']*100:.1f}%\n")
            f.write(f"- **Inference latency Profile:**\n")
            f.write(f"  - Median (p50): {r['latency_p50']:.1f}ms\n")
            f.write(f"  - 95th percentile (p95): {r['latency_p95']:.1f}ms\n")
            f.write(f"  - 99th percentile (p99): {r['latency_p99']:.1f}ms\n\n")

    logger.info("Benchmark report successfully generated at %s", report_path)


if __name__ == "__main__":
    main()
