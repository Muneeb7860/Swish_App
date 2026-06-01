import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  Text,
  View,
  ScrollView,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  Switch,
  Animated,
  Dimensions,
  SafeAreaView,
  StatusBar,
  FlatList,
  Modal
} from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';

const { width } = Dimensions.get('window');

// -------------------------------------------------------------
// CYBER-INDUSTRIAL DESIGN TOKENS (HSL STYLE GUIDE CODES)
// -------------------------------------------------------------
const THEME = {
  bgDark: '#070a13',       // Deep industrial void
  bgBody: '#0b0f19',       // Primary panel canvas
  bgCard: 'rgba(17, 24, 39, 0.75)',  // Semi-translucent carbon matrix
  bgCardHover: '#1f2937',  // Interactive component highlight
  borderColor: 'rgba(255, 255, 255, 0.08)', // Cyber grid separator
  textPrimary: '#f8fafc',  // Neon-white readout
  textSecondary: '#94a3b8',// Dim steel secondary
  textMuted: '#64748b',    // Shadowed label slate

  // Role-Specific Accent Themes (Matching System Design Style)
  customer: '#10b981',     // Emerald Green HSL(158, 64%, 52%)
  customerGlow: 'rgba(16, 185, 129, 0.15)',
  rider: '#f59e0b',        // Amber Gold HSL(38, 92%, 50%)
  riderGlow: 'rgba(245, 158, 11, 0.15)',
  business: '#8b5cf6',     // Hyper Violet HSL(258, 90%, 66%)
  admin: '#ef4444',        // Critical Red HSL(0, 84%, 60%)
  adminGlow: 'rgba(239, 68, 68, 0.15)',
  engine: '#06b6d4',       // Core Cyan HSL(188, 86%, 53%)
  engineGlow: 'rgba(6, 182, 212, 0.15)',
  vipGold: '#fbbf24'       // Gold
};

const MOCK_PRODUCTS = [
  { item_id: 'ITEM-MILK', name: 'Fresh Alpine Milk', price: 3.49, stock: 12, category: 'Dairy & Eggs', emoji: '🥛', perishable: true },
  { item_id: 'ITEM-BANANA', name: 'Organic Bananas (1kg)', price: 1.99, stock: 18, category: 'Fruits & Veggies', emoji: '🍌', perishable: false },
  { item_id: 'ITEM-BREAD', name: 'Artisan Sourdough', price: 4.50, stock: 5, category: 'Bakery', emoji: '🍞', perishable: false },
  { item_id: 'ITEM-CHICKEN', name: 'Free-Range Chicken Breast', price: 12.99, stock: 3, category: 'Meat & Seafood', emoji: '🍗', perishable: true },
  { item_id: 'ITEM-EGGS', name: 'Pasture-Raised Eggs (12pk)', price: 5.89, stock: 15, category: 'Dairy & Eggs', emoji: '🥚', perishable: true },
  { item_id: 'ITEM-AVOCADO', name: 'Hass Avocados (4pk)', price: 6.99, stock: 0, category: 'Fruits & Veggies', emoji: '🥑', perishable: false }
];

