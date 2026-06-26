# Phase 2: Shared Component Consolidation Analysis

**Status**: Complete analysis + extraction plan  
**Date**: 2026-06-26  
**Estimated Implementation**: 2-3 hours

---

## 🎯 Executive Summary

**12,401 lines of CSS** duplicated across 5 frontend apps. **Consolidation Win**: Extract 15+ reusable components + unified token system, reduce CSS to ~3,000 lines (75% savings).

**Tier 1 Priority** (immediate extraction):
- LoadingSkeleton variants (4 components)
- Unified AuthPortal (consolidate 3 auth flows)
- Design tokens (unified from all 5 apps)

---

## 📊 Current State: Component Inventory

### CSS Fragmentation
```
frontend-host:      3,371 lines (most complex)
frontend-rider:     2,870 lines
frontend-admin:     2,563 lines
frontend-customer:  2,333 lines
frontend-b2b:       1,264 lines (cleanest — our reference)
━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:             12,401 lines (overlapping + duplicated)
```

**Overlap Estimate**: 60-70% (tokens, common components, animations)

### By Reusability Tier

#### Tier 1: HIGH REUSE (Core Infrastructure)

| Component | Source | Type | Reuse | Effort | Priority |
|-----------|--------|------|-------|--------|----------|
| **LoadingSkeleton** (4 variants) | frontend-host | Loading state | **5/5** | 30min | 🔴 P0 |
| **AuthPortal** (unified) | admin/customer/host | Auth gate | **5/5** | 1hr | 🔴 P0 |
| **Design Tokens** | all 5 apps | Token system | **5/5** | 1.5hr | 🔴 P0 |
| **Base CSS Classes** | all apps | Styling | **4/5** | 30min | 🟠 P1 |
| **Animations** | all apps | Motion | **4/5** | 30min | 🟠 P1 |

**Tier 1 Subtotal**: ~4 hours implementation

---

#### Tier 2: MEDIUM REUSE (Domain Components)

| Component | Source | Type | Reuse | Effort | Priority |
|-----------|--------|------|-------|--------|----------|
| **SupportBot** | frontend-host | Chat widget | **3/5** | 45min | 🟠 P1 |
| **RiderTrackingPanel** | frontend-host | Map tracking | **2/5** | 45min | 🟡 P2 |
| **Dashboard Layouts** | admin/host | Panels | **2/5** | 1hr | 🟡 P2 |
| **Form Components** | all apps | Input/select | **3/5** | 1hr | 🟠 P1 |
| **Data Tables** | admin/host | Table display | **2/5** | 45min | 🟡 P2 |

**Tier 2 Subtotal**: ~4 hours implementation

---

#### Tier 3: APP-SPECIFIC (Limited Reuse)

| Component | Source | Scope | Reasoning |
|-----------|--------|-------|-----------|
| RbacBlocker | frontend-host | Access control | Only frontend-host needs RBAC enforcement |
| AdminPanel | frontend-admin | Admin UI shell | Specific to admin domain |
| SystemEngineRoom | frontend-admin | Monitoring | Admin-only telemetry |
| InventoryApp | frontend-admin | Business logic | Inventory-specific domain |
| CustomerApp | frontend-customer | App shell | Customer-specific wrapper |
| RiderApp | frontend-rider | App shell | Rider-specific wrapper |

**Decision**: Extract as examples/templates, not shared library components.

---

## 🔍 Detailed Tier 1 Analysis

### 1. LoadingSkeleton Variants (HIGHEST PRIORITY)

**Current**: frontend-host/src/components/LoadingSkeleton.tsx (107 lines)

**Exports**:
```tsx
export const ProductCardSkeleton: React.FC
export const ProductGridSkeleton: React.FC<{count?: 8}>
export const TableRowsSkeleton: React.FC<{rows?: 5; cols?: 4}>
export const GenericCardSkeleton: React.FC
```

