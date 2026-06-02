# Figma Design System Specification: Swish OS B2B SaaS Cockpit
**Version**: 2.0.0 (B2B SaaS Edition)

This document acts as the official design system blueprint and component library guide for the **Swish OS B2B Autonomous Procurement & Financial Operations Dashboard**. Designers can use this spec to replicate or extend the retro-neon cyberpunk B2B SaaS cockpit aesthetic in Figma.

---

## 1. Core Visual Theme (Aesthetic Concept)
* **Design Philosophy**: **"Cyber-Industrial Procurement Console"**. Blends clean high-information dashboard grids (similar to algorithmic trading platforms) with a high-contrast dark neon color palette.
* **Mood & Vibe**: High-tech, transaction-dense, automated, secure, and visually premium.
* **Global Styles**: Uses dynamic glassmorphism (semi-transparent slate backgrounds, heavy backdrop filters, and neon borders) to create layered visual depth.

---

## 2. Design Tokens (Colors & Typography)

### Color Palette (Hex & HSL Values)

| Role / Element | Primary Neon Accent | Glass Background | Border Color | Glow Shadow |
| :--- | :--- | :--- | :--- | :--- |
| **Global Base** | `#070a13` (Deep Void) | `rgba(15, 23, 42, 0.6)` | `rgba(255, 255, 255, 0.08)` | - |
| **B2B Procurement MFE**| `#10b981` (Loyal Emerald) | `rgba(16, 185, 129, 0.02)` | `rgba(16, 185, 129, 0.15)` | `rgba(16, 185, 129, 0.25)` |
| **Wholesaler Console MFE**| `#f59e0b` (Amber Neon) | `rgba(245, 158, 11, 0.02)` | `rgba(245, 158, 11, 0.15)` | `rgba(245, 158, 11, 0.25)` |
| **Ledger Auditing MFE** | `#3b82f6` (Cobalt Blue) | `rgba(59, 130, 246, 0.02)` | `rgba(59, 130, 246, 0.15)` | `rgba(59, 130, 246, 0.25)` |
| **Admin Exception Cockpit**| `#ef4444` (Hot Red) | `rgba(239, 68, 68, 0.03)` | `rgba(239, 68, 68, 0.2)` | `rgba(239, 68, 68, 0.3)` |
| **BFF & Ingress Telemetry** | `#06b6d4` (Cyan Laser) | `rgba(6, 182, 212, 0.02)` | `rgba(6, 182, 212, 0.15)` | `rgba(6, 182, 212, 0.3)` |

### Typography Scale
* **Primary Sans-Serif Font**: `Inter` (Google Fonts) – Used for body copy, controls, form fields, and labels.
* **Secondary Display Font**: `Outfit` (Google Fonts) – Used for headers, brand logos, and dashboard titles.
* **Monospace Font**: `Fira Code` or `SF Mono` – Used for live negotiation transcripts, ledger values, and OTel telemetry logs.

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
  - Content: Brand Logo (`Swish OS`) + Tenant Domain Selector (Dropdown) + Role Selection Tabs (Procurement, Wholesaler, Ledger, Admin Exception).
* **Main Dashboard Split Grid**:
  - Two-Column Layout:
    - **Main Operational Panel** (Left Side): Width `72%` (Fluid). Contains the active micro-frontend workspaces.
    - **System Engine Room Sidebar** (Right Side): Width `28%` (Fixed `360px`). Contains live telemetry graphs, database write latency, OTel distributed trace stats, and the live Kafka outbox scroll logs.

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

### A. B2B Procurement MFE (Emerald Theme)
1. **Depletion Alarms Banner**: Glowing emerald banner showing low-stock inventory items prompting automation.
2. **AI Negotiation Feeds**: Real-time dialogue feed tracking ongoing LLM negotiations with wholesale distributors (showing bid/counter-bid cycles).
3. **Draft Contracts Desk**: A list of concluded negotiations waiting on automated invoice generation.

### B. Wholesaler Console MFE (Amber Theme)
1. **Supply Proposals Panel**: List of incoming procurement requests dispatched by Swish OS buyer agents.
2. **Discount Engine Room**: Interface to manage Net-10/Net-30 payment margins and toggle wholesaler academy certifications.
3. **Active Restock Shipments**: Order tracking status for fulfilled B2B purchase orders.

### C. Ledger Auditing MFE (Cobalt Theme)
1. **Double-Entry Balance Grid**: Live balance sheet proving assets = liabilities + equity for all tenant accounts.
2. **Cryptographic Hashing Monitor**: Visual hash list showing the SHA-256 tamper-evident journal chain validation history.
3. **GDPR Profile Purge Button**: B2B buyer account anonymization action panel.

### D. Admin Exception Cockpit (Hot Red Theme)
1. **Guardrail Violation Alarms**: Hot-red warning cards showing orders blocked by rule violations (e.g., procurement limit > $5,000).
2. **HITL Override Queue**: Action table allowing supervisors to approve, override, or void transactions caught in the `HitlQueue`.
3. **Chaos Desk Panel**: Toggle switches to inject simulated latency, network partition faults, or wholesaler api outages.

---

## 5. Micro-Animations & Interactions Specs
* **Scanlines Overlay**: Add a subtle `linear-gradient(0deg, rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.25) 50%)` background with repeating size `4px` to simulate classic CRT monitors.
* **Glow Pulse**: Highlight indicators (like warning flags and active sensor warnings) pulse opacity between `0.4` and `1.0` every `1.5` seconds.
* **Fade-in Overlay**: Modal backgrounds (MFA logins, certificate canvases) animate opacity `0 -> 1` in `0.25s` with cubic-bezier curves.
