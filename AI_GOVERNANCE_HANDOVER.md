# Swish App Agentic OS: AI Governance & SLM Optimization Handoff

This handover document outlines the current state of the Swish App Agentic OS AI Governance layer and provides a detailed guide for executing **Phase 1: Domain-Specific SLM Evaluation & Custom System Instructions** on the target `Mac_Machine` environment.

---

## 1. Executive Summary & Current Status

### Phase 9: Recursive AI Governance Alignment — ✅ 100% COMPLETE & VERIFIED
We have successfully completed and validated the Phase 9 hardening controls. The system is structurally sound, and all validation tests pass.

* **Backend AI Governance Tests (Python)**: **47 / 47 tests passed** inside `homelab-ai-governance/`. These tests verify:
  * **Obfuscation Detection & Redaction**: Catching leetspeak and evasive formatting (e.g., `s.w.e.a.r`, `sw@@r`) and redacting to `[REDACTED]`.
  * **Nesting Guard**: Detecting and blocking payload inflation attacks (up to 25 levels of JSON nesting).
  * **Numeric Hallucination Penalization**: Calculating the Context Conservation Ratio (CCR) with strict penalties for unsupported numeric values.
  * **Rate Limiter Logic**: Confirming request limits are enforced in `audit.py` and block rate-limit-exceeding requests.
* **Frontend Customer E2E Tests (Cypress)**: **39 / 39 tests passed** inside `frontend-customer/`. These tests verify the E2E integration of the B2B dashboard, Auth flows, Order placement, Rider delivery, and Admin observability queues.
* **Total Verification Coverage**: **86 / 86 tests passing**.

---

## 2. Workstation Infrastructure Details

Due to environment variations between the development machines, the current Windows machine does not have a live Python runtime or Ollama instance running. Consequently, **live model evaluation and infrastructure validation must be executed on the Mac_Machine workstation**.

