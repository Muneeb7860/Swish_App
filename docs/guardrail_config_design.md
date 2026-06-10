# Shared Guardrail Library & Per-Agent Config — Design Spec (Phase 2)

## Overview

One shared policy library defines every safety/compliance rule. Each agent
references that library and supplies a small set of *overrides* — toggling
rules on/off or tuning their action. There are **not** N separate guardrail
implementations; there is one base library plus thin per-agent config files.

Each rule specifies both its **detection mechanism** (`detector`) and its
**enforcement action** (`default_action`). Agents may override the action but
never the detection logic — one detector implementation per rule, shared
everywhere.

## 1. Shared Base Policy Library (`shared_guardrails.yaml`)

```yaml
# shared_guardrails.yaml — single source of truth for all safety/policy rules
version: 2
# Schema version — all audit log entries include this version number.
# When adding/removing fields, bump the version. The weekly review script
# handles mixed-version entries by filling missing fields with defaults.
schema_version: "2.0"

# Override constraints by severity tier.
# Critical: cannot be disabled, cannot weaken action.
# High: cannot be disabled, action can be adjusted within bounds.
# Medium/Low: can be disabled, broader action range.
override_policy:
  critical:
    allow_disable: false
    allowed_actions: [block]
  high:
    allow_disable: false
    allowed_actions: [block, redact, warn]
  medium:
    allow_disable: true
    allowed_actions: [block, redact, warn, log]
  low:
    allow_disable: true
    allowed_actions: [block, redact, warn, log, pass]

rules:
  - id: pii_filter
    description: "Detects and redacts PII (emails, phone numbers, SSNs, credentials, etc.)"
    applies_to: [input, output]
    default_action: redact
    severity: high
    detector:
      type: regex
      patterns:
        - name: email
          pattern: '[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}'
        - name: ssn
          pattern: '\b\d{3}-\d{2}-\d{4}\b'
        - name: phone_us
          pattern: '\b(\+1[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\b'
        - name: credit_card
          pattern: '\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b'
        - name: ip_address
          pattern: '\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b'
        - name: connection_string
          pattern: '(?i)(postgres|mysql|mongodb|redis)://[^\s]+'
        - name: api_key
          pattern: '(?i)(api[_-]?key|secret|token|password)\s*[:=]\s*["\x27]?[a-zA-Z0-9_\-]{16,}'
    # NOTE: These same patterns are used by the pre-routing PII scan in the
    # Semantic Router (§1). They are defined once here and imported by the
    # router — not duplicated.

  - id: hate_speech_filter
    description: "Detects toxic, hateful, or harassing content using a lightweight scoring model"
    applies_to: [input, output]
    default_action: block
    severity: critical
    detector:
      type: toxicity_scorer
      # Uses a lightweight toxicity scoring model (e.g., detoxify/unitary
      # or a distilled BERT-tiny) that runs on CPU in <50ms per call.
      # This replaces keyword-list detection which is both trivially evadable
      # and produces guaranteed false positives on innocent words ("kill a
      # process", "execute the script").
      model: detectors/toxicity_scorer.onnx
      threshold: 0.85          # block if toxicity score >= 0.85
      warn_threshold: 0.60     # log a warning if score >= 0.60 (for review)
      max_input_chars: 2000    # truncate to first 2000 chars for speed
      # Fallback: if the model fails to load or crashes, fall back to the
      # keyword list (worse but better than nothing).
      fallback:
        type: keyword_list
        source: detectors/hate_speech_terms.txt
        match_mode: word_boundary  # match whole words only, not substrings
        # word_boundary prevents "kill" matching in "skill", "killed" matching
        # in "skilled", etc. Pattern: \b{term}\b

  - id: license_check
    description: "Flags generated code that closely matches GPL/AGPL-licensed snippets"
    applies_to: [output]
    default_action: warn
    severity: medium
    detector:
      type: fingerprint
      method: minhash_lsh
      corpus: detectors/gpl_agpl_corpus.idx
      similarity_threshold: 0.85
      min_snippet_lines: 5

  - id: prompt_injection_filter
    description: "Detects prompt injection attempts in user input AND retrieved context"
    applies_to: [input]
    default_action: strip
    # Action is "strip" not "block" — removes the injected segments but
    # continues processing the clean portions. Blocking entirely on
    # injection detection would let attackers DoS the system by poisoning
    # retrievable documents.
    severity: critical
    detector:
      type: heuristic
      rules:
        - pattern: '(?i)(ignore|disregard|forget)\s+(all\s+)?(previous|above|prior)\s+(instructions|rules|prompts)'
        - pattern: '(?i)you\s+are\s+now\s+'
        - pattern: '(?i)new\s+instructions?:'
        - pattern: '(?i)system\s*prompt\s*:'
        - pattern: '(?i)override\s+(mode|instructions|rules)'
        - pattern: '(?i)(?:assistant|ai|bot)\s*:\s*'  # role injection
        - pattern: '(?i)<<\s*(?:SYS|INST|system)\s*>>'  # chat template injection
    # IMPORTANT: This rule runs on the ENRICHED input (post-context-constructor),
    # not just the raw user query. This catches injections embedded in documents
    # retrieved from the Memory Mesh. The Semantic Router's input pipeline (§2)
    # runs this scan at step 3, before classification.

  - id: profanity_filter
    description: "Filters profanity in output"
    applies_to: [output]
    default_action: warn
    severity: low
    detector:
      type: keyword_list
      source: detectors/profanity_terms.txt
      match_mode: word_boundary  # whole-word matching prevents "ass" in "class"

  - id: code_safety_catastrophic
    description: "Blocks code that could cause irreversible system-wide damage"
    applies_to: [output]
    default_action: block
    severity: critical
    detector:
      type: regex
      patterns:
        # Only truly catastrophic patterns — root-level destruction, not
        # normal development commands targeting specific paths.
        - name: rm_rf_root
          pattern: '\brm\s+-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*\s+(/\s|/\*|/\b[^t/])'
          # Matches: rm -rf / , rm -rf /*, rm -rf /home
          # Does NOT match: rm -rf /tmp/cache, rm -rf ./build
        - name: dd_disk
          pattern: '\bdd\b.*\bof=/dev/(sd[a-z]|nvme|disk)\b'
        - name: format_root
          pattern: '(?i)\b(mkfs)\b.*\b/dev/(sd[a-z]|nvme)\b'
        - name: drop_database
          pattern: '(?i)\bDROP\s+DATABASE\b'

  - id: code_safety_cautionary
    description: "Warns on code with potentially destructive commands that may be intentional"
    applies_to: [output]
    default_action: warn
    severity: high
    detector:
      type: regex
      patterns:
        # These are often legitimate in development. Warn (don't block) so
        # the output reaches the user with a flag. The user decides.
        - name: rm_rf_general
          pattern: '\brm\s+-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*\b'
        - name: drop_table
          pattern: '(?i)\bDROP\s+TABLE\b'
        - name: truncate
          pattern: '(?i)\bTRUNCATE\s+TABLE\b'
        - name: chmod_777
          pattern: '\bchmod\s+777\b'
        - name: curl_pipe_bash
          pattern: '\bcurl\b.*\|\s*(ba)?sh\b'
        - name: sudo_rm
          pattern: '\bsudo\s+rm\b'

  - id: markdown_formatting_filter
    description: "Strips markdown formatting from outputs intended for plain-text channels"
    applies_to: [output]
    default_action: warn
    severity: low
    detector:
      type: regex
      patterns:
        - name: headers
          pattern: '^#{1,6}\s+'
        - name: bold_italic
          pattern: '\*{1,3}[^*]+\*{1,3}'
        - name: code_blocks
          pattern: '```[\s\S]*?```'
```

