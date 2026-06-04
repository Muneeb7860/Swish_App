import React, { useState, useEffect } from 'react';

const B2bDashboard = () => {
  const [orderStatus, setOrderStatus] = useState('PENDING');
  const [wsStatus, setWsStatus] = useState('Disconnected');

  useEffect(() => {
    // We pass userId=b2b-customer-123 and access_token to satisfy the secure handshake
    const ws = new WebSocket('ws://localhost:8080/ws/notifications/b2b?userId=b2b-customer-123&access_token=mock_token_for_now');

    ws.onopen = () => {
      console.log('Connected to Notification WebSockets via Gateway');
      setWsStatus('Connected');
    };

    ws.onmessage = (event) => {
      console.log('Received Push Notification:', event.data);
      try {
        const payload = JSON.parse(event.data);
        if (payload.ai_status) {
          // Instantly update the UI based on the Edge AI's decision
          setOrderStatus(payload.ai_status);
        } else if (payload.type === 'WELCOME') {
          console.log(payload.message);
        }
      } catch (err) {
        console.error('Failed to parse WebSocket message', err);
      }
    };

    ws.onclose = () => {
      setWsStatus('Disconnected');
    };

    return () => {
      ws.close();
    };
  }, []);

  return (
    <div style={{ padding: '20px', backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ color: '#2d3748', margin: 0 }}>B2B Wholesale Portal</h2>
        <span style={{ fontSize: '0.8rem', padding: '4px 8px', borderRadius: '12px', background: wsStatus === 'Connected' ? '#c6f6d5' : '#fed7d7', color: wsStatus === 'Connected' ? '#22543d' : '#822727' }}>
          Live Push: {wsStatus}
        </span>
      </div>
      <p>Place wholesale orders and monitor AI credit evaluation in real-time.</p>
      
      <div style={{ marginTop: '20px', padding: '20px', background: '#f7fafc', borderRadius: '8px' }}>
        <h3>Bulk Order Placement</h3>
        <p>Order Total: $1,250,000</p>
        <button 
          onClick={() => setOrderStatus('PENDING')}
          style={{ background: '#4299e1', color: 'white', border: 'none', padding: '10px 20px', borderRadius: '4px', cursor: 'pointer' }}
        >
          Submit Order (Simulate)
        </button>
        
        <div style={{ marginTop: '20px', padding: '15px', border: '1px solid #cbd5e0', borderRadius: '6px' }}>
          <h4>AI Evaluation Status</h4>
          <span style={{ 
            display: 'inline-block', 
            padding: '5px 10px', 
            borderRadius: '15px', 
            backgroundColor: orderStatus === 'PENDING' ? '#ecc94b' : orderStatus === 'APPROVED' ? '#48bb78' : '#f6ad55',
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