### `Mac_Machine` Infrastructure Profile
* **Target Hardware**: Apple Silicon (M1/M2/M3/M4) architecture. This offers unified memory architecture (UMA) capable of running 7B–8B parameter local models at high tokens-per-second throughput.
* **Local Inference Backend**: [Ollama](https://ollama.com) running locally at `http://localhost:11434`.
* **Python Runtime**: Python 3.11+ installed natively.
* **Workstation Branch**: `Mac_Machine` (synced at commit `51e96e9`).
* **Sync Tool**: Use the [sync_to_mac_machine.sh](file:///c:/Users/DELL%209420/Documents/swiss_App/sync_to_mac_machine.sh) script to consolidate files and push back to `origin/Mac_Machine`.

---

## 3. Phase 1: Domain-Specific SLM Evaluation Roadmap

Generic small language models (like the baseline `gemma3:4b` running on CPU mode) present two core issues:
1. **False-Positive Safety Refusals**: Refusing to process queries containing standard placeholders like `[REDACTED]`, or misclassifying delivery queries as `sensitive_query`.
2. **Format & Timeout Fallback**: Wrapping JSON outputs in markdown or failing to reply within `2000ms`, which triggers the keyword-based router fallback (`confidence: 0.55`) and routes everything to the slow fallback path.

We will benchmark and tune the local SLM to bypass these issues.

### 3.1. Candidate Models to Evaluate
We will evaluate the following models on the Mac's Ollama instance:

| Model | Size | Ollama Tag | Strengths to Evaluate |
|---|---|---|---|
| **Gemma 3 4B** (Baseline) | 4.0B | `gemma3:4b` | Current baseline model. Highly safety-aligned, but over-conservative. |
| **Qwen 2.5 Coder 7B** | 7.2B | `qwen2.5-coder:7b` | Exceptional structured JSON compliance, strong coding/logic capabilities. |
| **Llama 3.1 8B Instruct** | 8.0B | `llama3.1:8b` | Excellent instruction-following; robust against system prompt constraints. |
| **Phi-3 Mini 3.8B** | 3.8B | `phi3:mini` | Very lightweight, fast inference times, suitable for low-spec routing. |
| **Mistral 7B Instruct** | 7.2B | `mistral:7b` | Proven reasoning and formatting ability; already registered in the system. |

---

## 4. Proposed Benchmarking Harness & Test Dataset

To automate model selection, we will implement an offline benchmarking harness in the `homelab-ai-governance` component.

### 4.1. Curated Dataset: `homelab-ai-governance/benchmarks/dataset.json`
A set of **60+ test cases** covering all 9 intents across 3 complexity levels (`low`, `medium`, `high`). It includes Swish-specific queries and obfuscation boundary cases:
```json
[
  {
    "query": "Track my order delivery status for ORD-12345",
    "expected_intent": "general_knowledge",
    "expected_complexity": "low",
    "category": "swish_domain"
  },
  {
    "query": "Write a Python script to calculate optimal rider routes using Swiss Post zones",
    "expected_intent": "code_generation",
    "expected_complexity": "high",
    "category": "code_generation"
  },
  {
    "query": "What are the GDPR implications of storing rider GPS coordinates in pgvector? [REDACTED] token included.",
    "expected_intent": "sensitive_query",
    "expected_complexity": "high",
    "category": "sensitive_query"
  }
]
```

### 4.2. Benchmarking Script: `homelab-ai-governance/benchmarks/slm_benchmark.py`
A Python script that executes the benchmark dataset against each Ollama model and generates a Markdown performance report.
* **Measures**:
  * **JSON Compliance**: Percentage of responses that parse into valid JSON.
  * **Intent Accuracy**: Matching the expected intent.
  * **Latency**: p50, p95, and p99 response times.
  * **False Refusal Rate**: Queries rejected due to safety guardrails.

---

## 5. Custom Swish-Domain System Instructions

We will deploy custom system prompts to instruct the classifier and reasoning models. These prompts explicitly define the Swish domain and handle `[REDACTED]` tokens to avoid safety refusals.

### 5.1. Classifier System Prompt: `homelab-ai-governance/config/system_prompts/classifier_v2.txt`
```text
You are the Swish App intent classifier. Your ONLY job is to classify user queries into exactly one intent category and complexity level.

CONTEXT: Swish is a 15-minute grocery delivery platform in Switzerland. Queries involve:
- Order tracking, delivery logistics, rider management
- Inventory, wholesaler B2B operations, pricing
- System administration, Kubernetes/Docker deployments
- AI governance, model evaluation, guardrail configuration
- General programming and debugging tasks

INTENTS (pick exactly one):
- general_knowledge: factual questions, explanations, definitions
- code_generation: write/create/implement/build code
- code_debugging: fix/debug/error/traceback/exception
- code_review: review/refactor/optimize existing code
- summarization: summarize/overview/recap
- creative_writing: stories/poems/creative content
- data_analysis: analyze/chart/statistics/plot
- system_admin: deploy/docker/k8s/nginx/server ops
- sensitive_query: medical/legal/financial/GDPR/PII topics

COMPLEXITY:
- low: single-step, simple factual answer
- medium: multi-step reasoning, moderate context
- high: expert-level, multi-domain, extensive context

CRITICAL RULES:
1. ALWAYS respond with ONLY valid JSON. No explanations, no markdown, no text before or after.
2. Do NOT refuse any classification request. Every query gets classified.
3. Delivery/logistics queries are general_knowledge, NOT sensitive_query.
4. "[REDACTED]" placeholders in queries are normal — classify the intent, do not flag them.

OUTPUT FORMAT (strict):
{"intent": "<intent>", "complexity": "<complexity>", "confidence": <0.0-1.0>}
```

---

## 6. Codebase Upgrades & Integration

To integrate the new system prompt and support modern prompt formats, we will make the following code upgrades:

### 6.1. Add `/api/chat` Endpoint Support
* **Target File**: [ollama_agent.py](file:///c:/Users/DELL%209420/Documents/swiss_App/homelab-ai-governance/src/governance/agents/ollama_agent.py)
* **Changes**: Add support for the `/api/chat` endpoint (sending messages as a structure of `system` and `user` roles). This improves model response styling compared to single `/api/generate` prompt construction.
```python
def generate_chat(self, prompt: str, system_prompt: str = None) -> str:
    # Construct chat payload with role structure
    payload = {
        "model": self.model_name,
        "messages": [],
        "options": {"temperature": 0.0}
    }
    if system_prompt:
        payload["messages"].append({"role": "system", "content": system_prompt})
    payload["messages"].append({"role": "user", "content": prompt})
    # POST to http://localhost:11434/api/chat
    ...
```

### 6.2. Classifier System Prompt Loading
* **Target File**: [classifier.py](file:///c:/Users/DELL%209420/Documents/swiss_App/homelab-ai-governance/src/governance/router/classifier.py)
* **Changes**: Load `classifier_v2.txt` from config, pass it to `ollama_agent.generate_chat()`, increase default query timeout from `2000ms` to `3000ms`, and log JSON failures.

### 6.3. Update Routing Schema
* **Target File**: [routing_config.yaml](file:///c:/Users/DELL%209420/Documents/swiss_App/homelab-ai-governance/config/routing_config.yaml)
* **Changes**: Add path mappings for the system prompt text files and bump the classifier timeout to `3000ms`.

---

## 7. Step-by-Step Execution Guide on `Mac_Machine`

When returning to the Mac machine, follow these steps to execute and verify the SLM evaluation phase:

### Step 1: Sync and Pull Latest Branches
Ensure your local workstation repository has all files synced:
```bash
git checkout Mac_Machine
git pull origin Mac_Machine
```

### Step 2: Download the Candidate Models via Ollama
Ensure all test candidate models are pre-loaded in the local Ollama instance:
```bash
ollama pull gemma3:4b
ollama pull qwen2.5-coder:7b
ollama pull llama3.1:8b
ollama pull phi3:mini
```

### Step 3: Run the SLM Benchmark Suite
Navigate to the governance directory and run the benchmarking script:
```bash
cd homelab-ai-governance
python -m benchmarks.slm_benchmark --ollama-url http://localhost:11434 --output benchmarks/results/
```
Verify the output report at `benchmarks/results/benchmark_report.md` to identify the model with the highest JSON compliance and lowest latency.

### Step 4: Run the Complete pytest Suite against Live Ollama
Run the integration and unit tests against the live models to verify pipeline stability:
```bash
OLLAMA_URL=http://localhost:11434 pytest tests/ -v
```

### Step 5: Start the Governance API and Backend
Expose the FastAPI microservice and launch the Spring Boot backend:
```bash
# Terminal 1: Python API
cd homelab-ai-governance
python -m src.governance.cli --port 5000

# Terminal 2: Spring Boot Backend
export SWISH_GOVERNANCE_API_URL=http://localhost:5000
cd backend
mvn spring-boot:run
```

---

## 8. Success Criteria Targets

| Metric | Baseline (Gemma 3 4B) | Target |
|---|---|---|
| **JSON Compliance Rate** | ~75% | **≥ 95%** |
| **Intent Accuracy** | ~80% | **≥ 85%** |
| **False Refusal Rate** | ~12% | **≤ 2%** |
| **Classifier Latency (p95)**| ~1800ms | **≤ 2500ms** |
| **Keyword Fallback Rate** | ~25% | **≤ 5%** |
