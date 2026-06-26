# Swish B2B Design System

A premium, dark-mode-first React component library for wholesale and B2B applications. Built with React 19, Tailwind CSS, and custom design tokens.

## Components

### Payment & Commerce
- **CheckoutPanel** — Wholesale order checkout interface with payment controls, timeline, and status tracking
- **CreditCardMockup** — Premium 3D credit card visualization with hologram and chip effects

### Status & Timeline
- **OrderTimeline** — Multi-stage order progress indicator with active/completed/failed states
- **StatusIndicator** — WebSocket connection status pill with animated indicators and reconnect counter

### Notifications
- **NotificationInbox** — Animated notification cards with priority levels, timestamps, and trace IDs

### Forms & Configuration
- **ConnectionConfig** — Configuration form for connection parameters and settings

### Dashboard
- **RetailerOnboarding** — Multi-step retailer onboarding and setup flow
- **SensorProvisioning** — Sensor device provisioning and management interface
- **SandboxLogs** — Code/log output viewer with scrolling and line numbering

## Design System

### Color Palette (Dark Mode First)
- **Backgrounds**: Root, Surface, Elevated, Muted, Glass
- **Text**: Primary, Secondary, Muted, Disabled, Inverse
- **Status**: Success (green), Warning (amber), Error (red), Info (cyan)
- **Accent**: Indigo (`#6366f1`) + Purple (`#a855f7`)

### Typography
- **Sans**: "Outfit", "Inter"
- **Heading**: "Outfit" (bold, tight leading)
- **Mono**: "Fira Code"
- **Scale**: xs (11px) → 2xl (30px)

### Spacing Scale
0px, 4px, 8px, 12px, 16px, 20px, 24px, 32px, 40px, 48px, 64px

### Component Patterns
- **Glass Morphism**: Frosted glass effect with backdrop blur
- **Glow Cards**: Radial gradient glow on hover with smooth transitions
- **Premium Action**: Gradient buttons with shimmer effect
- **Status Badges**: Role-colored pills with animated dots

### Animations
- **Timing**: 150ms (fast), 250ms (normal), 400ms (slow), 600ms (glacial)
- **Easing**: ease-out, ease-spring, ease-smooth
- **Effects**: fade-in, slide, scale, pulse, hologram-shimmer, text-shine

## Usage

All components use **Tailwind CSS** for utilities and **CSS custom properties** (CSS variables) for design tokens. Components are fully typed with TypeScript.

### Basic Setup
```tsx
import { CheckoutPanel } from '@swish/design-system'

export function MyApp() {
  return (
    <CheckoutPanel
      orderId="ORD-123"
      orderStatus="payment"
      onCheckout={() => console.log('checkout')}
      // ... other props
    />
  )
}
```

### Token Usage
```css
/* Tokens are CSS variables, available globally */
.my-component {
  color: var(--text-primary);
  background: var(--bg-surface);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}
```

## Browser Support
- Chrome/Edge 90+
- Firefox 88+
- Safari 14+

## License
Proprietary — Swish Platform
