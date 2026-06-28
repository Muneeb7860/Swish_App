# @swish/design-system

**Unified design system for the Swish platform** — consolidated components, design tokens, and styling infrastructure for all frontend applications.

## 🎯 Purpose

This package provides a single source of truth for:
- **Design Tokens**: Colors, spacing, typography, shadows, animations, and motion curves
- **Components**: Reusable React components (AuthPortal, Skeleton, etc.)
- **Styling**: Glass morphism effects, utility classes, and animations

## 📦 Installation

```bash
npm install @swish/design-system
```

## 🚀 Quick Start

### Import Tokens

Tokens provide CSS custom properties and base styles:

```tsx
import "@swish/design-system/tokens";
```

### Use Components

```tsx
import { AuthPortal, Skeleton } from "@swish/design-system";

// Authentication
<AuthPortal
  role="customer"
  mfaEnabled={true}
  onAuthSuccess={(session) => handleAuth(session)}
/>

// Loading state
<Skeleton variant="product-grid" count={12} />
```

## 🎨 Design Tokens

All tokens are available as CSS custom properties. Access them in your CSS or inline styles:

### Colors

```css
/* Backgrounds */
--bg-root: #06090f;
--bg-surface: #0c1120;
--bg-elevated: #111827;

/* Text */
--text-primary: #f1f5f9;
--text-secondary: #94a3b8;
--text-muted: #64748b;

/* Status */
--success: #10b981;
--warning: #f59e0b;
--error: #ef4444;
--info: #06b6d4;

/* Role-specific */
--color-customer: #10b981;
--color-rider: #f59e0b;
--color-business: #8b5cf6;
--color-admin: #ef4444;
```

### Spacing Scale

4px baseline grid:

```css
--space-0: 0px;
--space-1: 4px;
--space-2: 8px;
--space-3: 12px;
--space-4: 16px;
--space-5: 20px;
--space-6: 24px;
--space-7: 32px;
--space-8: 40px;
--space-10: 48px;
--space-12: 64px;
```

### Typography

Consistent type scale across all apps:

```css
--text-xs: 0.6875rem;   /* 11px */
--text-sm: 0.8125rem;   /* 13px */
--text-base: 0.9375rem; /* 15px */
--text-lg: 1.125rem;    /* 18px */
--text-xl: 1.5rem;      /* 24px */
--text-2xl: 1.875rem;   /* 30px */

--leading-tight: 1.2;
--leading-normal: 1.5;
--leading-relaxed: 1.65;
```

### Shadows

```css
--shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.3), ...;
--shadow-md: 0 4px 12px rgba(0, 0, 0, 0.35), ...;
--shadow-lg: 0 12px 32px rgba(0, 0, 0, 0.45), ...;
--shadow-xl: 0 20px 50px rgba(0, 0, 0, 0.55), ...;
--shadow-glow: 0 0 20px rgba(99, 102, 241, 0.15), ...;
```

### Motion

```css
--ease-out: cubic-bezier(0.16, 1, 0.3, 1);
--ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);
--ease-smooth: cubic-bezier(0.4, 0, 0.2, 1);

--duration-fast: 150ms;
--duration-normal: 250ms;
--duration-slow: 400ms;
--duration-glacial: 600ms;
```

## 🧩 Components

### AuthPortal

Unified authentication component supporting customer, admin, and rider roles with MFA.

```tsx
import { AuthPortal, type AuthSession } from "@swish/design-system";

<AuthPortal
  role="customer"
  mfaEnabled={true}
  mfaMethods={["sms", "totp"]}
  onAuthSuccess={(session: AuthSession) => {
    console.log("Authenticated:", session.token);
  }}
  onAuthError={(error) => console.error("Auth failed:", error)}
/>
```

