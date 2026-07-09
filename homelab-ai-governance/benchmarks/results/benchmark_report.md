# SLM Intent Classifier Benchmark Performance Report

**Date/Time:** 2026-07-02 19:53:17
**Dataset size:** 63 queries
**Ollama URL:** `http://localhost:11434`

> **Target**: Intent Accuracy ≥ 85% | Keyword Fallback Rate ≤ 5%

## Performance Summary Matrix

| Model | JSON Compliance | Intent Accuracy | Keyword Fallback | False Refusal Rate | Latency (p50) | Latency (p95) | Latency (p99) | Score |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **gemma3:4b** | 100.0% | ❌ 79.4% | ✅ 0.0% | 0.0% | 3445.3ms | 3573.5ms | 4132.1ms | 0.776 |
| **phi3:mini** | 74.6% | ❌ 61.9% | ❌ 25.4% | 25.0% | 2660.3ms | 21110.1ms | 31767.8ms | 0.570 |
| **qwen2.5:3b ⭐** | 100.0% | ✅ 90.5% | ✅ 0.0% | 0.0% | 1063.3ms | 1233.8ms | 2395.9ms | 0.843 |
| **qwen2.5:7b** | 100.0% | ❌ 84.1% | ✅ 0.0% | 0.0% | 2055.4ms | 2486.8ms | 4655.3ms | 0.805 |
| **mistral:7b** | 100.0% | ✅ 88.9% | ✅ 0.0% | 0.0% | 1505.2ms | 1758.8ms | 4114.6ms | 0.833 |

## Per-Intent Accuracy Breakdown (%)

| Intent | gemma3:4b | phi3:mini | qwen2.5:3b | qwen2.5:7b | mistral:7b |
| --- | --- | --- | --- | --- | --- |
| `code_debugging` | ✅ 100% | ✅ 86% | ✅ 100% | ✅ 86% | ✅ 86% |
| `code_generation` | ✅ 100% | ✅ 100% | ✅ 100% | ✅ 100% | ✅ 100% |
| `code_review` | ✅ 100% | 🟡 71% | ✅ 100% | ✅ 100% | ✅ 100% |
| `creative_writing` | ✅ 86% | ❌ 57% | ✅ 100% | 🟡 71% | ✅ 100% |
| `data_analysis` | ❌ 43% | ❌ 57% | ✅ 100% | 🟡 71% | ✅ 100% |
| `general_knowledge` | ✅ 86% | ✅ 100% | 🟡 71% | ✅ 100% | ✅ 100% |
| `sensitive_query` | 🟡 71% | ❌ 43% | ✅ 86% | 🟡 71% | ❌ 57% |
| `summarization` | 🟡 71% | ❌ 57% | 🟡 71% | ✅ 86% | 🟡 71% |
| `system_admin` | ❌ 57% | ❌ 57% | ✅ 86% | 🟡 71% | ✅ 86% |

## Evaluation & Recommendations

✅ **Recommended model**: `qwen2.5:3b` — meets BOTH accuracy (≥85%) and fallback (≤5%) targets.

### Model Highlights & Details

#### qwen2.5:3b ⭐ **RECOMMENDED**
- **Score (weighted):** 0.843
- **JSON Compliance:** 100.0%
- **Intent Accuracy:** 90.5%
- **Keyword Fallback Rate:** 0.0%
- **False Refusal Rate:** 0.0%
- **Inference Latency:**
  - p50: 1063.3ms
  - p95: 1233.8ms
  - p99: 2395.9ms

#### mistral:7b
- **Score (weighted):** 0.833
- **JSON Compliance:** 100.0%
- **Intent Accuracy:** 88.9%
- **Keyword Fallback Rate:** 0.0%
- **False Refusal Rate:** 0.0%
- **Inference Latency:**
  - p50: 1505.2ms
  - p95: 1758.8ms
  - p99: 4114.6ms

#### qwen2.5:7b
- **Score (weighted):** 0.805
- **JSON Compliance:** 100.0%
- **Intent Accuracy:** 84.1%
- **Keyword Fallback Rate:** 0.0%
- **False Refusal Rate:** 0.0%
- **Inference Latency:**
  - p50: 2055.4ms
  - p95: 2486.8ms
  - p99: 4655.3ms

#### gemma3:4b
- **Score (weighted):** 0.776
- **JSON Compliance:** 100.0%
- **Intent Accuracy:** 79.4%
- **Keyword Fallback Rate:** 0.0%
- **False Refusal Rate:** 0.0%
- **Inference Latency:**
  - p50: 3445.3ms
  - p95: 3573.5ms
  - p99: 4132.1ms

#### phi3:mini
- **Score (weighted):** 0.570
- **JSON Compliance:** 74.6%
- **Intent Accuracy:** 61.9%
- **Keyword Fallback Rate:** 25.4%
- **False Refusal Rate:** 25.0%
- **Inference Latency:**
  - p50: 2660.3ms
  - p95: 21110.1ms
  - p99: 31767.8ms

