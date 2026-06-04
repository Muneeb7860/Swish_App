import React, { useState } from 'react';

const B2bDashboard = () => {
  const [orderStatus, setOrderStatus] = useState('PENDING');

  return (
    <div style={{ padding: '20px', backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
      <h2 style={{ color: '#2d3748' }}>B2B Wholesale Portal</h2>
      <p>Place wholesale orders and monitor AI credit evaluation in real-time.</p>
      
      <div style={{ marginTop: '20px', padding: '20px', background: '#f7fafc', borderRadius: '8px' }}>
        <h3>Bulk Order Placement</h3>
        <p>Order Total: $1,250,000</p>
        <button 
          onClick={() => setOrderStatus('HUMAN_TRIAGE')}
          style={{ background: '#48bb78', color: 'white', border: 'none', padding: '10px 20px', borderRadius: '4px', cursor: 'pointer' }}
        >
          Submit Order (Simulate)
        </button>
        
        <div style={{ marginTop: '20px', padding: '15px', border: '1px solid #cbd5e0', borderRadius: '6px' }}>
          <h4>AI Evaluation Status</h4>
          <span style={{ 
            display: 'inline-block', 
            padding: '5px 10px', 
            borderRadius: '15px', 
            backgroundColor: orderStatus === 'PENDING' ? '#ecc94b' : '#f6ad55',
            color: 'white',
            fontWeight: 'bold'
          }}>
            {orderStatus}
          </span>
          {orderStatus === 'HUMAN_TRIAGE' && (
            <p style={{ color: '#c53030', marginTop: '10px' }}>
              ⚠️ Alert: Order exceeds $1M threshold. Routed to human underwriter queue.
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default B2bDashboard;