## 2. Per-Agent Override Configs

Each agent's config is a small diff against the base library — only the rules
that differ from the default need to appear. Overrides are validated at load
time against the `override_policy` — attempting to disable a critical rule or
set an action outside `allowed_actions` raises a config validation error.

**Valid override keys:** `enabled`, `default_action`. Any other key in an
override block is a validation error (catches typos like `defualt_action`).

```yaml
# agents/gemma_reasoner.yaml
agent_id: gemma_reasoner
base_library: shared_guardrails.yaml
overrides:
  code_safety_cautionary:
    default_action: warn  # reasoner may include code in explanations — warn, don't block
  markdown_formatting_filter:
    enabled: false      # output is consumed internally; formatting irrelevant
  # NOTE: code_safety_catastrophic stays enabled (critical, cannot disable).
  # If Gemma Reasoner explains "to delete everything, run rm -rf /", the
  # catastrophic rule catches it. The cautionary rule warns on "rm -rf ./build"
  # which is fine in an explanation.
```

```yaml
# agents/mistral_summarizer.yaml
agent_id: mistral_summarizer
base_library: shared_guardrails.yaml
overrides:
  code_safety_cautionary:
    default_action: warn  # summarizer rarely generates code; warn (not block)
                          # if it does. Cannot disable — high severity.
  license_check:
    enabled: false      # not applicable to summaries
```