**Reuse Opportunity**: Used/needed in:
- frontend-customer (product listing, order list)
- frontend-host (initial page loads)
- frontend-admin (data tables, dashboards)
- frontend-rider (order tracking)

**Abstraction Plan**:
```tsx
// Create parameterized component:
interface SkeletonProps {
  variant: 'card' | 'product' | 'table' | 'generic'
  count?: number
  rows?: number
  cols?: number
}

export const Skeleton: React.FC<SkeletonProps>
```

**CSS Dependencies**: `.skeleton-shimmer`, `.glass-card`, `.product-card`, `.products-grid`

---

### 2. Auth Portal — Unified from 3 Sources

**Current fragmentation**:

| Source | Component | Concern | Lines |
|--------|-----------|---------|-------|
| frontend-customer | AuthGate | Login + Register dual-mode | 100+ |
| frontend-admin | AdminLogin | Admin JWT exchange | 60+ |
| frontend-host | MfaLoginPortal | MFA + TOTP + SMS | 150+ |

**Common Pattern**:
1. Form submission (email/password)
2. Error display
3. Loading state
4. Conditional steps (login → MFA → success)

**Unified API**:
```tsx
interface AuthPortalProps {
  role: 'customer' | 'admin' | 'rider'
  mfaEnabled?: boolean
  mfaMethods?: ('sms' | 'totp')[]
  onAuthSuccess: (session: AuthSession) => void
  onAuthError?: (error: string) => void
}

export const AuthPortal: React.FC<AuthPortalProps>
```

**Saves**: 300+ lines per app × 3 apps = **900 lines of duplication**

---

### 3. Design Tokens — Consolidation Strategy

**Token Fragmentation Across Apps**:

```
COLORS:
  frontend-b2b:       25 tokens (cleanest)
  frontend-customer:  18 tokens (role colors)
  frontend-admin:     22 tokens (admin roles)
  frontend-host:      28 tokens (complex)
  frontend-rider:     24 tokens (custom)

SPACING:
  All use different scales (4px, 8px, 12px ... 64px)
  frontend-b2b: Standardized (0-12)
  Others: Ad-hoc (1rem, 0.5rem, 1.5rem, etc.)

TYPOGRAPHY:
  All use: Outfit, Inter, Fira Code (consistent!)
  BUT: Different size scales (xs-2xl vs sm-lg vs various px)

ANIMATIONS:
  frontend-b2b:  15 keyframes (comprehensive)
  frontend-host: 8 keyframes
  frontend-admin: 6 keyframes
  Others: Minimal, inconsistent
```

**Unified Token System** (extract best from each):

```css
/* COLORS — Role-based + Status */
:root {
  /* Backgrounds (from b2b) */
  --bg-root: #06090f
  --bg-surface: #0c1120
  --bg-elevated: #111827
  --bg-muted: #1a2236
  --bg-glass: rgba(15, 23, 42, 0.65)

  /* Role-specific accents (from customer) */
  --color-customer: #10b981     (green)
  --color-rider: #f59e0b        (amber)
  --color-admin: #ef4444        (red)
  --color-inventory: #3b82f6    (blue)
  --color-engine: #06b6d4       (cyan)

  /* Status colors (universal) */
  --success: #10b981
  --warning: #f59e0b
  --error: #ef4444
  --info: #06b6d4

  /* Text (standardized) */
  --text-primary: #f1f5f9
  --text-secondary: #94a3b8
  --text-muted: #64748b
}

/* SPACING — Standardized 4px scale */
:root {
  --space-0: 0px
  --space-1: 4px
  --space-2: 8px
  --space-3: 12px
  --space-4: 16px
  --space-5: 20px
  --space-6: 24px
  --space-7: 32px
  --space-8: 40px
}

/* TYPOGRAPHY — Consistent scale */
:root {
  --font-sans: "Outfit", "Inter", system-ui, -apple-system, sans-serif
  --font-mono: "Fira Code", monospace
  
  --text-xs: 0.6875rem   (11px)
  --text-sm: 0.8125rem   (13px)
  --text-base: 0.9375rem (15px)
  --text-lg: 1.125rem    (18px)
  --text-xl: 1.5rem      (24px)
  --text-2xl: 1.875rem   (30px)
}

/* ANIMATIONS — Consolidated (25+ keyframes) */
@keyframes fade-in { ... }
@keyframes slide-up { ... }
@keyframes pulse-glow { ... }
/* ... all unified */
```

