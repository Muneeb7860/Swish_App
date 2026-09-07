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

---

## Sync run 2026-09-07 (first completed converter run)

### Repo shape — the thing that decides everything else
- **`frontend-b2b` is an application, not a library.** `"private": true`, no
  `main`/`module`/`types`/`exports`, and `npm run build` is a Vite **module-federation**
  app build whose `remoteEntry.js` exposes only `B2bDashboard`. There is no `.d.ts`
  emit anywhere. The converter reads its component list from a package's shipped
  `.d.ts` exports, so pointed at this package as-is it finds nothing.
- **Fix: a barrel entry.** `frontend-b2b/.ds-entry.ts` (gitignored, regenerate by listing
  `src/components/*.tsx`) re-exports the 9 components as named exports, and the build
  runs with `--entry ./frontend-b2b/.ds-entry.ts`. Without `--entry` the converter
  resolves `PKG_DIR` to `node_modules/frontend-b2b`, which npm never self-installs, and
  dies with `ENOENT ... node_modules/frontend-b2b/package.json`.
- Types still resolve well despite no declaration build: every `<Name>Props` interface is
  declared in its own component file and ts-morph reads them through the checker.
  `[DTS] 9/9 components`, all `.d.ts` parse clean.

### The global fix that mattered most
- **Every component must render inside `.b2b-mfe-container`.** That class (index.css,
  `@layer base`) sets `color-scheme: dark`, `background: var(--bg-root)`, the default text
  colour and the `h1`-`h4` font-family rules. Outside it, translucent `.glass-panel`
  surfaces show the page through (NotificationInbox rendered grey-on-white) and body text
  falls back to browser black.
- Wired as `cfg.provider` via `frontend-b2b/.ds-provider.tsx` (exports `B2bSurface`,
  gitignored, added through `extraEntries`). This is a **CSS class wrapper, not a React
  context provider** — the same config lever, a different reason.

### CSS entry rots without help
- `cfg.cssEntry` takes one literal path bounded to the package dir — no globs. The real
  stylesheet is Vite's content-hashed `dist/assets/style-<hash>.css`, so `buildCmd` copies
  it to the stable `frontend-b2b/.ds-compiled.css` (gitignored) and cssEntry points there.
  **Re-running `vite build` without that copy leaves a stale stylesheet.**
- Do not point cssEntry at `src/index.css`: it contains an unexpanded `@import "tailwindcss"`.

### Known render warns (expected — not new)
- `[FONT_REMOTE] "Outfit", "Inter"` — index.css `@import`s Google Fonts; the families load
  at runtime and correctly need no shipped `@font-face`. Fira Code renders too (verified on
  the SandboxLogs sheet), so this warn is informational every run.
- Small components (StatusIndicator, OrderTimeline) leave large vertical whitespace in
  their cells. They genuinely are a pill and a 30px rail; not a thin-render failure.

### Components with no renderable empty state
Two early-return `null` and therefore have **no story for that state** — an export for it
is a blank cell, not a variant:
- `ConnectionConfig` — `if (!isOpen) return null`
- `SensorProvisioning` — `if (currentRetailer?.status !== "ACTIVE") return null`

### conventions.md drift found this run
- 50/50 tokens, 12/12 component classes, 5/5 animations and 9/9 components all still verify
  against the built artifacts.
- **`import '@swish/design-system/styles.css'` was wrong and has been corrected.**
  `@swish/design-system` is an empty scaffold package (0 component files); the synced
  package is `frontend-b2b`, and the design agent receives the bound `styles.css`, not an
  npm specifier. It also contradicted the generated README's own body ("`styles.css` — the
  single stylesheet entry ... Link this one file"). Replaced with a `<link rel="stylesheet"
  href="styles.css">` snippet; the `.b2b-mfe-container` wrap that followed it was correct
  and is unchanged. Everything else in the file verified 100% against this build.

### Re-sync risks — what can silently go stale
- **`.ds-entry.ts` is a hand-maintained list.** A component added to `src/components/` is
  invisible to the sync until it is added there AND to `cfg.componentSrcMap`. Nothing warns.
- **`.ds-compiled.css` is a copy.** If `buildCmd` is run partially (vite build without the
  cp), the bundle ships the previous build's CSS and nothing fails.
- **`.ds-entry.ts` / `.ds-provider.tsx` / `.ds-compiled.css` are gitignored**, so a fresh
  clone has none of them. Recreate all three before the first build (contents documented above).
- Preview props are inlined fixtures modelled on `useRetailerApi`'s sandbox mock and
  `B2bDashboard`'s call sites. If those shapes change, the previews still render but stop
  being representative.
- The whole run was verified against **chromium-1243**; an older cached build in
  `~/Library/Caches/ms-playwright` (1228 was present) pins a different playwright version.
- **Never synced to a project.** No `projectId` is recorded and no `_ds_sync.json` has ever
  been uploaded, so the next run has no anchor and re-verifies all 9 from scratch.
