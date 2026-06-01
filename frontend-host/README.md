# 🚀 Swish App: Host Application Orchestrator (`frontend-host`)

Welcome to the **Swish App Frontend Host Orchestrator**. This application serves as the core shell and container in our Micro-Frontend (MFE) architecture, binding together the specialized portals for customers, riders, and administrators.

---

## 🏗️ Architecture & Module Federation

The Host application leverages **Vite Module Federation** (`@originjs/vite-plugin-federation`) to dynamically resolve and stitch remote micro-frontends at runtime.

### **Port Configuration**
*   **Host Orchestrator**: `http://localhost:3000`

### **Registered Remotes**
*   **Customer Remote** (`customer`): `http://localhost:3001/assets/remoteEntry.js`
*   **Rider Remote** (`rider`): `http://localhost:3002/assets/remoteEntry.js`
*   **Admin Remote** (`admin`): `http://localhost:3003/assets/remoteEntry.js`

### **Shared Packages**
*   `react`
*   `react-dom`

---

## 📊 Centralized Zustand State Architecture

The host application defines and exposes the global state machine utilizing a sliced **Zustand** store. Remote MFEs consume and interact with these state hooks:

1.  **`UserSlice`**:
    *   Manages user roles, address profiles, favorite items, loyalty vouchers, addresses, payment cards, wallet balances (customer, rider, merchant), trust scores, and onboarding queues.
2.  **`ProductSlice`**:
    *   Tracks catalog state, real-time inventory levels across dark stores, and catalog search volume metrics.
3.  **`OrderSlice`**:
    *   Manages the shopping cart, active delivery order states, historical logs, tip adjustments, and stock transfer requests.

---

## ⚡ Developer Guide

### **Getting Started**

1.  Install dependencies:
    ```bash
    npm install
    ```
2.  Launch the development server:
    ```bash
    npm run dev
    ```
3.  Build for production:
    ```bash
    npm run build
    ```
4.  Run End-to-End Cypress integration tests:
    ```bash
    npx cypress open
    # Or in headless mode:
    npx cypress run
    ```

---

## 🎨 Design System & Styling

*   **Vibrant Aesthetics**: Custom vanilla CSS tokens define high-contrast dark modes, smooth gradients, and glassmorphic backdrop filters.
*   **Micro-Animations**: Hover-triggered interactive scale shifts, skeleton load states, and sliding panels give the interface a premium, responsive feel.
