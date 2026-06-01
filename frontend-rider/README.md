# 🚴 Swish App: Rider Delivery MFE (`frontend-rider`)

Welcome to the **Rider Delivery Portal Micro-Frontend**. This remote module provides the complete rider logistics console: viewing incoming queue offers, completing order acceptance, monitoring traffic delays, updating coordinates, and executing proof of delivery handoffs.

---

## 🏗️ Architecture & Module Federation

This micro-frontend is exposed as a remote module that is dynamically loaded into the **Host Orchestrator** at runtime.

### **Port Configuration**
*   **Rider Remote**: `http://localhost:3002`

### **Exposed Interfaces**
```javascript
exposes: {
  './RiderApp': './src/components/RiderApp.jsx'
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

*   **Rider Operations Console**: Interactive panel displaying delivery queues, real-time routing alerts, and traffic toggle modes.
*   **Trust Score & Earnings Ledger**: Tracks positive ratings, completed jobs, trust score status, and wallet cash-outs.
*   **Simulated Geolocation Sync**: Provides testing capabilities for latitude and longitude updates dynamically sent to the backend.