```yaml
# agents/deepseek_coder.yaml
agent_id: deepseek_coder
base_library: shared_guardrails.yaml
overrides:
  markdown_formatting_filter:
    enabled: false      # code blocks must keep formatting
  pii_filter:
    default_action: warn   # code samples may legitimately contain placeholder
                            # PII-like data (test emails, dummy SSNs); warn
                            # instead of redacting so output stays usable
  license_check:
    default_action: block  # stricter than base default — coder agent blocks
                            # instead of warning on license matches
  code_safety_cautionary:
    default_action: warn   # coder output naturally contains rm, DROP, etc.
                            # warn so user sees the flag but gets the output
```

```yaml
# agents/cloud_frontier.yaml
agent_id: cloud_frontier
base_library: shared_guardrails.yaml
overrides:
  # Cloud responses need all guardrails — especially output-side filtering,
  # since we don't control the cloud model's behavior.
  # No rules disabled; all defaults apply.
  license_check:
    default_action: block  # cloud model may reproduce licensed code more
                            # readily due to larger training corpus
```

## 3. Runtime Loading & Enforcement

```python
import re
from typing import Optional

VALID_OVERRIDE_KEYS = {"enabled", "default_action"}
VALID_ACTIONS = {"block", "redact", "warn", "log", "pass", "strip"}

def load_guardrails(agent_id: str) -> list[dict]:
    """Load base rules, apply per-agent overrides with validation."""
    config = load_yaml("shared_guardrails.yaml")
    base = config["rules"]
    override_policy = config["override_policy"]
    overrides = load_yaml(f"agents/{agent_id}.yaml").get("overrides", {})

    resolved = []
    for rule in base:
        cfg = dict(rule)
        rule_id = rule["id"]

        if rule_id in overrides:
            agent_override = overrides[rule_id]

            # Validate override keys — catch typos like "defualt_action"
            invalid_keys = set(agent_override.keys()) - VALID_OVERRIDE_KEYS
            if invalid_keys:
                raise ConfigError(
                    f"Agent '{agent_id}', rule '{rule_id}': "
                    f"invalid override keys: {invalid_keys}. "
                    f"Valid keys: {VALID_OVERRIDE_KEYS}"
                )

            severity = rule["severity"]
            policy = override_policy[severity]

            # Enforce severity-based override constraints
            if not agent_override.get("enabled", True) and not policy["allow_disable"]:
                raise ConfigError(
                    f"Agent '{agent_id}' cannot disable {severity}-severity "
                    f"rule '{rule_id}'"
                )
            if "default_action" in agent_override:
                action = agent_override["default_action"]
                if action not in policy["allowed_actions"]:
                    raise ConfigError(
                        f"Agent '{agent_id}', rule '{rule_id}': "
                        f"action '{action}' not allowed for {severity} severity. "
                        f"Allowed: {policy['allowed_actions']}"
                    )

            cfg.update(agent_override)

        if cfg.get("enabled", True):
            resolved.append(cfg)

    return resolved


def apply_rules(rules: list[dict], phase: str, content: str,
                agent_id: str, input_hash: str) -> dict:
    """
    Run all rules that match the given phase ('input' or 'output') against
    the content. Returns the enforcement result.

    Each detector call is wrapped in a try/catch — a crashing detector skips
    that rule (logged as detector_error) rather than disabling all guardrails.
    """
    result = {
        "allowed": True,
        "content": content,
        "triggered_rules": [],
        "warnings": []
    }

    for rule in rules:
        if phase not in rule["applies_to"]:
            continue

        try:
            matched = run_detector(rule["detector"], result["content"])
        except Exception as e:
            # Detector crash — skip this rule, don't skip all guardrails
            log_audit_event(
                type="detector_error",
                agent_id=agent_id,
                rule_id=rule["id"],
                error=str(e),
                input_hash=input_hash
            )
            continue

        # Log match events per-event; log non-matches as aggregate counters
        # (see §4 for details on audit log structure)
        if matched:
            log_audit_event(
                type="guardrail_trigger",
                agent_id=agent_id,
                rule_id=rule["id"],
                action=rule["default_action"],
                phase=phase,
                input_hash=input_hash,
                matched=True
            )
        else:
            # Increment in-memory counter; flushed to audit log hourly
            increment_rule_eval_counter(rule["id"], agent_id, phase)
            continue

        action = rule["default_action"]
        trigger = {"rule_id": rule["id"], "action": action, "severity": rule["severity"]}
        result["triggered_rules"].append(trigger)

        if action == "block":
            result["allowed"] = False
            return result  # hard stop
        elif action == "redact":
            result["content"] = redact_matches(rule["detector"], result["content"])
        elif action == "strip":
            # Remove injected segments but keep clean content
            result["content"] = strip_matched_segments(rule["detector"], result["content"])
        elif action == "warn":
            result["warnings"].append(trigger)
            # content passes through with warning flag
        # "log" and "pass" — recorded but no content modification

    return result


def run_detector(detector_config: dict, content: str) -> bool:
    """Dispatch to the appropriate detector implementation."""
    dtype = detector_config["type"]

    if dtype == "regex":
        return any(
            re.search(p["pattern"], content)
            for p in detector_config["patterns"]
        )

    elif dtype == "keyword_list":
        terms = load_terms(detector_config["source"])
        mode = detector_config.get("match_mode", "substring")

        if mode == "word_boundary":
            # Whole-word matching: "kill" matches "kill" but not "skill"
            for term in terms:
                pattern = r'\b' + re.escape(term) + r'\b'
                flags = re.IGNORECASE if detector_config.get("match_mode") else 0
                if re.search(pattern, content, flags):
                    return True
            return False
        elif mode == "case_insensitive":
            content_lower = content.lower()
            return any(t.lower() in content_lower for t in terms)
        else:
            return any(t in content for t in terms)

    elif dtype == "heuristic":
        return any(
            re.search(r["pattern"], content)
            for r in detector_config["rules"]
        )

    elif dtype == "fingerprint":
        return fingerprint_match(
            content,
            detector_config["corpus"],
            detector_config["similarity_threshold"],
            detector_config["min_snippet_lines"]
        )

    elif dtype == "toxicity_scorer":
        try:
            score = run_toxicity_model(
                detector_config["model"],
                content[:detector_config.get("max_input_chars", 2000)]
            )
            if score >= detector_config["threshold"]:
                return True
            if score >= detector_config.get("warn_threshold", 1.0):
                # Log for review but don't trigger the rule action
                log_audit_event(
                    type="toxicity_warning",
                    score=score,
                    threshold=detector_config["threshold"]
                )
            return False
        except Exception:
            # Model failed — fall back to keyword list if configured
            fallback = detector_config.get("fallback")
            if fallback:
                return run_detector(fallback, content)
            raise  # no fallback, let the caller handle it

    else:
        raise ConfigError(f"Unknown detector type: {dtype}")
```

