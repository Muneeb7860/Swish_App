# Design System Sync - Notes & Findings

## Phase 1: frontend-b2b Sync Prep (2026-06-26)

### Overview
- **Package**: frontend-b2b
- **Shape**: Package (non-Storybook)
- **Components**: 9 exportable React components
- **Build**: Vite + React 19 + Tailwind + TypeScript
- **Status**: Ready for preview generation and sync

### Components Identified

| Component | Category | Status | Notes |
|-----------|----------|--------|-------|
| CheckoutPanel | Payment & Commerce | Ready | Wholesale order checkout with timeline, card, controls |
| CreditCardMockup | Payment & Commerce | Ready | Premium 3D credit card visualization |
| OrderTimeline | Status & Timeline | Ready | Multi-stage order progress visualization |
| StatusIndicator | Status & Timeline | Ready | WebSocket connection status pill with animations |
| NotificationInbox | Notifications | Ready | Animated notification cards with priority levels |
| ConnectionConfig | Forms & Input | Ready | Configuration form with input fields |
| RetailerOnboarding | Dashboard | Ready | Multi-step retailer onboarding interface |
| SensorProvisioning | Dashboard | Ready | Sensor device provisioning workflow |
| SandboxLogs | Dashboard | Ready | Code/log output viewer with scrolling |

### Design System Details

**Color Palette** (Dark Mode First):
- Background: `--bg-root`, `--bg-surface`, `--bg-elevated`, `--bg-muted`, `--bg-glass`
- Text: `--text-primary`, `--text-secondary`, `--text-muted`, `--text-disabled`
- Status: Success (`#10b981`), Warning (`#f59e0b`), Error (`#ef4444`), Info (`#06b6d4`)
- Accent: Indigo (`#6366f1`) + Purple (`#a855f7`)

**Typography**:
- Sans: "Outfit", "Inter"
- Heading: "Outfit"
- Mono: "Fira Code"
- Scale: xs (11px) → 2xl (30px)

**Component Patterns**:
- Glass morphism cards (`.glass-panel`, `.glow-card`, `.upgrade-glow-card`)
- Buttons: Primary (gradient), Ghost, Danger, Premium action
- Badges: Status, Order, Notification
- Animations: Fade, slide, scale, pulse, hologram shimmer

### Build & Dependencies
- **Vite Config**: esbuild with federation, cssCodeSplit: false
- **Tailwind**: v4.3.0 with custom token layer
- **Exports**: Via Vite federation (`remoteEntry.js`)
- **CSS**: Single layer cascade (reset → tokens → base → components → utilities)

### Phase 2 Findings: Shared Component Opportunities

**Reusable Components Across Frontends:**
- **LoadingSkeleton** (frontend-host): ProductCardSkeleton, ProductGridSkeleton — used in multiple apps
- **Auth Portals** (frontend-host, frontend-customer, frontend-admin): MfaLoginPortal, AuthGate, AdminLogin — could unify to single variant component
- **Layout & Grid**: Common card/grid patterns with glass morphism
- **Typography & Spacing**: Consistent Outfit/Inter/Fira Code usage

**App-Specific (Limited Reuse):**
- RiderTrackingPanel, SystemEngineRoom, InventoryApp — domain-specific logic
- BusinessApp, AdminPanel — dashboard wrappers

**Consolidation Strategy:**
1. Extract LoadingSkeleton to shared library (immediate win)
2. Create unified AuthPortal component with role variants
3. Establish shared token system (colors, spacing, typography)

### Phase 3 Goals: Unified Design System Package
- New package: `@swish/design-system`
- 15+ components across 6 categories
- Single source of truth for tokens & styles
- Storybook or equivalent documentation
- Vite ESM exports (replaces federation pattern)

### Next Steps
1. **Phase 1**: Build & bundle frontend-b2b components, prepare previews
2. **Phase 2**: Extract shared components from other frontends
3. **Phase 3**: Create unified design-system package with Storybook
