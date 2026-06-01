#!/usr/bin/env bash
set -e

echo "🔒 Enforcing Enterprise Branch Protection on Swish App"

# Ensure gh cli is installed and authenticated
if ! command -v gh &> /dev/null; then
    echo "❌ Error: GitHub CLI ('gh') is not installed."
    echo "Please install it from https://cli.github.com/ and run 'gh auth login' before executing this script."
    exit 1
fi

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)

if [ -z "$REPO" ]; then
    echo "❌ Error: Could not determine GitHub repository context."
    exit 1
fi

echo "Repository detected: $REPO"

PROTECTED_BRANCHES=("master" "develop")

for BRANCH in "${PROTECTED_BRANCHES[@]}"; do
    echo "Applying protection to branch: $BRANCH"
    
    gh api -X PUT "repos/$REPO/branches/$BRANCH/protection" \
        -H "Accept: application/vnd.github.v3+json" \
        -F required_status_checks[strict]=true \
        -F required_status_checks[contexts][]=build \
        -F enforce_admins=true \
        -F required_pull_request_reviews[dismiss_stale_reviews]=true \
        -F required_pull_request_reviews[require_code_owner_reviews]=false \
        -F required_pull_request_reviews[required_approving_review_count]=1 \
        -F restrictions=null

    echo "✅ Protection applied to $BRANCH"
done

echo "🎉 Enterprise Branch Protection successfully enabled."
