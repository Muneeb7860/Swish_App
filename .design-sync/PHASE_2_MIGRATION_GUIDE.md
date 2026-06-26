# Phase 2: Frontend Migration Guide

**Migrating 5 frontends from fragmented components → @swish/shared-ui**

---

## Migration Checklist

### ✅ frontend-customer (COMPLETED)
- [x] Add `@swish/shared-ui` to package.json dependencies
- [x] Import `AuthPortal` from `@swish/shared-ui`
- [x] Import `@swish/shared-ui/tokens` CSS
- [x] Replace `<AuthGate onAuth={...} />` with `<AuthPortal role="customer" mfaEnabled={true} onAuthSuccess={...} />`
- [x] Remove local `AuthGate.tsx` component (kept for now, can delete later)
- [ ] Remove redundant CSS tokens from `index.css`
- [ ] Test locally
- [ ] Commit

### ⏳ frontend-admin (NEXT)
- [ ] Add `@swish/shared-ui` to package.json
- [ ] Import `AuthPortal` from `@swish/shared-ui`
- [ ] Import `@swish/shared-ui/tokens` CSS
- [ ] Replace `<AdminLogin onLogin={...} />` with `<AuthPortal role="admin" onAuthSuccess={...} />`
- [ ] Remove redundant CSS tokens
- [ ] Test locally
- [ ] Commit

### ⏳ frontend-host (SKIP AUTHPORTAL, DO SKELETON)
- [ ] Add `@swish/shared-ui` to package.json
- [ ] Import `Skeleton`, `ProductGridSkeleton` from `@swish/shared-ui`
- [ ] Import `@swish/shared-ui/tokens` CSS
- [ ] Replace LoadingSkeleton imports with shared Skeleton
- [ ] Replace MfaLoginPortal with `AuthPortal role="rider" mfaMethods={['sms', 'totp']}`
- [ ] Remove redundant CSS tokens
- [ ] Test locally
- [ ] Commit

### ⏳ frontend-rider (SIMILAR TO CUSTOMER)
- [ ] Add `@swish/shared-ui` to package.json
- [ ] Import `AuthPortal` from `@swish/shared-ui`
- [ ] Import `@swish/shared-ui/tokens` CSS
- [ ] Replace local auth with `AuthPortal role="rider"`
- [ ] Remove redundant CSS tokens
- [ ] Test locally
- [ ] Commit

### ⏳ frontend-b2b (LIGHTEST MIGRATION)
- [ ] Add `@swish/shared-ui` to package.json
- [ ] Import `@swish/shared-ui/tokens` CSS
- [ ] Remove most of local token CSS (use shared instead)
- [ ] Keep local component CSS (glass-card, glow-card patterns still needed)
- [ ] Test locally
- [ ] Commit

---

## CSS Cleanup Strategy

### Before Migration
Each app has ~2,300-3,400 lines of CSS with heavy token duplication.

### After Migration
1. Keep app-specific styling (layout, components unique to that app)
2. Remove token definitions (use `@swish/shared-ui/tokens`)
3. Remove glass-card/glow-card/animation patterns (now in shared tokens)
4. Estimated savings per app:
   - frontend-customer: 1,600 lines saved
   - frontend-admin: 1,800 lines saved
   - frontend-host: 2,400 lines saved
   - frontend-rider: 1,700 lines saved
   - frontend-b2b: 900 lines saved
   - **Total: 8,400 lines saved** (vs 8,900 in raw fragmentation)

### How to Clean CSS
1. Remove all `--bg-*`, `--text-*`, `--color-*` token definitions
2. Remove all `--space-*`, `--radius-*`, `--shadow-*` tokens
3. Remove animation keyframes (now in shared tokens.css)
4. Remove `glass-card`, `glow-card`, `skeleton-*` classes
5. Keep only app-specific layouts and component overrides

---

## Package.json Changes

**Before:**
```json
"dependencies": {
  "react": "^19.2.6",
  "react-dom": "^19.2.6"
}
```

