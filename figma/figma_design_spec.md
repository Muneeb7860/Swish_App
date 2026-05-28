# Figma Design System Specification: Swiss Quick Commerce Cockpit

This document acts as the official design system blueprint and component library guide for the **Swiss Quick Commerce Operations & Resilience Simulator (`swiss_App`)**. Designers can use this spec to replicate or extend the retro-neon cyberpunk cockpit aesthetic in Figma.

---

## 1. Core Visual Theme (Aesthetic Concept)
* **Design Philosophy**: **"Cyber-Industrial Cockpit"**. Blends clean high-information dashboard grids (similar to aviation control panels) with a high-contrast dark neon color palette.
* **Mood & Vibe**: High-tech, data-dense, retro-futuristic, responsive, and visually premium.
* **Global Styles**: Uses dynamic glassmorphism (semi-transparent slate backgrounds, heavy backdrop filters, and neon borders) to create layered visual depth.

---

## 2. Design Tokens (Colors & Typography)

### Color Palette (Hex & HSL Values)

| Role / Element | Primary Neon Accent | Glass Background | Border Color | Glow Shadow |
| :--- | :--- | :--- | :--- | :--- |
| **Global Base** | `#070a13` (Deep Void) | `rgba(15, 23, 42, 0.6)` | `rgba(255, 255, 255, 0.08)` | - |
| **Customer App** | `#10b981` (Loyal Emerald) | `rgba(16, 185, 129, 0.02)` | `rgba(16, 185, 129, 0.15)` | `rgba(16, 185, 129, 0.25)` |
| **Rider Light** | `#f59e0b` (Amber Neon) | `rgba(245, 158, 11, 0.02)` | `rgba(245, 158, 11, 0.15)` | `rgba(245, 158, 11, 0.25)` |
| **Inventory MFC** | `#3b82f6` (Cobalt Blue) | `rgba(59, 130, 246, 0.02)` | `rgba(59, 130, 246, 0.15)` | `rgba(59, 130, 246, 0.25)` |
| **Business Console** | `#a855f7` (Retro Purple) | `rgba(168, 85, 247, 0.02)` | `rgba(168, 85, 247, 0.15)` | `rgba(168, 85, 247, 0.25)` |
| **Admin & Alerts** | `#ef4444` (Hot Red) | `rgba(239, 68, 68, 0.03)` | `rgba(239, 68, 68, 0.2)` | `rgba(239, 68, 68, 0.3)` |
| **System Engine** | `#06b6d4` (Cyan Laser) | `rgba(6, 182, 212, 0.02)` | `rgba(6, 182, 212, 0.15)` | `rgba(6, 182, 212, 0.3)` |

### Typography Scale
* **Primary Sans-Serif Font**: `Inter` (Google Fonts) – Used for body copy, controls, form fields, and labels.
* **Secondary Display Font**: `Outfit` (Google Fonts) – Used for headers, brand logos, and dashboard titles.
* **Monospace Font**: `Fira Code` or `SF Mono` – Used for live logs, metrics values, and telemetry telemetry data.

| Type Scale Name | Font Family | Weight | Size (px) | Line Height | Case |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **App Title** | `Outfit` | 800 (Extra Bold) | 22px | 120% | Title Case |
| **Section Header** | `Outfit` | 700 (Bold) | 16px | 130% | Title Case |
| **Card Header** | `Outfit` | 600 (Semi-Bold) | 14px | 130% | Title Case |
| **Body Primary** | `Inter` | 400 (Regular) | 13px | 150% | Sentence Case |
| **Body Secondary** | `Inter` | 400 (Regular) | 11px | 140% | Sentence Case |
| **Badge / Label** | `Inter` | 700 (Bold) | 10px | 100% | UPPERCASE |
| **Logs Monospace** | `Fira Code` | 400 (Regular) | 11px | 135% | Code |

---

## 3. UI Component Templates & Layout Grid

### Layout Structure (Desktop Width 1440px)
* **Header Bar**:
  - Height: `64px`
  - Content: Brand Logo (`swiss_App`) + Profile Environment Selector (Dropdown) + Role Selection Tabs (Customer, Rider, Inventory, Business, Admin).
* **Main Dashboard Split Grid**:
  - Two-Column Layout:
    - **Main Operational Panel** (Left Side): Width `72%` (Fluid). Contains the role-specific workspace views.
    - **System Engine Room Sidebar** (Right Side): Width `28%` (Fixed `360px`). Contains live telemetry graphs, database writes latency, JWT validation indicators, and the live Kafka scroll logs.

### Card Design (Glassmorphic Container)
* **Background**: `rgba(15, 23, 42, 0.6)`
* **Backdrop Filter**: `blur(20px)`
* **Border**: `1px solid rgba(255, 255, 255, 0.08)`
* **Border Radius**: `12px` (Internal cards) or `18px` (Major outer panels).
* **Padding**: `1.25rem (20px)`

### Buttons & States
* **Primary Glow Button**:
  - Background: `linear-gradient(135deg, var(--accent), darken(var(--accent), 10%))`
  - Hover State: Scale `1.02`, Increase drop-shadow glow radius by `5px`.
  - Active State: Scale `0.98`, Reduced opacity.
* **Secondary Border Button**:
  - Background: `transparent`
  - Border: `1px solid var(--border-color)`
  - Hover State: Border matches Accent color, text matches Accent color.

---

## 4. UI Dashboard Templates (Figma Layout Blueprint)

### A. Customer Super App View (Emerald Theme)
1. **Header Banner**: Emerald glow banner with user profile image and search input.
2. **Catalog Shelf**: Grid of grid-cards displaying products (Milk, Eggs, Bananas) with emojis, price tag, stock indicators, and "Add to Cart" button.
3. **Cart Drawer**: Slides out from the right showing selected items, tip option selectors ($2, $5, $10), ESG bag recycling option, and final checkouts.
4. **Statements Subtab**: Chronological statements with double-entry ledgers.

### B. Rider Light View (Amber Theme)
1. **Active Dispatch Map Card**: Large vector map container illustrating coordinates path.
2. **SLA Countdown Card**: Circular progress bar indicating elapsed SLA delivery window.
3. **IoT Telemetry Sensor Box**: Perishable warming telemetry with flashing warning flag (>8.0°C) and **"Inject Dry Ice ($2)"** button.
4. **Rider Academy courses**: Start training courses to earn certificates.

### C. Dark Store Inventory View (Cobalt Theme)
1. **Picker Checklist Panel**: Dynamic list of active orders with checklist items and speed badges.
2. **Imbalance scanning & Transfers**: balancing button showing transfers between Central & East stores.
3. **Restocking Panel**: Wholesaler reorder tracking.

### D. Chaos Admin Desk (Hot Red Theme)
1. **Fault Injection Desk**: Switches to activate network latencies, gateway timeouts, cold chain sensor breakdown, and B2B Wholesaler outages.
2. **Verification Checklist**: 3-level validation cards for rider, merchant, and payment gateway onboarding.
3. **HITL Queue**: List of pending payment authorization request tickets with Approve/Void dials.

---

## 5. Micro-Animations & Interactions Specs
* **Scanlines Overlay**: Add a subtle `linear-gradient(0deg, rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.25) 50%)` background with repeating size `4px` to simulate classic CRT monitors.
* **Glow Pulse**: Highlight indicators (like warning flags and active sensor warnings) pulse opacity between `0.4` and `1.0` every `1.5` seconds.
* **Fade-in Overlay**: Modal backgrounds (MFA logins, certificate canvases) animate opacity `0 -> 1` in `0.25s` with cubic-bezier curves.
