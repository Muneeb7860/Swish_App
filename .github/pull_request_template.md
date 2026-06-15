# 🚀 Pull Request Checklist

Please fill out this checklist before submitting your PR. This helps the team and CI evaluate your changes efficiently.

---

## 📋 1. Type of Change
*Select the appropriate checkbox:*
- [ ] 🎨 **Frontend UI Fix / Refactor** (Alignment, style, minor layout edits - *Bypasses backend test/coverage gates*)
- [ ] ⚙️ **Backend Core Change** (Java domain, API, DB migrations - *Triggers JaCoCo 75% coverage check*)
- [ ] 🤖 **AI / Governance Change** (Python microservice, RAG models)
- [ ] 🛠️ **DevOps & Infra** (GitHub workflows, Docker Compose, Kubernetes)

---

## ✅ 2. Pre-Merge Verification
*Ensure all of the following checks are complete before requesting review:*
- [ ] **Conventional Commits:** All commits in this branch follow the Conventional Commits format (e.g., `fix(ui): adjust button alignment` or `feat(auth): add MFA verification`).
- [ ] **Clean Lint & Build:** Local lints and builds pass without errors (e.g., `npm run lint` or `mvn compile`).
- [ ] **No Dependency Bloat:** Checked that no local file links (like `file:..` or hardcoded paths) have leaked into `package.json` or lockfiles.
- [ ] **Detailed Description:** The PR description field below contains a clear summary of the changes (min 10 characters to pass the branch protection check).

---

## 📝 3. Description & Context
*Provide a brief summary of the changes and the problem they solve:*

*(Write details here...)*

---

## 🧪 4. Testing & Coverage Status
*If this is a backend change, state if tests were added or modified:*
*   **New Unit Tests Added?** [Yes / No / N/A]
*   **Local Coverage Percentage Verified?** [Yes / No]