export default function CustomerScreen() {
  // App settings & configuration state
  const [serverUrl, setServerUrl] = useState('http://localhost:8081');
  const [customerId, setCustomerId] = useState('CUST-1');
  const [jwtToken, setJwtToken] = useState('');
  const [showConfig, setShowConfig] = useState(false);

  // Authentication State
  const [username, setUsername] = useState('customer1');
  const [password, setPassword] = useState('password123');
  const [loginStep, setLoginStep] = useState('unauthenticated'); // unauthenticated, mfa_pending, authenticated
  const [mfaSessionToken, setMfaSessionToken] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [isLoadingAuth, setIsLoadingAuth] = useState(false);

  // Customer Profile & Ledger Stats
  const [trustScore, setTrustScore] = useState(100);
  const [walletBalance, setWalletBalance] = useState(100.00);
  const [loyaltyPoints, setLoyaltyPoints] = useState(45);
  const [vipMember, setVipMember] = useState(true);
  const [isOnProbation, setIsOnProbation] = useState(false);

  // Catalog, Search, and Cart States
  const [catalog, setCatalog] = useState(MOCK_PRODUCTS);
  const [searchQuery, setSearchQuery] = useState('');
  const [cart, setCart] = useState([]);
  const [tipAmount, setTipAmount] = useState(0);
  const [esgBagsRebate, setEsgBagsRebate] = useState(false);
  const [isLoadingCatalog, setIsLoadingCatalog] = useState(false);
  const [activeTab, setActiveTab] = useState('store'); // store, profile, tracker

  // Ledger and past statement history
  const [ledgerLines, setLedgerLines] = useState([]);
  const [orderHistory, setOrderHistory] = useState([]);
  const [isLoadingProfileData, setIsLoadingProfileData] = useState(false);

  // Active Order & Real-time Telemetry state
  const [activeOrder, setActiveOrder] = useState(null);
  const [telemetryLogs, setTelemetryLogs] = useState([]);
  const [riderLocation, setRiderLocation] = useState(null);
  const [cargoTemp, setCargoTemp] = useState(4.0);
  const [dryIceInjected, setDryIceInjected] = useState(false);
  const [slaTimeRemaining, setSlaTimeRemaining] = useState(540); // 9 minutes
  const [sseStatus, setSseStatus] = useState('disconnected'); // disconnected, connecting, connected
  const [isSubmittingCheckout, setIsSubmittingCheckout] = useState(false);

  // Animation values
  const glowAnim = useRef(new Animated.Value(0.4)).current;
  const slideAnim = useRef(new Animated.Value(0)).current;

  // Pulse glow effect animation
  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(glowAnim, {
          toValue: 1.0,
          duration: 1500,
          useNativeDriver: true
        }),
        Animated.timing(glowAnim, {
          toValue: 0.4,
          duration: 1500,
          useNativeDriver: true
        })
      ])
    ).start();
  }, [glowAnim]);

  // SLA Countdown Timer
  useEffect(() => {
    let interval = null;
    if (activeOrder && activeOrder.status !== 'delivered' && activeOrder.status !== 'cancelled' && activeOrder.status !== 'spoiled') {
      interval = setInterval(() => {
        setSlaTimeRemaining(prev => {
          if (prev <= 1) {
            clearInterval(interval);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [activeOrder]);

  // Load catalog on auth state change
  useEffect(() => {
    if (loginStep === 'authenticated') {
      fetchCatalog();
      fetchProfileData();
    }
  }, [loginStep]);

  // -------------------------------------------------------------
  // API REQUESTS & BFF GATEWAY INTEGRATION
  // -------------------------------------------------------------
  const getHeaders = (includeIdempotency = false) => {
    const headers = {
      'Content-Type': 'application/json',
    };
    if (jwtToken) {
      headers['Authorization'] = `Bearer ${jwtToken}`;
    }
    if (includeIdempotency) {
      headers['X-Idempotency-Key'] = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
    }
    return headers;
  };

  const handleLogin = async () => {
    if (!username || !password) {
      Alert.alert('Authentication Blocked', 'Please enter username and password credentials.');
      return;
    }
    setIsLoadingAuth(true);
    try {
      const response = await fetch(`${serverUrl}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (!response.ok) {
        throw new Error('Invalid credentials');
      }

      const data = await response.json();
      if (data.mfa_required) {
        setLoginStep('mfa_pending');
        setMfaSessionToken(data.session_token);
        Alert.alert('MFA Challenge Injected', 'Enterprise 2FA required. Please verify security OTP code.');
      } else if (data.token) {
        setJwtToken(data.token);
        setLoginStep('authenticated');
        // Parse mock claims for customer identity, or set custom identity
        setCustomerId(username === 'customer1' ? 'CUST-1' : 'CUST-2');
        Alert.alert('Handshake Succeeded', 'Secure JWT session verified and Edge verification bypass initialized.');
      }
    } catch (error) {
      console.log('Login error:', error);
      // Mock Fallback for stand-alone local sandbox testing
      Alert.alert(
        'Offline/Mock Auth Mode',
        'Backend service unreachable. Bypassing gate and loading local secure simulated context.',
        [
          {
            text: 'Initialize Standalone',
            onPress: () => {
              setJwtToken('MOCK-SECURE-JWT-TOKEN');
              setLoginStep('authenticated');
              setCustomerId('CUST-1');
            }
          },
          { text: 'Cancel', style: 'cancel' }
        ]
      );
    } finally {
      setIsLoadingAuth(false);
    }
  };

  const handleVerifyMfa = async () => {
    if (!mfaCode) {
      Alert.alert('MFA Blocked', 'Please enter your 2FA verification code.');
      return;
    }
    setIsLoadingAuth(true);
    try {
      const response = await fetch(`${serverUrl}/api/auth/mfa/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          session_token: mfaSessionToken,
          code: mfaCode
        })
      });

      if (!response.ok) {
        throw new Error('MFA verification failed');
      }

      const data = await response.json();
      setJwtToken(data.token);
      setLoginStep('authenticated');
      setCustomerId(username === 'customer1' ? 'CUST-1' : 'CUST-2');
      Alert.alert('Security Cleared', 'MFA verified. Session authorized.');
    } catch (error) {
      Alert.alert('MFA Error', 'Verification code invalid or expired. Check logs.');
    } finally {
      setIsLoadingAuth(false);
    }
  };

  const fetchCatalog = async () => {
    setIsLoadingCatalog(true);
    try {
      const response = await fetch(`${serverUrl}/api/customer/catalog`, {
        method: 'GET',
        headers: getHeaders()
      });
      if (!response.ok) throw new Error('Failed to load store catalog');
      const data = await response.json();
      if (data && data.length > 0) {
        setCatalog(data);
      }
    } catch (e) {
      console.log('Catalog fetch error, fallback to local mocks:', e);
      setCatalog(MOCK_PRODUCTS); // Fallback to styled mock assets
    } finally {
      setIsLoadingCatalog(false);
    }
  };

  const fetchProfileData = async () => {
    setIsLoadingProfileData(true);
    try {
      // 1. Fetch double-entry ledger statements
      const ledgerRes = await fetch(`${serverUrl}/api/ledger?customerId=${customerId}`, {
        headers: getHeaders()
      });
      if (ledgerRes.ok) {
        const lines = await ledgerRes.json();
        setLedgerLines(lines);
        
        // Calculate ledger-based wallet balance dynamically
        let balance = 100.00; // base credit seed
        lines.forEach(line => {
          if (line.accountType === 'customer') {
            balance += (line.credit - line.debit);
          }
        });
        setWalletBalance(balance);
      }

      // 2. Fetch past orders statements
      const ordersRes = await fetch(`${serverUrl}/api/orders?customerId=${customerId}`, {
        headers: getHeaders()
      });
      if (ordersRes.ok) {
        const orders = await ordersRes.json();
        setOrderHistory(orders);
      }
    } catch (e) {
      console.log('Error loading profile statements:', e);
    } finally {
      setIsLoadingProfileData(false);
    }
  };

  const handleCheckout = async (paymentMethod) => {
    if (cart.length === 0) {
      Alert.alert('Empty Cart', 'Add products to your cart before checking out.');
      return;
    }

    setIsSubmittingCheckout(true);
    const cartSubtotal = cart.reduce((sum, item) => sum + item.price * item.qty, 0);
    const esgRebate = esgBagsRebate ? 0.50 : 0.00;
    const deliveryFee = 2.99;
    const totalCost = Math.max(0, cartSubtotal + deliveryFee + tipAmount - esgRebate);

    // Format request payload matching OrderRequest inside OrderController.java
    const checkoutPayload = {
      customerId: customerId,
      items: cart.map(item => ({
        itemId: item.item_id,
        quantity: item.qty
      })),
      paymentMethod: paymentMethod,
      tipAmount: tipAmount,
      bagsReturned: esgBagsRebate ? 1 : 0 // Simply return 1 bag if rebate is checked
    };

    try {
      // Primary: Post to the endpoint requested by user: http://localhost:8081/api/orders/checkout
      let response;
      let url = `${serverUrl}/api/orders/checkout`;
      
      console.log('Attempting primary checkout POST to:', url);
      try {
        response = await fetch(url, {
          method: 'POST',
          headers: getHeaders(true),
          body: JSON.stringify(checkoutPayload)
        });
      } catch (err) {
        // Network/parsing fallback immediately to /api/orders
        console.log('Primary route failed, attempting fallback endpoint POST to /api/orders');
        url = `${serverUrl}/api/orders`;
        response = await fetch(url, {
          method: 'POST',
          headers: getHeaders(true),
          body: JSON.stringify(checkoutPayload)
        });
      }

      // Handle response if fallback succeeded or errored
      if (!response || response.status === 404) {
        console.log('HTTP 404/Null: Trying alternative route /api/orders...');
        response = await fetch(`${serverUrl}/api/orders`, {
          method: 'POST',
          headers: getHeaders(true),
          body: JSON.stringify(checkoutPayload)
        });
      }

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Checkout failed');
      }

      const createdOrder = await response.json();
      
      // Update UI for successfully placed order
      setActiveOrder(createdOrder);
      setSlaTimeRemaining(createdOrder.sla_countdown_sec || 540);
      setCart([]);
      setActiveTab('tracker');
      
      Alert.alert('Checkout Placed!', `Order #${createdOrder.order_id} generated. Routing Picker handover dispatch.`);
      
      // Establish SSE telemetry hook
      connectTelemetryStream(createdOrder.order_id);
      
      // Refresh wallet and statements
      fetchProfileData();

    } catch (error) {
      console.log('Checkout failed:', error);
      // Standalone simulation checkout if backend is disconnected
      Alert.alert(
        'Offline Checkout Triggered',
        'Backend service unreachable. Simulate order checkout locally in memory?',
        [
          {
            text: 'Simulate Order',
            onPress: () => {
              const mockOrderId = Math.floor(Math.random() * 9000) + 1000;
              const localMockOrder = {
                order_id: mockOrderId,
                customerId: customerId,
                total_amount: totalCost,
                weather_surcharge: 0.00,
                payment_method: paymentMethod,
                status: 'picking',
                sla_countdown_sec: 540,
                created_at: new Date().toISOString()
              };
              setActiveOrder(localMockOrder);
              setSlaTimeRemaining(540);
              setCart([]);
              setActiveTab('tracker');
              simulateLocalTelemetry(mockOrderId);
              // Deduct mock money
              setWalletBalance(prev => Math.max(0, prev - totalCost));
              setOrderHistory(prev => [
                {
                  order_id: mockOrderId,
                  total_amount: totalCost,
                  status: 'picking',
                  created_at: new Date().toISOString()
                },
                ...prev
              ]);
            }
          },
          { text: 'Cancel', style: 'cancel' }
        ]
      );
    } finally {
      setIsSubmittingCheckout(false);
    }
  };

  const handleGdprPurge = async () => {
    Alert.alert(
      'GDPR Article 17 Purge',
      'This will anonymize all PII (email, addresses, cards) and place your account under probationary status. Proceed?',
      [
        {
          text: 'Confirm Erasure',
          style: 'destructive',
          onPress: async () => {
            try {
              const response = await fetch(`${serverUrl}/api/customer/profile/purge?customerId=${customerId}`, {
                method: 'POST',
                headers: getHeaders()
              });
              if (!response.ok) throw new Error('Purge failed');
              const data = await response.json();
              
              setTrustScore(data.probationary_trust_score || 75);
              setIsOnProbation(true);
              Alert.alert('GDPR Purge Completed', 'PII sanitized. Account is now on probationary status.');
              fetchProfileData();
            } catch (e) {
              console.log('GDPR Purge error, using local fallback:', e);
              // Local mock sanitization
              setTrustScore(75);
              setIsOnProbation(true);
              setLedgerLines([]);
              setOrderHistory([]);
              Alert.alert('Sanitized (Offline Mode)', 'Hashed probation identity set.');
            }
          }
        },
        { text: 'Cancel', style: 'cancel' }
      ]
    );
  };

  // -------------------------------------------------------------
  // SSE TELEMETRY STREAM & GPS HANDLER
  // -------------------------------------------------------------
  const sseXhr = useRef(null);

  const connectTelemetryStream = (orderId) => {
    if (sseXhr.current) {
      sseXhr.current.abort();
    }

    setSseStatus('connecting');
    setTelemetryLogs([]);
    console.log(`Connecting SSE to: ${serverUrl}/api/telemetry/stream/${orderId}`);

    try {
      const xhr = new XMLHttpRequest();
      sseXhr.current = xhr;
      
      xhr.open('GET', `${serverUrl}/api/telemetry/stream/${orderId}`);
      if (jwtToken) {
        xhr.setRequestHeader('Authorization', `Bearer ${jwtToken}`);
      }
      xhr.setRequestHeader('Accept', 'text/event-stream');

      let seenBytes = 0;
      
      xhr.onreadystatechange = () => {
        if (xhr.readyState === 3 || xhr.readyState === 4) {
          setSseStatus('connected');
          const responseText = xhr.responseText;
          const newData = responseText.substring(seenBytes);
          seenBytes = responseText.length;

          // Split responseText into chunked lines
          const lines = newData.split('\n');
          let currentEvent = null;
          
          lines.forEach(line => {
            const trimmed = line.trim();
            if (trimmed.startsWith('event:')) {
              currentEvent = trimmed.substring(6).trim();
            } else if (trimmed.startsWith('data:')) {
              const dataStr = trimmed.substring(5).trim();
              try {
                const payload = JSON.parse(dataStr);
                handleTelemetryTick(payload);
              } catch (e) {
                console.log('Error parsing telemetry SSE data:', dataStr, e);
              }
              currentEvent = null;
            }
          });
        }
      };

      xhr.onerror = (err) => {
        console.log('SSE connection error:', err);
        setSseStatus('disconnected');
      };

      xhr.onloadend = () => {
        setSseStatus('disconnected');
        console.log('SSE connection closed.');
      };

      xhr.send();

    } catch (e) {
      console.log('Failed to initialize SSE connection:', e);
      setSseStatus('disconnected');
    }
  };

  const handleTelemetryTick = (payload) => {
    // Expected fields: latitude, longitude, temperature, dryIceInjected, alertTriggered, timestamp
    setRiderLocation({
      latitude: parseFloat(payload.latitude) || 47.3769,
      longitude: parseFloat(payload.longitude) || 8.5417
    });
    setCargoTemp(parseFloat(payload.temperature) || 4.0);
    setDryIceInjected(payload.dryIceInjected || false);
    
    // Add to telemetry log buffer
    setTelemetryLogs(prev => [
      {
        time: new Date(payload.timestamp || Date.now()).toLocaleTimeString(),
        temp: payload.temperature,
        lat: payload.latitude,
        lng: payload.longitude,
        dryIce: payload.dryIceInjected
      },
      ...prev.slice(0, 10) // Keep latest 10 ticks
    ]);

    // Update active order state if status changed
    if (payload.status) {
      setActiveOrder(prev => ({
        ...prev,
        status: payload.status
      }));
    }
  };

  // Local Offline Simulation loop
  const simulationTimer = useRef(null);
  
  const simulateLocalTelemetry = (orderId) => {
    if (simulationTimer.current) {
      clearInterval(simulationTimer.current);
    }
    
    setSseStatus('connected');
    let tickCount = 0;
    let currentTemp = 4.2;
    let iceInjected = false;

    // Simulate coordinates starting from Zurich Dark Store (47.37, 8.54) towards client (47.39, 8.56)
    let lat = 47.3769;
    let lng = 8.5417;

    simulationTimer.current = setInterval(() => {
      tickCount++;
      
      // Update simulated order status based on timeframe
      let simulatedStatus = 'picking';
      if (tickCount > 3 && tickCount <= 8) simulatedStatus = 'picked';
      if (tickCount > 8 && tickCount <= 25) simulatedStatus = 'shipping';
      if (tickCount > 25) simulatedStatus = 'delivered';

      // Slowly heat up perishable cargo if not cooled
      if (simulatedStatus === 'shipping' && !iceInjected) {
        currentTemp += 0.8; // heat spike
      }

      // Add a small location delta
      if (simulatedStatus === 'shipping') {
        lat += 0.001;
        lng += 0.001;
      }

      const payload = {
        orderId: orderId,
        latitude: lat.toFixed(5),
        longitude: lng.toFixed(5),
        temperature: currentTemp.toFixed(1),
        dryIceInjected: iceInjected,
        timestamp: new Date().toISOString(),
        status: simulatedStatus
      };

      handleTelemetryTick(payload);

      if (simulatedStatus === 'delivered') {
        clearInterval(simulationTimer.current);
        setSseStatus('disconnected');
        Alert.alert('Delivery Complete!', 'Simulated rider arrived. Ledger balance adjusted.');
      }
    }, 4000);
  };

  const triggerLocalCoolantInjection = () => {
    // If backend is live, POST to the coolant endpoint
    if (jwtToken && jwtToken !== 'MOCK-SECURE-JWT-TOKEN') {
      fetch(`${serverUrl}/api/telemetry/${activeOrder.order_id}/dry-ice`, {
        method: 'POST',
        headers: getHeaders()
      })
      .then(res => {
        if (!res.ok) throw new Error('Injection failed');
        return res.json();
      })
      .then(data => {
        Alert.alert('Cooling Succeeded', 'Dry ice injected via API. Temperature stabilized.');
        // Refresh local balance since dry ice coolant charges the system
        fetchProfileData();
      })
      .catch(err => {
        Alert.alert('Coolant API Error', 'Unable to execute dry-ice injection downstream.');
      });
    } else {
      // Local mockup toggle
      setCargoTemp(4.0);
      setDryIceInjected(true);
      setWalletBalance(prev => Math.max(0, prev - 2.00)); // Charge client $2.00 for dry ice
      setLedgerLines(prev => [
        {
          lineId: Math.floor(Math.random() * 10000),
          entry_id: 999,
          accountType: 'customer',
          debit: 2.00,
          credit: 0.00
        },
        ...prev
      ]);
      Alert.alert('Coolant Injected', 'Dry ice loaded into cargo locker. Perishable temperature reset to 4.0°C. Charge of $2.00 committed to ledger.');
    }
  };

  useEffect(() => {
    return () => {
      if (simulationTimer.current) clearInterval(simulationTimer.current);
      if (sseXhr.current) sseXhr.current.abort();
    };
  }, []);

  // -------------------------------------------------------------
  // RENDERING UTILITIES
  // -------------------------------------------------------------
  const addToCart = (product) => {
    if (product.stock === 0) {
      Alert.alert('Stock Outage', 'Wholesaler rebalancing triggered. This item will be restocked shortly.');
      return;
    }
    setCart(prev => {
      const existing = prev.find(item => item.item_id === product.item_id);
      if (existing) {
        return prev.map(item => item.item_id === product.item_id ? { ...item, qty: item.qty + 1 } : item);
      }
      return [...prev, { ...product, qty: 1 }];
    });
  };

  const removeFromCart = (itemId) => {
    setCart(prev => prev.filter(item => item.item_id !== itemId));
  };

  const cartSubtotal = cart.reduce((sum, item) => sum + item.price * item.qty, 0);
  const esgRebate = esgBagsRebate ? 0.50 : 0.00;
  const deliveryFee = 2.99;
  const totalCost = Math.max(0, cartSubtotal + deliveryFee + tipAmount - esgRebate);

  const getSlaTimerText = () => {
    const min = Math.floor(slaTimeRemaining / 60);
    const sec = slaTimeRemaining % 60;
    return `${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`;
  };

  // Filter products by category or search term
  const filteredCatalog = catalog.filter(p =>
    p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    p.category.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // -------------------------------------------------------------
  // CORE AUTHENTICATION LOGIN VIEWS
  // -------------------------------------------------------------
  if (loginStep === 'unauthenticated' || loginStep === 'mfa_pending') {
    return (
      <SafeAreaView style={styles.authContainer}>
        <StatusBar barStyle="light-content" backgroundColor={THEME.bgDark} />
        <ScrollView contentContainerStyle={styles.authScroll}>
          {/* Cyber Brand Logo */}
          <View style={styles.brandContainer}>
            <View style={styles.brandIconContainer}>
              <MaterialCommunityIcons name="cube-send" size={38} color={THEME.customer} />
            </View>
            <Text style={styles.brandName}>SWISS <Text style={{ color: THEME.customer }}>QUICK</Text></Text>
            <Text style={styles.brandSub}>Q-Commerce Cybernet Terminal</Text>
          </View>

          {/* Configuration Settings Button */}
          <TouchableOpacity 
            style={styles.configToggleBtn}
            onPress={() => setShowConfig(!showConfig)}
          >
            <MaterialCommunityIcons name="cog" size={16} color={THEME.textSecondary} />
            <Text style={styles.configToggleText}>
              {showConfig ? 'Hide Network Node Configuration' : 'Show Network Node Configuration'}
            </Text>
          </TouchableOpacity>

          {showConfig && (
            <View style={styles.configCard}>
              <Text style={styles.configHeader}>BFF GATEWAY ROUTE</Text>
              <TextInput
                style={styles.authInput}
                value={serverUrl}
                onChangeText={setServerUrl}
                placeholder="http://localhost:8081"
                placeholderTextColor={THEME.textMuted}
                autoCapitalize="none"
              />
              <Text style={[styles.configHeader, { marginTop: 10 }]}>DIRECT SECURE JWT INJECTION</Text>
              <TextInput
                style={[styles.authInput, { height: 60 }]}
                value={jwtToken}
                onChangeText={(token) => {
                  setJwtToken(token);
                  if (token) setLoginStep('authenticated');
                }}
                multiline
                placeholder="Bearer eyJhbGci..."
                placeholderTextColor={THEME.textMuted}
                autoCapitalize="none"
              />
            </View>
          )}

          {loginStep === 'unauthenticated' ? (
            <View style={styles.authCard}>
              <Text style={styles.authTitle}>SECURE PORTAL AUTHENTICATION</Text>
              
              <Text style={styles.inputLabel}>OPERATIONAL USER SUBJECT</Text>
              <TextInput
                style={styles.authInput}
                value={username}
                onChangeText={setUsername}
                placeholder="Username (e.g. customer1)"
                placeholderTextColor={THEME.textMuted}
                autoCapitalize="none"
              />

              <Text style={styles.inputLabel}>ACCESS CREDENTIALS</Text>
              <TextInput
                style={styles.authInput}
                value={password}
                onChangeText={setPassword}
                secureTextEntry
                placeholder="Password"
                placeholderTextColor={THEME.textMuted}
                autoCapitalize="none"
              />

              <TouchableOpacity 
                style={styles.authSubmitBtn}
                onPress={handleLogin}
                disabled={isLoadingAuth}
              >
                {isLoadingAuth ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.authSubmitBtnText}>VERIFY SECURITY CREDENTIALS</Text>
                )}
              </TouchableOpacity>
            </View>
          ) : (
            <View style={styles.authCard}>
              <Text style={styles.authTitle}>ENTERPRISE MFA SECURITY SHIELD</Text>
              <Text style={styles.mfaSubtitle}>
                A verification one-time passcode has been sent. Input the 2FA token to complete JWT generation.
              </Text>
              
              <Text style={styles.inputLabel}>6-DIGIT MFA SECURITY CODE</Text>
              <TextInput
                value={mfaCode}
                onChangeText={setMfaCode}
                keyboardType="number-pad"
                maxLength={6}
                placeholder="000000"
                placeholderTextColor={THEME.textMuted}
                textAlign="center"
                style={[styles.authInput, { fontSize: 24, letterSpacing: 10, fontWeight: 'bold' }]}
              />

              <TouchableOpacity 
                style={styles.authSubmitBtn}
                onPress={handleVerifyMfa}
                disabled={isLoadingAuth}
              >
                {isLoadingAuth ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.authSubmitBtnText}>CONFIRM SECURITY PASSCODE</Text>
                )}
              </TouchableOpacity>

              <TouchableOpacity 
                style={styles.mfaCancelBtn}
                onPress={() => setLoginStep('unauthenticated')}
              >
                <Text style={styles.mfaCancelBtnText}>Cancel and Re-authenticate</Text>
              </TouchableOpacity>
            </View>
          )}
        </ScrollView>
      </SafeAreaView>
    );
  }

  // -------------------------------------------------------------
  // CUSTOMER DASHBOARD & WORKSPACE VIEW
  // -------------------------------------------------------------
  return (
    <SafeAreaView style={styles.appContainer}>
      <StatusBar barStyle="light-content" backgroundColor={THEME.bgDark} />
      
      {/* 1. Header Bar with System status */}
      <View style={styles.headerBar}>
        <View style={styles.headerBrandRow}>
          <MaterialCommunityIcons name="cube-send" size={24} color={THEME.customer} />
          <Text style={styles.headerTitle}>SWISS <Text style={{ color: THEME.customer }}>QUICK</Text></Text>
          <View style={styles.networkStatusIndicator}>
            <View style={[styles.statusDot, { backgroundColor: sseStatus === 'connected' ? THEME.customer : THEME.admin }]} />
            <Text style={styles.networkStatusText}>{sseStatus.toUpperCase()}</Text>
          </View>
        </View>
        
        <TouchableOpacity 
          style={styles.headerSettingsIcon}
          onPress={() => setShowConfig(!showConfig)}
        >
          <MaterialCommunityIcons name="server-network" size={20} color={THEME.textSecondary} />
        </TouchableOpacity>
      </View>

      {/* Interactive Endpoint Adjuster */}
      {showConfig && (
        <View style={styles.endpointDropdown}>
          <Text style={styles.endpointLabel}>ACTIVE BFF GATEWAY NODE:</Text>
          <TextInput
            style={styles.endpointInput}
            value={serverUrl}
            onChangeText={setServerUrl}
            placeholder="http://localhost:8081"
            placeholderTextColor={THEME.textMuted}
            autoCapitalize="none"
          />
          <View style={styles.endpointActions}>
            <TouchableOpacity 
              style={styles.endpointRefreshBtn}
              onPress={() => {
                fetchCatalog();
                fetchProfileData();
                Alert.alert('System Sync', 'Catalog & Ledger statements re-synchronized.');
              }}
            >
              <Text style={styles.endpointRefreshText}>Sync Cache</Text>
            </TouchableOpacity>
            <TouchableOpacity 
              style={[styles.endpointRefreshBtn, { backgroundColor: THEME.adminGlow }]}
              onPress={() => {
                setJwtToken('');
                setLoginStep('unauthenticated');
              }}
            >
              <Text style={[styles.endpointRefreshText, { color: THEME.admin }]}>Disconnect Session</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}

      {/* 2. Top Segment Tab Selectors */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tabButton, activeTab === 'store' && styles.tabButtonActive]}
          onPress={() => setActiveTab('store')}
        >
          <MaterialCommunityIcons name="storefront-outline" size={18} color={activeTab === 'store' ? THEME.customer : THEME.textSecondary} />
          <Text style={[styles.tabButtonText, activeTab === 'store' && styles.tabButtonTextActive]}>Store Catalog</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.tabButton, activeTab === 'profile' && styles.tabButtonActive]}
          onPress={() => setActiveTab('profile')}
        >
          <MaterialCommunityIcons name="account-circle-outline" size={18} color={activeTab === 'profile' ? THEME.customer : THEME.textSecondary} />
          <Text style={[styles.tabButtonText, activeTab === 'profile' && styles.tabButtonTextActive]}>Profile Statement</Text>
        </TouchableOpacity>

        {activeOrder && (
          <TouchableOpacity
            style={[styles.tabButton, activeTab === 'tracker' && styles.tabButtonActive]}
            onPress={() => setActiveTab('tracker')}
          >
            <Animated.View style={{ opacity: sseStatus === 'connected' ? glowAnim : 1 }}>
              <MaterialCommunityIcons name="radar" size={18} color={activeTab === 'tracker' ? THEME.customer : THEME.rider} />
            </Animated.View>
            <Text style={[styles.tabButtonText, activeTab === 'tracker' && { color: THEME.rider }]}>Track Order</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* 3. Main Scrollable Container */}
      <ScrollView contentContainerStyle={styles.scrollContent}>
        
        {/* A. PRODUCT SHELF VIEW */}
        {activeTab === 'store' && (
          <View>
            {/* Search Input bar */}
            <View style={styles.searchBarContainer}>
              <MaterialCommunityIcons name="magnify" size={20} color={THEME.textMuted} style={styles.searchIcon} />
              <TextInput
                style={styles.searchInput}
                value={searchQuery}
                onChangeText={setSearchQuery}
                placeholder="Search organic grocery, fresh dairy, bakery..."
                placeholderTextColor={THEME.textMuted}
              />
            </View>

            {/* Catalog list header */}
            <Text style={styles.sectionHeader}>LIGHTNING CATALOG</Text>

            {isLoadingCatalog ? (
              <ActivityIndicator size="large" color={THEME.customer} style={{ marginTop: 40 }} />
            ) : (
              <View style={styles.gridContainer}>
                {filteredCatalog.map(item => (
                  <View key={item.item_id} style={styles.productCard}>
                    {item.perishable && (
                      <View style={styles.perishableTag}>
                        <Text style={styles.perishableText}>COLD CHAIN</Text>
                      </View>
                    )}
                    
                    <Text style={styles.productEmoji}>{item.emoji}</Text>
                    <Text style={styles.productName} numberOfLines={1}>{item.name}</Text>
                    <Text style={styles.productCategory}>{item.category}</Text>
                    
                    <View style={styles.productFooter}>
                      <Text style={styles.productPrice}>${item.price.toFixed(2)}</Text>
                      
                      <TouchableOpacity 
                        style={styles.addToCartBtn}
                        onPress={() => addToCart(item)}
                      >
                        <MaterialCommunityIcons name="plus" size={16} color={THEME.customer} />
                      </TouchableOpacity>
                    </View>
                    
                    <Text style={[
                      styles.productStock, 
                      { color: item.stock === 0 ? THEME.admin : item.stock < 5 ? THEME.rider : THEME.textMuted }
                    ]}>
                      {item.stock === 0 ? 'RESTOCKING' : `Stock: ${item.stock} units`}
                    </Text>
                  </View>
                ))}
              </View>
            )}
          </View>
        )}

        {/* B. PROFILE STATEMENT & LEDGER AUDIT VIEW */}
        {activeTab === 'profile' && (
          <View>
            {/* Trust Shield Profile Header Card */}
            <View style={styles.trustShieldCard}>
              <View style={styles.trustScoreCol}>
                <Text style={styles.trustScoreTitle}>AUDITED TRUST RATING</Text>
                <Text style={[styles.trustScoreVal, { color: trustScore >= 75 ? THEME.customer : THEME.admin }]}>
                  {trustScore}/100
                </Text>
                {isOnProbation && (
                  <Text style={styles.probationWarningText}>⚠️ PROBATION IDENTITY ACTIVE</Text>
                )}
              </View>
              <View style={styles.loyaltyCol}>
                <View style={styles.loyaltyPointRow}>
                  <MaterialCommunityIcons name="crown" size={20} color={THEME.vipGold} />
                  <Text style={styles.vipBadgeText}>VIP MEMBER</Text>
                </View>
                <Text style={styles.loyaltyVal}>{loyaltyPoints} Points</Text>
                <Text style={styles.walletVal}>Wallet: ${walletBalance.toFixed(2)}</Text>
              </View>
            </View>

            {/* GDPR & GDPR PURGE BUTTON */}
            <View style={styles.gdprActionContainer}>
              <Text style={styles.gdprText}>GDPR Article 17 Shield</Text>
              <TouchableOpacity 
                style={styles.gdprPurgeBtn}
                onPress={handleGdprPurge}
              >
                <Text style={styles.gdprPurgeBtnText}>PURGE PROFILE STATEMENT</Text>
              </TouchableOpacity>
            </View>

            {/* Ledger Audit statements list */}
            <Text style={styles.sectionHeader}>TAMPER-EVIDENT JOURNAL LEDGER LOGS</Text>
            {isLoadingProfileData ? (
              <ActivityIndicator size="small" color={THEME.customer} style={{ marginTop: 20 }} />
            ) : ledgerLines.length === 0 ? (
              <View style={styles.emptyContainer}>
                <MaterialCommunityIcons name="shield-lock-outline" size={32} color={THEME.textMuted} />
                <Text style={styles.emptyText}>No double-entry statements loaded.</Text>
              </View>
            ) : (
              ledgerLines.map(line => (
                <View key={line.lineId || Math.random().toString()} style={styles.ledgerCard}>
                  <View style={styles.ledgerHeaderRow}>
                    <Text style={styles.ledgerCode}>ENTRY ID: #{line.entryId}</Text>
                    <Text style={[
                      styles.ledgerRef, 
                      { color: line.debit > 0 ? THEME.admin : THEME.customer }
                    ]}>
                      {line.debit > 0 ? 'DEBIT LEG' : 'CREDIT LEG'}
                    </Text>
                  </View>
                  <View style={styles.ledgerAmountRow}>
                    <Text style={styles.ledgerAccount}>Account: {line.accountType.toUpperCase()}</Text>
                    <Text style={styles.ledgerCash}>
                      {line.debit > 0 ? `-$${line.debit.toFixed(2)}` : `+$${line.credit.toFixed(2)}`}
                    </Text>
                  </View>
                </View>
              ))
            )}

            {/* Past orders checklist statements */}
            <Text style={[styles.sectionHeader, { marginTop: 20 }]}>ORDER TRANSACTIONS LOGS</Text>
            {orderHistory.length === 0 ? (
              <View style={styles.emptyContainer}>
                <Text style={styles.emptyText}>No order logs registered.</Text>
              </View>
            ) : (
              orderHistory.map(order => (
                <View key={order.order_id || order.orderId} style={styles.pastOrderCard}>
                  <View style={styles.pastOrderHeader}>
                    <Text style={styles.pastOrderId}>ORDER ID: #{order.order_id || order.orderId}</Text>
                    <Text style={styles.pastOrderDate}>{new Date(order.created_at || Date.now()).toLocaleDateString()}</Text>
                  </View>
                  <View style={styles.pastOrderFooter}>
                    <Text style={styles.pastOrderPrice}>Total: ${order.total_amount.toFixed(2)}</Text>
                    <Text style={[styles.pastOrderStatusText, { color: THEME.customer }]}>{order.status.toUpperCase()}</Text>
                  </View>
                </View>
              ))
            )}
          </View>
        )}

        {/* C. ACTIVE ORDER TRACKER VIEW */}
        {activeTab === 'tracker' && activeOrder && (
          <View>
            {/* Visual stopwatch countdown */}
            <View style={styles.slaStopwatchCard}>
              <Text style={styles.stopwatchTitle}>SLA GUARANTEE COUNTDOWN</Text>
              <View style={styles.stopwatchDisplay}>
                <MaterialCommunityIcons name="clock-outline" size={28} color={slaTimeRemaining < 120 ? THEME.admin : THEME.customer} />
                <Text style={[
                  styles.stopwatchDigits,
                  slaTimeRemaining < 120 && { color: THEME.admin }
                ]}>
                  {getSlaTimerText()}
                </Text>
              </View>
              <Text style={styles.stopwatchDisclaimer}>Order ETA violation triggers instant automated ledger refund.</Text>
            </View>

            {/* Outage and thermal spike warning banners */}
            {cargoTemp > 8.0 && (
              <View style={styles.criticalBreachBanner}>
                <MaterialCommunityIcons name="alert-decagram" size={20} color="#fff" />
                <View style={{ flex: 1, marginLeft: 10 }}>
                  <Text style={styles.breachTitle}>🚨 PERISHABLE TEMPERATURE BREACH</Text>
                  <Text style={styles.breachDesc}>Transit cooling breakdown! Current cargo temp: {cargoTemp}°C. Stabilize core temperature.</Text>
                </View>
                <TouchableOpacity 
                  style={styles.breachActionBtn}
                  onPress={triggerLocalCoolantInjection}
                >
                  <Text style={styles.breachActionText}>INJECT DRY ICE</Text>
                </TouchableOpacity>
              </View>
            )}

            {dryIceInjected && (
              <View style={[styles.criticalBreachBanner, { backgroundColor: 'rgba(16, 185, 129, 0.25)', borderColor: THEME.customer }]}>
                <MaterialCommunityIcons name="snowflake" size={20} color={THEME.customer} />
                <View style={{ flex: 1, marginLeft: 10 }}>
                  <Text style={[styles.breachTitle, { color: THEME.customer }]}>❄️ COOLANT MITIGATION DEPLOYED</Text>
                  <Text style={styles.breachDesc}>Dry-Ice injection active. Transit temperature stabilized at 4.0°C.</Text>
                </View>
              </View>
            )}

            {/* GPS Telemetry telemetry coordinates mapping display */}
            <View style={styles.telemetryMapCard}>
              <View style={styles.mapHeaderRow}>
                <Text style={styles.mapTitle}>IOT GPS ROUTE MONITOR</Text>
                <View style={styles.mapIndicatorRow}>
                  <View style={[styles.statusDot, { backgroundColor: sseStatus === 'connected' ? THEME.customer : THEME.textMuted }]} />
                  <Text style={styles.mapStatus}>{sseStatus.toUpperCase()}</Text>
                </View>
              </View>

              {/* Graphical GPS coordinates grid panel */}
              <View style={styles.mapGraphicGrid}>
                <MaterialCommunityIcons name="store" size={24} color={THEME.customer} style={styles.mapStoreIcon} />
                <View style={styles.mapProgressLineContainer}>
                  <View style={[styles.mapProgressLine, { width: activeOrder.status === 'delivered' ? '100%' : '50%' }]} />
                  {activeOrder.status !== 'delivered' && (
                    <Animated.View style={[styles.mapRiderDot, { opacity: glowAnim }]}>
                      <MaterialCommunityIcons name="bike-fast" size={20} color={THEME.rider} />
                    </Animated.View>
                  )}
                </View>
                <MaterialCommunityIcons name="home-map-marker" size={24} color={THEME.admin} style={styles.mapHomeIcon} />
              </View>

              <View style={styles.mapReadoutRow}>
                <View style={styles.readoutCol}>
                  <Text style={styles.readoutLabel}>RIDER GPS POSITION</Text>
                  <Text style={styles.readoutValue}>
                    {riderLocation ? `${riderLocation.latitude}, ${riderLocation.longitude}` : '47.3769, 8.5417'}
                  </Text>
                </View>
                <View style={styles.readoutCol}>
                  <Text style={styles.readoutLabel}>CARGO SENSOR TEMP</Text>
                  <Text style={[styles.readoutValue, { color: cargoTemp > 8.0 ? THEME.admin : THEME.customer }]}>
                    {cargoTemp}°C
                  </Text>
                </View>
              </View>
            </View>

            {/* Live streaming ticks readout logs */}
            <Text style={styles.sectionHeader}>TELEMETRY TICK BUFFER</Text>
            {telemetryLogs.length === 0 ? (
              <View style={styles.emptyContainer}>
                <Text style={styles.emptyText}>Awaiting telemetry streaming handshake...</Text>
              </View>
            ) : (
              telemetryLogs.map((log, idx) => (
                <View key={idx} style={styles.telemetryTickRow}>
                  <Text style={styles.tickTime}>{log.time}</Text>
                  <Text style={styles.tickLoc}>Coords: {log.lat}, {log.lng}</Text>
                  <Text style={[styles.tickTemp, { color: log.temp > 8.0 ? THEME.admin : THEME.customer }]}>
                    {log.temp}°C
                  </Text>
                  {log.dryIce && (
                    <MaterialCommunityIcons name="snowflake" size={12} color={THEME.engine} />
                  )}
                </View>
              ))
            )}
          </View>
        )}

      </ScrollView>

      {/* 4. Bottom Cart Drawer / Summary bar (Only shown when cart is not empty and not in tracker tab) */}
      {cart.length > 0 && activeTab !== 'tracker' && (
        <View style={styles.cartDrawer}>
          <View style={styles.cartHeaderRow}>
            <View style={styles.cartCountCol}>
              <MaterialCommunityIcons name="shopping" size={20} color={THEME.customer} />
              <Text style={styles.cartDrawerCount}>SHOPPING CART ({cart.reduce((sum, item) => sum + item.qty, 0)})</Text>
            </View>
            <TouchableOpacity onPress={() => setCart([])}>
              <Text style={styles.clearCartText}>Clear</Text>
            </TouchableOpacity>
          </View>

          {/* Cart items horizontal scroller */}
          <ScrollView horizontal style={styles.cartItemsScroll} contentContainerStyle={{ gap: 8 }}>
            {cart.map(item => (
              <View key={item.item_id} style={styles.cartItemPill}>
                <Text style={styles.cartItemEmoji}>{item.emoji}</Text>
                <Text style={styles.cartItemName}>{item.name} x{item.qty}</Text>
                <TouchableOpacity onPress={() => removeFromCart(item.item_id)}>
                  <MaterialCommunityIcons name="close-circle" size={16} color={THEME.textMuted} />
                </TouchableOpacity>
              </View>
            ))}
          </ScrollView>

          {/* Tip Selector */}
          <View style={styles.tipSection}>
            <Text style={styles.tipHeader}>ADD DRIVER TIP (STABILIZE DISPATCH RATE)</Text>
            <View style={styles.tipGrid}>
              {[0, 2, 5, 10].map(amt => (
                <TouchableOpacity
                  key={amt}
                  style={[styles.tipBtn, tipAmount === amt && styles.tipBtnActive]}
                  onPress={() => setTipAmount(amt)}
                >
                  <Text style={[styles.tipBtnText, tipAmount === amt && styles.tipBtnTextActive]}>
                    {amt === 0 ? 'No Tip' : `$${amt}`}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          {/* ESG Return bags checkbox */}
          <View style={styles.esgCheckboxRow}>
            <Switch
              value={esgBagsRebate}
              onValueChange={setEsgBagsRebate}
              trackColor={{ false: '#1e293b', true: THEME.customerGlow }}
              thumbColor={esgBagsRebate ? THEME.customer : THEME.textMuted}
            />
            <Text style={styles.esgCheckboxLabel}>🌳 Return recycling paper bags for $0.50 rebate offset</Text>
          </View>

          {/* Total summary calculations */}
          <View style={styles.checkoutBreakdown}>
            <View style={styles.breakdownRow}>
              <Text style={styles.breakdownLabel}>Subtotal: ${cartSubtotal.toFixed(2)}</Text>
              <Text style={styles.breakdownLabel}>Delivery Fee: $2.99</Text>
            </View>
            <View style={styles.breakdownRow}>
              {esgBagsRebate && <Text style={[styles.breakdownLabel, { color: THEME.customer }]}>ESG Rebate: -$0.50</Text>}
              {tipAmount > 0 && <Text style={styles.breakdownLabel}>Rider Tip: ${tipAmount.toFixed(2)}</Text>}
            </View>
          </View>

          {/* Checkout action buttons */}
          <View style={styles.checkoutActionsRow}>
            <View style={styles.checkoutTotalCol}>
              <Text style={styles.totalCostLabel}>TOTAL INVOICE</Text>
              <Text style={styles.totalCostValue}>${totalCost.toFixed(2)}</Text>
            </View>

            <View style={styles.checkoutButtonsGroup}>
              <TouchableOpacity
                style={styles.checkoutWalletBtn}
                onPress={() => handleCheckout('Wallet')}
                disabled={isSubmittingCheckout}
              >
                {isSubmittingCheckout ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.checkoutBtnText}>Pay via Wallet</Text>
                )}
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.checkoutSwipeBtn}
                onPress={() => handleCheckout('Swipe')}
                disabled={isSubmittingCheckout}
              >
                <Text style={styles.checkoutBtnText}>Swipe Inst.</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      )}
    </SafeAreaView>
  );
}

