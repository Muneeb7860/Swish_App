# Swish App: Recursive AI Governance Alignment & Verification Plan

## Goal Description
Perform a deep, recursive audit of the AI Governance system to find and resolve all prompt evaluation, safety, and integration misalignments. Ensure 100% of python unit tests and Promptfoo evaluation test cases pass successfully.

### Key Misalignments Identified
1. **Ollama Cold Start Timeouts**: The classifier has a strict 10s timeout, causing it to fall back to keyword matching when Ollama models are cold. Other agents suffer from latency on the first request.
2. **PII Leakage in DeepSeek Agent**: `deepseek_coder.yaml` overrides `pii_filter` to `warn` instead of `redact`, causing queries routed to the coder to leak unredacted email and SSN in the response.
3. **Context Blindness in Self-Correction**: The recursive self-correction loop's feedback template (`_FEEDBACK_TEMPLATE`) does not include the retrieved `context_docs`. When asked to ground its response, the model cannot see the original grounding documents.
4. **PII Leakage in Default Context**: The `MemoryMesh` default document template incorporates the raw, unredacted query (`f"Default context reference document for query: {query}"`), leaking PII into the context.
5. **CCR Redaction Penalties**: Correctly redacted outputs (containing `[REDACTED:EMAIL]`, `[REDACTED:SSN]`) are penalized under the Context Conservation Ratio (CCR) because words like "redacted", "email", and "ssn" do not exist in the source context.
6. **Offline Infrastructure**: Docker containers for `postgres-letta` (port 5434) and `phoenix` (OTel collector/UI on port 4318/6006) are currently offline, causing OTel trace export errors and RAG database connection refusals.

---

## Proposed Changes

### 1. Docker Infrastructure
- Start the required support services so that RAG (pgvector on port 5434) and OTel tracing (Arize Phoenix on port 4318) are fully functional.
```bash
docker compose -f docker-compose-local.yml up -d postgres-letta phoenix
```

### 2. PII Filter Override Alignment
#### [MODIFY] [deepseek_coder.yaml](file:///Users/muneeb/Documents/GitHub/Swissqcommerce/homelab-ai-governance/config/agents/deepseek_coder.yaml)
- Remove the `pii_filter` override completely or change it to `redact` to enforce data sovereignty for coder agent outputs.

### 3. Context Grounding in Self-Correction Feedback Loop
#### [MODIFY] [loop.py](file:///Users/muneeb/Documents/GitHub/Swish_App-1/homelab-ai-governance/src/governance/evaluator/loop.py)
- Update `_FEEDBACK_TEMPLATE` to include a `### CONTEXT DOCUMENTS` section containing `{context_docs}`.
- Format `feedback_prompt` with the `context_docs` variable inside `run_self_correction_loop`.

### 4. PII Redaction in Default RAG context document
#### [MODIFY] [memory_mesh.py](file:///Users/muneeb/Documents/GitHub/Swish_App-1/homelab-ai-governance/src/governance/stubs/memory_mesh.py)
- Import `redact_pii` from `governance.guardrails.pii_patterns`.
- Update `_get_default_document` to apply `redact_pii(query)` to prevent raw PII from leaking into context.

### 5. CCR Scoring Adjustment for Redaction Placeholders
#### [MODIFY] [metrics.py](file:///Users/muneeb/Documents/GitHub/Swish_App-1/homelab-ai-governance/src/governance/evaluator/metrics.py)
- In `compute_context_conservation`, add redaction-related tokens (`redacted`, `email`, `ssn`, `phone`, `card`, `ip`, `address`, `connection`, `string`, `api`, `key`) to the ignore list so that correctly redacted outputs do not trigger grounding penalties.

### 6. Timeout Tuning
#### [MODIFY] [routing_config.yaml](file:///Users/muneeb/Documents/GitHub/Swish_App-1/homelab-ai-governance/config/routing_config.yaml)
- Increase the classifier's `timeout_ms` from 10000 (10s) to 30000 (30s) to absorb model loading latency on cold starts.

---

## Verification Plan

### Automated Tests
- Run `pytest` to verify python unit and integration tests (55/55 passing).
- Run `npx promptfoo eval` and ensure that all 4 safety and capability test cases pass successfully.

### Manual Verification
- Verify in Arize Phoenix (`http://localhost:6006`) that trace context is correctly propagated from the Java backend to the Python FastAPI microservice.