**Enforcement call sites** — the router calls `apply_rules()` at two points:

```python
# In the routing pipeline (see Semantic Router §2 for full flow):
rules = load_guardrails(agent_id)

# 1. INPUT phase — before sending to agent
#    Note: prompt_injection_filter already ran on enriched input during
#    the pre-classification pipeline (Router §2, step 3). The input
#    guardrails here catch anything remaining (PII redaction, etc.)
input_result = apply_rules(rules, "input", enriched_input, agent_id, input_hash)
if not input_result["allowed"]:
    return blocked_response(input_result["triggered_rules"])
filtered_input = input_result["content"]  # may have redactions

# 2. Send to agent
response = call_agent(agent_id, filtered_input)

# 3. OUTPUT phase — before returning to user
output_result = apply_rules(rules, "output", response, agent_id, input_hash)
if not output_result["allowed"]:
    return blocked_response(output_result["triggered_rules"])
final_response = output_result["content"]  # may have redactions

# Attach warnings to response metadata (don't hide them from user)
if output_result["warnings"]:
    final_response = attach_warnings(final_response, output_result["warnings"])
```

## 4. Unified Audit Log (`audit.jsonl`)

All telemetry writes to a single append-only file with a `type` discriminator
and `schema_version` field.

### Log entry types

```json
{"schema_version":"2.0","timestamp":"2026-06-10T14:32:01Z","type":"guardrail_trigger","agent_id":"deepseek_coder","rule_id":"license_check","action":"block","phase":"output","input_hash":"a1b2c3","matched":true}
{"schema_version":"2.0","timestamp":"2026-06-10T14:35:18Z","type":"guardrail_trigger","agent_id":"gemma_reasoner","rule_id":"pii_filter","action":"redact","phase":"input","input_hash":"d4e5f6","matched":true}
{"schema_version":"2.0","timestamp":"2026-06-10T14:40:00Z","type":"routing_decision","input_hash":"g7h8i9","classification":{"intent":"code_debugging","complexity":"high","confidence":0.88},"matched_rule":7,"route":"cloud_frontier","escalated":false,"local_only":false,"budget_constrained":false}
{"schema_version":"2.0","timestamp":"2026-06-10T15:00:00Z","type":"rule_eval_counters","period":"2026-06-10T14:00:00Z/2026-06-10T15:00:00Z","counters":{"pii_filter":{"gemma_reasoner":{"input":12,"output":12},"deepseek_coder":{"output":8}},"hate_speech_filter":{"gemma_reasoner":{"input":12,"output":12}}}}
{"schema_version":"2.0","timestamp":"2026-06-10T14:33:00Z","type":"detector_error","agent_id":"deepseek_coder","rule_id":"pii_filter","error":"regex timeout on oversized input","input_hash":"j1k2l3"}
{"schema_version":"2.0","timestamp":"2026-06-10T14:41:00Z","type":"cloud_api_call","agent_id":"cloud_frontier","input_tokens":1200,"output_tokens":800,"estimated_cost_usd":0.032,"daily_cumulative_usd":3.45}
```

