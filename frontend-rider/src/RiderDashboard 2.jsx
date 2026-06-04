import React from 'react';

const RiderDashboard = () => {
  return (
    <div style={{ padding: '20px', backgroundColor: '#f0f4f8', borderRadius: '8px' }}>
      <h2 style={{ color: '#1a365d' }}>Rider Terminal</h2>
      <p>GPS tracking and Cold-Chain IoT status will be displayed here.</p>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginTop: '20px' }}>
        <div style={{ background: 'white', padding: '15px', borderRadius: '6px', boxShadow: '0 2px 4px rgba(0,0,0,0.1)' }}>
          <h4>Active Order</h4>
          <p>Order #7890 - En Route</p>
          <button style={{ background: '#3182ce', color: 'white', border: 'none', padding: '8px 16px', borderRadius: '4px' }}>Update Status</button>
        </div>
        <div style={{ background: 'white', padding: '15px', borderRadius: '6px', boxShadow: '0 2px 4px rgba(0,0,0,0.1)' }}>
          <h4>IoT Sensor</h4>
          <p>Temperature: -18°C (Optimal)</p>
          <span style={{ color: 'green', fontWeight: 'bold' }}>Stable</span>
        </div>
      </div>
    </div>
  );
};

export default RiderDashboard;
