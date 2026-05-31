import React, { useState, useEffect, useMemo, useRef, Suspense } from 'react';
import * as Lucide from 'lucide-react';
import { useEnvProfiles } from './hooks/useEnvProfiles';
import MfaLoginPortal from './components/MfaLoginPortal';
import RbacBlocker from './components/RbacBlocker';
import SupportBot from './components/SupportBot';
import RiderTrackingPanel from './components/RiderTrackingPanel';

// Strict MFE Origin Whitelist Check to prevent module hijacking
const MFE_WHITELIST = [
  'localhost',
  '127.0.0.1'
];

const verifyMfeOrigin = (importPromise, remoteName) => {
  return importPromise.then(module => {
    const scriptElements = Array.from(document.querySelectorAll('script'));
    const remoteScript = scriptElements.find(s => s.src && s.src.includes(remoteName));
    if (remoteScript) {
      const url = new URL(remoteScript.src);
      if (!MFE_WHITELIST.includes(url.hostname) && url.origin !== window.location.origin) {
        throw new Error(`Security Exception: Untrusted MFE Remote origin blocked: ${url.origin}`);
      }
    }
    return module;
  });
};

// Lazy loaded remote Micro-Frontends with Whitelist Verification
const CustomerApp = React.lazy(() => verifyMfeOrigin(import('customer/CustomerApp'), 'customer'));
const RiderApp = React.lazy(() => verifyMfeOrigin(import('rider/RiderApp'), 'rider'));
const AdminPanel = React.lazy(() => verifyMfeOrigin(import('admin/AdminPanel'), 'admin'));
const BusinessApp = React.lazy(() => verifyMfeOrigin(import('admin/BusinessApp'), 'admin'));
const InventoryApp = React.lazy(() => verifyMfeOrigin(import('admin/InventoryApp'), 'admin'));
const SystemEngineRoom = React.lazy(() => verifyMfeOrigin(import('admin/SystemEngineRoom'), 'admin'));

class LocalErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error(`MFE Error Boundary caught failure for [${this.props.name}]:`, error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="glass-card" style={{ padding: '2.5rem', textAlign: 'center', borderColor: '#ef4444', borderWidth: '1px', borderStyle: 'dashed', borderRadius: '12px', background: 'rgba(239, 68, 68, 0.02)' }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.85rem' }}>
            <div style={{ color: '#ef4444', background: 'rgba(239, 68, 68, 0.1)', padding: '0.6rem', borderRadius: '50%', display: 'inline-flex' }}>
              <Lucide.AlertOctagon size={24} />
            </div>
            <h4 style={{ margin: 0, color: '#f8fafc', fontSize: '1.05rem', fontWeight: 800 }}>Micro-Frontend Load Failure</h4>
            <p style={{ margin: 0, fontSize: '0.8rem', color: '#94a3b8', maxWidth: '440px', lineHeight: '1.4' }}>
              The federated remote panel [<strong>{this.props.name}</strong>] failed to load or experienced a runtime crash. Downstream systems and checkout capabilities remain operational.
            </p>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}


// Default mock product catalog
const INITIAL_PRODUCTS = [
  { id: 'p1', name: 'Organic Fresh Milk', price: 3.49, stock: 12, stockEast: 15, category: 'Dairy & Eggs', emoji: '🥛', perishable: true },
  { id: 'p2', name: 'Chiquita Bananas (1kg)', price: 1.99, stock: 18, stockEast: 20, category: 'Fruits & Veggies', emoji: '🍌', perishable: false },
  { id: 'p3', name: 'Fresh Hass Avocado (Pair)', price: 2.99, stock: 8, stockEast: 0, category: 'Fruits & Veggies', emoji: '🥑', perishable: false },
  { id: 'p4', name: 'Coca Cola Zero 6-Pack', price: 5.49, stock: 15, stockEast: 15, category: 'Snacks & Drinks', emoji: '🥤', perishable: false },
  { id: 'p5', name: 'Whole Wheat Sourdough', price: 4.29, stock: 6, stockEast: 8, category: 'Bakery', emoji: '🍞', perishable: false },
  { id: 'p6', name: 'Double Chocolate Muffins', price: 3.89, stock: 2, stockEast: 5, category: 'Bakery', emoji: '🧁', perishable: false },
  { id: 'p7', name: 'Free Range Eggs (Dozen)', price: 4.99, stock: 10, stockEast: 12, category: 'Dairy & Eggs', emoji: '🥚', perishable: true },
  { id: 'p8', name: 'Potato Chips (Sea Salt)', price: 2.49, stock: 25, stockEast: 30, category: 'Snacks & Drinks', emoji: '🥔', perishable: false }
];

export default function App() {
  // --- CORE COCKPIT STATES ---
  const { envProfiles, setEnvProfiles, activeProfileKey, setActiveProfileKey, activeProfile } = useEnvProfiles();
  const [activeRole, setActiveRole] = useState('customer'); // customer, rider, business, inventory, admin
  const [products, setProducts] = useState(INITIAL_PRODUCTS);
  const [cart, setCart] = useState([]);
  const [activeOrder, setActiveOrder] = useState(null);
  const [orderHistory, setOrderHistory] = useState([
    { id: 8901, date: 'May 24', items: '2x Organic Milk, 1x Bananas', total: 8.97, status: 'delivered', paymentMethod: 'Wallet' },
    { id: 8710, date: 'May 20', items: '1x Wheat Sourdough, 1x Free Range Eggs', total: 9.28, status: 'delivered', paymentMethod: 'PayPal' }
  ]);
  const [weather, setWeather] = useState('Sunny'); // Sunny, Heavy Rain, Thunderstorm
  
  // Financials
  const [customerWallet, setCustomerWallet] = useState(100.00);
  const [customerPoints, setCustomerPoints] = useState(45);
  const [riderWallet, setRiderWallet] = useState(15.00);
  const [merchantWallet, setMerchantWallet] = useState(1542.80);
  const [customerOrderCount, setCustomerOrderCount] = useState(2);
  const [customerRefundCount, setCustomerRefundCount] = useState(0);

  // --- SWIGGY-STYLE USER PROFILE HUB STATES ---
  const [customerTab, setCustomerTab] = useState('catalog'); // catalog, profile
  const [profileSubTab, setProfileSubTab] = useState('vip'); // vip, vouchers, rewards, orders, saved, addresses, payments, statements
  const [savedAddresses, setSavedAddresses] = useState([
    { id: 'a1', label: 'Home (Primary)', address: 'Flat 402, Sunset Towers, Bangalore, Karnataka', coords: '12.971, 77.594' },
    { id: 'a2', label: 'Work (Google Office)', address: 'Tower C, Google Signature Road, Bangalore, Karnataka', coords: '12.912, 77.621' }
  ]);
  const [savedCards, setSavedCards] = useState([
    { id: 'c1', bank: 'Visa Premium Credit Card', number: '•••• •••• •••• 9823', expiry: '12/28' },
    { id: 'c2', bank: 'Mastercard Gold Debit', number: '•••• •••• •••• 4120', expiry: '05/29' }
  ]);
  const [favorites, setFavorites] = useState(['Organic Fresh Milk', 'Chiquita Bananas (1kg)', 'Fresh Hass Avocado (Pair)']);
  const [vipMember, setVipMember] = useState(true);
  const [vouchers, setVouchers] = useState([
    { code: 'SWISSWELCOME5', value: 5.00, minCart: 15.00, desc: 'Get $5.00 cash voucher on your first grocery basket!' },
    { code: 'FRESH10', value: 10.00, minCart: 30.00, desc: 'Flat $10.00 discount coupon on organic dairy orders.' }
  ]);

  // --- AUTHENTICATION & MFA SECURITY SYSTEM ---
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [currentUserSession, setCurrentUserSession] = useState(null); // { role: null }
  const [mfaStep, setMfaStep] = useState('credentials'); // 'credentials', 'otp'
  const [mfaRole, setMfaRole] = useState('customer');
  const [mfaPassword, setMfaPassword] = useState('');
  const [mfaOtpSentCode, setMfaOtpSentCode] = useState(null);
  const [mfaOtpInput, setMfaOtpInput] = useState('');
  const [mfaMethod, setMfaMethod] = useState('sms'); // 'sms', 'totp'
  const [totpSecretCode, setTotpSecretCode] = useState('');
  const [totpTimer, setTotpTimer] = useState(30);
  const [sessionToken, setSessionToken] = useState('');
  const [authToken, setAuthToken] = useState(localStorage.getItem('jwt_token') || '');

  // --- ADVANCED Q-COMMERCE AUTOMATION STATES ---
  const [searchVolumeMap, setSearchVolumeMap] = useState({});
  const [activeStockTransfers, setActiveStockTransfers] = useState([]);
  const [tipAmount, setTipAmount] = useState(0);
  const [esgCheckbox, setEsgCheckbox] = useState(false);
  const [totalCo2Offset, setTotalCo2Offset] = useState(1250); // in grams
  const [pickerSlaDuration, setPickerSlaDuration] = useState(3.2);
  const [pickerBadge, setPickerBadge] = useState('Standard');
  const [backupPickersCount, setBackupPickersCount] = useState(0);
  const [cartIdleTime, setCartIdleTime] = useState(0);
  const [simulateTelemetryFraud, setSimulateTelemetryFraud] = useState(false);
  const [pickingBacklogQueue, setPickingBacklogQueue] = useState(0);
  const [activePickingCongested, setActivePickingCongested] = useState(false);
  const [selectedCertRole, setSelectedCertRole] = useState('customer'); // customer, rider, picker, b2b
  const [activeTrainingRole, setActiveTrainingRole] = useState(null);
  const [trainingProgress, setTrainingProgress] = useState(0);
  const [earnedCertifications, setEarnedCertifications] = useState([]); 
  const [b2bDiscountActive, setB2bDiscountActive] = useState(false);

  // Trust Scores
  const [customerTrustScore, setCustomerTrustScore] = useState(100);
  const [riderTrustScore, setRiderTrustScore] = useState(100);
  const [pickerTrustScore, setPickerTrustScore] = useState(100);
  const [wholesalerTrustScore, setWholesalerTrustScore] = useState(100);
  const [trustLogs, setTrustLogs] = useState([
    { id: 'T0', time: new Date().toLocaleTimeString(), actor: 'system', event: 'Security trust systems initialized', delta: 0, current: 100 }
  ]);

  // Chaos & Resilience Extension States
  const [coldChainBreakdownActive, setColdChainBreakdownActive] = useState(false);
  const [wholesalerOutageActive, setWholesalerOutageActive] = useState(false);
  const [paymentOutageActive, setPaymentOutageActive] = useState(false);
  const [redisCrashActive, setRedisCrashActive] = useState(false);
  const [dbLatencyActive, setDbLatencyActive] = useState(false);
  const [riderTrafficActive, setRiderTrafficActive] = useState(false);

  // Virtual Capacity & Scaling States
  const [centralCapacity, setCentralCapacity] = useState(120);
  const [eastCapacity, setEastCapacity] = useState(120);
  const [centralScalingCount, setCentralScalingCount] = useState(0);
  const [eastScalingCount, setEastScalingCount] = useState(0);
  const [gdprTokenProbation, setGdprTokenProbation] = useState(false);

  // Onboarding applications
  const [onboardingQueue, setOnboardingQueue] = useState([
    { id: 'rid-1', name: 'Rider Dave', type: 'rider', approvals: { l1: false, l2: false, l3: false } },
    { id: 'mer-1', name: 'FreshGrocer Store', type: 'merchant', approvals: { l1: false, l2: false, l3: false } }
  ]);
  const [riderOnboardStatus, setRiderOnboardStatus] = useState('unapplied'); // unapplied, pending, active
  const [businessOnboardStatus, setBusinessOnboardStatus] = useState('unapplied');
  const [gatewayOnboardStatus, setGatewayOnboardStatus] = useState('active');

  // HITL Queue
  const [hitlQueue, setHitlQueue] = useState([]);
  const [agentMetrics, setAgentMetrics] = useState({ dailyCost: 0.0, hourlyRequestCount: 0, dailyBudgetLimit: 5.0, hourlyRequestLimit: 100 });


  // Telemetry Metrics
  const [oltpWriteLatency, setOltpWriteLatency] = useState(4);
  const [olapSyncTimer, setOlapSyncTimer] = useState(0);
  const [jwtFlash, setJwtFlash] = useState(false);
  const [vaultTimer, setVaultTimer] = useState(15);
  const [latencyHistory, setLatencyHistory] = useState([4, 6, 4, 5, 8, 4, 4]);
  const [cacheHits, setCacheHits] = useState(14);
  const [cacheMisses, setCacheMisses] = useState(2);
  const [circuitBreakerTripped, setCircuitBreakerTripped] = useState(false);
  const [rateLimitActive, setRateLimitActive] = useState(false);

  // Logs
  const [kafkaLogs, setKafkaLogs] = useState([
    { id: 'L0', time: new Date().toLocaleTimeString(), event: 'KAFKA EVENT CONSOLE ACTIVE', source: 'system', meta: 'Connected to broker-1:9092. Subscribed to telemetry.events' }
  ]);
  const [ledger, setLedger] = useState([
    { id: 'TX0', time: new Date().toLocaleTimeString(), type: 'system', ref: 'SYS-INIT', desc: 'Simulated payment processing backend initialized', debit: 0, credit: 0 }
  ]);
  const [toasts, setToasts] = useState([]);
  const [botOpen, setBotOpen] = useState(false);
  const [botInputText, setBotInputText] = useState('');
  const [botMessages, setBotMessages] = useState([
    { sender: 'bot', text: 'Hi! I am SwissBot, your AI support assistant. Need help with checkouts, orders, refunds, or shelf updates?' }
  ]);
  const [certModalOpen, setCertModalOpen] = useState(false);

  const canvasRef = useRef(null);
  const riderTimerRef = useRef(null);
  const sseRef = useRef(null); // Holds the active EventSource for rider telemetry SSE

  // Live rider GPS coordinates streamed via SSE from BFF /api/telemetry/stream/{orderId}
  const [riderCoords, setRiderCoords] = useState(null); // { lat, lng, temperature, timestamp }

  // Teardown SSE connection cleanly
  const closeSseStream = () => {
    if (sseRef.current) {
      sseRef.current.close();
      sseRef.current = null;
    }
  };

  // Cleanup on unmount
  useEffect(() => () => { closeSseStream(); clearInterval(riderTimerRef.current); }, []);

  // Helper log triggers
  const triggerToast = (msg, borderType = 'system') => {
    const id = Date.now() + Math.random();
    setToasts(prev => [...prev, { id, msg, borderType }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4500);
  };

  const logKafka = (source, event, meta) => {
    if (activeProfile.logLevel === 'error') {
      if (source !== 'admin' && !event.includes('error') && !event.includes('fail') && !event.includes('limit')) return;
    } else if (activeProfile.logLevel === 'info') {
      if (event.includes('autocomplete') || event.includes('keystroke')) return;
    }

    setKafkaLogs(prev => [
      ...prev,
      { id: 'L-' + Date.now() + '-' + Math.random(), time: new Date().toLocaleTimeString(), event: `${event.toUpperCase()}`, source, meta }
    ].slice(-40)); // Keep last 40 logs
  };

  const logLedger = (type, ref, desc, debit, credit) => {
    setLedger(prev => [
      ...prev,
      { id: 'TX-' + Date.now() + '-' + Math.random(), time: new Date().toLocaleTimeString(), type, ref, desc, debit, credit }
    ]);
  };

  const logTrust = (actor, event, delta, current) => {
    setTrustLogs(prev => [
      ...prev,
      { id: 'TL-' + Date.now(), time: new Date().toLocaleTimeString(), actor, event, delta, current }
    ]);
    logKafka('system', `${actor}.trust_score_changed`, `${event}. Delta: ${delta >= 0 ? '+' : ''}${delta}. Score: ${current}`);
  };

  const updateCustomerTrust = (delta, event) => {
    setCustomerTrustScore(prev => {
      const next = Math.max(0, Math.min(100, prev + delta));
      logTrust('customer', event, delta, next);
      return next;
    });
  };

  const updateRiderTrust = (delta, event) => {
    setRiderTrustScore(prev => {
      const next = Math.max(0, Math.min(100, prev + delta));
      logTrust('rider', event, delta, next);
      return next;
    });
  };

  const updatePickerTrust = (delta, event) => {
    setPickerTrustScore(prev => {
      const next = Math.max(0, Math.min(100, prev + delta));
      logTrust('picker', event, delta, next);
      return next;
    });
  };

  const updateWholesalerTrust = (delta, event) => {
    setWholesalerTrustScore(prev => {
      const next = Math.max(0, Math.min(100, prev + delta));
      logTrust('wholesaler', event, delta, next);
      return next;
    });
  };

  // Dynamic system timers effects
  useEffect(() => {
    let timer;
    if (!isAuthenticated) {
      const generateCode = () => {
        const timeStep = Math.floor(Date.now() / 30000);
        const code = String((timeStep * 1337) % 900000 + 100000);
        setTotpSecretCode(code);
      };
      generateCode();
      timer = setInterval(() => {
        generateCode();
        setTotpTimer(30);
      }, 30000);
    }
    return () => clearInterval(timer);
  }, [isAuthenticated]);

  useEffect(() => {
    let timer;
    if (!isAuthenticated) {
      timer = setInterval(() => {
        setTotpTimer(t => (t <= 1 ? 30 : t - 1));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [isAuthenticated]);

  // Telemetry loop effect
  useEffect(() => {
    const interval = setInterval(() => {
      setOltpWriteLatency(() => {
        const base = dbLatencyActive ? 180 : activeProfile?.dbLatencyDefault;
        return base + Math.floor(Math.random() * 8);
      });
      setVaultTimer(t => (t <= 1 ? 15 : t - 1));
      setJwtFlash(f => !f);
    }, 1500);
    return () => clearInterval(interval);
  }, [dbLatencyActive, activeProfile?.dbLatencyDefault]);

  useEffect(() => {
    const interval = setInterval(() => {
      setOlapSyncTimer(t => {
        if (t >= 7) {
          logKafka('business', 'olap.sync_complete', 'OLAP analytics database synced successfully. ETL pipeline flushed.');
          return 0;
        }
        return t + 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  const fetchAgentMetrics = () => {
    if (!authToken) return;
    fetch('http://localhost:8081/api/agent/metrics', {
      headers: {
        'Authorization': `Bearer ${authToken}`
      }
    })
      .then(res => {
        if (res.ok) return res.json();
        throw new Error('Metrics offline');
      })
      .then(data => {
        if (data && typeof data.dailyCost === 'number') {
          setAgentMetrics(data);
        }
      })
      .catch(() => {});
  };

  useEffect(() => {
    if (authToken) {
      fetchAgentMetrics();
      const interval = setInterval(fetchAgentMetrics, 4000);
      return () => clearInterval(interval);
    }
  }, [authToken]);


  // MFA Handlers
  const handleMfaSendOtp = () => {
    if (!mfaPassword) {
      triggerToast('Please enter password', 'admin');
      return;
    }

    const payload = {
      username: mfaRole === 'admin' ? 'swissadmin' : 'swissuser',
      password: mfaPassword
    };

    fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    })
    .then(async res => {
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(errText || 'Authentication failed');
      }
      return res.json();
    })
    .then(data => {
      if (data.mfaRequired) {
        setSessionToken(data.sessionToken);
        setMfaStep('otp');
        logKafka('system', 'auth.mfa_otp_sent', `MFA requested. Session token generated. Check backend stdout console for PIN.`);
        triggerToast(`MFA verification required. Please check Spring Boot console for OTP code.`, 'system');
      } else {
        // Direct login
        setIsAuthenticated(true);
        setCurrentUserSession({ role: mfaRole });
        setActiveRole(mfaRole);
        setAuthToken(data.token);
        localStorage.setItem('jwt_token', data.token);
        logKafka('system', 'auth.success', `Authenticated successfully via backend for role: ${mfaRole}`);
        triggerToast(`Welcome back, authorized ${mfaRole}!`, 'customer');
        setMfaPassword('');
      }
    })
    .catch(err => {
      logKafka('system', 'auth.failure', `Failed login credentials check: ${err.message}`);
      triggerToast(`Login Error: ${err.message}`, 'admin');
    });
  };

  const handleMfaVerify = () => {
    fetch('/api/auth/mfa/verify', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        sessionToken: sessionToken,
        code: mfaOtpInput
      })
    })
    .then(async res => {
      if (!res.ok) {
        const errText = await res.text();
        throw new Error(errText || 'MFA Verification failed');
      }
      return res.json();
    })
    .then(data => {
      setIsAuthenticated(true);
      setCurrentUserSession({ role: mfaRole });
      setActiveRole(mfaRole);
      setAuthToken(data.token);
      localStorage.setItem('jwt_token', data.token);
      logKafka('system', 'auth.success', `MFA authenticated successfully via backend for role: ${mfaRole}`);
      triggerToast(`Welcome back, authorized ${mfaRole}!`, 'customer');
      setMfaStep('credentials');
      setMfaPassword('');
      setMfaOtpInput('');
    })
    .catch(err => {
      logKafka('system', 'auth.failure', `Failed MFA verification for role: ${mfaRole}. Error: ${err.message}`);
      triggerToast(`Invalid Passcode: ${err.message}. (Note: Backend uses SMS OTP printed in stdout console)`, 'admin');
    });
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setCurrentUserSession(null);
    setMfaStep('credentials');
    setAuthToken('');
    localStorage.removeItem('jwt_token');
    logKafka('system', 'auth.session_terminated', 'Session locked. User signed out.');
    triggerToast('Session locked successfully.', 'system');
  };

  // Onboarding Handlers
  const handleApplyOnboard = (type, name) => {
    logKafka('admin', 'onboard.applied', `${name} submitted onboarding application.`);
  };

  const handleApproveOnboard = (appId, level) => {
    setOnboardingQueue(prev => prev.map(app => {
      if (app.id === appId) {
        const nextApprovals = { ...app.approvals, [level]: true };
        const active = nextApprovals.l1 && nextApprovals.l2 && nextApprovals.l3;
        
        if (active) {
          if (app.type === 'rider') {
            setRiderOnboardStatus('active');
            triggerToast('Onboarding Complete: Rider Dave is now ACTIVE!', 'rider');
            logKafka('admin', 'rider.onboarded', `Rider Dave approved across all 3 layers. System dispatch credentials generated.`);
          } else if (app.type === 'merchant') {
            setBusinessOnboardStatus('active');
            triggerToast('Onboarding Complete: Merchant is now ACTIVE!', 'business');
            logKafka('admin', 'merchant.onboarded', 'FreshGrocer Store verified. Catalog sync activated.');
          }
        } else {
          logKafka('admin', `onboard.l${level.slice(1)}_approved`, `Approved Level ${level.slice(1)} check for ${app.name}`);
        }
        return { ...app, approvals: nextApprovals };
      }
      return app;
    }));
  };

  // Interactive Checkout
  const handleCheckout = (paymentMethod) => {
    if (cart.length === 0) {
      triggerToast('Cart is empty', 'admin');
      return;
    }

    const subtotal = cart.reduce((sum, item) => sum + item.price * item.qty, 0);
    const rebate = esgCheckbox ? 0.50 : 0.00;
    const finalAmount = Math.max(0, subtotal + 2.99 + tipAmount - rebate);

    if (paymentMethod === 'Wallet' && customerWallet < finalAmount) {
      triggerToast('Insufficient Wallet balance!', 'admin');
      return;
    }

    logKafka('customer', 'order.checkout_triggered', `Checkout requested for ${cart.length} items. Total: $${finalAmount.toFixed(2)}.`);

    if (paymentMethod === 'Wallet') {
      setCustomerWallet(w => w - finalAmount);
      setMerchantWallet(w => w + finalAmount);
      logLedger('customer', 'CUST-WALLET-PAY', `Purchased groceries via Wallet`, 0, finalAmount);
    } else if (paymentOutageActive) {
      logKafka('system', 'gateway.outage', 'Swipe gateway timeout! Initiating fallback chain Swipe ➔ PayPal...');
      triggerToast('Swipe down! Falling back to PayPal gateway...', 'system');
      logLedger('customer', 'GATEWAY-FAILOVER', 'Swipe timed out. Charged via PayPal', 0, finalAmount);
    } else {
      setMerchantWallet(w => w + finalAmount);
      logLedger('customer', 'GATEWAY-CHARGE', `Swipe API charge authorization success`, 0, finalAmount);
    }

    const itemsDescription = cart.map(item => `${item.qty}x ${item.name}`).join(', ');
    const hasPerishables = cart.some(item => item.perishable);

    const nextOrder = {
      id: Math.floor(1000 + Math.random() * 9000),
      items: itemsDescription,
      total: finalAmount,
      progress: 0,
      status: 'picking',
      perishable: hasPerishables,
      temperature: hasPerishables ? 4.0 : null,
      slaRemaining: activePickingCongested ? 280 : 180
    };

    setActiveOrder(nextOrder);
    setCart([]);
    setCustomerOrderCount(c => c + 1);
    
    setPickingBacklogQueue(q => {
      const nextQ = q + 1;
      if (nextQ > 1) {
        setActivePickingCongested(true);
        setPickerSlaDuration(6.8);
      }
      return nextQ;
    });

    logKafka('inventory', 'order.received', `Order #${nextOrder.id} dispatched to picker queue.`);
    triggerToast(`Order #${nextOrder.id} placed successfully!`, 'customer');
  };

  const handlePickerHandover = () => {
    if (!activeOrder) return;

    const orderId = activeOrder.id;
    setActiveOrder(prev => ({ ...prev, status: 'transit' }));
    logKafka('rider', 'order.dispatched', `Order #${orderId} loaded on delivery cargo. Transit started.`);
    triggerToast(`Order #${orderId} handed over to Rider Dave!`, 'rider');

    setPickingBacklogQueue(q => {
      const nextQ = Math.max(0, q - 1);
      if (nextQ <= 1) {
        setActivePickingCongested(false);
        setPickerSlaDuration(2.1);
      }
      return nextQ;
    });

    const duration = pickerSlaDuration;
    if (duration < 4.0) {
      setPickerBadge('Lightning Picker');
      setMerchantWallet(w => w - 1.00);
      logLedger('system', 'PICKER-BONUS', 'Lightning Picker badge speed bonus paid', 1.00, 0);
      updatePickerTrust(10, 'Lightning picking SLA completed');
      triggerToast('Picker awarded Lightning speed bonus!', 'inventory');
    } else {
      setPickerBadge('Standard');
      updatePickerTrust(5, 'Picking completed on time');
    }

    // ── SSE: Subscribe to live BFF telemetry stream ─────────────────────────
    // Targets: BFF → /api/telemetry/stream/{orderId} (text/event-stream)
    closeSseStream(); // close any prior connection
    const sseUrl = `/api/telemetry/stream/${orderId}`;
    const es = new EventSource(sseUrl);
    sseRef.current = es;

    es.onopen = () => {
      logKafka('system', 'sse.connected', `EventSource connected to rider telemetry stream for Order #${orderId}`);
    };

    es.onmessage = (event) => {
      try {
        const tick = JSON.parse(event.data);
        // Bind streamed coordinates to React UI state for live map marker updates
        setRiderCoords({
          lat: tick.latitude,
          lng: tick.longitude,
          temperature: tick.temperature,
          timestamp: tick.timestamp || new Date().toISOString()
        });
        logKafka('rider', 'sse.tick_received', `Lat ${tick.latitude?.toFixed(4)} Lng ${tick.longitude?.toFixed(4)} Temp ${tick.temperature}°C`);
      } catch (parseErr) {
        console.warn('[SSE] Could not parse telemetry tick:', event.data);
      }
    };

    es.onerror = () => {
      // BFF not reachable — graceful silent degradation, telemetry via fetch still runs
      console.log('[SSE] Rider stream unavailable — falling back to fetch-based telemetry.');
      closeSseStream();
    };
    // ── End SSE ──────────────────────────────────────────────────────────────

    riderTimerRef.current = setInterval(() => {
      setActiveOrder(prev => {
        if (!prev) return null;
        if (prev.progress >= 100) {
          clearInterval(riderTimerRef.current);
          handleOrderDeliveryComplete(prev);
          return null;
        }

        const nextProgress = prev.progress + 10;
        let nextTemp = prev.temperature;

        if (prev.perishable && coldChainBreakdownActive) {
          nextTemp = prev.temperature + 1.8;
          if (nextTemp >= 12.0) {
            clearInterval(riderTimerRef.current);
            handlePerishablesSpoiled(prev);
            return null;
          }
        }

        // Fetch-based telemetry ingestion (push to BFF gateway)
        const lat = 47.3769 + (nextProgress * 0.0001);
        const lng = 8.5417 + (nextProgress * 0.0001);
        fetch('/api/telemetry/tick', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${authToken}`
          },
          body: JSON.stringify({
            orderId: prev.id,
            latitude: lat,
            longitude: lng,
            temperature: nextTemp || 0.0,
            dryIceInjected: false
          })
        }).catch(err => console.log('BFF Telemetry link offline:', err.message));

        return {
          ...prev,
          progress: nextProgress,
          temperature: nextTemp,
          slaRemaining: Math.max(0, prev.slaRemaining - 10)
        };
      });
    }, 1000);
  };

  const handleOrderDeliveryComplete = (order) => {
    // Close SSE stream — order lifecycle complete
    closeSseStream();
    setRiderCoords(null);

    const tipPaid = tipAmount;
    if (tipPaid > 0) {
      setRiderWallet(w => w + tipPaid);
      setCustomerWallet(w => w - tipPaid);
      logLedger('customer', 'RIDER-TIP-DEBIT', `Rider Dave coordinate tip payout`, tipPaid, 0);
      logKafka('rider', 'wallet.tip_received', `Rider Dave received tip: $${tipPaid.toFixed(2)}.`);
    }

    setRiderWallet(w => w + 5.00);
    updateRiderTrust(5, 'Order delivered successfully');
    updateCustomerTrust(5, 'Order completed without issue');

    setOrderHistory(prev => [
      { id: order.id, date: 'Today', items: order.items, total: order.total, status: 'delivered', paymentMethod: 'Wallet' },
      ...prev
    ]);

    logKafka('rider', 'order.delivered', `Order #${order.id} delivered to customer flat.`);
    triggerToast(`Order #${order.id} delivered!`, 'customer');
    setActiveOrder(null);
    setTipAmount(0);
  };

  const handlePerishablesSpoiled = (order) => {
    // Close SSE stream — order terminated due to spoilage
    closeSseStream();
    setRiderCoords(null);

    updateRiderTrust(-30, 'Perishable cargo spoilage breach');
    logLedger('system', 'COLD-BREACH-DEBIT', `Perishable write-off debit`, order.total, 0);
    logKafka('system', 'coldchain.telemetry_failure', `Order #${order.id} cargo spoiled! Temperature exceeded 12.0°C limit. Delivery canceled.`);
    triggerToast('CRITICAL: Cargo spoiled in transit! Order canceled.', 'admin');
    setActiveOrder(null);
    setTipAmount(0);
  };

  const handleInjectDryIce = () => {
    if (!activeOrder) return;
    setMerchantWallet(w => w - 2.00);
    logLedger('system', 'DRY-ICE-DEBIT', 'Rider manual coolant mitigation applied', 2.00, 0);
    
    // Trigger Real BFF Telemetry Coolant Integration
    fetch(`/api/telemetry/${activeOrder.id}/dry-ice`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${authToken}`
      }
    }).catch(err => console.log('BFF Telemetry link offline:', err.message));

    setActiveOrder(prev => ({ ...prev, temperature: 4.0 }));
    logKafka('rider', 'telemetry.mitigation_applied', 'Coolant injected: Perishable temperature reset to 4.0°C.');
    triggerToast('Dry ice coolant injected successfully!', 'rider');
  };

  const handleDeployBackupPicker = () => {
    setMerchantWallet(w => w - 10.00);
    logLedger('system', 'WAGE-DEBIT', 'Deployed backup dark store picker', 10.00, 0);
    setBackupPickersCount(c => c + 1);
    setActivePickingCongested(false);
    setPickerSlaDuration(2.1);
    triggerToast('Backup picker deployed: backlog queue cleared!', 'inventory');
  };

  const handleBalanceStores = () => {
    const centralStock = products.reduce((sum, p) => sum + p.stock, 0);
    const eastStock = products.reduce((sum, p) => sum + p.stockEast, 0);
    const delta = Math.abs(centralStock - eastStock);

    if (delta <= 3) {
      triggerToast('Stock levels already balanced across stores.', 'inventory');
      return;
    }

    const direction = centralStock > eastStock ? 'Central ➔ East' : 'East ➔ Central';
    const tr = {
      id: Date.now(),
      itemName: `Inter-Store Inventory Rebalancing (${direction})`,
      progress: 0
    };

    setActiveStockTransfers(prev => [...prev, tr]);
    logKafka('inventory', 'balancing.dispatched', `Imbalance delta of ${delta} units detected. Transfer truck dispatched.`);

    let trProgress = 0;
    const interval = setInterval(() => {
      trProgress += 20;
      setActiveStockTransfers(prev => prev.map(t => t.id === tr.id ? { ...t, progress: trProgress } : t));
      if (trProgress >= 100) {
        clearInterval(interval);
        setActiveStockTransfers(prev => prev.filter(t => t.id !== tr.id));
        
        setProducts(prev => prev.map(p => {
          const totalItem = p.stock + p.stockEast;
          const avg = Math.floor(totalItem / 2);
          return { ...p, stock: avg, stockEast: totalItem - avg };
        }));

        logKafka('inventory', 'balancing.completed', 'Stock levels balanced and synced successfully across MFC locations.');
        triggerToast('Rebalancing complete: inventory matched.', 'inventory');
      }
    }, 1000);
  };

  const handleGdprPurge = () => {
    if (window.confirm("CRITICAL WARNING: This action will permanently erase your order transaction logs under GDPR Article 17 (Right to Erasure). Financial ledger metrics will be anonymized. Continue?")) {
      setOrderHistory([]);
      setCustomerOrderCount(0);
      setCustomerRefundCount(0);
      setLedger(prev => prev.map(l => l.type === 'customer' ? { ...l, ref: 'ANONYMIZED-GDPR-CUST', desc: 'Anonymized Transaction Details (GDPR Article 17 Purge)' } : l));
      setGdprTokenProbation(true);
      updateCustomerTrust(-customerTrustScore + 75, 'GDPR Purge Probationary Score Applied (Token Anonymization)');
      logKafka('customer', 'gdpr.data_purge', 'GDPR ARTICLE 17 PURGE: Erased past customer order logs. Anonymized double-entry transaction ledgers.');
      triggerToast('GDPR Data Purge complete: Purchase history wiped.', 'admin');
    }
  };

  const handleScaleCapacity = (store) => {
    const isCentral = store === 'Central';
    const count = isCentral ? centralScalingCount : eastScalingCount;
    const fee = count === 0 ? 15.00 : count === 1 ? 25.00 : count === 2 ? 35.00 : 0;

    if (fee === 0 || count >= 3) {
      triggerToast(`${store} Capacity max scaled!`, 'admin');
      return;
    }

    setMerchantWallet(w => w - fee);
    logLedger('system', 'SYS-SCALING-DEBIT', 'Merchant rented virtual overflow warehouse bay due to capacity bottleneck', fee, 0);

    const nextCount = count + 1;
    if (isCentral) {
      setCentralCapacity(c => c + 40);
      setCentralScalingCount(nextCount);
    } else {
      setEastCapacity(e => e + 40);
      setEastScalingCount(nextCount);
    }
    logKafka('system', 'capacity_scaled', `${store} MFC Scaled: Rented overflow bay. Capacity increased by +40.`);
    triggerToast(`MANUAL SCALE: ${store} capacity expanded! (Charged $${fee})`, 'inventory');
  };

  const handleReleaseHitl = (ticket) => {
    setHitlQueue(prev => prev.filter(t => t.id !== ticket.id));
    if (ticket.type === 'b2b_funds') {
      setMerchantWallet(w => w - ticket.amount);
      logLedger('system', 'RESTOCK-FUND-RELEASE', `Restocked inventory: approved release to wholesaler`, ticket.amount, 0);
      logKafka('system', 'hitl.authorized', `Admin authorized B2B Funds: $${ticket.amount.toFixed(2)} transferred to wholesaler.`);
    } else {
      setCustomerWallet(w => w + ticket.amount);
      setMerchantWallet(w => w - ticket.amount);
      logLedger('system', 'CUSTOMER-REFUND', 'Approved support bot customer refund request', ticket.amount, 0);
      logKafka('system', 'hitl.authorized', `Admin authorized Support Bot refund of $${ticket.amount.toFixed(2)} to customer.`);
    }
  };

  const handleVoidHitl = (ticket) => {
    setHitlQueue(prev => prev.filter(t => t.id !== ticket.id));
    if (ticket.type === 'b2b_funds') {
      logKafka('admin', 'hitl.voided', `Admin VOIDED B2B procurement request for ${ticket.actionData.itemName}`);
    } else {
      logKafka('admin', 'hitl.voided', 'Admin VOIDED AI Bot customer refund request.');
    }
  };

  const runRulesEngine = (text, attachmentUrl) => {
    setTimeout(() => {
      let botResponse = `I received: "${text}". How can I help resolve this operational request?`;

      if (activeRole === 'rider') {
        if (text.toLowerCase().includes('breakdown') || text.toLowerCase().includes('accident') || attachmentUrl) {
          botResponse = `🚨 EMERGENCY DISPATCH: Vehicle breakdown/accident detected. Automatically routing backup rider to salvage cargo. Transit SLA paused. Incident report logged.`;
          updateRiderTrust(-5, 'Breakdown reported in transit');
          setHitlQueue(prev => [...prev, {
            id: 'HITL-' + Date.now(),
            type: 'rider_emergency',
            desc: 'Emergency dispatch rider salvage fee',
            amount: 15.00,
            actionData: { orderId: activeOrder?.id }
          }]);
          logKafka('rider', 'incident_reported', 'Emergency breakdown logged. Salvage protocols initiated.');
        } else if (text.toLowerCase().includes('traffic') || text.toLowerCase().includes('blocked') || text.toLowerCase().includes('delay')) {
          botResponse = `🚦 TRAFFIC ALERT: Congestion detected on primary route. Alternative GPS route generated: Take Expressway bypass. Verify perishable seal.`;
          logKafka('rider', 'rerouted', 'Expressway navigation bypass loaded in Rider GPS.');
        }
      } else if (activeRole === 'inventory' || activeRole === 'admin') {
        if (text.toLowerCase().includes('damaged') || text.toLowerCase().includes('spoiled')) {
          botResponse = `🍌 ITEM DAMAGE LOGGED: Damaged produce reported. Auto-dispatched a B2B replenishment reorder for safety. Damaged items written off.`;
          setProducts(prev => prev.map(p => p.id === 'p2' ? { ...p, stock: Math.max(0, p.stock - 2) } : p));
          logLedger('system', 'SHELF-DAMAGE', 'Spoiled bananas stock write-off', 4.50, 0);
          logKafka('inventory', 'item_damaged', 'Bananas written off due to shelf damage. Triggering B2B reorder.');
        } else if (text.toLowerCase().includes('congested') || text.toLowerCase().includes('backlog')) {
          botResponse = `📦 PICKER BACKLOG CONGESTION: Queue delays detected. Recommend deploying the Dark Store operations backup picker to restore standard speeds.`;
        }
      } else {
        if (text.toLowerCase().includes('refund')) {
          if (customerTrustScore < 65) {
            botResponse = `⛔ REFUND REFUSED: Your Customer Trust Score is below the 65-point safety threshold. Account flagged.`;
          } else if (gdprTokenProbation) {
            botResponse = `⛔ REFUND REFUSED: Your account is currently under GDPR Anonymization Probation. Automated bot refunds are blocked until completing 3 successful orders.`;
          } else if (simulateTelemetryFraud) {
            botResponse = `⛔ REFUND BLOCKED: Telemetry Correlation Audit Failed. Rider GPS logs indicate delivery took place at coordinates (12.971, 77.594) but customer profile claims geofence breach. Furthermore, uploaded photo EXIF metadata indicates timestamp mismatch. Refund request rejected.`;
            updateCustomerTrust(-25, 'Telemetry check fraud alert');
            logKafka('system', 'fraud.telemetry_failed', 'Fraud Shield Check: Telemetry correlation mismatch. GPS / EXIF validation failed. Rejected refund request for Customer.');
          } else {
            botResponse = `✅ REFUND REQUESTED: Telemetry and EXIF audits passed. Your refund request of $8.97 has been submitted. Awaiting Admin HITL approval.`;
            setHitlQueue(prev => [...prev, {
              id: 'HITL-' + Date.now(),
              type: 'customer_refund',
              desc: 'AI Support Bot customer order refund',
              amount: 8.97,
              actionData: { customerId: 'CUST-Dave' }
            }]);
            triggerToast('Support Bot filed refund request. Awaiting HITL authorization.', 'system');
          }
        }
      }

      setBotMessages(prev => [...prev, { sender: 'bot', text: botResponse }]);
    }, 1000);
  };

  const handleSendBotMessage = (attachmentUrl = null) => {
    const text = botInputText.trim();
    if (!text && !attachmentUrl) return;

    setBotMessages(prev => [...prev, { sender: 'user', text: text || 'Vision Image Uploaded', attachmentUrl }]);
    setBotInputText('');

    if (activeRole === 'customer') {
      // Connect directly to the Spring Boot Agentic backend via the BFF Gateway
      fetch('http://localhost:8081/api/agent/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': authToken ? `Bearer ${authToken}` : ''
        },
        body: JSON.stringify({
          message: text,
          conversationId: 'CONV-CUST-Dave',
          customerId: 'CUST-Dave'
        })
      })
      .then(async res => {
        if (!res.ok) {
          throw new Error('API error');
        }
        return res.json();
      })
      .then(data => {
        let reply = data.reply;
        logKafka('system', 'agent.message_processed', `Agent conversation processed. Cost: $${data.tokenCost.toFixed(5)}`);
        
        if (data.hitlStatus) {
          reply += `\n\n[HITL ESCALATION: Request routed to human operator. Ticket ID: ${data.ticketId}]`;
          triggerToast(`Low confidence score (${Math.round(data.confidenceScore * 100)}%). Escalated ticket ${data.ticketId} to HITL queue.`, 'system');
          logKafka('system', 'agent.hitl_escalated', `HITL Ticket generated: ${data.ticketId}. Reason: Confidence score low.`);
          
          // Re-fetch administrative queue to refresh the Admin view
          fetch('http://localhost:8081/api/admin/hitl', {
            headers: { 'Authorization': authToken ? `Bearer ${authToken}` : '' }
          })
          .then(r => r.json())
          .then(hitlData => {
            if (Array.isArray(hitlData)) {
              setHitlQueue(hitlData);
            }
          })
          .catch(() => {});
        }
        setBotMessages(prev => [...prev, { sender: 'bot', text: reply }]);
        fetchAgentMetrics();
      })

      .catch(() => {
        // Transparent fallback to local rule-based system if gateway/backend is not serving the agent
        runRulesEngine(text, attachmentUrl);
      });
    } else {
      runRulesEngine(text, attachmentUrl);
    }
  };

  const downloadRegulatoryReport = () => {
    let csvContent = 'data:text/csv;charset=utf-8,';
    csvContent += "ID,Time,Type,Ref Code,Description,Debit,Credit\n";
    ledger.forEach(l => {
      csvContent += `"${l.id}","${l.time}","${l.type}","${l.ref}","${l.desc}","${l.debit.toFixed(2)}","${l.credit.toFixed(2)}"\n`;
    });
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", "swiss-audit-ledger.csv");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    triggerToast('OLAP CSV Regulatory Export download successful.', 'business');
  };

  const generateCertificate = (role) => {
    setSelectedCertRole(role);
    setCertModalOpen(true);
    setTimeout(() => {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#070a13';
      ctx.fillRect(0, 0, 560, 360);
      
      ctx.strokeStyle = role === 'rider' ? '#f59e0b' : role === 'picker' ? '#3b82f6' : role === 'b2b' ? '#a855f7' : '#10b981';
      ctx.lineWidth = 10;
      ctx.strokeRect(20, 20, 520, 320);

      ctx.fillStyle = '#ffffff';
      ctx.font = 'bold 24px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('SWISS QUICK COMMERCE ACADEMY', 280, 80);

      ctx.fillStyle = 'var(--text-secondary)';
      ctx.font = '14px sans-serif';
      ctx.fillText('This certifies that the user has completed course requirements for', 280, 130);

      ctx.fillStyle = ctx.strokeStyle;
      ctx.font = 'bold 20px sans-serif';
      const roleTitle = role.toUpperCase() === 'RIDER' ? 'IoT COLD CHAIN LOGISTICS EXPERT' : role.toUpperCase() === 'PICKER' ? 'SLA PICKER COMPLIANCE SPECIALIST' : role.toUpperCase() === 'B2B' ? 'CERTIFIED WHOLESALE REPLENISHMENT LEADER' : 'LOYAL GREEN EXPRESS BUYER';
      ctx.fillText(roleTitle, 280, 180);

      ctx.fillStyle = '#ffffff';
      ctx.font = '12px monospace';
      ctx.fillText(`Certificate ID: SEC-ACAD-${Date.now()}`, 280, 240);
      ctx.fillText('HashiCorp Vault Sign Key: SHA256-VAULT-AUTHENTIC', 280, 260);

      ctx.fillStyle = 'rgba(255,255,255,0.03)';
      ctx.font = 'bold 80px sans-serif';
      ctx.fillText('VERIFIED', 280, 220);
    }, 100);
  };

  const handleDownloadCert = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const url = canvas.toDataURL('image/png');
    const link = document.createElement("a");
    link.href = url;
    link.download = `swiss-cert-${selectedCertRole}.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const hasRoleAccess = (targetRole) => {
    if (!isAuthenticated) return false;
    if (currentUserSession?.role === 'admin') return true;
    return currentUserSession?.role === targetRole;
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      
      <div className="toast-container">
        {toasts.map(t => (
          <div key={t.id} className={`toast toast-border-${t.borderType}`}>
            <Lucide.Bell size={16} className={`event-${t.borderType}`} />
            <div>{t.msg}</div>
          </div>
        ))}
      </div>

      <MfaLoginPortal 
        isAuthenticated={isAuthenticated}
        activeProfile={activeProfile}
        mfaStep={mfaStep}
        setMfaStep={setMfaStep}
        mfaRole={mfaRole}
        setMfaRole={setMfaRole}
        mfaPassword={mfaPassword}
        setMfaPassword={setMfaPassword}
        mfaOtpInput={mfaOtpInput}
        setMfaOtpInput={setMfaOtpInput}
        mfaMethod={mfaMethod}
        setMfaMethod={setMfaMethod}
        totpSecretCode={totpSecretCode}
        totpTimer={totpTimer}
        handleMfaSendOtp={handleMfaSendOtp}
        handleMfaVerify={handleMfaVerify}
      />

      <header className="app-header">
        <div className="brand-section">
          <div className="brand-logo">
            <Lucide.Zap size={22} fill="currentColor" />
            <h1 style={{ margin: 0, padding: 0, fontSize: 'inherit', fontWeight: 'inherit', display: 'inline-flex', alignItems: 'center' }}>swiss_App</h1>
          </div>
          <span className="brand-badge">Quick Commerce Architecture V1.0 (Federated MFEs)</span>
          
          <div style={{ marginLeft: '1rem', display: 'inline-flex', alignItems: 'center', gap: '0.4rem', background: 'rgba(255,255,255,0.03)', padding: '0.2rem 0.5rem', borderRadius: '6px', border: '1px solid var(--border-color)' }}>
            <Lucide.Globe size={12} className="event-system" />
            <select 
              id="select-env-profile"
              value={activeProfileKey} 
              onChange={(e) => {
                const newProfile = e.target.value;
                setActiveProfileKey(newProfile);
                triggerToast(`ENVIRONMENT SWITCHED: Loaded config for [${newProfile.toUpperCase()}] profile.`, 'system');
                logKafka('system', 'profile.switched', `Switched environment profile to ${newProfile.toUpperCase()}. Loaded variables: Rate Limit = ${envProfiles[newProfile].rateLimit} req/s, Base Latency = ${envProfiles[newProfile].dbLatencyDefault}ms, MFA = ${envProfiles[newProfile].mfaRequired ? 'Required' : 'Bypassed'}.`);
              }}
              style={{ background: 'transparent', color: 'var(--text-primary)', border: 'none', fontSize: '0.7rem', fontWeight: 'bold', fontFamily: 'var(--font-sans)', outline: 'none', cursor: 'pointer' }}
            >
              <option value="development" style={{ background: '#0b0f19', color: '#ffffff' }}>development</option>
              <option value="staging" style={{ background: '#0b0f19', color: '#ffffff' }}>staging</option>
              <option value="production" style={{ background: '#0b0f19', color: '#ffffff' }}>production</option>
            </select>
          </div>
        </div>

        <nav className="role-navigation">
          <button id="tab-customer" className={`role-tab ${activeRole === 'customer' ? 'active' : ''}`} onClick={() => setActiveRole('customer')}>
            <Lucide.ShoppingBag size={15} />
            <span>Customer Super App</span>
          </button>
          <button id="tab-rider" className={`role-tab ${activeRole === 'rider' ? 'active' : ''}`} onClick={() => setActiveRole('rider')}>
            <Lucide.Bike size={15} />
            <span>Rider Light</span>
          </button>
          <button id="tab-inventory" className={`role-tab ${activeRole === 'inventory' ? 'active' : ''}`} onClick={() => setActiveRole('inventory')}>
            <Lucide.Package size={15} />
            <span>Dark Store Inventory</span>
          </button>
          <button id="tab-business" className={`role-tab ${activeRole === 'business' ? 'active' : ''}`} onClick={() => setActiveRole('business')}>
            <Lucide.BarChart3 size={15} />
            <span>Business Console</span>
          </button>
          <button id="tab-admin" className={`role-tab ${activeRole === 'admin' ? 'active' : ''}`} onClick={() => setActiveRole('admin')}>
            <Lucide.ShieldCheck size={15} />
            <span>System Admin</span>
          </button>
          {isAuthenticated && (
            <button className="role-tab" style={{ color: 'var(--color-admin)', borderColor: 'rgba(239, 68, 68, 0.2)' }} onClick={handleLogout}>
              <Lucide.LogOut size={15} />
              <span>Lock Cockpit</span>
            </button>
          )}
        </nav>
      </header>

      <main className="cockpit-main-layout">
        
        <section className="workspace-main-panel">
          {/* ── Live Rider Tracking Panel (Global — visible on all tabs during transit) ── */}
          <RiderTrackingPanel activeOrder={activeOrder} riderCoords={riderCoords} />

          <Suspense fallback={<div className="glass-card" style={{ padding: '3rem', textAlign: 'center' }}><Lucide.Loader2 size={28} className="spin" /><p style={{ color: 'var(--text-secondary)', marginTop: '0.8rem' }}>Loading Federated Micro-Frontend...</p></div>}>
            {activeRole === 'customer' && (hasRoleAccess('customer') ? (
              <LocalErrorBoundary name="Customer App">
                <CustomerApp 
                  products={products}
                  cart={cart}
                  setCart={setCart}
                  customerWallet={customerWallet}
                  setCustomerWallet={setCustomerWallet}
                  customerPoints={customerPoints}
                  setCustomerPoints={setCustomerPoints}
                  customerTab={customerTab}
                  setCustomerTab={setCustomerTab}
                  profileSubTab={profileSubTab}
                  setProfileSubTab={setProfileSubTab}
                  savedAddresses={savedAddresses}
                  savedCards={savedCards}
                  favorites={favorites}
                  vipMember={vipMember}
                  vouchers={vouchers}
                  customerTrustScore={customerTrustScore}
                  gdprTokenProbation={gdprTokenProbation}
                  handleGdprPurge={handleGdprPurge}
                  orderHistory={orderHistory}
                  esgCheckbox={esgCheckbox}
                  setEsgCheckbox={setEsgCheckbox}
                  tipAmount={tipAmount}
                  setTipAmount={setTipAmount}
                  handleCheckout={handleCheckout}
                  activeOrder={activeOrder}
                  generateCertificate={generateCertificate}
                />
              </LocalErrorBoundary>
            ) : <RbacBlocker targetRole="customer" currentUserSession={currentUserSession} handleLogout={handleLogout} logKafka={logKafka} triggerToast={triggerToast} />)}

            {activeRole === 'rider' && (hasRoleAccess('rider') ? (
              <LocalErrorBoundary name="Rider App">
                <RiderApp 
                  riderWallet={riderWallet}
                  riderTrustScore={riderTrustScore}
                  riderOnboardStatus={riderOnboardStatus}
                  setRiderOnboardStatus={setRiderOnboardStatus}
                  activeOrder={activeOrder}
                  generateCertificate={generateCertificate}
                  coldChainBreakdownActive={coldChainBreakdownActive}
                  handleInjectDryIce={handleInjectDryIce}
                  handleApplyOnboard={handleApplyOnboard}
                  logKafka={logKafka}
                />
              </LocalErrorBoundary>
            ) : <RbacBlocker targetRole="rider" currentUserSession={currentUserSession} handleLogout={handleLogout} logKafka={logKafka} triggerToast={triggerToast} />)}

            {activeRole === 'inventory' && (hasRoleAccess('inventory') ? (
              <LocalErrorBoundary name="Inventory App">
                <InventoryApp 
                  products={products}
                  setProducts={setProducts}
                  pickerTrustScore={pickerTrustScore}
                  pickerBadge={pickerBadge}
                  activeOrder={activeOrder}
                  activeStockTransfers={activeStockTransfers}
                  handleBalanceStores={handleBalanceStores}
                  handlePickerCheckItem={null}
                  handlePickerHandover={handlePickerHandover}
                  handleDeployBackupPicker={handleDeployBackupPicker}
                  pickingBacklogQueue={pickingBacklogQueue}
                  activePickingCongested={activePickingCongested}
                />
              </LocalErrorBoundary>
            ) : <RbacBlocker targetRole="inventory" currentUserSession={currentUserSession} handleLogout={handleLogout} logKafka={logKafka} triggerToast={triggerToast} />)}

            {activeRole === 'business' && (hasRoleAccess('business') ? (
              <LocalErrorBoundary name="Business App">
                <BusinessApp 
                  products={products}
                  merchantWallet={merchantWallet}
                  ledger={ledger}
                  trustLogs={trustLogs}
                  customerTrustScore={customerTrustScore}
                  riderTrustScore={riderTrustScore}
                  pickerTrustScore={pickerTrustScore}
                  wholesalerTrustScore={wholesalerTrustScore}
                  centralCapacity={centralCapacity}
                  eastCapacity={eastCapacity}
                  centralScalingCount={centralScalingCount}
                  eastScalingCount={eastScalingCount}
                  handleScaleCapacity={handleScaleCapacity}
                  downloadRegulatoryReport={downloadRegulatoryReport}
                />
              </LocalErrorBoundary>
            ) : <RbacBlocker targetRole="business" currentUserSession={currentUserSession} handleLogout={handleLogout} logKafka={logKafka} triggerToast={triggerToast} />)}

            {activeRole === 'admin' && (hasRoleAccess('admin') ? (
              <LocalErrorBoundary name="Admin Panel">
                <AdminPanel 
                  coldChainBreakdownActive={coldChainBreakdownActive}
                  setColdChainBreakdownActive={setColdChainBreakdownActive}
                  wholesalerOutageActive={wholesalerOutageActive}
                  setWholesalerOutageActive={setWholesalerOutageActive}
                  paymentOutageActive={paymentOutageActive}
                  setPaymentOutageActive={setPaymentOutageActive}
                  redisCrashActive={redisCrashActive}
                  setRedisCrashActive={setRedisCrashActive}
                  dbLatencyActive={dbLatencyActive}
                  setDbLatencyActive={setDbLatencyActive}
                  riderTrafficActive={riderTrafficActive}
                  setRiderTrafficActive={setRiderTrafficActive}
                  simulateTelemetryFraud={simulateTelemetryFraud}
                  setSimulateTelemetryFraud={setSimulateTelemetryFraud}
                  onboardingQueue={onboardingQueue}
                  handleApproveOnboard={handleApproveOnboard}
                  hitlQueue={hitlQueue}
                  handleReleaseHitl={handleReleaseHitl}
                  handleVoidHitl={handleVoidHitl}
                />
              </LocalErrorBoundary>
            ) : <RbacBlocker targetRole="admin" currentUserSession={currentUserSession} handleLogout={handleLogout} logKafka={logKafka} triggerToast={triggerToast} />)}
          </Suspense>
        </section>

        <LocalErrorBoundary name="System Control Room">
          <Suspense fallback={<div className="engine-room-loading">Loading Telemetry Control Room...</div>}>
            <SystemEngineRoom 
              rateLimitActive={rateLimitActive}
              dbLatencyActive={dbLatencyActive}
              redisCrashActive={redisCrashActive}
              paymentOutageActive={paymentOutageActive}
              riderTrafficActive={riderTrafficActive}
              circuitBreakerTripped={circuitBreakerTripped}
              activeProfile={activeProfile}
              oltpWriteLatency={oltpWriteLatency}
              olapSyncTimer={olapSyncTimer}
              jwtFlash={jwtFlash}
              vaultTimer={vaultTimer}
              latencyHistory={latencyHistory}
              cacheHits={cacheHits}
              cacheMisses={cacheMisses}
              kafkaLogs={kafkaLogs}
              agentMetrics={agentMetrics}
            />
          </Suspense>
        </LocalErrorBoundary>



      </main>

      {certModalOpen && (
        <div className="cert-modal-overlay">
          <div className="cert-modal-content">
            <div style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h4 style={{ fontWeight: 800, color: 'var(--color-business)', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <Lucide.Sparkles size={18} />
                Swiss Loyalty Certificate Desk
              </h4>
              <button className="ai-bot-close-btn" onClick={() => setCertModalOpen(false)}>
                <Lucide.X size={18} />
              </button>
            </div>
            
            <canvas ref={canvasRef} width="560" height="360" className="cert-canvas" />
            
            <div className="cert-modal-actions">
              <button className="btn-primary-glow" style={{ background: 'var(--color-business)', color: '#ffffff', cursor: 'pointer' }} onClick={handleDownloadCert}>
                Download Certificate (.PNG)
              </button>
              <button className="btn-secondary-glow" style={{ cursor: 'pointer' }} onClick={() => setCertModalOpen(false)}>
                Dismiss Desk
              </button>
            </div>
          </div>
        </div>
      )}

      <SupportBot 
        botOpen={botOpen}
        setBotOpen={setBotOpen}
        botMessages={botMessages}
        botInputText={botInputText}
        setBotInputText={setBotInputText}
        handleSendBotMessage={handleSendBotMessage}
        triggerToast={triggerToast}
        activeRole={activeRole}
      />

    </div>
  );
}