**After:**
```json
"dependencies": {
  "@swish/shared-ui": "file:../shared-ui",
  "react": "^19.2.6",
  "react-dom": "^19.2.6"
}
```

**Why `file:` path?**
- Monorepo local development
- Works with `npm install` / `yarn install` / `pnpm install`
- Symlinks to shared-ui for hot reloading during development
- Later: publish to npm as `@swish/shared-ui` package

---

## Import Changes by App

### frontend-customer
```tsx
// Before
import AuthGate from "./components/AuthGate"

// After
import { AuthPortal } from "@swish/shared-ui"
import "@swish/shared-ui/tokens"

// Usage
<AuthPortal role="customer" mfaEnabled={true} onAuthSuccess={setSession} />
```

### frontend-admin
```tsx
// Before
import AdminLogin from "./components/AdminLogin"

// After
import { AuthPortal } from "@swish/shared-ui"
import "@swish/shared-ui/tokens"

// Usage
<AuthPortal role="admin" onAuthSuccess={setSession} />
```

### frontend-host
```tsx
// Before
import { ProductCardSkeleton, ProductGridSkeleton, MfaLoginPortal } from "./components"

// After
import { Skeleton, AuthPortal } from "@swish/shared-ui"
import "@swish/shared-ui/tokens"

// Usage
<Skeleton variant="product-grid" count={12} />
<AuthPortal role="rider" mfaMethods={['sms', 'totp']} onAuthSuccess={setSession} />
```

### frontend-rider & frontend-b2b
Similar to customer/admin patterns

---

## Testing After Migration

### Per-App Smoke Test
1. `npm install` (pulls shared-ui)
2. `npm run dev` (start dev server)
3. Navigate auth flow → should work with shared AuthPortal
4. Navigate to loading states → should see Skeleton components
5. Check browser DevTools → should see shared CSS tokens applied
6. Visual regression → no visual changes expected

### Cross-App Consistency
After all migrations:
- All apps use same color tokens
- All apps use same spacing scale
- All apps use same animations
- Button/card/badge styles consistent across apps

---

## Rollback Plan

If migration breaks something:
1. Revert local imports back to app-specific components
2. Comment out `@swish/shared-ui/tokens` import
3. Re-enable local token CSS
4. Debug what broke
5. Fix in shared-ui package
6. Retry migration

---

## Git Commit Pattern

Each app gets its own commit:

```bash
# frontend-customer
git commit -m "refactor(frontend-customer): migrate to @swish/shared-ui

Replace local AuthGate with shared AuthPortal component.
Import unified design tokens from @swish/shared-ui.
Reduces local CSS by 1,600 lines (remove token duplication).

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>"

# frontend-admin (similar)
# frontend-host (similar)
# etc.
```

---

## Timeline

| App | Status | Effort | Savings |
|-----|--------|--------|---------|
| frontend-customer | ✅ Done | 15 min | 1,600 lines |
| frontend-admin | ⏳ Next | 15 min | 1,800 lines |
| frontend-host | ⏳ Queued | 20 min | 2,400 lines |
| frontend-rider | ⏳ Queued | 15 min | 1,700 lines |
| frontend-b2b | ⏳ Queued | 10 min | 900 lines |
| **TOTAL** | — | **75 min** | **8,400 lines** |

---

## Success Criteria

✅ All 5 frontends import from `@swish/shared-ui`  
✅ All 5 frontends import `@swish/shared-ui/tokens`  
✅ No visual regressions in any app  
✅ All apps pass smoke tests  
✅ 8,400+ lines of CSS saved  
✅ All commits follow pattern above

---

## Notes

- **No breaking changes**: Apps remain fully functional during migration
- **Gradual rollout**: Can migrate one app at a time
- **Safe to iterate**: Shared-ui fixes benefit all apps after migration
- **Future-proof**: Ready to publish @swish/shared-ui to npm registry
