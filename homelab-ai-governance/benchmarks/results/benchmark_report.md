# SLM Intent Classifier Benchmark Performance Report

**Date/Time:** 2026-06-29 10:51:44
**Dataset size:** 63 queries
**Ollama URL:** `http://localhost:11434`

## Performance Summary Matrix

| Model | JSON Compliance | Intent Accuracy | False Refusal Rate | Latency (p50) | Latency (p95) | Latency (p99) |
| --- | --- | --- | --- | --- | --- | --- |
| **gemma3:4b** | 100.0% | 74.6% | 0.0% | 1131.9ms | 1388.2ms | 2015.2ms |
| **qwen2.5:0.5b** | 79.4% | 23.8% | 28.6% | 573.3ms | 3311.9ms | 4068.5ms |

## Evaluation & Recommendations

Based on the evaluation criteria, the recommended model to deploy as the intent classifier is **gemma3:4b**.

### Model Highlights & Details

#### gemma3:4b
- **JSON Compliance:** 100.0%
- **Intent Accuracy:** 74.6%
- **False Refusal Rate:** 0.0%
- **Inference latency Profile:**
  - Median (p50): 1131.9ms
  - 95th percentile (p95): 1388.2ms
  - 99th percentile (p99): 2015.2ms

#### qwen2.5:0.5b
- **JSON Compliance:** 79.4%
- **Intent Accuracy:** 23.8%
- **False Refusal Rate:** 28.6%
- **Inference latency Profile:**
  - Median (p50): 573.3ms
  - 95th percentile (p95): 3311.9ms
  - 99th percentile (p99): 4068.5ms