**Output**: Single `tokens.css` (~300 lines) replaces 12,401 lines of fragmented CSS.

---

## 📋 Extraction Roadmap

### Phase 2A: Tier 1 Extraction (2 hours)

**1. Extract LoadingSkeleton** (15 min)
```bash
# Create shared-ui/src/components/Skeleton.tsx
# Copy + parameterize from frontend-host
# Add to shared exports
```

**2. Extract Tokens** (45 min)
```bash
# Create shared-ui/src/tokens.css
# Merge from all 5 apps, pick best per category
# Create TS token constants (optional)
```

**3. Extract AuthPortal** (1 hour)
```bash
# Create shared-ui/src/components/AuthPortal.tsx
# Combine AdminLogin + AuthGate + MfaLoginPortal logic
# Parameterize by role + features
# Test with all 3 configurations
```

---

### Phase 2B: Tier 2 Components (2 hours, optional)

**1. Extract SupportBot** (45 min)
**2. Extract Form Primitives** (45 min)
**3. Extract Dashboard Layouts** (30 min)

---

### Phase 2C: Token Migration (1 hour, refactor)

**1. Update all 5 frontends** to import unified tokens
**2. Remove local CSS** (reduce by ~9,000 lines)
**3. Verify no visual regressions** (smoke test each app)

---

## 💾 Shared Library Structure

```
shared-ui/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts (exports)
│   ├── tokens.css (unified)
│   ├── components/
│   │   ├── Skeleton.tsx (4 variants)
│   │   ├── AuthPortal.tsx (3 roles)
│   │   ├── SupportBot.tsx
│   │   ├── FormInput.tsx
│   │   ├── FormSelect.tsx
│   │   └── ...
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useFetch.ts
│   │   └── ...
│   └── utils/
│       ├── classNames.ts
│       └── ...
└── dist/
```

---

## 🎯 Success Metrics

| Metric | Before | After | Savings |
|--------|--------|-------|---------|
| CSS Lines | 12,401 | ~3,500 | 72% |
| Component Duplication | 15 instances | 1 (shared) | ~1,500 lines |
| Auth Code | 300+ lines/app × 3 | 1 component | 600+ lines |
| Token Maintenance | 5 sources | 1 source | 80% easier |
| Bundle Size | Fragmented | Unified | ~20% reduction |
| DX (dev experience) | High friction | Single source | ✓ |

---

## 🚀 Phase 3 Dependency

Phase 3 (unified `@swish/design-system` package) **depends on** Phase 2:
- Phase 2 output = shared-ui source
- Phase 3 input = shared-ui + frontend-b2b components → unified DS

---

## 📝 Implementation Checklist

- [ ] **Skeleton**: Extract ProductCard/Grid/Table/Generic variants
- [ ] **AuthPortal**: Unify customer login + admin JWT + MFA flows
- [ ] **Tokens**: Consolidate CSS custom properties from all 5 apps
- [ ] **Base CSS**: Extract common classes (.glass-panel, .glow-card, etc.)
- [ ] **Testing**: Smoke test each app after token migration
- [ ] **Documentation**: API docs + usage guide per component

---

## 📌 Notes

- **Lucide React**: All apps already use it (icons consistent)
- **Tailwind**: frontend-b2b uses v4.3.0, others use inline styles → standardize on Tailwind
- **MCP/Federation**: frontend-b2b uses Vite federation, others use standard builds → clarify build strategy
- **No breaking changes**: Extraction is additive, existing code continues to work until refactored

---

**Next**: User confirmation → Begin Phase 2A extraction (LoadingSkeleton + Tokens + AuthPortal)
