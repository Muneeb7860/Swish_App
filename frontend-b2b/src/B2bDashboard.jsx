import React, { useState, useEffect, useRef, useCallback } from 'react';

const B2bDashboard = () => {
  const [orderStatus, setOrderStatus] = useState('PENDING');
  const [wsStatus, setWsStatus] = useState('Disconnected');
  const [notifications, setNotifications] = useState([]);
  const wsRef = useRef(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimeoutRef = useRef(null);
  const MAX_RECONNECT_ATTEMPTS = 10;

  const connect = useCallback(() => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) return;

    const ws = new WebSocket('ws://localhost:8080/ws/notifications/b2b?userId=b2b-customer-123&access_token=mock_token_for_now');

    ws.onopen = () => {
      console.log('Connected to Notification WebSockets via Gateway');
      setWsStatus('Connected');
      reconnectAttemptRef.current = 0; // Reset on successful connect
    };

    ws.onmessage = (event) => {
      console.log('Received Push Notification:', event.data);
      try {
        const payload = JSON.parse(event.data);
        
        // Handle different notification envelope types
        if (payload.type === 'WELCOME') {
          console.log(payload.message);
        } else if (payload.type === 'ORDER_EVALUATED') {
          setOrderStatus(payload.payload?.ai_status || payload.ai_status || 'UNKNOWN');
        } else if (payload.type === 'PAYMENT_CONFIRMED') {
          setOrderStatus('APPROVED');
        } else if (payload.type === 'PAYMENT_FAILED') {
          setOrderStatus('PAYMENT_FAILED');
        } else if (payload.type === 'HEARTBEAT') {
          // Server ping — no UI action needed
          return;
        } else if (payload.ai_status) {
          // Legacy fallback for unversioned payloads
          setOrderStatus(payload.ai_status);
        }

        // Store notification in bell icon inbox
        if (payload.type && payload.type !== 'WELCOME' && payload.type !== 'HEARTBEAT') {
          setNotifications(prev => [payload, ...prev].slice(0, 50));
        }
      } catch (err) {
        console.error('Failed to parse WebSocket message', err);
      }
    };

    ws.onclose = (event) => {
      setWsStatus('Reconnecting...');
      wsRef.current = null;

      // Don't reconnect on intentional close (code 1000) or auth failure (4003)
      if (event.code === 1000 || event.code === 4003) {
        setWsStatus('Disconnected');
        return;
      }

      // Exponential backoff with jitter
      if (reconnectAttemptRef.current < MAX_RECONNECT_ATTEMPTS) {
        const delay = Math.min(
          1000 * Math.pow(2, reconnectAttemptRef.current) + Math.random() * 500,
          30000
        );
        console.log(`Reconnecting in ${Math.round(delay)}ms (attempt ${reconnectAttemptRef.current + 1}/${MAX_RECONNECT_ATTEMPTS})`);
        reconnectAttemptRef.current += 1;
        reconnectTimeoutRef.current = setTimeout(connect, delay);
      } else {
        setWsStatus('Disconnected (max retries)');
        console.error('Max reconnection attempts reached. Please refresh the page.');
      }
    };

    ws.onerror = (err) => {
      console.error('WebSocket error:', err);
      ws.close();
    };

    wsRef.current = ws;
  }, []);

  useEffect(() => {
    connect();
    return () => {
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) wsRef.current.close(1000, 'Component unmount');
    };
  }, [connect]);

  return (
    <div style={{ padding: '20px', backgroundColor: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ color: '#2d3748', margin: 0 }}>B2B Wholesale Portal</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{ position: 'relative', cursor: 'pointer', fontSize: '1.2rem' }} title={`${notifications.length} unread`}>
            🔔 {notifications.length > 0 && (
              <span style={{ position: 'absolute', top: '-5px', right: '-8px', background: '#e53e3e', color: 'white', borderRadius: '50%', fontSize: '0.6rem', padding: '2px 5px', fontWeight: 'bold' }}>
                {notifications.length}
              </span>
            )}
          </span>
          <span style={{ fontSize: '0.8rem', padding: '4px 8px', borderRadius: '12px', background: wsStatus === 'Connected' ? '#c6f6d5' : wsStatus.startsWith('Reconnecting') ? '#fefcbf' : '#fed7d7', color: wsStatus === 'Connected' ? '#22543d' : wsStatus.startsWith('Reconnecting') ? '#744210' : '#822727' }}>
            Live Push: {wsStatus}
          </span>
        </div>
      </div>
      <p>Place wholesale orders and monitor AI credit evaluation in real-time.</p>
      
      <div style={{ marginTop: '20px', padding: '20px', background: '#f7fafc', borderRadius: '8px' }}>
        <h3>Bulk Order Placement</h3>
        <p>Order Total: $1,250,000</p>
        
        {orderStatus === 'PENDING' ? (
          <div style={{ padding: '15px', border: '1px solid #cbd5e0', borderRadius: '6px', background: '#fff' }}>
             <h4 style={{ margin: '0 0 10px 0' }}>Secure Checkout (Stripe Mock)</h4>
             <div style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
                <input type="text" placeholder="Card Number" defaultValue="4242 4242 4242 4242" disabled style={{ flex: 1, padding: '8px', borderRadius: '4px', border: '1px solid #e2e8f0' }} />
                <input type="text" placeholder="MM/YY" defaultValue="12/26" disabled style={{ width: '80px', padding: '8px', borderRadius: '4px', border: '1px solid #e2e8f0' }} />
                <input type="text" placeholder="CVC" defaultValue="123" disabled style={{ width: '60px', padding: '8px', borderRadius: '4px', border: '1px solid #e2e8f0' }} />
             </div>
             <button 
                onClick={() => setOrderStatus('PAYMENT_PROCESSING')}
                style={{ width: '100%', background: '#4299e1', color: 'white', border: 'none', padding: '10px 20px', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}
              >
                Pay $1,250,000 (Simulate Webhook)
              </button>
          </div>
        ) : orderStatus === 'PAYMENT_PROCESSING' ? (
           <div style={{ padding: '15px', textAlign: 'center', color: '#718096' }}>
              Processing Payment via Stripe API...
           </div>
        ) : (
          <div style={{ marginTop: '20px', padding: '15px', border: '1px solid #cbd5e0', borderRadius: '6px' }}>
            <h4>AI Evaluation Status</h4>
            <span style={{ 
              display: 'inline-block', 
              padding: '5px 10px', 
              borderRadius: '15px', 
              backgroundColor: orderStatus === 'APPROVED' ? '#48bb78' : orderStatus === 'PAYMENT_FAILED' ? '#e53e3e' : '#f6ad55',
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
        )}
      </div>
    </div>
  );
};

export default B2bDashboard;
