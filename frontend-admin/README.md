# 👑 Swish App: Admin & Control Console MFE (`frontend-admin`)

Welcome to the **Admin & Control Console Micro-Frontend**. This remote module provides full-platform administrative capabilities across multiple dimensions: business metrics dashboards, catalog management, inventory control, and system engine room performance testing.

---

## 🏗️ Architecture & Module Federation

This micro-frontend is exposed as a remote module that is dynamically loaded into the **Host Orchestrator** at runtime.

### **Port Configuration**
*   **Admin Remote**: `http://localhost:3003`

### **Exposed Interfaces**
```javascript
exposes: {
  './AdminPanel': './src/components/AdminPanel.jsx',
  './BusinessApp': './src/components/BusinessApp.jsx',
  './InventoryApp': './src/components/InventoryApp.jsx',
  './SystemEngineRoom': './src/components/SystemEngineRoom.jsx'
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

## 🎨 Admin Control Panels

1.  **Admin Panel**: The master console orchestrating multi-tab configurations.
2.  **Business Dashboard**: Visualizes overall system telemetry, order velocities, refund rates, and B2B pricing modifiers.
3.  **Inventory App**: Facilitates catalog modifications, stock updates, dark store management, and transfer requests.
4.  **System Engine Room**: A specialized control panel designed for running high-frequency simulated transaction ticks and load benchmarks.
