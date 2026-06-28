# Swish B2B Design System — Conventions & Usage Guide

## For Claude Design Agent

This document guides the design agent in using this component library to build applications. The system is **dark-mode-first** with a premium, glassmorphic aesthetic.

### Setup & Wrapping

All components require the design system's CSS to render correctly. Include at the root:

```tsx
import '@swish/design-system/styles.css'

export function App() {
  return (
    <div className="b2b-mfe-container">
      {/* Your app using Swish components */}
    </div>
  )
}
```

The container sets up:
- Font stack (Outfit/Inter for sans, Fira Code for mono)
- Dark color scheme
- Baseline typography and spacing
- Custom scrollbar styling

### Styling Idiom: CSS Custom Properties + Tailwind

**DO NOT** write custom CSS classes. Use **CSS variables** for tokens and **Tailwind utilities** for layout:

```tsx
// ✅ CORRECT
<div style={{
  color: 'var(--text-primary)',
  backgroundColor: 'var(--bg-surface)',
  padding: 'var(--space-4)',
  borderRadius: 'var(--radius-md)'
}} className="flex gap-4 items-center">
  {/* content */}
</div>

// ❌ WRONG — Don't write custom CSS
<div className="my-custom-class">
```

### Token Families (Use These Names)

**Colors:**
- Backgrounds: `--bg-root`, `--bg-surface`, `--bg-elevated`, `--bg-muted`, `--bg-glass`, `--bg-glass-strong`
- Text: `--text-primary`, `--text-secondary`, `--text-muted`, `--text-disabled`
- Status: `--success`, `--warning`, `--error`, `--info`
- Accent: `--accent`, `--accent-hover`, `--accent-muted`, `--purple`

**Spacing (4px increments):**
```
--space-0 (0px)
--space-1 (4px)  → padding-1, margin-1
--space-2 (8px)
--space-3 (12px)
--space-4 (16px) ← most common
--space-5 (20px)
--space-6 (24px)
--space-7 (32px)
--space-8 (40px)
```

**Radius:**
- `--radius-sm` (6px) — small badges, inputs
- `--radius-md` (10px) — buttons, cards
- `--radius-lg` (14px) — large panels
- `--radius-xl` (20px) — premium cards
- `--radius-full` (9999px) — pills

**Shadows:**
- `--shadow-sm` — subtle
- `--shadow-md` — default
- `--shadow-lg` — hover states
- `--shadow-glow` — accent glow
- `--shadow-glow-success/error` — status glows

**Timing:**
- `--duration-fast` (150ms) — micro-interactions
- `--duration-normal` (250ms) — default
- `--duration-slow` (400ms) — hovers
- `--ease-smooth` — default easing

### Component Classes (Use for Styling)

These are pre-styled components — use className:

```tsx
// Status indicators
<div className="status-badge status-badge--connected">
  <span className="status-dot status-dot--connected" />
  Connected
</div>

// Cards
<div className="glass-panel">Glass card content</div>
<div className="glow-card">Card with glow on hover</div>

// Order workflow
<div className="order-badge order-badge--processing">Processing</div>

// Notifications
<div className="notification-card priority-high">Alert</div>

// Logs viewer
<pre className="logs-output">console output here</pre>
```

### Component API — What Props to Pass

Each component is fully typed; pass these canonical props:

**CheckoutPanel:**
- `orderId`, `orderStatus`, `userId`, `lastTraceId`
- `isSimulating`, `simulationMode`, `onSimulationModeChange`
- `onCheckout`, `onResetOrder`
- `copiedIndex`, `onCopy`

**StatusIndicator:**
- `status` ("CONNECTED" | "CONNECTING" | "RECONNECTING" | "DISCONNECTED")
- `reconnectAttempts` (number)

**CreditCardMockup:** (no props, pure display)

**OrderTimeline:** (state-driven, data passed as children or props)

**NotificationInbox:** (array of notification objects)

**ConnectionConfig:** (form with input/select fields)

**RetailerOnboarding, SensorProvisioning, SandboxLogs:** (dashboard panels)

### Layout Composition

Combine components using **Tailwind layout utilities** (not custom CSS):

```tsx
// ✅ CORRECT — Flexbox via Tailwind
<div className="flex flex-col gap-6 p-6">
  <CheckoutPanel {...props} />
  <StatusIndicator status="CONNECTED" reconnectAttempts={0} />
</div>

// ❌ WRONG — Custom CSS
<div style={{
  display: 'flex',
  flexDirection: 'column',
  gap: '24px'
}}>
```

### Color Usage by Role

When designing multi-role apps, use role-specific accent colors:

```tsx
// Wholesale/B2B (primary)
const accentColor = 'var(--accent)' // Indigo

// Secondary accent
const secondaryColor = 'var(--purple)' // Purple

// Status roles
const successColor = 'var(--success)' // Green
const warningColor = 'var(--warning)' // Amber
const errorColor = 'var(--error)' // Red
```

### Animation Usage

Use Tailwind animation classes or CSS variables for timing:

```tsx
// ✅ CORRECT — Predefined animations
<div style={{
  animation: 'fade-in 250ms var(--ease-smooth)'
}}>
  Fades in smoothly
</div>

// Preset animations available:
// fade-in, slide-in-right, pulse-glow, pulse-ring, hologram-shimmer
```

### Dark Mode Only

This system is **dark mode only**. Do not attempt light mode theming. All colors are optimized for dark backgrounds with high contrast for accessibility.

### Typography Hierarchy

```tsx
// Headings (use Outfit)
<h1 style={{fontSize: 'var(--text-2xl)', fontWeight: 800}}>Large Title</h1>
<h2 style={{fontSize: 'var(--text-xl)', fontWeight: 700}}>Section</h2>
<h3 style={{fontSize: 'var(--text-lg)', fontWeight: 700}}>Subsection</h3>

// Body text
<p style={{color: 'var(--text-secondary)'}}>Regular paragraph</p>
<span style={{color: 'var(--text-muted)', fontSize: 'var(--text-sm)'}}>
  Secondary info
</span>

// Code/monospace
<code style={{fontFamily: 'var(--font-mono)', fontSize: 'var(--text-xs)'}}>
  API_KEY=xxx
</code>
```

### Accessibility

- All interactive elements have `:focus-visible` states (indigo outline)
- Status badges use color + icon (not color alone)
- Text contrast meets WCAG AA standards
- Keyboard navigation supported on all components
- ARIA labels where appropriate

### Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Requires CSS custom properties and modern CSS Grid/Flexbox