### Non-match logging strategy

Logging every non-match per-event floods the log (90%+ of entries are non-events).
Instead:

- **Matches:** logged per-event with full context (rule_id, action, phase, input_hash)
- **Non-matches:** aggregated as hourly counters (`type: "rule_eval_counters"`).
  Each counter records how many times a rule was evaluated (and passed) per
  agent per phase. This gives the weekly review script enough data to compute
  trigger rates (`matches / total_evals`) without 10× log volume.

### Log rotation

```yaml
# audit_log_config.yaml
rotation:
  max_file_size_mb: 100
  max_files: 12            # keep 12 rotated files (~1.2 GB max)
  rotation_format: "audit-{date}.jsonl"
  compress_rotated: true   # gzip old files
  retention_days: 365      # delete files older than 1 year
```

Rotation is handled by a simple cron job or Python's `logging.handlers.RotatingFileHandler`.
The weekly review script reads all files in the retention window.

### Schema versioning

Every log entry includes `schema_version`. When fields are added or removed:
1. Bump the `schema_version` in `shared_guardrails.yaml`
2. The weekly review script handles mixed versions by filling missing fields
   with defaults (e.g., if `budget_constrained` doesn't exist in v1.0 entries,
   treat it as `false`)
3. A migration script can backfill old entries if needed, but it's not required —
   the review script is tolerant by design

## 5. Mapping to Roadmap

| Roadmap item | This design |
|---|---|
| Shared Guardrail Layer (Current System) | `shared_guardrails.yaml` with detector specs |
| Per-Agent Policy Tuning (Shared Base Library) | `agents/*.yaml` overrides (validated) |
| Guardrail Drift Tracking (medium priority) | Unified `audit.jsonl` + weekly report |
| Local Audit Trail (low priority) | Same `audit.jsonl` (type, agent_id, rule_id, action, timestamp) |
| PII Security Boundary | Pre-routing regex scan (shared patterns with `pii_filter`) |
| Prompt Injection Defense | Heuristic filter on enriched input + `strip` action |
| Toxicity Detection | ONNX toxicity scorer with keyword fallback |

Total new infrastructure: one shared YAML, one override YAML per agent (including
cloud_frontier), one detector resources directory (ONNX model, keyword lists,
license corpus), one append-only log file with rotation, and the pre-routing PII
scan (shared code with the guardrail library). No microservices, no ledger, no
per-agent guardrail binaries.
