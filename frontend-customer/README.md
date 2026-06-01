# 🛒 Swish App: Customer Portal MFE (`frontend-customer`)

Welcome to the **Customer Portal Micro-Frontend**. This remote module provides the entire customer experience: browsing fresh catalog groceries, managing the cart, configuring loyalty points/vouchers, selecting delivery locations, and tracking order completion in real-time.

---

## 🏗️ Architecture & Module Federation

This micro-frontend is exposed as a remote module that is dynamically loaded into the **Host Orchestrator** at runtime.

### **Port Configuration**
*   **Customer Remote**: `http://localhost:3001`

### **Exposed Interfaces**
```javascript
exposes: {
  './CustomerApp': './src/components/CustomerApp.jsx'
}
```

### **Shared Packages**
*   `react`
*   `react-dom`

---

## ⚡ Development & Integration

### **Running Locally**

To run this micro-frontend independently during development:
1.  Install dependencies:
    ```bash
    npm install
    ```
2.  Start the development server:
    ```bash
    npm run dev
    ```
3.  Build the remote assets (generates the `/assets/remoteEntry.js` bundle):
    ```bash
    npm run build
    ```

---

## 🎨 Layout & Premium Features

*   **Responsive Dark Mode Grid**: Optimized catalog viewing for fruits, bakery, drinks, and fresh dairy.
*   **Active Cart & Promotions**: Integrated with loyalty discount slices, VIP tiers, and real-time checkout updates.
*   **Live Order Tracking**: Dynamic step-by-step progress tracking for active deliveries.
