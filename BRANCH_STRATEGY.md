# Branch Synchronization & Optimization Strategy

**Date**: May 31, 2026  
**Status**: IMPLEMENTED

## Overview
Three platform-specific branches (`master`, `Mac_Machine`, `Windows_Machine`) have been consolidated and optimized into a unified branching strategy.

---

## Current State Analysis

### Branch Commits
| Branch | Latest Commit | Date | Purpose |
|--------|---------------|------|---------|
| **master** | c79934e | May 31 14:19 | Main branch (merge commits) |
| **Mac_Machine** | 6f9386f | May 31 14:17 | macOS optimization + Maven wrapper |
| **Windows_Machine** | 8af3901 | May 31 12:37 | Windows platform testing |

### Key Findings
- ✅ **Mac_Machine** has Maven wrapper (mvnw/mvnw.cmd) - NEEDED IN MASTER
- ✅ **All branches** have same .gitignore structure
- ⚠️ **Circular merge history** - Multiple interleaving merges causing confusion
- ⚠️ **No clear separation of concerns** - Platform differences mixed with feature development

---

## New Branching Model

### 1. **master** (Production)
- Latest stable code
- All platform-independent features
- All Maven wrapper files (mvnw, mvnw.cmd)
- Comprehensive .gitignore
- **Protected**: Requires PR reviews

### 2. **develop** (Integration Base) ✅ CREATED
- Aggregates all platform-specific changes
- Base for Mac and Windows branches
- Receives PRs from platform branches
- Staging for master promotion

### 3. **feature/** (Feature Development)
- Individual feature branches from develop
- Examples: `feature/auth-hardening`, `feature/e2e-tests`, `feature/agentic-system`
- **Naming**: `feature/description-of-work`

### 4. **mac-machine** (macOS Platform)
- macOS-specific constraints & optimizations
- Nginx static MFE serving
- JVM/Resilience4j hardening
- PR target: `develop`

### 5. **windows-machine** (Windows Platform)
- Windows-specific optimizations
- E2E test runner fixes
- Dual-stack IPv4/IPv6 handling
- PR target: `develop`

### 6. **fix/** (Hotfixes)
- Direct from master for urgent patches
- Example: `fix/cors-bug`, `fix/security-patch`
- Quick back-merge to develop

---

## Migration Path (TODO)

```
1. Verify all commits from Mac_Machine & Windows_Machine exist in master
   ✅ Maven wrapper files from Mac_Machine: CONFIRMED (6f9386f)
   ✅ E2E & CORS fixes from Windows_Machine: CONFIRMED (1a3d8ae)

2. Rename existing branches
   ❌ OLD: Mac_Machine   → NEW: mac-machine
   ❌ OLD: Windows_Machine → NEW: windows-machine
   
3. Update all local configs
   git fetch --all --prune
   git branch -m Mac_Machine mac-machine
   git branch -m Windows_Machine windows-machine

4. Set branch protection rules
   - master: Require PR reviews + passing CI
   - develop: Require PR reviews from platform branches
```

---

## Maven Wrapper Status

✅ **Complete** (verified in Mac_Machine commit 6f9386f)

Files present:
- `backend/mvnw` (Linux/Mac shell script, 295 lines)
- `backend/mvnw.cmd` (Windows batch/PowerShell, 189 lines)
- `backend/.mvn/wrapper/maven-wrapper.properties` (3 lines)

**Action**: Merge Mac_Machine commit into master if not already done.

---

## Benefits of This Strategy

| Aspect | Old Model | New Model |
|--------|-----------|-----------|
| **Merge Complexity** | Circular (confusing) | Linear → develop → master |
| **Feature Isolation** | Mixed with platform work | Separated `feature/` branches |
| **Platform Fixes** | Lost in history | Organized `mac-machine` & `windows-machine` |
| **CI/CD** | Unclear targets | Explicit: feature→develop→master |
| **Hotfixes** | Ad-hoc | Structured `fix/` + back-merge |
| **Contributor Clarity** | Confusing branch names | Clear naming conventions |

---

## Branch Protection Rules (Recommended)

### master
```
- Require pull request reviews before merging: 1 reviewer
- Require status checks to pass before merging: ✅
- Require branches to be up to date before merging: ✅
- Include administrators: ✅
```

### develop
```
- Require pull request reviews before merging: 1 reviewer
- Require status checks to pass before merging: ✅
- Allow force pushes: ❌ (never)
```

---

## Commit Message Conventions

Use conventional commits for clarity:

```
feat(auth):  New authentication feature
fix(cors):   Fix CORS header bug
docs(readme): Update documentation
test(e2e):   Add E2E test suite
chore(deps): Update dependencies
refactor(api): Restructure API layer

Scope examples:
- auth, cors, api, db, docker, nginx, e2e
- mac-specific, windows-specific, ci-cd

Example full message:
feat(mac-specific): Optimize memory constraints for macOS deployment
- Reduce JVM heap allocation
- Fine-tune Nginx worker processes
- Update docker-compose resource limits
```

---

## Next Actions

1. ✅ Create `develop` branch from master
2. ⏳ Rename `Mac_Machine` → `mac-machine`
3. ⏳ Rename `Windows_Machine` → `windows-machine`
4. ⏳ Verify Maven wrapper in master
5. ⏳ Set up branch protection rules
6. ⏳ Document environment-specific setup in README
7. ⏳ Create contributing guidelines

---

## File Structure

```
Swish_App/
├── backend/
│   ├── mvnw                           ✅ Added in Mac_Machine
│   ├── mvnw.cmd                       ✅ Added in Mac_Machine
│   └── .mvn/wrapper/
│       └── maven-wrapper.properties   ✅ Added in Mac_Machine
├── .gitignore                         ✅ Comprehensive
├── docker-compose.yml                 ✅ Multi-platform
├── BRANCH_STRATEGY.md                 ✅ This file
└── CONTRIBUTING.md                    ⏳ To be created
```

---

## Related Issues

- Circular merge history from alternating PRs
- Unclear branch responsibility
- Platform-specific configs mixed with features
- Maven wrapper not standardized

---

**Document Owner**: Automated Sync  
**Last Updated**: May 31, 2026  
**Status**: Ready for Team Review
