# Design System Sync: Complete Phase 1-3 Plan

## Phase 1: frontend-b2b Single App Sync ✅ IN PROGRESS

### Goal
Sync frontend-b2b's 9 premium components to Claude Design as the **Swish B2B Design System**.

### Components (9)
| Category | Components |
|----------|------------|
| **Payment & Commerce** | CheckoutPanel, CreditCardMockup |
| **Status & Timeline** | OrderTimeline, StatusIndicator |
| **Notifications** | NotificationInbox |
| **Forms** | ConnectionConfig |
| **Dashboard** | RetailerOnboarding, SensorProvisioning, SandboxLogs |

### Design Tokens & System
- **Colors**: Dark mode with indigo accent + purple secondary
- **Spacing**: 0-12 scale (0px → 64px)
- **Typography**: Outfit/Inter (sans), Fira Code (mono)
- **Radii**: 6px → 9999px
- **Shadows & Blur**: Glass morphism + glow effects
- **Animations**: 15+ keyframes (fade, slide, pulse, shimmer, etc.)

### Deliverables
- [ ] Component previews (9 cards)
- [ ] API documentation (.d.ts + .prompt.md)
- [ ] Color/token reference guide
- [ ] Component README with conventions

---

## Phase 2: Shared Component Consolidation 🔍 DISCOVERY

### Components Identified Across Frontends

#### frontend-customer (2)
- `CustomerApp.tsx` — main app wrapper
- `AuthGate.tsx` — login/auth conditional

#### frontend-host (5)
- `LoadingSkeleton.tsx` — **⭐ REUSABLE**: ProductCardSkeleton, ProductGridSkeleton
- `RbacBlocker.tsx` — access denied fallback
- `MfaLoginPortal.tsx` — multi-factor auth portal
- `RiderTrackingPanel.tsx` — map + tracking UI
- `SupportBot.tsx` — chat widget

#### frontend-admin (5)
- `AdminLogin.tsx` — admin auth form
- `AdminPanel.tsx` — admin dashboard wrapper
- `SystemEngineRoom.tsx` — monitoring/metrics UI
- `BusinessApp.tsx` — business analytics
- `InventoryApp.tsx` — inventory management

#### frontend-rider (1)
- `RiderApp.tsx` — rider app entry point

### Consolidation Candidates
**Tier 1 (High Reuse):**
- LoadingSkeleton (used across apps)
- Auth portals (MfaLoginPortal, AdminLogin, AuthGate — could unify)
- Common layout patterns

**Tier 2 (Medium Reuse):**
- Tracking panel (map + live data)
- Support bot widget
- RBAC UI component

**Tier 3 (App-Specific):**
- Dashboard layouts
- Business/inventory apps

### Goals
1. Extract reusable components into `shared-ui/` library
2. Create component abstractions (e.g., `<AuthPortal>` with variants)
3. Consolidate design tokens across all apps (pick best patterns from each)

---

## Phase 3: Unified Design System Package 🏗️ ARCHITECTURE

### New Package Structure
```
design-system/
├── package.json
├── tsconfig.json
├── vite.config.ts
├── src/
│   ├── index.ts (main export)
│   ├── index.css (unified tokens + styles)
│   ├── components/
│   │   ├── Forms/
│   │   │   ├── AuthPortal.tsx (variant: customer|admin|mfa)
│   │   │   ├── ConnectionConfig.tsx
│   │   │   └── FormInput.tsx
│   │   ├── Commerce/
│   │   │   ├── CheckoutPanel.tsx
│   │   │   ├── CreditCardMockup.tsx
│   │   │   └── ProductCard.tsx
│   │   ├── Status/
│   │   │   ├── StatusIndicator.tsx
│   │   │   ├── OrderTimeline.tsx
│   │   │   └── LoadingSkeleton.tsx
│   │   ├── Notifications/
│   │   │   └── NotificationInbox.tsx
│   │   ├── Dashboard/
│   │   │   ├── RetailerOnboarding.tsx
│   │   │   ├── SensorProvisioning.tsx
│   │   │   ├── SandboxLogs.tsx
│   │   │   ├── RiderTrackingPanel.tsx
│   │   │   └── SystemEngineRoom.tsx
│   │   └── Navigation/
│   │       └── (from app shells)
│   └── tokens/
│       ├── colors.ts
│       ├── spacing.ts
│       ├── typography.ts
│       └── animations.ts
├── .storybook/ (optional)
└── dist/
```

### Unified Token Strategy
1. **Base Palette**: Dark mode (consolidate b2b + customer + admin)
2. **Role Colors**: customer (green), rider (amber), admin (red), inventory (blue), engine (cyan)
3. **Spacing Scale**: 0-12 (harmonize with tailwind)
4. **Typography**: Outfit/Inter/Fira Code across all apps
5. **Animation Library**: Central keyframes, reusable utilities

### Component API Principles
- **Props-first**: No CSS classes, full control via props/variants
- **Composition**: Small, composable primitives
- **Theme-aware**: Accept role/context colors
- **Accessible**: ARIA labels, keyboard nav, focus states

### Export Strategy (Vite Federation or ESM)
- Publish to `@swish/design-system` (npm or monorepo internal)
- Export per-app color themes as preset hooks
- Storybook preview for documentation

---

## Timeline & Dependencies

| Phase | Duration | Blockers | Output |
|-------|----------|----------|--------|
| **Phase 1** | 2-4 hours | None (local) | 9 components → Claude Design project |
| **Phase 2** | 1-2 hours | Phase 1 completion | Shared component analysis + extraction plan |
| **Phase 3** | 4-6 hours | Phase 2 completion | `design-system/` package + Storybook |

---

## Success Criteria

✅ **Phase 1**: Frontend-b2b components synced, visible in Claude Design, can be composed  
✅ **Phase 2**: 3-5 shared components extracted, unification plan documented  
✅ **Phase 3**: New package exports 15+ components, Storybook renders all  

---

## Notes & Gotchas

- **Tailwind fragmentation**: frontend-b2b uses Tailwind v4, others use inline styles — need unified approach
- **Color token drift**: Each app defines its own palette; consolidation required
- **Component scope**: Some are full "apps" (AdminApp) vs reusable components — need clear boundaries
- **Federation exports**: frontend-b2b uses Vite federation; Phase 3 package should use standard ESM
