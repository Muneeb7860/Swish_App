import React, { useState, useEffect } from 'react';
import { useResilientWebSocket } from './useResilientWebSocket';

interface NotificationPayload {
  ai_status?: string;
  amount?: number;
  orderId?: string;
  status?: string;
  [key: string]: any;
}

interface NotificationEnvelope {
  id: string;
  type: string;
  timestamp: string;
  recipientId: string;
  priority?: string;
  correlationId?: string;
  payload?: NotificationPayload;
}

interface LogEntry {
  text: string;
  type: 'info' | 'success' | 'warning' | 'error';
  time: string;
}

const B2bDashboard: React.FC = () => {
  // Configurable connection settings
  const [gatewayUrl, setGatewayUrl] = useState('http://localhost:8080');
  const [userId, setUserId] = useState('b2b-customer-123');
  const [accessToken, setAccessToken] = useState(() => localStorage.getItem('jwt_token') || 'mock_token_for_now');
  const [isConfigOpen, setIsConfigOpen] = useState(false);

  // Business state
  const [orderId, setOrderId] = useState('ORD-' + Math.floor(100000 + Math.random() * 900000));
  const [orderStatus, setOrderStatus] = useState('PENDING'); // PENDING, PAYMENT_PROCESSING, PROCESSING, APPROVED, HUMAN_TRIAGE, PAYMENT_FAILED
  const [lastTraceId, setLastTraceId] = useState<string | null>(null);
  const [copiedIndex, setCopiedIndex] = useState<number | string | null>(null);
  
  // E2E Simulation state
  const [simulationMode, setSimulationMode] = useState<'AUTO' | 'LOCAL_MOCK'>('AUTO');
  const [simulationLog, setSimulationLog] = useState<LogEntry[]>([]);
  const [isSimulating, setIsSimulating] = useState(false);

  // Derive WebSocket URL from HTTP Gateway URL
  const getWsUrl = (httpUrl: string) => {
    try {
      const urlObj = new URL(httpUrl);
      const wsProtocol = urlObj.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${wsProtocol}//${urlObj.host}/ws/notifications/b2b`;
    } catch (e) {
      return 'ws://localhost:8080/ws/notifications/b2b';
    }
  };

  const wsUrl = getWsUrl(gatewayUrl);

  const addLog = (message: string, type: 'info' | 'success' | 'warning' | 'error' = 'info') => {
    setSimulationLog(prev => [{ text: message, type, time: new Date().toLocaleTimeString() }, ...prev].slice(0, 20));
  };

  // Handle incoming websocket messages
  const handleWsMessage = (envelope: NotificationEnvelope) => {
    console.log('[B2bDashboard] Received message envelope:', envelope);
    if (envelope.correlationId) {
      setLastTraceId(envelope.correlationId);
      const event = new CustomEvent('ws-packet-received', { detail: { ...envelope, source: 'B2bDashboard' } });
      window.dispatchEvent(event);
    }

    const payload = envelope.payload || {};
    
    switch (envelope.type) {
      case 'ORDER_EVALUATED':
        addLog(`AI credit evaluation completed: ${payload.ai_status || 'UNKNOWN'}`, 'success');
        setOrderStatus(payload.ai_status || 'UNKNOWN');
        break;
      case 'PAYMENT_CONFIRMED':
        addLog('Payment confirmed. Order sent to AI credit check.', 'success');
        setOrderStatus('PROCESSING');
        break;
      case 'PAYMENT_FAILED':
        addLog('Payment failed. Transaction aborted.', 'error');
        setOrderStatus('PAYMENT_FAILED');
        break;
      default:
        if (payload.ai_status) {
          setOrderStatus(payload.ai_status);
        }
    }
  };

  // Connect via resilient hook
  const {
    status: wsStatus,
    notifications,
    reconnectAttempts,
    reconnect,
    disconnect,
    clearNotifications
  } = useResilientWebSocket(wsUrl, {
    userId,
    accessToken,
    onMessage: handleWsMessage
  });

  // Handle simulated E2E payment and webhook
  const handleCheckout = async () => {
    setIsSimulating(true);
    setOrderStatus('PAYMENT_PROCESSING');
    const idempotencyKey = 'idemp-' + Math.floor(Math.random() * 100000000);
    const traceId = 'trace-' + Math.floor(Math.random() * 100000000);
    
    addLog(`Initiating checkout. Order: ${orderId}, Idempotency: ${idempotencyKey}`, 'info');

    if (simulationMode === 'LOCAL_MOCK') {
      runLocalMock(idempotencyKey, traceId);
      return;
    }

    try {
      addLog(`Calling Gateway: POST /api/v1/checkout/intents...`, 'info');
      const intentRes = await fetch(`${gatewayUrl}/api/v1/checkout/intents`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Idempotency-Key': idempotencyKey,
          ...(accessToken && accessToken !== 'mock_token_for_now' ? { 'Authorization': `Bearer ${accessToken}` } : {})
        },
        body: JSON.stringify({
          customerId: userId,
          orderId: orderId,
          amount: 1250000
        })
      });

      if (!intentRes.ok) {
        throw new Error(`Intent API returned status ${intentRes.status}`);
      }

      const intentData = await intentRes.json();
      addLog(`PaymentIntent created. ID: ${intentData.paymentId}. Client Secret acquired.`, 'success');

      // Now trigger Stripe Webhook
      addLog(`Calling Gateway: POST /api/webhooks/payments/stripe...`, 'info');
      const webhookRes = await fetch(`${gatewayUrl}/api/webhooks/payments/stripe`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Stripe-Signature': 'mock_sig_for_dev'
        },
        body: JSON.stringify({
          id: 'evt_' + Math.floor(Math.random() * 100000),
          type: 'payment_intent.succeeded',
          paymentIntentId: intentData.paymentId,
          correlationId: traceId
        })
      });

      if (!webhookRes.ok) {
        throw new Error(`Webhook API returned status ${webhookRes.status}`);
      }

      addLog(`Simulated Stripe Webhook dispatched successfully. Waiting for WebSocket message...`, 'success');
      setLastTraceId(traceId);

    } catch (error: any) {
      addLog(`API Connection Failed: ${error.message}. Falling back to Local Mock.`, 'warning');
      runLocalMock(idempotencyKey, traceId);
    } finally {
      setIsSimulating(false);
    }
  };

  const runLocalMock = (idempotencyKey: string, traceId: string) => {
    setTimeout(() => {
      addLog(`[Local Mock] Payment succeeded.`, 'success');
      setOrderStatus('PROCESSING');

      window.dispatchEvent(new CustomEvent('ws-packet-received', {
        detail: {
          id: 'mock-msg-' + Math.random(),
          type: 'PAYMENT_CONFIRMED',
          timestamp: new Date().toISOString(),
          recipientId: userId,
          correlationId: traceId,
          payload: { status: 'CONFIRMED', orderId }
        }
      }));

      setTimeout(() => {
        const isApproved = Math.random() > 0.3;
        const statusOutcome = isApproved ? 'APPROVED' : 'HUMAN_TRIAGE';
        
        setOrderStatus(statusOutcome);
        addLog(`[Local Mock] AI Evaluation complete: ${statusOutcome}`, 'success');

        window.dispatchEvent(new CustomEvent('ws-packet-received', {
          detail: {
            id: 'mock-msg-' + Math.random(),
            type: 'ORDER_EVALUATED',
            timestamp: new Date().toISOString(),
            recipientId: userId,
            correlationId: traceId,
            payload: { ai_status: statusOutcome, orderId }
          }
        }));
      }, 3000);

    }, 1500);
    setIsSimulating(false);
  };

  const resetOrder = () => {
    setOrderId('ORD-' + Math.floor(100000 + Math.random() * 900000));
    setOrderStatus('PENDING');
    addLog('Order state reset.');
  };

  const copyToClipboard = (text: string, index: number | string) => {
    navigator.clipboard.writeText(text);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex(null), 1500);
  };

  return (
    <div className="flex flex-col gap-6 animate-fade-in">
      {/* Top Bar with Status and Config Toggle */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-b border-slate-200 dark:border-slate-800 pb-5 gap-4">
        <div className="flex flex-col">
          <h2 className="m-0 text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">B2B Wholesale Portal</h2>
          <p className="m-0 mt-1 text-sm text-slate-500 dark:text-slate-400">High-value order clearing & real-time credit underwriting</p>
        </div>
        <div className="flex items-center gap-4">
          <button 
            className={`px-4 py-2 text-sm font-semibold rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 cursor-pointer transition ${isConfigOpen ? 'border-indigo-400 dark:border-indigo-700 text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-950/40' : ''}`}
            onClick={() => setIsConfigOpen(!isConfigOpen)}
          >
            ⚙️ Configure Socket
          </button>
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-100 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-800 text-xs font-semibold text-slate-700 dark:text-slate-300">
            <span className={`w-2 h-2 rounded-full inline-block ${
              wsStatus === 'CONNECTED' ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' :
              wsStatus.startsWith('RECONNECTING') || wsStatus === 'CONNECTING' ? 'bg-amber-500 animate-pulse' : 'bg-rose-500'
            }`}></span>
            <span className="capitalize">{wsStatus.toLowerCase()}</span>
            {reconnectAttempts > 0 && wsStatus === 'RECONNECTING' && (
              <span className="text-[10px] text-slate-400">({reconnectAttempts}/10)</span>
            )}
          </div>
        </div>
      </div>

      {/* Socket Configuration Drawer */}
      {isConfigOpen && (
        <div className="bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 rounded-xl p-5 shadow-lg animate-slide-up flex flex-col gap-4">
          <h3 className="m-0 text-sm font-bold text-slate-900 dark:text-slate-100">WebSocket Connection Settings</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-500">Gateway URL:</label>
              <input type="text" className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500" value={gatewayUrl} onChange={(e) => setGatewayUrl(e.target.value)} />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-500">User ID:</label>
              <input type="text" className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500" value={userId} onChange={(e) => setUserId(e.target.value)} />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-semibold text-slate-500">Access Token (JWT):</label>
              <input type="password" className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 text-sm focus:outline-none focus:border-indigo-500" value={accessToken} onChange={(e) => setAccessToken(e.target.value)} />
            </div>
          </div>
          <div className="flex gap-3 mt-1">
            <button className="px-3 py-1.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg cursor-pointer transition shadow" onClick={reconnect}>Reinitialize Socket</button>
            <button className="px-3 py-1.5 text-xs font-semibold text-rose-600 bg-rose-50 dark:bg-rose-950/20 border border-rose-200 dark:border-rose-800/60 hover:bg-rose-100 rounded-lg cursor-pointer transition" onClick={disconnect}>Disconnect</button>
          </div>
        </div>
      )}

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-6">
        {/* Left Column: Order Flow */}
        <div className="bg-white dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800 rounded-2xl p-6 shadow-sm flex flex-col">
          <div className="flex justify-between items-center mb-2">
            <h3 className="m-0 text-base font-bold text-slate-900 dark:text-slate-100">Wholesale Order Checkout</h3>
            {orderStatus !== 'PENDING' && (
              <button className="text-xs text-slate-400 hover:text-slate-950 dark:hover:text-slate-100 cursor-pointer" onClick={resetOrder}>🔄 Reset Sandbox</button>
            )}
          </div>

          <div className="bg-slate-50 dark:bg-slate-800/40 border border-slate-200 dark:border-slate-800/60 rounded-xl p-4 my-4 flex flex-col gap-2.5">
            <div className="flex justify-between text-xs text-slate-500 dark:text-slate-400">
              <span>Order Number:</span>
              <strong className="font-mono text-slate-800 dark:text-slate-200">{orderId}</strong>
            </div>
            <div className="flex justify-between text-xs text-slate-500 dark:text-slate-400">
              <span>Order Total:</span>
              <strong className="text-indigo-600 dark:text-indigo-400 font-bold text-sm">$1,250,000.00 USD</strong>
            </div>
            <div className="flex justify-between text-xs text-slate-500 dark:text-slate-400">
              <span>Customer ID:</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">{userId}</span>
            </div>
          </div>

          {/* Checkout Status Timeline Visualizer */}
          <div className="flex justify-between my-6 relative px-2.5">
            <div className="absolute top-4 left-[30px] right-[30px] h-[2px] bg-slate-200 dark:bg-slate-800 z-10"></div>
            
            <div className="flex flex-col items-center gap-2 z-20 flex-1">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition duration-300 border-2 ${orderStatus === 'PENDING' ? 'bg-indigo-50 dark:bg-indigo-950/40 border-indigo-500 text-indigo-600 dark:text-indigo-400' : 'bg-emerald-500 border-emerald-500 text-white'}`}>1</div>
              <div className={`text-[10px] font-semibold ${orderStatus === 'PENDING' ? 'text-indigo-600 dark:text-indigo-400' : 'text-emerald-500'}`}>Draft</div>
            </div>
            <div className="flex flex-col items-center gap-2 z-20 flex-1">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition duration-300 border-2 ${orderStatus === 'PAYMENT_PROCESSING' ? 'bg-indigo-50 dark:bg-indigo-950/40 border-indigo-500 text-indigo-600 dark:text-indigo-400' : orderStatus !== 'PENDING' ? 'bg-emerald-500 border-emerald-500 text-white' : 'bg-slate-50 dark:bg-slate-800 border-slate-200 dark:border-slate-800 text-slate-400 dark:text-slate-600'}`}>2</div>
              <div className={`text-[10px] font-semibold ${orderStatus === 'PAYMENT_PROCESSING' ? 'text-indigo-600 dark:text-indigo-400' : orderStatus !== 'PENDING' ? 'text-emerald-500' : 'text-slate-400 dark:text-slate-600'}`}>Stripe Payment</div>
            </div>
            <div className="flex flex-col items-center gap-2 z-20 flex-1">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition duration-300 border-2 ${
                orderStatus === 'PROCESSING' ? 'bg-indigo-50 dark:bg-indigo-950/40 border-indigo-500 text-indigo-600 dark:text-indigo-400' : 
                (orderStatus === 'APPROVED' || orderStatus === 'HUMAN_TRIAGE') ? 'bg-emerald-500 border-emerald-500 text-white' : 
                orderStatus === 'PAYMENT_FAILED' ? 'bg-rose-500 border-rose-500 text-white' : 
                'bg-slate-50 dark:bg-slate-800 border-slate-200 dark:border-slate-800 text-slate-400 dark:text-slate-600'
              }`}>3</div>
              <div className={`text-[10px] font-semibold ${
                orderStatus === 'PROCESSING' ? 'text-indigo-600 dark:text-indigo-400' : 
                (orderStatus === 'APPROVED' || orderStatus === 'HUMAN_TRIAGE') ? 'text-emerald-500' : 
                orderStatus === 'PAYMENT_FAILED' ? 'text-rose-500' : 
                'text-slate-400 dark:text-slate-600'
              }`}>AI Risk Check</div>
            </div>
            <div className="flex flex-col items-center gap-2 z-20 flex-1">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition duration-300 border-2 ${
                orderStatus === 'APPROVED' ? 'bg-emerald-500 border-emerald-500 text-white' : 
                orderStatus === 'HUMAN_TRIAGE' ? 'bg-amber-500 border-amber-500 text-white' : 
                'bg-slate-50 dark:bg-slate-800 border-slate-200 dark:border-slate-800 text-slate-400 dark:text-slate-600'
              }`}>4</div>
              <div className={`text-[10px] font-semibold ${
                orderStatus === 'APPROVED' ? 'text-emerald-500' : 
                orderStatus === 'HUMAN_TRIAGE' ? 'text-amber-500' : 
                'text-slate-400 dark:text-slate-600'
              }`}>Final Release</div>
            </div>
          </div>

          {/* Interactive Actions */}
          <div className="border border-slate-200 dark:border-slate-800 rounded-xl p-5 mb-5 bg-white dark:bg-slate-900/20">
            {orderStatus === 'PENDING' ? (
              <div className="flex flex-col">
                <h4 className="m-0 mb-3 text-xs font-bold text-slate-500 uppercase tracking-wider">Secure Credit Card Input (Stripe Simulator)</h4>
                <div className="flex flex-col sm:flex-row gap-2 mb-4">
                  <input type="text" placeholder="Card Number" defaultValue="4242 4242 4242 4242" disabled className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-800 dark:text-slate-200 text-sm flex-1" />
                  <input type="text" placeholder="MM/YY" defaultValue="12/28" disabled className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-800 dark:text-slate-200 text-sm w-full sm:w-20" />
                  <input type="text" placeholder="CVC" defaultValue="888" disabled className="px-3 py-2 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-800 dark:text-slate-200 text-sm w-full sm:w-16" />
                </div>
                <div className="mb-4">
                  <label className="text-xs font-semibold text-slate-500 flex items-center gap-2">
                    Simulation Mode:
                    <select className="px-2 py-1 rounded border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 text-slate-800 dark:text-slate-200 text-xs" value={simulationMode} onChange={(e) => setSimulationMode(e.target.value as 'AUTO' | 'LOCAL_MOCK')}>
                      <option value="AUTO">Auto (Gateway API &rarr; Webhooks)</option>
                      <option value="LOCAL_MOCK">Offline Client-Side Mock</option>
                    </select>
                  </label>
                </div>
                <button 
                  className="w-full py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-200 dark:disabled:bg-slate-800 disabled:text-slate-400 dark:disabled:text-slate-600 text-white font-bold text-sm rounded-lg shadow-md transition duration-200 hover:-translate-y-0.5 cursor-pointer disabled:transform-none" 
                  disabled={isSimulating}
                  onClick={handleCheckout}
                >
                  {isSimulating ? 'Sending Request...' : 'Pay $1,250,000'}
                </button>
              </div>
            ) : (
              <div className="flex flex-col gap-4">
                <div className="flex justify-between items-center">
                  <span className="text-sm text-slate-500 dark:text-slate-400">Current Clearance Status:</span>
                  <div className={`px-2.5 py-1 rounded-full text-xs font-bold tracking-wider uppercase ${
                    orderStatus === 'PAYMENT_PROCESSING' ? 'bg-indigo-50 dark:bg-indigo-950/40 text-indigo-600 dark:text-indigo-400' :
                    orderStatus === 'PROCESSING' ? 'bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400' :
                    orderStatus === 'APPROVED' ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-600 dark:text-emerald-400' :
                    orderStatus === 'HUMAN_TRIAGE' ? 'bg-amber-50 dark:bg-amber-950/40 text-amber-600 dark:text-amber-400' :
                    orderStatus === 'PAYMENT_FAILED' ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400' :
                    'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400'
                  }`}>
                    {orderStatus}
                  </div>
                </div>

                {orderStatus === 'PAYMENT_PROCESSING' && (
                  <div className="px-4 py-2.5 bg-indigo-50 dark:bg-indigo-950/30 text-indigo-600 dark:text-indigo-400 rounded-lg text-xs flex items-center gap-2 font-medium">
                    <span className="w-3.5 h-3.5 border-2 border-indigo-200 border-t-indigo-600 rounded-full animate-spin"></span>
                    Processing payment authorization with Stripe Gateway...
                  </div>
                )}

                {orderStatus === 'PROCESSING' && (
                  <div className="px-4 py-2.5 bg-amber-50 dark:bg-amber-950/30 text-amber-600 dark:text-amber-400 rounded-lg text-xs flex items-center gap-2 font-medium">
                    <span className="w-2.5 h-2.5 rounded-full bg-amber-500 animate-ping"></span>
                    Credit limit exceeds $1M. Invoking n8n AI Engine + LLM Credit Score evaluator...
                  </div>
                )}

                {orderStatus === 'HUMAN_TRIAGE' && (
                  <div className="px-4 py-2.5 bg-rose-50 dark:bg-rose-950/30 text-rose-600 dark:text-rose-400 rounded-lg text-xs font-semibold">
                    ⚠️ Order blocked from automated release. Placed in underwriting queue.
                  </div>
                )}

                {orderStatus === 'APPROVED' && (
                  <div className="px-4 py-2.5 bg-emerald-50 dark:bg-emerald-950/30 text-emerald-600 dark:text-emerald-400 rounded-lg text-xs font-semibold">
                    ✅ Credit approved. Shipping labels created and sent to Cold-Chain Rider team.
                  </div>
                )}

                {lastTraceId && (
                  <div className="flex items-center justify-between bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-800/80 px-3 py-2 rounded-lg text-xs">
                    <span className="text-slate-400">Active correlationId:</span>
                    <code className="font-mono text-indigo-600 dark:text-indigo-400 text-[10px]">{lastTraceId}</code>
                    <button className="px-2 py-0.5 border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 rounded text-[10px] text-slate-500 hover:text-indigo-600 cursor-pointer" onClick={() => copyToClipboard(lastTraceId, 'trace')}>
                      {copiedIndex === 'trace' ? 'Copied!' : 'Copy'}
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Sandbox Logs */}
          <div className="flex flex-col">
            <h4 className="m-0 mb-2 text-xs font-bold text-slate-400 uppercase tracking-wider">Sandbox Simulation Logs</h4>
            <div className="h-[180px] bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-xl p-3 overflow-y-auto font-mono text-[10px] flex flex-col gap-1.5">
              {simulationLog.length === 0 ? (
                <div className="text-slate-500 text-center py-12 italic">No logs generated. Click "Pay" to start checkout events.</div>
              ) : (
                simulationLog.map((log, index) => (
                  <div key={index} className="leading-relaxed">
                    <span className="text-slate-500 mr-2">[{log.time}]</span>
                    <span className={
                      log.type === 'success' ? 'text-emerald-400 font-medium' :
                      log.type === 'warning' ? 'text-amber-400 font-medium' :
                      log.type === 'error' ? 'text-rose-400 font-medium' : 'text-slate-300'
                    }>{log.text}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Right Column: Live Push Inbox */}
        <div className="bg-white dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800 rounded-2xl p-6 shadow-sm max-h-[700px] flex flex-col">
          <div className="flex justify-between items-center mb-1">
            <h3 className="m-0 text-base font-bold text-slate-900 dark:text-slate-100">Live Push Inbox</h3>
            <div className="flex items-center gap-2">
              <span className="bg-indigo-600 text-white text-[10px] font-bold rounded-full px-2 py-0.5">{notifications.length}</span>
              {notifications.length > 0 && (
                <button className="text-xs text-slate-400 hover:text-slate-950 dark:hover:text-slate-100 cursor-pointer" onClick={clearNotifications}>Clear</button>
              )}
            </div>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0 mb-4 leading-relaxed">Messages received in real-time from the notification-engine via Gateway.</p>

          <div className="flex-1 overflow-y-auto flex flex-col gap-3 pr-1">
            {notifications.length === 0 ? (
              <div className="flex flex-col items-center justify-center text-center py-20 text-slate-400">
                <div className="text-3xl mb-3 animate-bounce">🔔</div>
                <p className="font-semibold text-sm text-slate-800 dark:text-slate-200 mb-1">No active notifications</p>
                <span className="text-[10px] text-slate-400">Waiting for real-time transactions to trigger events...</span>
              </div>
            ) : (
              notifications.map((notif, index) => (
                <div key={index} className={`border-l-4 bg-slate-50 dark:bg-slate-800/40 rounded-r-xl p-3.5 flex flex-col gap-1.5 shadow-sm border border-slate-200 dark:border-slate-800/60 border-l-slate-300 ${
                  notif.priority === 'HIGH' ? 'border-l-rose-500' :
                  notif.priority === 'MEDIUM' ? 'border-l-amber-500' : 'border-l-emerald-500'
                }`}>
                  <div className="flex justify-between items-center">
                    <span className="text-[9px] font-bold text-slate-700 dark:text-slate-300 bg-slate-200 dark:bg-slate-800 px-2 py-0.5 rounded tracking-wide">{notif.type}</span>
                    <span className="text-[9px] text-slate-400">{new Date(notif.timestamp).toLocaleTimeString()}</span>
                  </div>
                  <p className="text-xs text-slate-800 dark:text-slate-200 m-0 leading-relaxed font-medium">
                    {notif.type === 'ORDER_EVALUATED' && `AI evaluation complete: ${notif.payload?.ai_status}`}
                    {notif.type === 'PAYMENT_CONFIRMED' && `Mock payment of $${(notif.payload?.amount || 1250000).toLocaleString()} confirmed`}
                    {notif.type === 'PAYMENT_FAILED' && `Stripe reported payment failure`}
                    {!['ORDER_EVALUATED', 'PAYMENT_CONFIRMED', 'PAYMENT_FAILED'].includes(notif.type) && JSON.stringify(notif.payload || {})}
                  </p>
                  <div className="text-[10px] flex items-center gap-1.5 border-t border-dashed border-slate-200 dark:border-slate-800/80 pt-2 mt-1">
                    <span className="text-slate-400">trace:</span>
                    <span className="text-indigo-600 dark:text-indigo-400 cursor-pointer underline font-mono text-[9px]" onClick={() => copyToClipboard(notif.correlationId || 'none', index)}>
                      {copiedIndex === index ? 'Copied!' : (notif.correlationId || 'none').substring(0, 15) + '...'}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default B2bDashboard;