**Props:**
- `role`: `"customer" | "admin" | "rider"` — User role
- `mfaEnabled?`: `boolean` — Enable MFA verification (default: false)
- `mfaMethods?`: `("sms" | "totp")[]` — Available MFA methods
- `apiUrl?`: `string` — Auth API endpoint
- `onAuthSuccess`: `(session: AuthSession) => void` — Success callback
- `onAuthError?`: `(error: Error) => void` — Error callback
- `defaultEmail?`: `string` — Pre-fill email field
- `formLabel?`: `string` — Form title
- `submitLabel?`: `string` — Submit button text

### Skeleton

Loading skeleton component with 4 variants: product cards, product grids, table rows, and generic cards.

```tsx
import { Skeleton } from "@swish/design-system";

// Product grid
<Skeleton variant="product-grid" count={12} />

// Table rows
<Skeleton variant="table-rows" rows={10} cols={5} />

// Generic card
<Skeleton variant="generic-card" />
```

**Props:**
- `variant`: `"product-card" | "product-grid" | "table-rows" | "generic-card"` — Skeleton style
- `count?`: `number` — Items to display (default: 8)
- `rows?`: `number` — Rows for table variant (default: 5)
- `cols?`: `number` — Columns for table variant (default: 4)

**Backward compatibility exports:**
- `ProductCardSkeleton`
- `ProductGridSkeleton`
- `TableRowsSkeleton`
- `GenericCardSkeleton`

## 🎬 Utility Classes

### Glass Morphism

```html
<div class="glass-card">Content</div>
<div class="glow-card">Glowing content</div>
```

### Animations

```css
/* Built-in animations */
@keyframes fade-in { /* ... */ }
@keyframes slide-up { /* ... */ }
@keyframes slide-down { /* ... */ }
@keyframes slide-in-right { /* ... */ }
@keyframes scale-in { /* ... */ }
@keyframes pulse-glow { /* ... */ }
@keyframes pulse-ring { /* ... */ }
@keyframes float { /* ... */ }
@keyframes spin { /* ... */ }
```

## 🔧 Usage in React Apps

### Step 1: Install

```bash
npm install @swish/design-system
```

### Step 2: Import Tokens (Usually in main.tsx or App.tsx)

```tsx
import "@swish/design-system/tokens";
```

### Step 3: Use Components

```tsx
import { AuthPortal, Skeleton } from "@swish/design-system";

export function App() {
  return <AuthPortal role="customer" onAuthSuccess={handleAuth} />;
}
```

## 📊 Consolidation Metrics

This design system consolidates from 5 frontend applications:

| Frontend | Before | After | Saved |
|----------|--------|-------|-------|
| frontend-customer | 1,600 lines | Tokens | 1,600 |
| frontend-admin | 1,800 lines | Tokens | 1,800 |
| frontend-host | 2,400 lines | Tokens | 2,400 |
| frontend-rider | 1,700 lines | Tokens | 1,700 |
| frontend-b2b | 900 lines | Tokens | 900 |
| **Total** | **8,400** | **~2,400** | **72% reduction** |

## 🏗️ Architecture

```
@swish/design-system/
├── src/
│   ├── tokens.css        # Design tokens (colors, spacing, typography)
│   ├── components/
│   │   ├── AuthPortal.tsx
│   │   ├── Skeleton.tsx
│   │   └── index.ts
│   └── index.ts
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## 🔄 Monorepo Integration

Installed via file path in consuming apps:

```json
{
  "dependencies": {
    "@swish/design-system": "file:../design-system"
  }
}
```

## 📝 TypeScript Support

All components have full TypeScript types:

```tsx
import { AuthPortal, type AuthSession, type AuthPortalProps } from "@swish/design-system";
```

## 🤝 Contributing

When adding new components or tokens:

1. Add to appropriate file in `src/`
2. Export from `src/components/index.ts` or `src/index.ts`
3. Update README with usage examples
4. Test across all consuming apps

## 📄 License

MIT

---

**Part of the Swish Platform Design System Consolidation (Phase 2-3)**
