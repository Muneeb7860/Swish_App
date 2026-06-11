#!/bin/bash
# Pre-commit hook script to validate code quality, unit tests, and prompt guardrails.
set -e

echo "🔍 Running pre-commit verification checks..."

# 1. Run Python Unit Tests
echo "🐍 Running Python unit and integration tests..."
cd homelab-ai-governance
.venv/bin/pytest
cd ..

# 2. Check if the Governance API is running to execute Promptfoo assertions
if curl -s http://localhost:8000/health | grep -q "UP"; then
  echo "🎯 Governance service is live. Running Promptfoo evaluations..."
  cd homelab-ai-governance
  npx -y promptfoo@latest eval
  cd ..
else
  echo "⚠️ Governance service is not running on port 8000. Skipping Promptfoo evaluations."
fi

echo "✅ All checks passed successfully!"
exit 0
