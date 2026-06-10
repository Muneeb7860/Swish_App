# Homelab AI Governance Platform

This directory contains the semantic routing, guardrail enforcement, and recursive validation system for private on-premise AI models.

## Structure

- `config/`: Core routing configs and agent profiles.
- `detectors/`: Regex, keyword, and ONNX models for safety policies.
- `src/governance/`: Main source package.
  - `agents/`: Local and cloud agent backends.
  - `evaluator/`: Recursive quality loop and scoring heuristics.
  - `guardrails/`: Loader and enforcer engines.
  - `router/`: Classification, token limits, and decision table.
- `tests/`: Automated unit and integration testing suite.

## Development and Verification

Run tests:
```bash
pytest tests/ -v --cov=src/governance --cov-report=term-missing
```
