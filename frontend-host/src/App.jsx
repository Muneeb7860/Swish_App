import React, { lazy, Suspense, useState } from 'react';
import './App.css';

// Dynamically import the Micro-Frontends
const RemoteRider = lazy(() => import('remoteRider/RiderDashboard').catch(() => {
  return { default: () => <div className="error">Failed to load Rider MFE</div> };
}));

const RemoteB2B = lazy(() => import('remoteB2B/B2bDashboard').catch(() => {
  return { default: () => <div className="error">Failed to load B2B MFE</div> };
}));

function App() {
  const [activeTab, setActiveTab] = useState('b2b');

  return (
    <div className="app-shell">
      <header className="shell-header">
        <h1>Global Polyglot Platform Shell</h1>
        <nav>
          <button onClick={() => setActiveTab('b2b')} className={activeTab === 'b2b' ? 'active' : ''}>
            B2B Wholesale Portal
          </button>
          <button onClick={() => setActiveTab('rider')} className={activeTab === 'rider' ? 'active' : ''}>
            Rider Terminal
          </button>
        </nav>
      </header>

      <main className="shell-content">
        <Suspense fallback={<div className="loading">Loading Module...</div>}>
          {activeTab === 'b2b' ? <RemoteB2B /> : <RemoteRider />}
        </Suspense>
      </main>
    </div>
  );
}

export default App;