// -------------------------------------------------------------
// STYLING SYSTEM IN THE CODES (HSL INSPIRED DARK MODE STYLE)
// -------------------------------------------------------------
const styles = StyleSheet.create({
  // Authentication & MFA Styles
  authContainer: {
    flex: 1,
    backgroundColor: THEME.bgDark
  },
  authScroll: {
    padding: 20,
    paddingTop: 60,
    alignItems: 'center'
  },
  brandContainer: {
    alignItems: 'center',
    marginBottom: 40
  },
  brandIconContainer: {
    width: 80,
    height: 80,
    borderRadius: 20,
    backgroundColor: 'rgba(16, 185, 129, 0.1)',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(16, 185, 129, 0.3)',
    marginBottom: 15
  },
  brandName: {
    fontFamily: 'sans-serif-medium',
    fontSize: 32,
    fontWeight: '800',
    color: THEME.textPrimary,
    letterSpacing: 2
  },
  brandSub: {
    color: THEME.textSecondary,
    fontSize: 14,
    marginTop: 5
  },
  configToggleBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 8,
    padding: 10,
    marginBottom: 20
  },
  configToggleText: {
    color: THEME.textSecondary,
    fontSize: 12
  },
  configCard: {
    width: '100%',
    backgroundColor: THEME.bgCard,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    padding: 15,
    marginBottom: 20
  },
  configHeader: {
    color: THEME.customer,
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
    marginBottom: 5
  },
  authCard: {
    width: '100%',
    backgroundColor: THEME.bgCard,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    padding: 20,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 5
  },
  authTitle: {
    color: THEME.textPrimary,
    fontSize: 14,
    fontWeight: 'bold',
    letterSpacing: 1.5,
    borderBottomWidth: 1,
    borderBottomColor: THEME.borderColor,
    paddingBottom: 10,
    marginBottom: 20,
    textAlign: 'center'
  },
  mfaSubtitle: {
    color: THEME.textSecondary,
    fontSize: 12,
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 20
  },
  inputLabel: {
    color: THEME.textSecondary,
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 1,
    marginBottom: 8
  },
  authInput: {
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 10,
    color: THEME.textPrimary,
    fontSize: 14,
    paddingHorizontal: 15,
    paddingVertical: 12,
    marginBottom: 20
  },
  authSubmitBtn: {
    backgroundColor: THEME.customer,
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
    shadowColor: THEME.customer,
    shadowOpacity: 0.4,
    shadowRadius: 5,
    elevation: 3
  },
  authSubmitBtnText: {
    color: THEME.bgDark,
    fontSize: 14,
    fontWeight: 'bold',
    letterSpacing: 1
  },
  mfaCancelBtn: {
    marginTop: 15,
    padding: 10,
    alignItems: 'center'
  },
  mfaCancelBtnText: {
    color: THEME.admin,
    fontSize: 12
  },

  // Main Dashboard Styles
  appContainer: {
    flex: 1,
    backgroundColor: THEME.bgDark
  },
  headerBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 15,
    backgroundColor: 'rgba(7, 10, 19, 0.9)',
    borderBottomWidth: 1,
    borderBottomColor: THEME.borderColor
  },
  headerBrandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '900',
    color: THEME.textPrimary,
    letterSpacing: 1
  },
  networkStatusIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderRadius: 20,
    paddingHorizontal: 8,
    paddingVertical: 3,
    marginLeft: 10
  },
  statusDot: {
    width: 6,
    height: 6,
    borderRadius: 3
  },
  networkStatusText: {
    color: THEME.textSecondary,
    fontSize: 9,
    fontWeight: 'bold'
  },
  headerSettingsIcon: {
    padding: 5
  },
  endpointDropdown: {
    backgroundColor: THEME.bgBody,
    borderBottomWidth: 1,
    borderBottomColor: THEME.borderColor,
    padding: 15
  },
  endpointLabel: {
    color: THEME.textSecondary,
    fontSize: 9,
    fontWeight: 'bold',
    marginBottom: 5
  },
  endpointInput: {
    backgroundColor: 'rgba(255,255,255,0.03)',
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 8,
    color: THEME.textPrimary,
    fontSize: 13,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 10
  },
  endpointActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 10
  },
  endpointRefreshBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    borderRadius: 6,
    paddingVertical: 6,
    paddingHorizontal: 12
  },
  endpointRefreshText: {
    color: THEME.textPrimary,
    fontSize: 11,
    fontWeight: 'bold'
  },

  // Segment Tabs Selection
  tabContainer: {
    flexDirection: 'row',
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    borderBottomWidth: 1,
    borderBottomColor: THEME.borderColor,
    padding: 8
  },
  tabButton: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
    paddingVertical: 8,
    borderRadius: 8
  },
  tabButtonActive: {
    backgroundColor: 'rgba(255, 255, 255, 0.06)'
  },
  tabButtonText: {
    color: THEME.textSecondary,
    fontSize: 12,
    fontWeight: '600'
  },
  tabButtonTextActive: {
    color: THEME.customer
  },

  // Scroll Container Content
  scrollContent: {
    padding: 20,
    paddingBottom: 260
  },
  sectionHeader: {
    color: THEME.textSecondary,
    fontSize: 10,
    fontWeight: 'bold',
    letterSpacing: 2,
    marginTop: 15,
    marginBottom: 12
  },

  // Search Input bar
  searchBarContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 12,
    paddingHorizontal: 12,
    height: 44,
    marginBottom: 15
  },
  searchIcon: {
    marginRight: 8
  },
  searchInput: {
    flex: 1,
    color: THEME.textPrimary,
    fontSize: 14
  },

  // Grid Catalog container
  gridContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 12
  },
  productCard: {
    width: (width - 52) / 2, // dynamic split grid
    backgroundColor: THEME.bgCard,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 14,
    padding: 12,
    position: 'relative'
  },
  perishableTag: {
    position: 'absolute',
    top: 8,
    left: 8,
    backgroundColor: 'rgba(6, 182, 212, 0.15)',
    borderWidth: 0.5,
    borderColor: THEME.engine,
    borderRadius: 4,
    paddingHorizontal: 4,
    paddingVertical: 2
  },
  perishableText: {
    color: THEME.engine,
    fontSize: 7,
    fontWeight: 'bold'
  },
  productEmoji: {
    fontSize: 32,
    textAlign: 'center',
    marginVertical: 12
  },
  productName: {
    color: THEME.textPrimary,
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 2
  },
  productCategory: {
    color: THEME.textMuted,
    fontSize: 10,
    marginBottom: 10
  },
  productFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 'auto'
  },
  productPrice: {
    color: THEME.customer,
    fontSize: 15,
    fontWeight: '800'
  },
  addToCartBtn: {
    backgroundColor: THEME.customerGlow,
    borderWidth: 1,
    borderColor: THEME.customer,
    width: 28,
    height: 28,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center'
  },
  productStock: {
    fontSize: 9,
    marginTop: 8,
    fontWeight: '500'
  },

  // Profile View Card details
  trustShieldCard: {
    flexDirection: 'row',
    backgroundColor: THEME.bgCard,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 16,
    padding: 15,
    marginBottom: 20
  },
  trustScoreCol: {
    flex: 1,
    borderRightWidth: 1,
    borderRightColor: THEME.borderColor,
    paddingRight: 10
  },
  trustScoreTitle: {
    color: THEME.textMuted,
    fontSize: 8,
    fontWeight: 'bold',
    letterSpacing: 1
  },
  trustScoreVal: {
    fontSize: 28,
    fontWeight: '800',
    marginVertical: 4
  },
  probationWarningText: {
    color: THEME.admin,
    fontSize: 8,
    fontWeight: 'bold'
  },
  loyaltyCol: {
    flex: 1,
    paddingLeft: 15,
    justifyContent: 'center'
  },
  loyaltyPointRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4
  },
  vipBadgeText: {
    color: THEME.vipGold,
    fontSize: 10,
    fontWeight: 'bold'
  },
  loyaltyVal: {
    color: THEME.textPrimary,
    fontSize: 16,
    fontWeight: '800',
    marginVertical: 2
  },
  walletVal: {
    color: THEME.customer,
    fontSize: 12,
    fontWeight: '700'
  },
  gdprActionContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: 'rgba(239, 68, 68, 0.05)',
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.15)',
    borderRadius: 12,
    padding: 12,
    marginBottom: 20
  },
  gdprText: {
    color: THEME.textSecondary,
    fontSize: 11
  },
  gdprPurgeBtn: {
    backgroundColor: THEME.adminGlow,
    borderWidth: 1,
    borderColor: THEME.admin,
    borderRadius: 8,
    paddingVertical: 6,
    paddingHorizontal: 10
  },
  gdprPurgeBtnText: {
    color: THEME.admin,
    fontSize: 9,
    fontWeight: 'bold'
  },
  ledgerCard: {
    backgroundColor: THEME.bgCard,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 12,
    padding: 12,
    marginBottom: 8
  },
  ledgerHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6
  },
  ledgerCode: {
    color: THEME.textMuted,
    fontSize: 9,
    fontFamily: 'monospace'
  },
  ledgerRef: {
    fontSize: 9,
    fontWeight: 'bold'
  },
  ledgerAmountRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center'
  },
  ledgerAccount: {
    color: THEME.textPrimary,
    fontSize: 11
  },
  ledgerCash: {
    color: THEME.textPrimary,
    fontSize: 13,
    fontWeight: 'bold'
  },
  pastOrderCard: {
    backgroundColor: THEME.bgCard,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 12,
    padding: 12,
    marginBottom: 8
  },
  pastOrderHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8
  },
  pastOrderId: {
    color: THEME.textPrimary,
    fontSize: 12,
    fontWeight: '700'
  },
  pastOrderDate: {
    color: THEME.textMuted,
    fontSize: 10
  },
  pastOrderFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center'
  },
  pastOrderPrice: {
    color: THEME.textSecondary,
    fontSize: 12
  },
  pastOrderStatusText: {
    fontSize: 11,
    fontWeight: '800'
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 30,
    backgroundColor: 'rgba(255,255,255,0.01)',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: THEME.borderColor
  },
  emptyText: {
    color: THEME.textMuted,
    fontSize: 12,
    marginTop: 8
  },

  // Active Order Tracker UI elements
  slaStopwatchCard: {
    alignItems: 'center',
    backgroundColor: 'rgba(15, 23, 42, 0.85)',
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 16,
    padding: 20,
    marginBottom: 20
  },
  stopwatchTitle: {
    color: THEME.textMuted,
    fontSize: 9,
    fontWeight: 'bold',
    letterSpacing: 2
  },
  stopwatchDisplay: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginVertical: 12
  },
  stopwatchDigits: {
    color: THEME.textPrimary,
    fontSize: 32,
    fontWeight: '900',
    fontFamily: 'monospace'
  },
  stopwatchDisclaimer: {
    color: THEME.textMuted,
    fontSize: 8,
    textAlign: 'center'
  },
  criticalBreachBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(239, 68, 68, 0.2)',
    borderWidth: 1,
    borderColor: THEME.admin,
    borderRadius: 12,
    padding: 12,
    marginBottom: 20
  },
  breachTitle: {
    color: THEME.admin,
    fontSize: 11,
    fontWeight: 'bold',
    marginBottom: 2
  },
  breachDesc: {
    color: THEME.textPrimary,
    fontSize: 10,
    lineHeight: 14
  },
  breachActionBtn: {
    backgroundColor: THEME.admin,
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
    marginLeft: 10
  },
  breachActionText: {
    color: THEME.bgDark,
    fontSize: 10,
    fontWeight: 'bold'
  },
  telemetryMapCard: {
    backgroundColor: THEME.bgCard,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 16,
    padding: 15,
    marginBottom: 20
  },
  mapHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: THEME.borderColor,
    paddingBottom: 10,
    marginBottom: 15
  },
  mapTitle: {
    color: THEME.textSecondary,
    fontSize: 10,
    fontWeight: 'bold',
    letterSpacing: 1
  },
  mapIndicatorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6
  },
  mapStatus: {
    color: THEME.textSecondary,
    fontSize: 9,
    fontWeight: '700'
  },
  mapGraphicGrid: {
    height: 80,
    backgroundColor: 'rgba(0,0,0,0.2)',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    marginBottom: 15
  },
  mapStoreIcon: {
    opacity: 0.8
  },
  mapProgressLineContainer: {
    flex: 1,
    height: 4,
    backgroundColor: 'rgba(255,255,255,0.05)',
    marginHorizontal: 10,
    borderRadius: 2,
    position: 'relative',
    justifyContent: 'center'
  },
  mapProgressLine: {
    height: '100%',
    backgroundColor: THEME.customer,
    borderRadius: 2
  },
  mapRiderDot: {
    position: 'absolute',
    left: '50%',
    marginLeft: -10,
    top: -8
  },
  mapHomeIcon: {
    opacity: 0.8
  },
  mapReadoutRow: {
    flexDirection: 'row',
    justifyContent: 'space-between'
  },
  readoutCol: {
    flex: 1
  },
  readoutLabel: {
    color: THEME.textMuted,
    fontSize: 8,
    fontWeight: 'bold',
    marginBottom: 4
  },
  readoutValue: {
    color: THEME.textPrimary,
    fontSize: 12,
    fontWeight: '700',
    fontFamily: 'monospace'
  },
  telemetryTickRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderLeftWidth: 2,
    borderLeftColor: THEME.borderColor,
    paddingLeft: 10,
    paddingVertical: 8,
    marginBottom: 6
  },
  tickTime: {
    color: THEME.textMuted,
    fontSize: 10,
    fontFamily: 'monospace'
  },
  tickLoc: {
    color: THEME.textSecondary,
    fontSize: 10,
    flex: 1,
    marginLeft: 10
  },
  tickTemp: {
    fontSize: 11,
    fontWeight: 'bold',
    fontFamily: 'monospace',
    marginRight: 5
  },

  // Bottom Cart Drawer Summary Styles
  cartDrawer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'rgba(11, 15, 25, 0.98)',
    borderTopWidth: 1,
    borderTopColor: THEME.borderColor,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 15,
    shadowColor: '#000',
    shadowOpacity: 0.4,
    shadowRadius: 15,
    elevation: 10
  },
  cartHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10
  },
  cartCountCol: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8
  },
  cartDrawerCount: {
    color: THEME.textPrimary,
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 1
  },
  clearCartText: {
    color: THEME.admin,
    fontSize: 11,
    fontWeight: '600'
  },
  cartItemsScroll: {
    maxHeight: 40,
    marginBottom: 10
  },
  cartItemPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderWidth: 0.5,
    borderColor: THEME.borderColor,
    borderRadius: 8,
    paddingHorizontal: 8,
    paddingVertical: 4,
    gap: 6
  },
  cartItemEmoji: {
    fontSize: 14
  },
  cartItemName: {
    color: THEME.textPrimary,
    fontSize: 11,
    fontWeight: '600'
  },
  tipSection: {
    marginBottom: 10
  },
  tipHeader: {
    color: THEME.textSecondary,
    fontSize: 8,
    fontWeight: 'bold',
    letterSpacing: 1,
    marginBottom: 6
  },
  tipGrid: {
    flexDirection: 'row',
    gap: 6
  },
  tipBtn: {
    flex: 1,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 8,
    paddingVertical: 5,
    alignItems: 'center'
  },
  tipBtnActive: {
    borderColor: THEME.customer,
    backgroundColor: THEME.customerGlow
  },
  tipBtnText: {
    color: THEME.textSecondary,
    fontSize: 11,
    fontWeight: '600'
  },
  tipBtnTextActive: {
    color: THEME.customer
  },
  esgCheckboxRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 10
  },
  esgCheckboxLabel: {
    color: THEME.textSecondary,
    fontSize: 10
  },
  checkoutBreakdown: {
    borderTopWidth: 1,
    borderTopColor: THEME.borderColor,
    paddingTop: 8,
    marginBottom: 12
  },
  breakdownRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 2
  },
  breakdownLabel: {
    color: THEME.textMuted,
    fontSize: 10
  },
  checkoutActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between'
  },
  checkoutTotalCol: {
    flexDirection: 'column'
  },
  totalCostLabel: {
    color: THEME.textMuted,
    fontSize: 8,
    fontWeight: 'bold',
    letterSpacing: 1
  },
  totalCostValue: {
    color: THEME.customer,
    fontSize: 22,
    fontWeight: '900'
  },
  checkoutButtonsGroup: {
    flexDirection: 'row',
    gap: 8,
    flex: 1,
    justifyContent: 'flex-end',
    marginLeft: 15
  },
  checkoutWalletBtn: {
    backgroundColor: THEME.customer,
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 12,
    alignItems: 'center',
    flex: 1.2
  },
  checkoutSwipeBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    borderWidth: 1,
    borderColor: THEME.borderColor,
    borderRadius: 8,
    paddingVertical: 10,
    paddingHorizontal: 12,
    alignItems: 'center',
    flex: 1
  },
  checkoutBtnText: {
    color: THEME.bgDark,
    fontSize: 12,
    fontWeight: '800'
  }
});
