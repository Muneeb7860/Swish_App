import React, { useState, useEffect, useRef } from 'react';
import {
  StyleSheet,
  Text,
  View,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Modal,
  ActivityIndicator,
  Alert,
  Switch,
  Dimensions,
  Platform,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import { Ionicons, MaterialCommunityIcons, FontAwesome5 } from '@expo/vector-icons';

// Dimensions for responsive styling
const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

// ----------------------------------------------------
// CYBER-INDUSTRIAL DESIGN TOKENS (HSL counterparts)
// ----------------------------------------------------
const THEME = {
  bgDark: '#070a13',         // hsl(224, 46%, 5%)
  bgBody: '#0b0f19',         // hsl(222, 40%, 7%)
  bgCard: 'rgba(17, 24, 39, 0.8)', // hsla(224, 71%, 4%, 0.8)
  bgCardHover: 'rgba(31, 41, 55, 0.95)',
  borderColor: 'rgba(255, 255, 255, 0.08)',
  
  // Glowing colors
  customer: '#10b981',       // Green (Success/Ready) - hsl(158, 64%, 42%)
  customerGlow: 'rgba(16, 185, 129, 0.2)',
  
  rider: '#f59e0b',          // Amber (Warning/Riders) - hsl(38, 92%, 50%)
  riderGlow: 'rgba(245, 158, 11, 0.2)',
  
  inventory: '#3b82f6',      // Blue (Theme color / Inventory) - hsl(217, 91%, 60%)
  inventoryGlow: 'rgba(59, 130, 246, 0.2)',
  
  admin: '#ef4444',          // Red (Alert/SLA critical) - hsl(0, 84%, 60%)
  adminGlow: 'rgba(239, 68, 68, 0.2)',
  
  engine: '#06b6d4',         // Cyan (System/Engine) - hsl(188, 86%, 53%)
  engineGlow: 'rgba(6, 182, 212, 0.2)',
  
  textPrimary: '#f8fafc',    // hsl(210, 100%, 98%)
  textSecondary: '#94a3b8',  // hsl(215, 25%, 72%)
  textMuted: '#64748b',      // hsl(218, 11%, 47%)
};

// ----------------------------------------------------
// DEFAULT SEED DATA
// ----------------------------------------------------
const MOCK_ORDER = {
  id: 4890,
  customerName: 'Marcus K. (Zurich Enge)',
  address: 'Gotthardstrasse 11, 8002 Zürich',
  items: [
    { itemId: 'milk', name: 'Swiss Whole Milk 1L', quantity: 2, emoji: '🥛', perishable: true },
    { itemId: 'icecream', name: 'Mövenpick Vanilla 500ml', quantity: 1, emoji: '🍨', perishable: true },
    { itemId: 'bread', name: 'Artisan Sourdough Loaf', quantity: 1, emoji: '🍞', perishable: false }
  ],
  total: 18.90,
  slaRemaining: 540, // 9 mins in sec
  weather: 'Sunny',
  gpsDestination: { latitude: 47.3621, longitude: 8.5309 },
  gpsStart: { latitude: 47.3769, longitude: 8.5417 },
};

export default function RiderScreen() {
  // Navigation Tabs: 'navigation' | 'telemetry' | 'incidents'
  const [activeTab, setActiveTab] = useState('navigation');
  
  // Connection states
  const [useSimulator, setUseSimulator] = useState(true);
  const [bffUrl, setBffUrl] = useState('http://localhost:8081'); // Standard BFF Gateway URL
  const [jwtToken, setJwtToken] = useState('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJyaWRlcl9kYXZlIiwicm9sZXMiOiJSSURVUiIsImlhdCI6MTc4MDAwMDAwMH0.signature');
  const [isConnected, setIsConnected] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [testLoading, setTestLoading] = useState(false);

  // Authentication Panel Inputs
  const [authUsername, setAuthUsername] = useState('swissuser');
  const [authPassword, setAuthPassword] = useState('swissqcommerce2026');
  const [authMfaSession, setAuthMfaSession] = useState(null);
  const [authOtpCode, setAuthOtpCode] = useState('');
  const [authLoading, setAuthLoading] = useState(false);
  const [authStep, setAuthStep] = useState('login'); // 'login' | 'mfa'

  // Rider Profile Stats
  const [riderStats, setRiderStats] = useState({
    walletBalance: 42.50,
    trustScore: 98,
    deliveriesCount: 37,
    incidentCount: 1,
  });

  // Transit States
  // 'idle' | 'pending_accept' | 'accepted' | 'in_transit' | 'delivered' | 'incident_blocked'
  const [deliveryState, setDeliveryState] = useState('idle');
  const [activeOrder, setActiveOrder] = useState(null);
  const [orderIdInput, setOrderIdInput] = useState('');
  
  // Simulated Sensors
  const [temperature, setTemperature] = useState(4.0);
  const [humidity, setHumidity] = useState(82);
  const [gpsCoords, setGpsCoords] = useState({ latitude: 47.3769, longitude: 8.5417 });
  const [routeProgress, setRouteProgress] = useState(0);
  const [slaCountdown, setSlaCountdown] = useState(540);

  // Telemetry Log Tracker
  const [telemetryLogs, setTelemetryLogs] = useState([]);
  
  // Dry Ice Coolant actions
  const [coolingLoading, setCoolingLoading] = useState(false);

  // Emergency Incident Breakdown workflow
  const [showIncidentModal, setShowIncidentModal] = useState(false);
  const [selectedIncidentType, setSelectedIncidentType] = useState('breakdown');
  const [incidentDetail, setIncidentDetail] = useState('');
  const [incidentReported, setIncidentReported] = useState(false);
  const [backupRiderStatus, setBackupRiderStatus] = useState('none'); // 'none' | 'dispatching' | 'arrived'

  // Timers & SSE refs
  const tickTimerRef = useRef(null);
  const routeTimerRef = useRef(null);
  const sseEmitterRef = useRef(null);

  // ----------------------------------------------------
  // SIMULATION TICK LOOPS
  // ----------------------------------------------------
  useEffect(() => {
    if (deliveryState === 'in_transit') {
      // Start Telemetry logs and sensors
      addTelemetryLog('Fulfillment telemetry stream initiated.', 'info');
      
      tickTimerRef.current = setInterval(() => {
        // 1. Increment temperature slowly
        setTemperature(prevTemp => {
          let tempIncrease = 0.1 + Math.random() * 0.15; // Random increase
          const newTemp = parseFloat((prevTemp + tempIncrease).toFixed(1));
          
          // Log generation
          const timeStr = new Date().toLocaleTimeString();
          const isBreach = newTemp > 8.0;
          const isSpoiled = newTemp >= 12.0;

          if (isSpoiled) {
            addTelemetryLog(`[${timeStr}] 🚨 CRITICAL BREACH: Cargo temp hit ${newTemp}°C. Perishables ruined!`, 'error');
            Alert.alert('Cold Chain Breach', `Cargo temperature reached ${newTemp}°C. Perishables are spoiled! Delivery failed.`);
            setDeliveryState('incident_blocked');
            setRiderStats(prev => ({ ...prev, trustScore: Math.max(50, prev.trustScore - 20) }));
          } else if (isBreach) {
            addTelemetryLog(`[${timeStr}] ⚠️ THERMAL ALERT: Cargo warming up at ${newTemp}°C!`, 'warning');
          } else {
            addTelemetryLog(`[${timeStr}] Telemetry updated. Temp: ${newTemp}°C | Humidity: ${humidity}%`, 'normal');
          }

          // If connected to BFF, dispatch tick payload
          if (!useSimulator && isConnected) {
            dispatchBffTelemetryTick(newTemp);
          }

          return newTemp;
        });

        // 2. Decrement SLA countdown
        setSlaCountdown(prev => Math.max(0, prev - 3));

      }, 3000);

      // Route Progress simulation
      routeTimerRef.current = setInterval(() => {
        setRouteProgress(prev => {
          const next = prev + 5;
          if (next >= 100) {
            clearInterval(routeTimerRef.current);
            addTelemetryLog('Rider arrived at customer destination geofence.', 'info');
            return 100;
          }
          
          // Simulate GPS path movement
          setGpsCoords(prevGps => {
            const dest = activeOrder ? activeOrder.gpsDestination : MOCK_ORDER.gpsDestination;
            const start = activeOrder ? activeOrder.gpsStart : MOCK_ORDER.gpsStart;
            const latDelta = (dest.latitude - start.latitude) * (next / 100);
            const lngDelta = (dest.longitude - start.longitude) * (next / 100);
            
            return {
              latitude: parseFloat((start.latitude + latDelta).toFixed(5)),
              longitude: parseFloat((start.longitude + lngDelta).toFixed(5)),
            };
          });

          return next;
        });
      }, 4000);

    } else {
      // Clear timers when not in transit
      if (tickTimerRef.current) clearInterval(tickTimerRef.current);
      if (routeTimerRef.current) clearInterval(routeTimerRef.current);
    }

    return () => {
      if (tickTimerRef.current) clearInterval(tickTimerRef.current);
      if (routeTimerRef.current) clearInterval(routeTimerRef.current);
    };
  }, [deliveryState, isConnected, useSimulator]);

  // Helper to add lines to rolling telemetry log
  const addTelemetryLog = (message, type = 'normal') => {
    setTelemetryLogs(prev => [
      { id: Date.now() + Math.random().toString(), text: message, type },
      ...prev.slice(0, 49) // Keep last 50 logs
    ]);
  };

  // ----------------------------------------------------
  // API INTEGRATIONS (BFF GATEWAY)
  // ----------------------------------------------------

  // Test Connection
  const testBffConnection = async () => {
    setTestLoading(true);
    try {
      const response = await fetch(`${bffUrl}/api/admin/health`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${jwtToken}`,
          'Accept': 'application/json'
        }
      });
      
      if (response.ok) {
        setIsConnected(true);
        setUseSimulator(false);
        Alert.alert('Gateway Connected', 'Connected to BFF Gateway. JWT authentication validated successfully.');
        setShowSettings(false);
      } else {
        throw new Error(`HTTP ${response.status}`);
      }
    } catch (err) {
      setIsConnected(false);
      Alert.alert('Connection Failed', `Could not reach BFF Gateway at ${bffUrl}.\nEnsure the server is running and reachable from your device/emulator.\n\nDetail: ${err.message}`);
    } finally {
      setTestLoading(false);
    }
  };

  // Authenticate & MFA workflow
  const handleBffLogin = async () => {
    if (!authUsername || !authPassword) {
      Alert.alert('Input Error', 'Please enter username and password.');
      return;
    }
    
    authUsername.trim();
    
    setAuthLoading(true);
    try {
      const response = await fetch(`${bffUrl}/api/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify({
          username: authUsername,
          password: authPassword
        })
      });

      if (response.ok) {
        const data = await response.json();
        if (data.mfaRequired || data.sessionToken) {
          setAuthMfaSession(data.sessionToken);
          setAuthStep('mfa');
          Alert.alert('MFA Verification Needed', `Login successful! MFA PIN code dispatched to terminal/logs.\nPlease retrieve the PIN code and verify.`);
        } else if (data.token) {
          setJwtToken(data.token);
          setIsConnected(true);
          setUseSimulator(false);
          setAuthStep('login');
          Alert.alert('Authentication Success', 'JWT obtained directly. Session established.');
          setShowSettings(false);
        }
      } else {
        const errText = await response.text();
        throw new Error(errText || `HTTP ${response.status}`);
      }
    } catch (err) {
      Alert.alert('Auth Failed', `Credentials verification failed at BFF: ${err.message}`);
    } finally {
      setAuthLoading(false);
    }
  };

  const handleVerifyMfa = async () => {
    if (!authOtpCode) {
      Alert.alert('Input Error', 'Please enter the 6-digit verification PIN.');
      return;
    }

    setAuthLoading(true);
    try {
      const response = await fetch(`${bffUrl}/api/auth/mfa/verify`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify({
          sessionToken: authMfaSession,
          code: authOtpCode
        })
      });

      if (response.ok) {
        const token = await response.text(); // Returns raw token string usually
        
        let cleanedToken = token;
        try {
          // If returned as JSON
          const data = JSON.parse(token);
          cleanedToken = data.token || data.jwt || token;
        } catch (e) {}

        setJwtToken(cleanedToken);
        setIsConnected(true);
        setUseSimulator(false);
        setAuthOtpCode('');
        setAuthMfaSession(null);
        setAuthStep('login');
        Alert.alert('MFA Authenticated', 'Secure JWT session established. Ready for logistics dispatch.');
        setShowSettings(false);
      } else {
        const errText = await response.text();
        throw new Error(errText || `HTTP ${response.status}`);
      }
    } catch (err) {
      Alert.alert('MFA Error', `Verification failed: ${err.message}`);
    } finally {
      setAuthLoading(false);
    }
  };

  // POST Ingest Telemetry Tick
  const dispatchBffTelemetryTick = async (currentTemp) => {
    try {
      const response = await fetch(`${bffUrl}/api/telemetry/tick`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${jwtToken}`,
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify({
          orderId: activeOrder ? activeOrder.id : MOCK_ORDER.id,
          latitude: gpsCoords.latitude,
          longitude: gpsCoords.longitude,
          temperature: currentTemp,
          dryIceInjected: false
        })
      });

      if (!response.ok) {
        console.warn(`BFF Ingestion rejected tick: HTTP ${response.status}`);
      }
    } catch (err) {
      console.warn(`Failed to dispatch telemetry tick to BFF: ${err.message}`);
    }
  };

  // POST Inject Dry Ice Coolant
  const injectDryIceCoolant = async () => {
    const orderId = activeOrder ? activeOrder.id : MOCK_ORDER.id;
    setCoolingLoading(true);

    if (useSimulator) {
      setTimeout(() => {
        setTemperature(4.0);
        addTelemetryLog(`[${new Date().toLocaleTimeString()}] ❄️ DRY ICE INJECTED: Coolant container active. Temp reset to 4.0°C.`, 'info');
        setCoolingLoading(false);
        setRiderStats(prev => ({ ...prev, walletBalance: prev.walletBalance - 2.00 }));
        Alert.alert('Coolant Injected', 'Simulated dry ice cooling completed. Temperature reset to 4.0°C.');
      }, 1000);
    } else {
      try {
        const response = await fetch(`${bffUrl}/api/telemetry/${orderId}/dry-ice`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${jwtToken}`,
            'Accept': 'application/json'
          }
        });

        if (response.ok) {
          const data = await response.json();
          setTemperature(4.0);
          addTelemetryLog(`[${new Date().toLocaleTimeString()}] ❄️ DRY ICE INJECTED (BFF): Telemetry database updated. Temp reset to 4.0°C.`, 'info');
          setRiderStats(prev => ({ ...prev, walletBalance: prev.walletBalance - 2.00 }));
          Alert.alert('Dry Ice Injected', 'Dry ice cargo cooling completed. Telemetry database updated.');
        } else {
          const errText = await response.text();
          throw new Error(errText || `HTTP ${response.status}`);
        }
      } catch (err) {
        Alert.alert('API Error', `Could not trigger coolant injection at BFF Gateway:\n${err.message}`);
      } finally {
        setCoolingLoading(false);
      }
    }
  };

  // ----------------------------------------------------
  // STATE MACHINE & TRANSIT FLOWS
  // ----------------------------------------------------
  
  // Simulate dispatch allocation (for testing without backend)
  const triggerMockDispatch = () => {
    setActiveOrder(MOCK_ORDER);
    setDeliveryState('pending_accept');
    setTemperature(4.0);
    setRouteProgress(0);
    setSlaCountdown(540);
    setGpsCoords(MOCK_ORDER.gpsStart);
    setTelemetryLogs([]);
    addTelemetryLog('New delivery dispatch proposal received from Zurich MFC.', 'warning');
  };

  // Assign and accept dispatch
  const handleAcceptDelivery = () => {
    if (useSimulator) {
      if (deliveryState === 'idle') {
        // If idle, just create mock order immediately
        setActiveOrder(MOCK_ORDER);
        setDeliveryState('accepted');
        setTemperature(4.0);
        setRouteProgress(0);
        setSlaCountdown(540);
        setGpsCoords(MOCK_ORDER.gpsStart);
        setTelemetryLogs([]);
        addTelemetryLog('Rider accepted Order #4890. Initializing dispatch preparation.', 'info');
      } else {
        setDeliveryState('accepted');
        addTelemetryLog(`Rider accepted Order #${activeOrder.id}. Preparing E-Bike loading.`, 'info');
      }
    } else {
      // Connect to real order
      const orderId = parseInt(orderIdInput);
      if (isNaN(orderId)) {
        Alert.alert('Input Error', 'Please enter a valid numeric Order ID from the queue.');
        return;
      }
      
      setActiveOrder({
        ...MOCK_ORDER,
        id: orderId,
        customerName: `Customer (Order #${orderId})`,
      });
      setDeliveryState('accepted');
      setTemperature(4.0);
      setRouteProgress(0);
      setSlaCountdown(540);
      setGpsCoords(MOCK_ORDER.gpsStart);
      setTelemetryLogs([]);
      addTelemetryLog(`Rider manually assigned and accepted Order #${orderId}.`, 'info');
    }
  };

  const handleDepartTransit = () => {
    setDeliveryState('in_transit');
    addTelemetryLog('Transit departed. GPS route mapping started towards client geofence.', 'info');
  };

  const handleCompleteDelivery = () => {
    // Check if perishables were ruined
    if (temperature >= 12.0) {
      Alert.alert('Spoilage Alert', 'Cannot complete delivery. Cargo temperature exceeded 12°C. Perishables are spoiled!');
      return;
    }

    setDeliveryState('delivered');
    const isLightning = (540 - slaCountdown) < 180; // completed in under 3 minutes
    const payout = isLightning ? 6.50 : 5.00;

    setRiderStats(prev => ({
      ...prev,
      deliveriesCount: prev.deliveriesCount + 1,
      walletBalance: prev.walletBalance + payout,
      trustScore: Math.min(100, prev.trustScore + 2),
    }));

    Alert.alert(
      'Delivery Complete',
      `SLA Guarantee Satisfied!\nPayout: CHF ${payout.toFixed(2)} credited to Rider Wallet.${isLightning ? ' (Lightning Delivery Bonus! ⚡)' : ''}`
    );
  };

  const resetRiderState = () => {
    setDeliveryState('idle');
    setActiveOrder(null);
    setOrderIdInput('');
    setRouteProgress(0);
    setTemperature(4.0);
    setTelemetryLogs([]);
    setIncidentReported(false);
    setBackupRiderStatus('none');
  };

  // ----------------------------------------------------
  // EMERGENCY INCIDENT BREAKDOWN WORKFLOW
  // ----------------------------------------------------
  const handleReportIncident = () => {
    if (!selectedIncidentType) return;

    setIncidentReported(true);
    addTelemetryLog(`[${new Date().toLocaleTimeString()}] 🚨 EMERGENCY REPORTED: ${selectedIncidentType.toUpperCase()}`, 'error');
    if (incidentDetail) {
      addTelemetryLog(`Incident Details: "${incidentDetail}"`, 'error');
    }

    // Deduct trust points for breakdown incidents
    setRiderStats(prev => ({
      ...prev,
      incidentCount: prev.incidentCount + 1,
      trustScore: Math.max(50, prev.trustScore - 5)
    }));

    // Trigger simulated salvage operations
    setBackupRiderStatus('dispatching');
    addTelemetryLog('Operations Control: Dispatching backup E-Bike courier for cargo salvage.', 'warning');
    
    // Timer to simulate backup rider arriving
    setTimeout(() => {
      setBackupRiderStatus('arrived');
      addTelemetryLog('Operations Control: Backup courier arrived at rider coordinates. Transferring cargo.', 'info');
      Alert.alert(
        'Backup Courier Arrived',
        'Backup rider has reached your location. Cargo transferred. SLA timer paused. Returning to standby.'
      );
    }, 8000);

    setShowIncidentModal(false);
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor={THEME.bgDark} />

      {/* ----------------- HEADER ----------------- */}
      <View style={styles.header}>
        <View style={styles.brandRow}>
          <Text style={styles.logoText}>SWISS <Text style={{ color: THEME.rider }}>Q-COMMERCE</Text></Text>
          <View style={styles.connectionBadge}>
            <View style={[styles.ledIndicator, { backgroundColor: useSimulator ? THEME.rider : THEME.customer }]} />
            <Text style={[styles.connectionText, { color: useSimulator ? THEME.rider : THEME.customer }]}>
              {useSimulator ? 'SIMULATOR MODE' : 'BFF CONNECTED'}
            </Text>
          </View>
        </View>
        
        <View style={styles.subHeader}>
          <View style={styles.roleContainer}>
            <Ionicons name="bicycle-sharp" size={16} color={THEME.rider} />
            <Text style={styles.roleText}>RIDER COCKPIT & COLD-CHAIN IoT</Text>
          </View>
          
          <TouchableOpacity style={styles.settingsBtn} onPress={() => setShowSettings(true)}>
            <Ionicons name="settings-sharp" size={18} color={THEME.textSecondary} />
          </TouchableOpacity>
        </View>
      </View>

      {/* ----------------- METRIC BAR ----------------- */}
      <View style={styles.metricsContainer}>
        <View style={styles.metricItem}>
          <Text style={styles.metricLabel}>RIDER TRUST</Text>
          <Text style={[styles.metricValue, { color: riderStats.trustScore >= 85 ? THEME.customer : THEME.admin }]}>
            {riderStats.trustScore}%
          </Text>
        </View>
        <View style={styles.metricItem}>
          <Text style={styles.metricLabel}>WALLET BALANCE</Text>
          <Text style={[styles.metricValue, { color: THEME.rider }]}>
            CHF {riderStats.walletBalance.toFixed(2)}
          </Text>
        </View>
        <View style={styles.metricItem}>
          <Text style={styles.metricLabel}>DELIVERIES</Text>
          <Text style={[styles.metricValue, { color: THEME.engine }]}>{riderStats.deliveriesCount}</Text>
        </View>
        <View style={styles.metricItem}>
          <Text style={styles.metricLabel}>INCIDENTS LOGGED</Text>
          <Text style={[styles.metricValue, { color: riderStats.incidentCount > 1 ? THEME.admin : THEME.textSecondary }]}>
            {riderStats.incidentCount}
          </Text>
        </View>
      </View>

      {/* ----------------- SCREEN CONTENT ----------------- */}
      <ScrollView contentContainerStyle={styles.content}>
        
        {/* SETTINGS BANNER WARNING */}
        {!useSimulator && !isConnected && (
          <View style={styles.warningBanner}>
            <Ionicons name="warning-outline" size={16} color="#070a13" style={{ marginRight: 8 }} />
            <Text style={styles.warningBannerText}>Real BFF mode active but disconnected! Check Settings.</Text>
          </View>
        )}

        {/* INCIDENT REPORT PENDING BANNER */}
        {backupRiderStatus === 'dispatching' && (
          <View style={styles.incidentBanner}>
            <ActivityIndicator size="small" color="#070a13" style={{ marginRight: 8 }} />
            <Text style={styles.incidentBannerText}>EMERGENCY SALVAGE: Backup rider dispatching to coordinates...</Text>
          </View>
        )}

        {/* TAB NAVIGATION COCKPIT SELECTOR */}
        <View style={styles.pillInputRow}>
          <TouchableOpacity 
            style={[styles.miniTab, activeTab === 'navigation' && styles.miniTabActive]}
            onPress={() => setActiveTab('navigation')}
          >
            <Ionicons name="map-outline" size={14} color={activeTab === 'navigation' ? '#070a13' : THEME.textSecondary} />
            <Text style={[styles.miniTabText, activeTab === 'navigation' && styles.miniTabTextActive]}>Navigation</Text>
          </TouchableOpacity>
          
          <TouchableOpacity 
            style={[styles.miniTab, activeTab === 'telemetry' && styles.miniTabActive]}
            onPress={() => setActiveTab('telemetry')}
          >
            <Ionicons name="pulse-outline" size={14} color={activeTab === 'telemetry' ? '#070a13' : THEME.textSecondary} />
            <Text style={[styles.miniTabText, activeTab === 'telemetry' && styles.miniTabTextActive]}>IoT Logs</Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={[styles.miniTab, activeTab === 'incidents' && styles.miniTabActive]}
            onPress={() => setActiveTab('incidents')}
          >
            <Ionicons name="alert-circle-outline" size={14} color={activeTab === 'incidents' ? '#070a13' : THEME.textSecondary} />
            <Text style={[styles.miniTabText, activeTab === 'incidents' && styles.miniTabTextActive]}>Emergency</Text>
          </TouchableOpacity>
        </View>

        {/* ----------------- TAB 1: NAVIGATION PANEL ----------------- */}
        {activeTab === 'navigation' && (
          <View style={styles.tabContent}>
            
            {/* STATE IDLE: AWAITING ORDERS */}
            {deliveryState === 'idle' && (
              <View style={styles.card}>
                <View style={styles.idleAnimationRow}>
                  <View style={styles.radarRing}>
                    <Ionicons name="radio-outline" size={32} color={THEME.rider} />
                  </View>
                  <Text style={styles.idleTitle}>Rider Standby Mode</Text>
                  <Text style={styles.idleDesc}>
                    Standby at Central Dark Store MFC. Geofence active. Awaiting client dispatches...
                  </Text>
                </View>

                {useSimulator ? (
                  <TouchableOpacity style={styles.actionBtnPrimary} onPress={triggerMockDispatch}>
                    <Text style={styles.actionBtnText}>SIMULATE INCOMING DISPATCH</Text>
                    <Ionicons name="flash" size={16} color="#070a13" />
                  </TouchableOpacity>
                ) : (
                  <View style={styles.manualAssignBox}>
                    <Text style={styles.inputLabel}>Enter Order ID from Picker Queue:</Text>
                    <TextInput 
                      style={styles.textInput}
                      keyboardType="numeric"
                      placeholder="e.g. 3011"
                      placeholderTextColor={THEME.textMuted}
                      value={orderIdInput}
                      onChangeText={setOrderIdInput}
                    />
                    <TouchableOpacity style={styles.actionBtnPrimary} onPress={handleAcceptDelivery}>
                      <Text style={styles.actionBtnText}>ACCEPT MANUAL ASSIGNMENT</Text>
                      <Ionicons name="checkmark-circle-outline" size={16} color="#070a13" />
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            )}

            {/* STATE PENDING ACCEPT: INCOMING DISPATCH */}
            {deliveryState === 'pending_accept' && activeOrder && (
              <View style={[styles.card, { borderColor: THEME.rider, borderWidth: 1 }]}>
                <View style={styles.cardHeaderRow}>
                  <Text style={styles.cardHeaderTitle}>🚨 INCOMING DELIVERY DISPATCH</Text>
                  <Text style={styles.orderLabel}>Order #{activeOrder.id}</Text>
                </View>

                <View style={styles.orderInfoBox}>
                  <Text style={styles.infoLabel}>Client Address:</Text>
                  <Text style={styles.infoText}>{activeOrder.address}</Text>
                  
                  <Text style={styles.infoLabel}>Fulfillment Items:</Text>
                  <View style={styles.itemsPreviewRow}>
                    {activeOrder.items.map((item, idx) => (
                      <View key={idx} style={styles.itemEmojiBadge}>
                        <Text style={styles.emojiBadgeText}>{item.emoji} x{item.quantity}</Text>
                      </View>
                    ))}
                  </View>
                  
                  <Text style={styles.infoLabel}>Weather Condition:</Text>
                  <Text style={styles.infoText}>{activeOrder.weather} (SLA Paused on Extreme Storms)</Text>
                </View>

                <TouchableOpacity style={styles.actionBtnPrimary} onPress={handleAcceptDelivery}>
                  <Text style={styles.actionBtnText}>ACCEPT ROUTE DISPATCH</Text>
                  <Ionicons name="bicycle-outline" size={16} color="#070a13" />
                </TouchableOpacity>
              </View>
            )}

            {/* STATE ACCEPTED: LOADED & READY */}
            {deliveryState === 'accepted' && activeOrder && (
              <View style={styles.card}>
                <View style={styles.cardHeaderRow}>
                  <Text style={styles.cardHeaderTitle}>DISPATCH PREPARATION</Text>
                  <Text style={styles.statusBadgeAccepted}>ACCEPTED</Text>
                </View>
                
                <Text style={styles.cardDescription}>
                  Load cargo into the temperature-regulated thermal saddlebag. Verify cooling seal before departure.
                </Text>

                <View style={styles.telemetryGrid}>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Fulfillment Target:</Text>
                    <Text style={styles.telemetryVal}>Order #{activeOrder.id}</Text>
                  </View>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Saddlebag Temp Status:</Text>
                    <Text style={[styles.telemetryVal, { color: THEME.customer }]}>{temperature}°C (Optimal)</Text>
                  </View>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Destination Address:</Text>
                    <Text style={styles.telemetryVal}>{activeOrder.address}</Text>
                  </View>
                </View>

                <TouchableOpacity style={styles.actionBtnTransit} onPress={handleDepartTransit}>
                  <Text style={styles.actionBtnText}>DEPART FOR TRANSIT</Text>
                  <Ionicons name="navigate-outline" size={16} color="#070a13" />
                </TouchableOpacity>
              </View>
            )}

            {/* STATE IN TRANSIT: ACTIVE TELEMETRY LOGS */}
            {deliveryState === 'in_transit' && activeOrder && (
              <View style={styles.card}>
                <View style={styles.cardHeaderRow}>
                  <Text style={styles.cardHeaderTitle}>LIVE TRANSIT NAVIGATION</Text>
                  <View style={styles.slaTimerBox}>
                    <Ionicons name="time-outline" size={12} color={slaCountdown < 90 ? THEME.admin : THEME.textSecondary} />
                    <Text style={[styles.slaTimerText, slaCountdown < 90 && { color: THEME.admin }]}>
                      SLA: {Math.floor(slaCountdown / 60)}:{String(slaCountdown % 60).padStart(2, '0')}
                    </Text>
                  </View>
                </View>

                {/* Progress bar */}
                <View style={styles.progressSection}>
                  <View style={styles.progressHeader}>
                    <Text style={styles.progressLabel}>Transit Route Distance:</Text>
                    <Text style={styles.progressVal}>{routeProgress}% Complete</Text>
                  </View>
                  <View style={styles.progressBarBg}>
                    <View style={[styles.progressBarFill, { width: `${routeProgress}%` }]} />
                  </View>
                  <Text style={styles.gpsCoordText}>
                    GPS Node Coordinates: {gpsCoords.latitude}, {gpsCoords.longitude}
                  </Text>
                </View>

                {/* Cold Chain IoT telemetry node */}
                <View style={styles.coldChainTelemetryBlock}>
                  <View style={styles.tempDisplayHeader}>
                    <Text style={styles.tempLabel}>IoT COLD-CHAIN THERMOMETER</Text>
                    {temperature > 8.0 && (
                      <View style={styles.spoiledWarning}>
                        <Text style={styles.spoiledWarningText}>⚠️ OVERHEATING</Text>
                      </View>
                    )}
                  </View>
                  
                  <View style={styles.tempDigitalRow}>
                    <Text style={[
                      styles.tempValue, 
                      temperature < 6.0 && { color: THEME.customer },
                      temperature >= 6.0 && temperature <= 8.0 && { color: THEME.rider },
                      temperature > 8.0 && { color: THEME.admin }
                    ]}>
                      {temperature.toFixed(1)} °C
                    </Text>
                    <View style={styles.humidityBox}>
                      <Ionicons name="water-outline" size={14} color={THEME.engine} />
                      <Text style={styles.humidityText}>{humidity}% RH</Text>
                    </View>
                  </View>

                  <Text style={styles.coldChainDesc}>
                    Warning: Perishables spoil rapidly above 8.0°C! Trigger dry ice coolant mitigation immediately if warning alerts trigger.
                  </Text>

                  {/* Inject Coolant Button */}
                  <TouchableOpacity 
                    style={[
                      styles.coolantBtn, 
                      temperature > 8.0 && styles.coolantBtnAlert,
                      coolingLoading && { opacity: 0.7 }
                    ]}
                    onPress={injectDryIceCoolant}
                    disabled={coolingLoading}
                  >
                    {coolingLoading ? (
                      <ActivityIndicator size="small" color="#070a13" />
                    ) : (
                      <>
                        <Ionicons name="snow-outline" size={16} color="#070a13" />
                        <Text style={styles.coolantBtnText}>INJECT DRY ICE / COOLANT</Text>
                      </>
                    )}
                  </TouchableOpacity>
                </View>

                {/* Emergency breakdown incident */}
                <View style={styles.emergencyIncidentSection}>
                  <TouchableOpacity style={styles.incidentTriggerBtn} onPress={() => setShowIncidentModal(true)}>
                    <Ionicons name="warning-sharp" size={14} color={THEME.admin} />
                    <Text style={styles.incidentTriggerText}>REPORT INCIDENT / VEHICLE BREAKDOWN</Text>
                  </TouchableOpacity>
                </View>

                <TouchableOpacity 
                  style={[styles.actionBtnPrimary, routeProgress < 100 && { backgroundColor: THEME.textMuted }]}
                  disabled={routeProgress < 100}
                  onPress={handleCompleteDelivery}
                >
                  <Text style={styles.actionBtnText}>ARRIVED: COMPLETE DELIVERY</Text>
                  <Ionicons name="checkmark-done-circle" size={16} color="#070a13" />
                </TouchableOpacity>
              </View>
            )}

            {/* STATE DELIVERED: SUMMARY & EARNINGS */}
            {deliveryState === 'delivered' && activeOrder && (
              <View style={styles.card}>
                <View style={styles.deliveredHeader}>
                  <View style={styles.deliveredBadgeRing}>
                    <Ionicons name="gift-outline" size={32} color={THEME.customer} />
                  </View>
                  <Text style={styles.celebrationTitle}>DELIVERY TRANSACTION COMPLETE</Text>
                  <Text style={styles.celebrationSub}>
                    Order #{activeOrder.id} successfully completed. SLA verification matched client expectations.
                  </Text>
                </View>

                <View style={styles.telemetryGrid}>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Fulfillment Time:</Text>
                    <Text style={styles.telemetryVal}>{540 - slaCountdown}s elapsed</Text>
                  </View>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Fulfillment Status:</Text>
                    <Text style={[styles.telemetryVal, { color: THEME.customer }]}>SLA Guarantee Verified</Text>
                  </View>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Base Commission Payout:</Text>
                    <Text style={styles.telemetryVal}>CHF 5.00</Text>
                  </View>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Lightning SLA Bonus:</Text>
                    <Text style={[styles.telemetryVal, { color: '#fbbf24' }]}>
                      {(540 - slaCountdown) < 180 ? 'CHF 1.50 ⚡' : 'None'}
                    </Text>
                  </View>
                </View>

                <TouchableOpacity style={styles.actionBtnPrimary} onPress={resetRiderState}>
                  <Text style={styles.actionBtnText}>RETURN TO ACTIVE STANDBY</Text>
                  <Ionicons name="arrow-back" size={16} color="#070a13" />
                </TouchableOpacity>
              </View>
            )}

            {/* STATE INCIDENT BLOCKED */}
            {deliveryState === 'incident_blocked' && (
              <View style={[styles.card, { borderColor: THEME.admin, borderWidth: 1 }]}>
                <View style={styles.deliveredHeader}>
                  <View style={[styles.deliveredBadgeRing, { borderColor: THEME.admin, backgroundColor: 'rgba(239, 68, 68, 0.1)' }]}>
                    <Ionicons name="close-circle-outline" size={32} color={THEME.admin} />
                  </View>
                  <Text style={[styles.celebrationTitle, { color: THEME.admin }]}>TRANSIT BLOCKED</Text>
                  <Text style={styles.celebrationSub}>
                    Cold Chain breach or vehicle incident forced transit halt. Deliveries safety protocols activated.
                  </Text>
                </View>

                <View style={styles.telemetryGrid}>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Halt Reason:</Text>
                    <Text style={[styles.telemetryVal, { color: THEME.admin }]}>Cargo Temperature Spoiled (12°C+)</Text>
                  </View>
                  <View style={styles.telemetryRow}>
                    <Text style={styles.telemetryLabel}>Trust Score Impact:</Text>
                    <Text style={[styles.telemetryVal, { color: THEME.admin }]}>-20 points (Fulfillment breach)</Text>
                  </View>
                </View>

                <TouchableOpacity style={styles.actionBtnPrimary} onPress={resetRiderState}>
                  <Text style={styles.actionBtnText}>RETURN TO ACTIVE STANDBY</Text>
                  <Ionicons name="refresh" size={16} color="#070a13" />
                </TouchableOpacity>
              </View>
            )}

          </View>
        )}

        {/* ----------------- TAB 2: TELEMETRY LOG TRACKER ----------------- */}
        {activeTab === 'telemetry' && (
          <View style={styles.tabContent}>
            <View style={styles.card}>
              <View style={styles.cardHeaderRow}>
                <Text style={styles.cardHeaderTitle}>IoT Telemetry Log Tracker</Text>
                <TouchableOpacity style={styles.clearLogsBtn} onPress={() => setTelemetryLogs([])}>
                  <Text style={styles.clearLogsBtnText}>Clear Console</Text>
                </TouchableOpacity>
              </View>
              
              <Text style={styles.cardDescription}>
                Rolling logs from the E-Bike telemetry sensor node and BFF API server connections (refreshes on telemetry ticks).
              </Text>

              <ScrollView style={styles.consoleContainer} nestedScrollEnabled={true}>
                {telemetryLogs.length === 0 ? (
                  <Text style={styles.consolePlaceholder}>[Telemetry log is empty. Start transit route to poll sensors]</Text>
                ) : (
                  telemetryLogs.map(log => (
                    <Text 
                      key={log.id} 
                      style={[
                        styles.consoleLine,
                        log.type === 'info' && { color: THEME.engine },
                        log.type === 'warning' && { color: THEME.rider },
                        log.type === 'error' && { color: THEME.admin },
                      ]}
                    >
                      {log.text}
                    </Text>
                  ))
                )}
              </ScrollView>
            </View>
          </View>
        )}

        {/* ----------------- TAB 3: EMERGENCY INCIDENTS ----------------- */}
        {activeTab === 'incidents' && (
          <View style={styles.tabContent}>
            <View style={styles.card}>
              <Text style={styles.cardHeaderTitle}>Emergency Incident Handling</Text>
              <Text style={styles.cardDescription}>
                Use this dashboard to flag transit halts, vehicle breakdowns, or route accidents. Reporting incidents automatically invokes operations salvage protocols.
              </Text>

              {deliveryState !== 'in_transit' ? (
                <View style={styles.emptyContainer}>
                  <Ionicons name="shield-checkmark-outline" size={48} color={THEME.customer} />
                  <Text style={styles.emptyText}>Safety Protocols Optimal</Text>
                  <Text style={styles.emptySubText}>Incident console is only accessible during active transit dispatch.</Text>
                </View>
              ) : (
                <View style={styles.incidentControlArea}>
                  <Text style={styles.inputLabel}>Select Incident Category:</Text>
                  <View style={styles.incidentTypeRow}>
                    <TouchableOpacity 
                      style={[styles.incidentTypeBtn, selectedIncidentType === 'breakdown' && styles.incidentTypeBtnActive]}
                      onPress={() => setSelectedIncidentType('breakdown')}
                    >
                      <Ionicons name="construct-outline" size={18} color={selectedIncidentType === 'breakdown' ? '#070a13' : THEME.textPrimary} />
                      <Text style={[styles.incidentTypeText, selectedIncidentType === 'breakdown' && styles.incidentTypeTextActive]}>Breakdown</Text>
                    </TouchableOpacity>

                    <TouchableOpacity 
                      style={[styles.incidentTypeBtn, selectedIncidentType === 'accident' && styles.incidentTypeBtnActive]}
                      onPress={() => setSelectedIncidentType('accident')}
                    >
                      <Ionicons name="car-sport-outline" size={18} color={selectedIncidentType === 'accident' ? '#070a13' : THEME.textPrimary} />
                      <Text style={[styles.incidentTypeText, selectedIncidentType === 'accident' && styles.incidentTypeTextActive]}>Accident</Text>
                    </TouchableOpacity>

                    <TouchableOpacity 
                      style={[styles.incidentTypeBtn, selectedIncidentType === 'weather' && styles.incidentTypeBtnActive]}
                      onPress={() => setSelectedIncidentType('weather')}
                    >
                      <Ionicons name="thunderstorm-outline" size={18} color={selectedIncidentType === 'weather' ? '#070a13' : THEME.textPrimary} />
                      <Text style={[styles.incidentTypeText, selectedIncidentType === 'weather' && styles.incidentTypeTextActive]}>Weather</Text>
                    </TouchableOpacity>
                  </View>

                  <Text style={styles.inputLabel}>Explain Incident Details:</Text>
                  <TextInput 
                    style={[styles.textInput, { height: 64, textAlignVertical: 'top' }]}
                    multiline={true}
                    numberOfLines={3}
                    placeholder="Provide details for operations dispatchers (e.g. flat tire near Bahnhofstrasse)..."
                    placeholderTextColor={THEME.textMuted}
                    value={incidentDetail}
                    onChangeText={setIncidentDetail}
                  />

                  <TouchableOpacity style={styles.actionBtnAdmin} onPress={handleReportIncident}>
                    <Text style={styles.actionBtnText}>SUBMIT CRITICAL EMERGENCY REPORT</Text>
                    <Ionicons name="alert-circle" size={16} color="#070a13" />
                  </TouchableOpacity>
                </View>
              )}
            </View>
          </View>
        )}

      </ScrollView>

      {/* ----------------- SETTINGS & AUTH MODAL ----------------- */}
      <Modal
        visible={showSettings}
        animationType="slide"
        transparent={true}
        onRequestClose={() => setShowSettings(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.settingsModalCard}>
            <View style={styles.modalHeader}>
              <Text style={styles.settingsTitle}>SETTINGS & AUTHENTICATION</Text>
              <TouchableOpacity style={styles.closeModalBtn} onPress={() => setShowSettings(false)}>
                <Ionicons name="close" size={20} color={THEME.textSecondary} />
              </TouchableOpacity>
            </View>

            <ScrollView contentContainerStyle={styles.settingsScrollContent}>
              {/* Simulator vs Real BFF */}
              <View style={styles.settingsToggleRow}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.toggleLabel}>Simulator Mode</Text>
                  <Text style={styles.toggleDesc}>
                    When active, telemetry logs and dry-ice injections are mocked. Turn off for live BFF integration.
                  </Text>
                </View>
                <Switch 
                  value={useSimulator}
                  onValueChange={(val) => {
                    setUseSimulator(val);
                    if (!val) setIsConnected(false); // require testing connection
                  }}
                  trackColor={{ false: THEME.bgDark, true: THEME.rider }}
                  thumbColor={useSimulator ? THEME.bgDark : THEME.textSecondary}
                />
              </View>

              {/* BFF Configuration inputs */}
              <View style={styles.bffConfigArea}>
                <Text style={styles.inputLabel}>BFF Gateway Server Endpoint:</Text>
                <TextInput 
                  style={styles.textInput}
                  value={bffUrl}
                  onChangeText={setBffUrl}
                  placeholder="e.g. http://192.168.1.100:8081"
                  placeholderTextColor={THEME.textMuted}
                />

                <Text style={styles.inputLabel}>Authorization JWT Token:</Text>
                <TextInput 
                  style={[styles.textInput, { fontSize: 10, fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace' }]}
                  multiline={true}
                  numberOfLines={4}
                  value={jwtToken}
                  onChangeText={setJwtToken}
                  placeholder="Bearer JWT Claims Signature Token"
                  placeholderTextColor={THEME.textMuted}
                />
                
                <TouchableOpacity 
                  style={[styles.testBtn, { backgroundColor: THEME.rider }]} 
                  onPress={testBffConnection}
                  disabled={testLoading}
                >
                  {testLoading ? (
                    <ActivityIndicator size="small" color="#070a13" />
                  ) : (
                    <>
                      <Ionicons name="cloud-outline" size={14} color="#070a13" />
                      <Text style={styles.testBtnText}>TEST BACKEND CONNECTION</Text>
                    </>
                  )}
                </TouchableOpacity>
              </View>

              {/* AUTH FLOW SECTION */}
              <View style={styles.authSection}>
                <Text style={styles.authSectionTitle}>Secure Identity Verification</Text>
                
                {authStep === 'login' ? (
                  <View style={styles.authForm}>
                    <Text style={styles.inputLabel}>BFF Username:</Text>
                    <TextInput 
                      style={styles.textInput}
                      value={authUsername}
                      onChangeText={setAuthUsername}
                      placeholder="e.g. swissuser"
                      placeholderTextColor={THEME.textMuted}
                    />
                    <Text style={styles.inputLabel}>Password:</Text>
                    <TextInput 
                      style={styles.textInput}
                      secureTextEntry={true}
                      value={authPassword}
                      onChangeText={setAuthPassword}
                      placeholder="Security credentials password"
                      placeholderTextColor={THEME.textMuted}
                    />
                    <TouchableOpacity 
                      style={styles.authActionBtn} 
                      onPress={handleBffLogin}
                      disabled={authLoading}
                    >
                      {authLoading ? (
                        <ActivityIndicator size="small" color="#ffffff" />
                      ) : (
                        <Text style={styles.authActionBtnText}>LOG IN & REQUEST MFA PIN</Text>
                      )}
                    </TouchableOpacity>
                  </View>
                ) : (
                  <View style={styles.authForm}>
                    <Text style={styles.mfaInstructionText}>
                      Enter the 6-digit MFA verification PIN printed in the spring-boot backend terminal console.
                    </Text>
                    <TextInput 
                      style={[styles.textInput, { textAlign: 'center', fontSize: 18, letterSpacing: 4 }]}
                      keyboardType="numeric"
                      maxLength={6}
                      value={authOtpCode}
                      onChangeText={setAuthOtpCode}
                      placeholder="••••••"
                      placeholderTextColor={THEME.textMuted}
                    />
                    <TouchableOpacity 
                      style={[styles.authActionBtn, { backgroundColor: THEME.customer }]} 
                      onPress={handleVerifyMfa}
                      disabled={authLoading}
                    >
                      {authLoading ? (
                        <ActivityIndicator size="small" color="#070a13" />
                      ) : (
                        <Text style={[styles.authActionBtnText, { color: '#070a13' }]}>VERIFY MFA & UNLOCK SESSION</Text>
                      )}
                    </TouchableOpacity>
                    <TouchableOpacity 
                      style={[styles.authCancelBtn]} 
                      onPress={() => setAuthStep('login')}
                    >
                      <Text style={styles.authCancelBtnText}>Back to Credentials</Text>
                    </TouchableOpacity>
                  </View>
                )}
              </View>

            </ScrollView>
          </View>
        </View>
      </Modal>

      {/* ----------------- EMERGENCY MODAL ----------------- */}
      <Modal
        visible={showIncidentModal}
        animationType="slide"
        transparent={true}
        onRequestClose={() => setShowIncidentModal(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.settingsModalCard}>
            <View style={styles.modalHeader}>
              <Text style={[styles.settingsTitle, { color: THEME.admin }]}>🚨 REPORT TRANSIT INCIDENT</Text>
              <TouchableOpacity style={styles.closeModalBtn} onPress={() => setShowIncidentModal(false)}>
                <Ionicons name="close" size={20} color={THEME.textSecondary} />
              </TouchableOpacity>
            </View>

            <View style={styles.settingsScrollContent}>
              <Text style={styles.inputLabel}>Incident Type:</Text>
              <View style={styles.incidentTypeRow}>
                <TouchableOpacity 
                  style={[styles.incidentTypeBtn, selectedIncidentType === 'breakdown' && styles.incidentTypeBtnActive]}
                  onPress={() => setSelectedIncidentType('breakdown')}
                >
                  <Text style={[styles.incidentTypeText, selectedIncidentType === 'breakdown' && styles.incidentTypeTextActive]}>E-Bike Breakdown</Text>
                </TouchableOpacity>

                <TouchableOpacity 
                  style={[styles.incidentTypeBtn, selectedIncidentType === 'accident' && styles.incidentTypeBtnActive]}
                  onPress={() => setSelectedIncidentType('accident')}
                >
                  <Text style={[styles.incidentTypeText, selectedIncidentType === 'accident' && styles.incidentTypeTextActive]}>Road Accident</Text>
                </TouchableOpacity>
              </View>

              <Text style={styles.inputLabel}>Explain situation details:</Text>
              <TextInput 
                style={[styles.textInput, { height: 60, textAlignVertical: 'top' }]}
                multiline={true}
                placeholder=" Bahnhofstrasse junction, flat rear tire..."
                placeholderTextColor={THEME.textMuted}
                value={incidentDetail}
                onChangeText={setIncidentDetail}
              />

              <TouchableOpacity style={styles.actionBtnAdmin} onPress={handleReportIncident}>
                <Text style={styles.actionBtnText}>SUBMIT EMERGENCY INCIDENT REPORT</Text>
                <Ionicons name="alert-circle" size={16} color="#070a13" />
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

    </SafeAreaView>
  );
}

// ----------------------------------------------------
// CYBER-INDUSTRIAL STYLING
// ----------------------------------------------------
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: THEME.bgDark,
  },
  header: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: THEME.bgDark,
    borderBottomWidth: 1,
    borderBottomColor: THEME.borderColor,
  },
  brandRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  logoText: {
    fontSize: 14,
    fontWeight: '900',
    color: THEME.textPrimary,
    letterSpacing: 0.5,
  },
  connectionBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 6,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  ledIndicator: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 6,
  },
  connectionText: {
    fontSize: 8,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  subHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 8,
  },
  roleContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  roleText: {
    fontSize: 10,
    fontWeight: '800',
    color: THEME.textSecondary,
    letterSpacing: 0.5,
  },
  settingsBtn: {
    padding: 4,
  },
  metricsContainer: {
    flexDirection: 'row',
    backgroundColor: THEME.bgBody,
    borderBottomWidth: 1,
    borderBottomColor: THEME.borderColor,
    paddingVertical: 10,
  },
  metricItem: {
    flex: 1,
    alignItems: 'center',
    borderRightWidth: 1,
    borderRightColor: 'rgba(255, 255, 255, 0.04)',
  },
  metricLabel: {
    fontSize: 8,
    color: THEME.textMuted,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  metricValue: {
    fontSize: 12,
    fontWeight: '800',
    marginTop: 2,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  content: {
    padding: 16,
    paddingBottom: 40,
    gap: 16,
  },
  tabContent: {
    gap: 16,
  },
  warningBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(245, 158, 11, 0.15)',
    borderColor: THEME.rider,
    borderWidth: 1,
    borderRadius: 8,
    padding: 10,
  },
  warningBannerText: {
    color: THEME.rider,
    fontSize: 10,
    fontWeight: '700',
    flex: 1,
  },
  incidentBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderColor: THEME.admin,
    borderWidth: 1,
    borderRadius: 8,
    padding: 10,
  },
  incidentBannerText: {
    color: THEME.admin,
    fontSize: 10,
    fontWeight: '700',
    flex: 1,
  },
  card: {
    backgroundColor: THEME.bgCard,
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 14,
    padding: 16,
    gap: 14,
  },
  cardHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.04)',
    paddingBottom: 10,
  },
  cardHeaderTitle: {
    fontSize: 13,
    fontWeight: '800',
    color: THEME.textPrimary,
    letterSpacing: 0.5,
  },
  orderLabel: {
    fontSize: 11,
    fontWeight: '800',
    color: THEME.rider,
  },
  cardDescription: {
    color: THEME.textSecondary,
    fontSize: 11,
    lineHeight: 16,
  },
  idleAnimationRow: {
    alignItems: 'center',
    paddingVertical: 20,
    gap: 10,
  },
  radarRing: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: 'rgba(245, 158, 11, 0.08)',
    borderColor: 'rgba(245, 158, 11, 0.3)',
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  idleTitle: {
    fontSize: 14,
    fontWeight: '800',
    color: THEME.textPrimary,
  },
  idleDesc: {
    fontSize: 11,
    color: THEME.textSecondary,
    textAlign: 'center',
    paddingHorizontal: 20,
    lineHeight: 16,
  },
  manualAssignBox: {
    gap: 8,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.04)',
    paddingTop: 12,
  },
  inputLabel: {
    fontSize: 10,
    fontWeight: '800',
    color: THEME.textSecondary,
    letterSpacing: 0.3,
  },
  textInput: {
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
    color: THEME.textPrimary,
    fontSize: 12,
  },
  actionBtnPrimary: {
    backgroundColor: THEME.rider,
    borderRadius: 8,
    paddingVertical: 12,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
    marginTop: 4,
  },
  actionBtnTransit: {
    backgroundColor: THEME.engine,
    borderRadius: 8,
    paddingVertical: 12,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
    marginTop: 4,
  },
  actionBtnAdmin: {
    backgroundColor: THEME.admin,
    borderRadius: 8,
    paddingVertical: 12,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
    marginTop: 4,
  },
  actionBtnText: {
    color: '#070a13',
    fontWeight: '900',
    fontSize: 11,
    letterSpacing: 0.5,
  },
  orderInfoBox: {
    backgroundColor: 'rgba(7, 10, 19, 0.4)',
    borderRadius: 10,
    padding: 12,
    borderColor: THEME.borderColor,
    borderWidth: 1,
    gap: 8,
  },
  infoLabel: {
    fontSize: 8,
    color: THEME.textMuted,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  infoText: {
    fontSize: 12,
    color: THEME.textPrimary,
    fontWeight: '600',
  },
  itemsPreviewRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginVertical: 2,
  },
  itemEmojiBadge: {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 6,
    paddingVertical: 4,
    paddingHorizontal: 8,
  },
  emojiBadgeText: {
    color: THEME.textPrimary,
    fontSize: 11,
    fontWeight: '700',
  },
  statusBadgeAccepted: {
    backgroundColor: 'rgba(59, 130, 246, 0.15)',
    borderColor: THEME.inventory,
    borderWidth: 1,
    borderRadius: 6,
    paddingVertical: 2,
    paddingHorizontal: 8,
    color: THEME.inventory,
    fontSize: 8,
    fontWeight: '800',
  },
  telemetryGrid: {
    gap: 8,
  },
  telemetryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.02)',
    paddingBottom: 6,
  },
  telemetryLabel: {
    color: THEME.textSecondary,
    fontSize: 11,
  },
  telemetryVal: {
    color: THEME.textPrimary,
    fontSize: 11,
    fontWeight: '700',
  },
  slaTimerBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 6,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  slaTimerText: {
    fontSize: 10,
    fontWeight: '800',
    color: THEME.textSecondary,
  },
  progressSection: {
    gap: 6,
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  progressLabel: {
    fontSize: 10,
    color: THEME.textSecondary,
    fontWeight: '700',
  },
  progressVal: {
    fontSize: 10,
    color: THEME.rider,
    fontWeight: '800',
  },
  progressBarBg: {
    height: 8,
    backgroundColor: '#020408',
    borderRadius: 4,
    overflow: 'hidden',
  },
  progressBarFill: {
    height: '100%',
    backgroundColor: THEME.rider,
  },
  gpsCoordText: {
    fontSize: 9,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    color: THEME.textMuted,
  },
  coldChainTelemetryBlock: {
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
    gap: 8,
  },
  tempDisplayHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  tempLabel: {
    fontSize: 8,
    color: THEME.textMuted,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  spoiledWarning: {
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderColor: THEME.admin,
    borderWidth: 1,
    borderRadius: 4,
    paddingVertical: 1,
    paddingHorizontal: 4,
  },
  spoiledWarningText: {
    color: THEME.admin,
    fontSize: 8,
    fontWeight: '900',
  },
  tempDigitalRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  tempValue: {
    fontSize: 28,
    fontWeight: '900',
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  humidityBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  humidityText: {
    color: THEME.textPrimary,
    fontSize: 12,
    fontWeight: '700',
  },
  coldChainDesc: {
    fontSize: 9,
    color: THEME.textSecondary,
    lineHeight: 12,
  },
  coolantBtn: {
    backgroundColor: THEME.rider,
    borderRadius: 6,
    paddingVertical: 8,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
  },
  coolantBtnAlert: {
    backgroundColor: THEME.rider,
    shadowColor: THEME.rider,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.8,
    shadowRadius: 10,
    elevation: 5,
  },
  coolantBtnText: {
    color: '#070a13',
    fontWeight: '800',
    fontSize: 10,
    letterSpacing: 0.3,
  },
  emergencyIncidentSection: {
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.04)',
    paddingTop: 8,
  },
  incidentTriggerBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 6,
  },
  incidentTriggerText: {
    color: THEME.admin,
    fontSize: 9,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  deliveredHeader: {
    alignItems: 'center',
    paddingVertical: 10,
    gap: 8,
  },
  deliveredBadgeRing: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: 'rgba(16, 185, 129, 0.1)',
    borderColor: THEME.customer,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  celebrationTitle: {
    color: THEME.textPrimary,
    fontSize: 13,
    fontWeight: '800',
    textAlign: 'center',
  },
  celebrationSub: {
    color: THEME.textSecondary,
    fontSize: 11,
    textAlign: 'center',
    lineHeight: 14,
  },
  pillInputRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 8,
  },
  miniTab: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
  },
  miniTabActive: {
    backgroundColor: THEME.rider,
    borderColor: THEME.rider,
  },
  miniTabText: {
    color: THEME.textSecondary,
    fontSize: 11,
    fontWeight: '700',
  },
  miniTabTextActive: {
    color: '#070a13',
    fontWeight: '800',
  },
  clearLogsBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 6,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  clearLogsBtnText: {
    color: THEME.textSecondary,
    fontSize: 8,
    fontWeight: '800',
  },
  consoleContainer: {
    backgroundColor: '#020408',
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 10,
    padding: 10,
    height: 250,
  },
  consolePlaceholder: {
    color: THEME.textMuted,
    fontSize: 10,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    textAlign: 'center',
    marginTop: 100,
  },
  consoleLine: {
    color: THEME.textPrimary,
    fontSize: 10,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
    marginBottom: 4,
    lineHeight: 14,
  },
  incidentControlArea: {
    gap: 12,
  },
  incidentTypeRow: {
    flexDirection: 'row',
    gap: 8,
  },
  incidentTypeBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: THEME.borderColor,
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    borderRadius: 8,
  },
  incidentTypeBtnActive: {
    backgroundColor: THEME.admin,
    borderColor: THEME.admin,
  },
  incidentTypeText: {
    color: THEME.textPrimary,
    fontSize: 10,
    fontWeight: '700',
  },
  incidentTypeTextActive: {
    color: '#070a13',
    fontWeight: '800',
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 40,
    gap: 10,
  },
  emptyText: {
    fontSize: 14,
    fontWeight: '800',
    color: THEME.textPrimary,
  },
  emptySubText: {
    fontSize: 11,
    color: THEME.textSecondary,
    textAlign: 'center',
    paddingHorizontal: 20,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 16,
  },
  settingsModalCard: {
    backgroundColor: THEME.bgBody,
    borderColor: THEME.borderColor,
    borderWidth: 1,
    borderRadius: 16,
    width: '100%',
    maxHeight: '90%',
    padding: 16,
    gap: 16,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: THEME.borderColor,
    paddingBottom: 8,
  },
  settingsTitle: {
    fontSize: 14,
    fontWeight: '900',
    color: THEME.textPrimary,
    letterSpacing: 0.5,
  },
  closeModalBtn: {
    padding: 4,
  },
  settingsScrollContent: {
    gap: 12,
    paddingBottom: 10,
  },
  settingsToggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    padding: 10,
    borderRadius: 10,
    borderColor: THEME.borderColor,
    borderWidth: 1,
  },
  toggleLabel: {
    color: THEME.textPrimary,
    fontSize: 12,
    fontWeight: '700',
  },
  toggleDesc: {
    color: THEME.textSecondary,
    fontSize: 9,
    marginTop: 2,
    paddingRight: 10,
  },
  bffConfigArea: {
    gap: 8,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.04)',
    paddingBottom: 12,
  },
  testBtn: {
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 6,
    marginTop: 4,
  },
  testBtnText: {
    color: '#070a13',
    fontWeight: '900',
    fontSize: 11,
  },
  authSection: {
    gap: 10,
    marginTop: 4,
  },
  authSectionTitle: {
    fontSize: 12,
    fontWeight: '800',
    color: THEME.rider,
    letterSpacing: 0.3,
  },
  authForm: {
    gap: 8,
  },
  authActionBtn: {
    backgroundColor: THEME.rider,
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 4,
  },
  authActionBtnText: {
    color: '#070a13',
    fontWeight: '900',
    fontSize: 11,
  },
  mfaInstructionText: {
    fontSize: 10,
    color: THEME.textSecondary,
    lineHeight: 14,
    marginBottom: 4,
  },
  authCancelBtn: {
    alignItems: 'center',
    paddingVertical: 6,
  },
  authCancelBtnText: {
    color: THEME.textSecondary,
    fontSize: 11,
    fontWeight: '600',
  },
});
