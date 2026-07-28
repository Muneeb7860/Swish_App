# SwishOS — Homelab AI Governance Platform

> Zero-latency, fail-closed AI agent governance and semantic routing engine.

---

## Quickstart (5 Minutes)

### 1. Prerequisites
- Python 3.10+
- [Ollama](https://ollama.com/) (for local model inference)

### 2. Environment Setup

```bash
git clone https://github.com/muneeb7860/Swish_App.git
cd Swish_App/homelab-ai-governance

# Create virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

### 3. Run Local Governance Engine

```bash
# Allow mock fallback if Ollama is not yet initialized
export GOVERNANCE_ALLOW_MOCK_FALLBACK=1
export PYTHONPATH=src

# Launch FastAPI governance server on port 8000
python3 -c "import uvicorn; uvicorn.run('governance.server:app', host='127.0.0.1', port=8000)"
```

### 4. Send Test Governance Payload

```bash
curl -X POST http://localhost:8000/api/v1/govern \
  -H "Content-Type: application/json" \
  -d '{"query": "How do I optimize SQL queries for Postgres?"}'
```

### 5. Run Red-Team Security Gate

```bash
pip install agentic-redteam

# Run security benchmark sweep
agentic-redteam --target-url http://localhost:8000/api/v1/govern --ci
```

---

## Architecture

```
POST /api/v1/govern
  │
  ├── [G1] NeMo Pattern Gate (Colang matcher, < 5ms)
  ├── [G2] PII Regex Scanner (Forces local_only on hit)
  ├── [R1] Intent Classifier (qwen2.5:3b via Ollama)
  ├── [R2] 27-Rule Decision Table (Intent × Complexity map)
  ├── [A]  Agent Backend (Gemma 4B / Mistral / DeepSeek / Groq)
  ├── [G3] Output Guardrails & Self-Correction Loop (≤3 retries)
  └── [X]  Append-Only JSONL Audit Log (DuckDB analytics)
```

---

## Verification & Testing

Run full unit and integration test suite:

```bash
PYTHONPATH=src pytest tests/ -v
```

---

## Configuration

- `config/routing_config.yaml`: Agent definitions, model routing tables, daily cost ceilings.
- `config/shared_guardrails.yaml`: Global input/output guardrail rules.
- `docs/GOVERNANCE_SPEC.md`: Canonical architecture specification.
