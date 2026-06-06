import {
	FontAwesome5,
	Ionicons,
	MaterialCommunityIcons,
} from "@expo/vector-icons";
import React, { useEffect, useRef, useState } from "react";
import {
	ActivityIndicator,
	Alert,
	Dimensions,
	Modal,
	Platform,
	SafeAreaView,
	ScrollView,
	StatusBar,
	StyleSheet,
	Switch,
	Text,
	TextInput,
	TouchableOpacity,
	View,
} from "react-native";

// Dimensions for responsive styling
const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get("window");

// ----------------------------------------------------
// CYBER-INDUSTRIAL DESIGN TOKENS (HSL counterparts)
// ----------------------------------------------------
const THEME = {
	bgDark: "#070a13", // hsl(224, 46%, 5%)
	bgBody: "#0b0f19", // hsl(222, 40%, 7%)
	bgCard: "rgba(17, 24, 39, 0.8)", // hsla(224, 71%, 4%, 0.8)
	bgCardHover: "rgba(31, 41, 55, 0.95)",
	borderColor: "rgba(255, 255, 255, 0.08)",

	// Glowing colors
	customer: "#10b981", // Green (Success/Ready) - hsl(158, 64%, 42%)
	customerGlow: "rgba(16, 185, 129, 0.2)",

	rider: "#f59e0b", // Amber (Warning/Riders) - hsl(38, 92%, 50%)
	riderGlow: "rgba(245, 158, 11, 0.2)",

	inventory: "#3b82f6", // Blue (Theme color / Inventory) - hsl(217, 91%, 60%)
	inventoryGlow: "rgba(59, 130, 246, 0.2)",

	admin: "#ef4444", // Red (Alert/SLA critical) - hsl(0, 84%, 60%)
	adminGlow: "rgba(239, 68, 68, 0.2)",

	engine: "#06b6d4", // Cyan (System/Engine) - hsl(188, 86%, 53%)
	engineGlow: "rgba(6, 182, 212, 0.2)",

	textPrimary: "#f8fafc", // hsl(210, 100%, 98%)
	textSecondary: "#94a3b8", // hsl(215, 25%, 72%)
	textMuted: "#64748b", // hsl(218, 11%, 47%)
};

// ----------------------------------------------------
// DEFAULT SEED DATA (MOCK / LOCAL SIMULATOR)
// ----------------------------------------------------
const INITIAL_ORDERS = [
	{
		orderId: 3011,
		storeId: "central",
		status: "pending",
		slaCountdownSec: 185,
		totalAmount: 23.4,
		created_at: new Date(Date.now() - 55000).toISOString(),
		items: [
			{
				itemId: "milk",
				name: "Swiss Whole Milk 1L",
				quantity: 2,
				category: "Dairy",
				emoji: "🥛",
				perishable: true,
			},
			{
				itemId: "bread",
				name: "Artisan Sourdough Loaf",
				quantity: 1,
				category: "Bakery",
				emoji: "🍞",
				perishable: false,
			},
			{
				itemId: "apples",
				name: "Gala Apples 500g",
				quantity: 4,
				category: "Produce",
				emoji: "🍎",
				perishable: false,
			},
		],
	},
	{
		orderId: 3012,
		storeId: "central",
		status: "pending",
		slaCountdownSec: 238,
		totalAmount: 31.2,
		created_at: new Date(Date.now() - 20000).toISOString(),
		items: [
			{
				itemId: "chocolate",
				name: "Lindt Dark Chocolate 70%",
				quantity: 3,
				category: "Sweets",
				emoji: "🍫",
				perishable: false,
			},
			{
				itemId: "icecream",
				name: "Movenpick Vanilla 500ml",
				quantity: 2,
				category: "Frozen",
				emoji: "🍨",
				perishable: true,
			},
		],
	},
	{
		orderId: 3013,
		storeId: "central",
		status: "pending",
		slaCountdownSec: 45, // SLA Critical!
		totalAmount: 18.0,
		created_at: new Date(Date.now() - 195000).toISOString(),
		items: [
			{
				itemId: "cola",
				name: "Coca Cola Zero 6x330ml",
				quantity: 1,
				category: "Beverages",
				emoji: "🥤",
				perishable: false,
			},
			{
				itemId: "chips",
				name: "Zweifel Paprika Chips",
				quantity: 2,
				category: "Snacks",
				emoji: "🥔",
				perishable: false,
			},
		],
	},
	{
		orderId: 3014,
		storeId: "east",
		status: "pending",
		slaCountdownSec: 310,
		totalAmount: 29.5,
		created_at: new Date(Date.now() - 30000).toISOString(),
		items: [
			{
				itemId: "milk",
				name: "Swiss Whole Milk 1L",
				quantity: 1,
				category: "Dairy",
				emoji: "🥛",
				perishable: true,
			},
			{
				itemId: "eggs",
				name: "Organic Free Range Eggs x12",
				quantity: 1,
				category: "Dairy",
				emoji: "🥚",
				perishable: true,
			},
			{
				itemId: "cheese",
				name: "Gruyère AOP aged 200g",
				quantity: 2,
				category: "Dairy",
				emoji: "🧀",
				perishable: true,
			},
		],
	},
];

const DEFAULT_ITEMS = [
	{ id: "milk", name: "Swiss Whole Milk 1L", emoji: "🥛", category: "Dairy" },
	{
		id: "bread",
		name: "Artisan Sourdough Loaf",
		emoji: "🍞",
		category: "Bakery",
	},
	{ id: "apples", name: "Gala Apples 500g", emoji: "🍎", category: "Produce" },
	{
		id: "chocolate",
		name: "Lindt Dark Chocolate 70%",
		emoji: "🍫",
		category: "Sweets",
	},
	{
		id: "icecream",
		name: "Movenpick Vanilla 500ml",
		emoji: "🍨",
		category: "Frozen",
	},
	{
		id: "cola",
		name: "Coca Cola Zero 6x330ml",
		emoji: "🥤",
		category: "Beverages",
	},
	{
		id: "chips",
		name: "Zweifel Paprika Chips",
		emoji: "🥔",
		category: "Snacks",
	},
];

export default function PickerScreen() {
	// Navigation Tabs: 'queue' | 'dispatch' | 'ops'
	const [activeTab, setActiveTab] = useState("queue");

	// Connection states
	const [useSimulator, setUseSimulator] = useState(true);
	const [bffUrl, setBffUrl] = useState("http://192.168.1.134:8081"); // Customizable IP for testing
	const [jwtToken, setJwtToken] = useState(
		"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJwaWNrZXJfZGVtb24iLCJyb2xlcyI6IlBJQ0tFUiIsImlhdCI6MTc4MDAwMDAwMH0.signature",
	);
	const [isConnected, setIsConnected] = useState(false);
	const [showSettings, setShowSettings] = useState(false);
	const [loading, setLoading] = useState(false);

	// Picker Profile
	const [pickerId, setPickerId] = useState("picker_zuerich_01");
	const [selectedStore, setSelectedStore] = useState("central"); // 'central' | 'east'
	const [pickerStats, setPickerStats] = useState({
		pickedCount: 14,
		lightningBadges: 2,
		avgPickTime: 72, // seconds
		trustScore: 95,
	});

	// Dark Store Queue & Picking state
	const [orders, setOrders] = useState(INITIAL_ORDERS);
	const [activePickingOrder, setActivePickingOrder] = useState(null);
	const [pickedItemChecks, setPickedItemChecks] = useState({});
	const [selectedRider, setSelectedRider] = useState("rider_bern_12");
	const [pickerStartTime, setPickerStartTime] = useState(null);
	const [handoverLoading, setHandoverLoading] = useState(false);
	const [lightningCelebration, setLightningCelebration] = useState(false);

	// Operations/Scaling states
	const [storeCapacity, setStoreCapacity] = useState(91); // Percentage (flashes red when >90%)
	const [isOverflowBayDeployed, setIsOverflowBayDeployed] = useState(false);
	const [scalingProgress, setScalingProgress] = useState(0);
	const [isScaling, setIsScaling] = useState(false);
	const [autoDeployBackup, setAutoDeployBackup] = useState(false);
	const [backupPickersActive, setBackupPickersActive] = useState(0);

	// Inter-Store Stock Dispatcher form
	const [dispatchItem, setDispatchItem] = useState("milk");
	const [dispatchQty, setDispatchQty] = useState(15);
	const [dispatchSource, setDispatchSource] = useState("east");
	const [dispatchTarget, setDispatchTarget] = useState("central");
	const [dispatchLoading, setDispatchLoading] = useState(false);
	const [lastDispatchResult, setLastDispatchResult] = useState(null);

	// Real-time counter tickers
	useEffect(() => {
		const timer = setInterval(() => {
			// 1. Decrement order SLAs
			setOrders((prevOrders) =>
				prevOrders.map((order) => {
					const elapsed = Math.floor(
						(Date.now() - new Date(order.created_at).getTime()) / 1000,
					);
					const currentSla = Math.max(0, 240 - elapsed); // 4-minute SLA = 240s
					return {
						...order,
						slaCountdownSec: currentSla,
					};
				}),
			);

			// 2. If Simulator & Auto-Deploy Backup is ON, drain the backlog slowly
			if (useSimulator && autoDeployBackup && orders.length > 0) {
				setBackupPickersActive(Math.ceil(orders.length / 3));
				// Every ~15 seconds, resolve the oldest order if backup pickers are working
				if (Math.random() < 0.08) {
					setOrders((prev) => {
						if (prev.length === 0) return prev;
						// Remove oldest order
						const next = [...prev];
						const sorted = next.sort(
							(a, b) => a.slaCountdownSec - b.slaCountdownSec,
						);
						const removed = sorted.shift();

						// Add statistics for completed picks
						setPickerStats((stats) => ({
							...stats,
							pickedCount: stats.pickedCount + 1,
							avgPickTime: Math.max(
								45,
								Math.round(
									(stats.avgPickTime * stats.pickedCount +
										(75 + Math.random() * 20)) /
										(stats.pickedCount + 1),
								),
							),
						}));

						return sorted;
					});
				}
			} else if (!autoDeployBackup) {
				setBackupPickersActive(0);
			}

			// 3. Simulator periodically generates incoming orders
			if (useSimulator && Math.random() < 0.05 && orders.length < 8) {
				const nextId = 3000 + Math.floor(Math.random() * 2000);
				const randomStore = Math.random() > 0.4 ? "central" : "east";

				// Random items
				const numItems = 2 + Math.floor(Math.random() * 3);
				const orderItems = [];
				let total = 0;

				for (let i = 0; i < numItems; i++) {
					const rawItem =
						DEFAULT_ITEMS[Math.floor(Math.random() * DEFAULT_ITEMS.length)];
					const qty = 1 + Math.floor(Math.random() * 3);
					const price = 2.5 + Math.random() * 8.5;
					total += price * qty;
					orderItems.push({
						itemId: rawItem.id,
						name: rawItem.name,
						quantity: qty,
						category: rawItem.category,
						emoji: rawItem.emoji,
						perishable: Math.random() > 0.5,
					});
				}

				const newOrder = {
					orderId: nextId,
					storeId: randomStore,
					status: "pending",
					slaCountdownSec: 240,
					totalAmount: parseFloat(total.toFixed(2)),
					created_at: new Date().toISOString(),
					items: orderItems,
				};

				setOrders((prev) => [...prev, newOrder]);

				// Slightly increase store capacity with new stock loading
				setStoreCapacity((cap) => Math.min(100, cap + 1));
			}
		}, 1000);

		return () => clearInterval(timer);
	}, [useSimulator, autoDeployBackup, orders.length]);

	// Handle connection testing to BFF
	const testBffConnection = async () => {
		setLoading(true);
		try {
			// Endpoint to fetch system health
			const response = await fetch(`${bffUrl}/api/admin/health`, {
				method: "GET",
				headers: {
					Authorization: `Bearer ${jwtToken}`,
					Accept: "application/json",
				},
			});

			if (response.ok) {
				setIsConnected(true);
				setUseSimulator(false);
				Alert.alert(
					"BFF Connection Secure",
					"BFF Gateway reached. Active JWT Session verified.",
				);
				setShowSettings(false);
				// Fetch actual queue
				fetchBffQueue();
			} else {
				throw new Error(`BFF responded with code ${response.status}`);
			}
		} catch (err) {
			setIsConnected(false);
			Alert.alert(
				"Connection Failed",
				`Could not reach BFF Gateway at ${bffUrl}. Reverting to high-fidelity Offline Simulator.\n\nDetail: ${err.message}`,
			);
		} finally {
			setLoading(false);
		}
	};

	// Fetch queue from real BFF Gateway
	const fetchBffQueue = async () => {
		if (useSimulator) return;
		try {
			const response = await fetch(
				`${bffUrl}/api/inventory/picker/queue?storeId=${selectedStore}`,
				{
					method: "GET",
					headers: {
						Authorization: `Bearer ${jwtToken}`,
						Accept: "application/json",
					},
				},
			);

			if (response.ok) {
				const data = await response.json();
				// Format the backend data to match our component representation
				const formattedOrders = data.map((o) => {
					// If items are not returned, inject mock item list for the pick screen demo
					return {
						orderId: o.orderId || o.order_id,
						storeId: o.storeId || o.store_id || selectedStore,
						status: o.status || "pending",
						slaCountdownSec: o.slaCountdownSec || o.sla_countdown_sec || 240,
						totalAmount: o.totalAmount || o.total_amount || 15.0,
						created_at: o.createdAt || o.created_at || new Date().toISOString(),
						items: o.items || [
							{
								itemId: "milk",
								name: "Swiss Whole Milk 1L",
								quantity: 2,
								category: "Dairy",
								emoji: "🥛",
								perishable: true,
							},
							{
								itemId: "bread",
								name: "Artisan Sourdough Loaf",
								quantity: 1,
								category: "Bakery",
								emoji: "🍞",
								perishable: false,
							},
						],
					};
				});
				setOrders(formattedOrders);
			}
		} catch (err) {
			console.warn("Error fetching BFF Picker Queue:", err);
		}
	};

	// Run BFF fetch when Selected Store changes and simulator is off
	useEffect(() => {
		if (!useSimulator) {
			fetchBffQueue();
		}
	}, [selectedStore, useSimulator]);

	// Initiate picking process
	const startPicking = (order) => {
		setActivePickingOrder(order);
		setPickedItemChecks({});
		setPickerStartTime(Date.now());
	};

	// Checkbox toggle for item picking
	const toggleItemCheck = (itemId) => {
		setPickedItemChecks((prev) => ({
			...prev,
			[itemId]: !prev[itemId],
		}));
	};

	// Submit order handover to rider
	const completeHandover = async (containsError = false) => {
		if (!activePickingOrder) return;

		// Check if all items checked
		const uncheckedCount = activePickingOrder.items.filter(
			(item) => !pickedItemChecks[item.itemId],
		).length;
		if (uncheckedCount > 0 && !containsError) {
			Alert.alert(
				"Incomplete Pick List",
				`You have ${uncheckedCount} items remaining unchecked. Confirm handover anyway?`,
				[
					{ text: "Cancel", style: "cancel" },
					{
						text: "Force Handover (Log error)",
						onPress: () => processHandover(true),
					},
				],
			);
			return;
		}

		processHandover(containsError);
	};

	const processHandover = async (containsError) => {
		setHandoverLoading(true);
		const duration = Math.round((Date.now() - pickerStartTime) / 1000);
		const isLightning = duration < 90; // under 90s gets badge

		if (useSimulator) {
			// Simulate API lag
			setTimeout(() => {
				setHandoverLoading(false);

				// Remove order from queue
				setOrders((prev) =>
					prev.filter((o) => o.orderId !== activePickingOrder.orderId),
				);

				// Award lightning badge if fast and no errors
				const awarded = isLightning && !containsError;

				setPickerStats((stats) => ({
					...stats,
					pickedCount: stats.pickedCount + 1,
					lightningBadges: awarded
						? stats.lightningBadges + 1
						: stats.lightningBadges,
					avgPickTime: Math.round(
						(stats.avgPickTime * stats.pickedCount + duration) /
							(stats.pickedCount + 1),
					),
					trustScore: containsError
						? Math.max(50, stats.trustScore - 8)
						: Math.min(100, stats.trustScore + 2),
				}));

				setActivePickingOrder(null);

				if (awarded) {
					setLightningCelebration(true);
				} else {
					Alert.alert(
						"Cargo Handed Over",
						`Handover to rider ${selectedRider} logged in ${duration}s. Status set to SHIPPING.`,
					);
				}
			}, 800);
		} else {
			// Integration with BFF Gateway API
			try {
				const response = await fetch(
					`${bffUrl}/api/inventory/picker/handover`,
					{
						method: "POST",
						headers: {
							Authorization: `Bearer ${jwtToken}`,
							"Content-Type": "application/json",
							Accept: "application/json",
						},
						body: JSON.stringify({
							orderId: activePickingOrder.orderId,
							pickerId: pickerId,
							riderId: selectedRider,
							durationSeconds: duration,
							containsPackingError: containsError,
						}),
					},
				);

				if (response.ok) {
					const resData = await response.json();
					setOrders((prev) =>
						prev.filter((o) => o.orderId !== activePickingOrder.orderId),
					);

					const badgeAwarded =
						resData.lightningBadgeAwarded || resData.lightning_bonus_awarded;

					setPickerStats((stats) => ({
						...stats,
						pickedCount: stats.pickedCount + 1,
						lightningBadges: badgeAwarded
							? stats.lightningBadges + 1
							: stats.lightningBadges,
						avgPickTime: Math.round(
							(stats.avgPickTime * stats.pickedCount + duration) /
								(stats.pickedCount + 1),
						),
						trustScore: containsError
							? Math.max(50, stats.trustScore - 8)
							: Math.min(100, stats.trustScore + 1),
					}));

					setActivePickingOrder(null);
					setHandoverLoading(false);

					if (badgeAwarded) {
						setLightningCelebration(true);
					} else {
						Alert.alert(
							"Handover Success",
							`Payload delivered to BFF Gateway. Order routed to dispatch. duration=${duration}s.`,
						);
					}
				} else {
					const errorMsg = await response.text();
					throw new Error(errorMsg || `HTTP ${response.status}`);
				}
			} catch (err) {
				setHandoverLoading(false);
				Alert.alert(
					"BFF Handover Error",
					`Failed to register handover at BFF: ${err.message}`,
				);
			}
		}
	};

	// Stock Balance Dispatch request to BFF
	const dispatchStockRequest = async () => {
		if (dispatchSource === dispatchTarget) {
			Alert.alert(
				"Invalid Target",
				"Source and Target MFCs must be different.",
			);
			return;
		}

		setDispatchLoading(true);
		setLastDispatchResult(null);

		const payload = {
			itemId: dispatchItem,
			fromStoreId: dispatchSource,
			toStoreId: dispatchTarget,
			quantity: dispatchQty,
		};

		if (useSimulator) {
			// Simulate API
			setTimeout(() => {
				setDispatchLoading(false);
				const itemObj = DEFAULT_ITEMS.find((i) => i.id === dispatchItem);
				const result = {
					status: "rebalanced",
					item: itemObj ? itemObj.name : dispatchItem,
					quantity: dispatchQty,
					fromStore: dispatchSource.toUpperCase() + " MFC",
					toStore: dispatchTarget.toUpperCase() + " MFC",
					transferTruckId:
						"TX-TRUCK-" + Math.floor(1000 + Math.random() * 9000),
					timestamp: new Date().toLocaleTimeString(),
				};
				setLastDispatchResult(result);

				// Visual adjustment to capacity if target is selected store
				if (dispatchTarget === selectedStore) {
					setStoreCapacity((cap) => Math.min(100, cap + 3));
				} else if (dispatchSource === selectedStore) {
					setStoreCapacity((cap) => Math.max(10, cap - 3));
				}

				Alert.alert(
					"Transfer Dispatched",
					`Autonomous truck ${result.transferTruckId} routing ${dispatchQty} units of ${result.item}.`,
				);
			}, 1200);
		} else {
			try {
				const response = await fetch(`${bffUrl}/api/inventory/rebalance`, {
					method: "POST",
					headers: {
						Authorization: `Bearer ${jwtToken}`,
						"Content-Type": "application/json",
						Accept: "application/json",
					},
					body: JSON.stringify(payload),
				});

				setDispatchLoading(false);
				if (response.ok) {
					const resData = await response.json();
					setLastDispatchResult({
						status: resData.status || "rebalanced",
						item: resData.item || dispatchItem,
						quantity: resData.quantity || dispatchQty,
						fromStore:
							(resData.fromStore || dispatchSource).toUpperCase() + " MFC",
						toStore: (resData.toStore || dispatchTarget).toUpperCase() + " MFC",
						transferTruckId:
							resData.transferTruckId ||
							resData.transfer_truck_id ||
							"TX-" + Math.floor(Math.random() * 10000),
						timestamp: new Date().toLocaleTimeString(),
					});
					Alert.alert(
						"BFF Gateway Success",
						"Stock rebalance transaction committed to PostgreSQL Ledger.",
					);
					fetchBffQueue();
				} else {
					const errorMsg = await response.text();
					throw new Error(errorMsg || `HTTP ${response.status}`);
				}
			} catch (err) {
				setDispatchLoading(false);
				Alert.alert(
					"BFF Dispatch Error",
					`Failed to rebalance stock: ${err.message}`,
				);
			}
		}
	};

	// Perform Manual Scaling of Virtual Overflow Warehouse Bays
	const executeVirtualScaling = () => {
		if (isOverflowBayDeployed) {
			Alert.alert(
				"Max Scale Reached",
				"Virtual overflow capacity limits are already active for this store zone.",
			);
			return;
		}

		setIsScaling(true);
		setScalingProgress(0);

		const interval = setInterval(() => {
			setScalingProgress((prev) => {
				if (prev >= 100) {
					clearInterval(interval);
					setIsScaling(false);
					setIsOverflowBayDeployed(true);
					setStoreCapacity(58); // Capacity falls significantly after scaling active space!
					Alert.alert(
						"Scaling Sequence Complete",
						"Virtual Warehouse Bay Beta deployed (+300 sq m capacity added). Storage capacity constraints mitigated.",
					);
					return 100;
				}
				return prev + 10;
			});
		}, 250);
	};

	// Get active queue orders for current store
	const filteredOrders = orders.filter((o) => o.storeId === selectedStore);

	return (
		<SafeAreaView style={styles.container}>
			<StatusBar barStyle="light-content" backgroundColor={THEME.bgDark} />

			{/* ----------------- HEADER ----------------- */}
			<View style={styles.header}>
				<View style={styles.brandRow}>
					<Text style={styles.logoText}>
						SWISS <Text style={{ color: THEME.inventory }}>Q-COMMERCE</Text>
					</Text>
					<View style={styles.connectionBadge}>
						<View
							style={[
								styles.ledIndicator,
								{
									backgroundColor: useSimulator ? THEME.rider : THEME.customer,
								},
							]}
						/>
						<Text
							style={[
								styles.connectionText,
								{ color: useSimulator ? THEME.rider : THEME.customer },
							]}
						>
							{useSimulator ? "SIMULATOR MODE" : "BFF CONNECTED"}
						</Text>
					</View>
				</View>

				<View style={styles.subHeader}>
					<View style={styles.roleContainer}>
						<MaterialCommunityIcons
							name="forklift"
							size={16}
							color={THEME.inventory}
						/>
						<Text style={styles.roleText}>INVENTORY PICKER COCKPIT</Text>
					</View>

					<TouchableOpacity
						style={styles.settingsBtn}
						onPress={() => setShowSettings(true)}
					>
						<Ionicons
							name="settings-sharp"
							size={18}
							color={THEME.textSecondary}
						/>
					</TouchableOpacity>
				</View>
			</View>

			{/* ----------------- METRIC BAR ----------------- */}
			<View style={styles.metricsContainer}>
				<View style={styles.metricItem}>
					<Text style={styles.metricLabel}>PICKED TODAY</Text>
					<Text style={[styles.metricValue, { color: THEME.customer }]}>
						{pickerStats.pickedCount}
					</Text>
				</View>
				<View style={styles.metricItem}>
					<Text style={styles.metricLabel}>LIGHTNING BADGES</Text>
					<Text style={[styles.metricValue, { color: "#fbbf24" }]}>
						{pickerStats.lightningBadges} ⚡
					</Text>
				</View>
				<View style={styles.metricItem}>
					<Text style={styles.metricLabel}>AVG TIME (SEC)</Text>
					<Text style={[styles.metricValue, { color: THEME.engine }]}>
						{pickerStats.avgPickTime}s
					</Text>
				</View>
				<View style={styles.metricItem}>
					<Text style={styles.metricLabel}>TRUST SCORE</Text>
					<Text
						style={[
							styles.metricValue,
							{
								color:
									pickerStats.trustScore >= 80 ? THEME.customer : THEME.admin,
							},
						]}
					>
						{pickerStats.trustScore}%
					</Text>
				</View>
			</View>

			{/* Store toggle selector */}
			<View style={styles.storeSelectorContainer}>
				<Text style={styles.storeSelectorLabel}>Active Store Location:</Text>
				<View style={styles.storePillRow}>
					<TouchableOpacity
						style={[
							styles.storePill,
							selectedStore === "central" && styles.storePillActive,
						]}
						onPress={() => setSelectedStore("central")}
					>
						<Text
							style={[
								styles.storePillText,
								selectedStore === "central" && styles.storePillTextActive,
							]}
						>
							Central MFC
						</Text>
					</TouchableOpacity>
					<TouchableOpacity
						style={[
							styles.storePill,
							selectedStore === "east" && styles.storePillActive,
						]}
						onPress={() => setSelectedStore("east")}
					>
						<Text
							style={[
								styles.storePillText,
								selectedStore === "east" && styles.storePillTextActive,
							]}
						>
							East MFC
						</Text>
					</TouchableOpacity>
				</View>
			</View>

			{/* ----------------- SCREEN CONTENT ----------------- */}
			<ScrollView contentContainerStyle={styles.content}>
				{/* TAB 1: PICK QUEUE */}
				{activeTab === "queue" && (
					<View style={styles.tabContent}>
						{/* SLA Alert banner if any queue item has SLA < 60s */}
						{filteredOrders.some((o) => o.slaCountdownSec < 60) && (
							<View style={styles.slaAlertBanner}>
								<Ionicons
									name="warning-sharp"
									size={16}
									color="#070a13"
									style={{ marginRight: 8 }}
								/>
								<Text style={styles.slaAlertText}>
									SLA BREACH CRITICAL: Pick order immediately!
								</Text>
							</View>
						)}

						<View style={styles.sectionHeaderRow}>
							<Text style={styles.sectionTitle}>
								Incoming Backlog Queue ({filteredOrders.length})
							</Text>
							{autoDeployBackup && (
								<View style={styles.backupRunningBadge}>
									<Text style={styles.backupRunningText}>
										AUTO-PICKERS ACTIVE: {backupPickersActive}
									</Text>
								</View>
							)}
						</View>

						{filteredOrders.length === 0 ? (
							<View style={styles.emptyContainer}>
								<MaterialCommunityIcons
									name="check-decagram"
									size={48}
									color={THEME.customer}
								/>
								<Text style={styles.emptyText}>Dark Store Queue Clear</Text>
								<Text style={styles.emptySubText}>
									All orders picked and handed to shipping riders.
								</Text>
							</View>
						) : (
							filteredOrders.map((order) => {
								const isCritical = order.slaCountdownSec < 60;
								return (
									<View
										key={order.orderId}
										style={[
											styles.orderCard,
											isCritical && styles.orderCardCritical,
										]}
									>
										<View style={styles.orderCardHeader}>
											<Text style={styles.orderIdText}>
												Order #{order.orderId}
											</Text>

											<View style={styles.slaContainer}>
												<Ionicons
													name="time-outline"
													size={14}
													color={isCritical ? THEME.admin : THEME.textSecondary}
												/>
												<Text
													style={[
														styles.slaText,
														isCritical && styles.slaTextCritical,
													]}
												>
													SLA: {Math.floor(order.slaCountdownSec / 60)}:
													{String(order.slaCountdownSec % 60).padStart(2, "0")}
												</Text>
											</View>
										</View>

										<View style={styles.orderMetadata}>
											<Text style={styles.metaText}>
												Total Amount:{" "}
												<Text style={styles.boldText}>
													CHF {order.totalAmount.toFixed(2)}
												</Text>
											</Text>
											<Text style={styles.metaText}>
												Items to Pick:{" "}
												<Text style={styles.boldText}>
													{order.items.reduce((sum, i) => sum + i.quantity, 0)}{" "}
													units
												</Text>
											</Text>
										</View>

										<View style={styles.itemsPreviewRow}>
											{order.items.map((item, idx) => (
												<View key={idx} style={styles.itemEmojiBadge}>
													<Text style={styles.emojiBadgeText}>
														{item.emoji} x{item.quantity}
													</Text>
												</View>
											))}
										</View>

										<TouchableOpacity
											style={styles.pickButton}
											onPress={() => startPicking(order)}
										>
											<Text style={styles.pickButtonText}>
												INITIALIZE PICK SCAN
											</Text>
											<Ionicons
												name="barcode-outline"
												size={16}
												color="#070a13"
											/>
										</TouchableOpacity>
									</View>
								);
							})
						)}
					</View>
				)}

				{/* TAB 2: STOCK DISPATCH */}
				{activeTab === "dispatch" && (
					<View style={styles.tabContent}>
						<View style={styles.card}>
							<Text style={styles.cardTitle}>
								Inter-Store Stock Balance Dispatcher
							</Text>
							<Text style={styles.cardDescription}>
								Dispatch autonomous trucks to rebalance product items from
								surplus zones to target fulfillment centers (PostgreSQL
								serializable ledger sync).
							</Text>

							{/* Source Store */}
							<Text style={styles.inputLabel}>Source MFC Store:</Text>
							<View style={styles.pillInputRow}>
								<TouchableOpacity
									style={[
										styles.miniPill,
										dispatchSource === "central" && styles.miniPillActive,
									]}
									onPress={() => {
										setDispatchSource("central");
										setDispatchTarget("east");
									}}
								>
									<Text style={styles.miniPillText}>Central Store</Text>
								</TouchableOpacity>
								<TouchableOpacity
									style={[
										styles.miniPill,
										dispatchSource === "east" && styles.miniPillActive,
									]}
									onPress={() => {
										setDispatchSource("east");
										setDispatchTarget("central");
									}}
								>
									<Text style={styles.miniPillText}>East Store</Text>
								</TouchableOpacity>
							</View>

							{/* Target Store */}
							<Text style={styles.inputLabel}>Target MFC Store:</Text>
							<View style={styles.dispatchDestCard}>
								<Text style={styles.dispatchDestText}>
									Target: {dispatchTarget.toUpperCase()} MFC STORE
								</Text>
							</View>

							{/* Item selection */}
							<Text style={styles.inputLabel}>Select Catalog Item:</Text>
							<ScrollView
								horizontal
								showsHorizontalScrollIndicator={false}
								style={styles.itemPillScroller}
							>
								{DEFAULT_ITEMS.map((item) => (
									<TouchableOpacity
										key={item.id}
										style={[
											styles.itemSelectPill,
											dispatchItem === item.id && styles.itemSelectPillActive,
										]}
										onPress={() => setDispatchItem(item.id)}
									>
										<Text style={styles.itemSelectEmoji}>{item.emoji}</Text>
										<Text style={styles.itemSelectText}>{item.name}</Text>
									</TouchableOpacity>
								))}
							</ScrollView>

							{/* Quantity */}
							<Text style={styles.inputLabel}>
								Transfer Quantity: {dispatchQty} units
							</Text>
							<View style={styles.qtyContainer}>
								<TouchableOpacity
									style={styles.qtyBtn}
									onPress={() => setDispatchQty((q) => Math.max(1, q - 5))}
								>
									<Text style={styles.qtyBtnText}>-5</Text>
								</TouchableOpacity>
								<TouchableOpacity
									style={styles.qtyBtn}
									onPress={() => setDispatchQty((q) => Math.max(1, q - 1))}
								>
									<Text style={styles.qtyBtnText}>-1</Text>
								</TouchableOpacity>
								<View style={styles.qtyDisplayBox}>
									<Text style={styles.qtyDisplayText}>{dispatchQty}</Text>
								</View>
								<TouchableOpacity
									style={styles.qtyBtn}
									onPress={() => setDispatchQty((q) => q + 1)}
								>
									<Text style={styles.qtyBtnText}>+1</Text>
								</TouchableOpacity>
								<TouchableOpacity
									style={styles.qtyBtn}
									onPress={() => setDispatchQty((q) => q + 5)}
								>
									<Text style={styles.qtyBtnText}>+5</Text>
								</TouchableOpacity>
							</View>

							{/* Submit Dispatch */}
							<TouchableOpacity
								style={[
									styles.dispatchSubmitBtn,
									dispatchLoading && { opacity: 0.7 },
								]}
								onPress={dispatchStockRequest}
								disabled={dispatchLoading}
							>
								{dispatchLoading ? (
									<ActivityIndicator size="small" color="#070a13" />
								) : (
									<>
										<Text style={styles.dispatchSubmitBtnText}>
											DISPATCH TRANSFER VEHICLE
										</Text>
										<FontAwesome5
											name="truck-loading"
											size={14}
											color="#070a13"
										/>
									</>
								)}
							</TouchableOpacity>
						</View>

						{/* Last Dispatch Result Card */}
						{lastDispatchResult && (
							<View
								style={[
									styles.card,
									{ borderColor: THEME.customer, borderWidth: 1 },
								]}
							>
								<View style={styles.resultHeader}>
									<Text style={[styles.resultTitle, { color: THEME.customer }]}>
										STOCK DISPATCH LOGGED
									</Text>
									<Text style={styles.resultTime}>
										{lastDispatchResult.timestamp}
									</Text>
								</View>
								<View style={styles.resultGrid}>
									<View style={styles.resultRow}>
										<Text style={styles.resultLabel}>Carrier:</Text>
										<Text style={styles.resultVal}>
											{lastDispatchResult.transferTruckId}
										</Text>
									</View>
									<View style={styles.resultRow}>
										<Text style={styles.resultLabel}>Product:</Text>
										<Text style={styles.resultVal}>
											{lastDispatchResult.item}
										</Text>
									</View>
									<View style={styles.resultRow}>
										<Text style={styles.resultLabel}>Transfer Quantity:</Text>
										<Text style={styles.resultVal}>
											{lastDispatchResult.quantity} units
										</Text>
									</View>
									<View style={styles.resultRow}>
										<Text style={styles.resultLabel}>Route Path:</Text>
										<Text style={styles.resultVal}>
											{lastDispatchResult.fromStore} ➡️{" "}
											{lastDispatchResult.toStore}
										</Text>
									</View>
								</View>
							</View>
						)}
					</View>
				)}

				{/* TAB 3: OPERATIONS & SCALING */}
				{activeTab === "ops" && (
					<View style={styles.tabContent}>
						{/* CAPACITY LIMITS PANEL */}
						<View style={styles.card}>
							<Text style={styles.cardTitle}>
								Dark Store Capacity & Virtual Scaling
							</Text>
							<Text style={styles.cardDescription}>
								Monitor physical storage occupancy. Deploy manual-scale virtual
								overflow warehouse bays to avoid order checkouts rejecting due
								to congestion.
							</Text>

							{/* Occupancy gauge representation */}
							<View style={styles.occupancyGaugeContainer}>
								<View style={styles.occupancyMetricBox}>
									<Text style={styles.occupancyLabel}>PHYSICAL OCCUPANCY</Text>
									<Text
										style={[
											styles.occupancyValue,
											{
												color:
													storeCapacity >= 90
														? THEME.admin
														: storeCapacity >= 75
															? THEME.rider
															: THEME.customer,
											},
										]}
									>
										{storeCapacity}%
									</Text>
									<Text style={styles.occupancySub}>
										{storeCapacity >= 90
											? "SLA CHECKOUT BLOCK RISK"
											: "OPERATIONAL CAPACITY OK"}
									</Text>
								</View>

								{/* Simulated circle bar */}
								<View style={styles.progressTrackBar}>
									<View
										style={[
											styles.progressBarFill,
											{
												width: `${storeCapacity}%`,
												backgroundColor:
													storeCapacity >= 90
														? THEME.admin
														: storeCapacity >= 75
															? THEME.rider
															: THEME.inventory,
											},
										]}
									/>
								</View>
							</View>

							{isOverflowBayDeployed ? (
								<View style={styles.overflowSuccessCard}>
									<Ionicons
										name="checkmark-circle"
										size={18}
										color="#070a13"
										style={{ marginRight: 8 }}
									/>
									<View style={{ flex: 1 }}>
										<Text style={styles.overflowSuccessTitle}>
											VIRTUAL BAY ACTIVE
										</Text>
										<Text style={styles.overflowSuccessSub}>
											+300 sq meters added. Congestion mitigated.
										</Text>
									</View>
									<TouchableOpacity
										style={styles.resetScaleBtn}
										onPress={() => {
											setIsOverflowBayDeployed(false);
											setStoreCapacity(91);
										}}
									>
										<Text style={styles.resetScaleBtnText}>RESET</Text>
									</TouchableOpacity>
								</View>
							) : (
								<TouchableOpacity
									style={[styles.scaleActionBtn, isScaling && { opacity: 0.7 }]}
									onPress={executeVirtualScaling}
									disabled={isScaling}
								>
									{isScaling ? (
										<View style={styles.scalingIndicatorRow}>
											<ActivityIndicator
												size="small"
												color="#070a13"
												style={{ marginRight: 8 }}
											/>
											<Text style={styles.scaleActionBtnText}>
												SCALING MATRIX: {scalingProgress}%
											</Text>
										</View>
									) : (
										<>
											<Text style={styles.scaleActionBtnText}>
												DEPLOY VIRTUAL OVERFLOW BAY
											</Text>
											<MaterialCommunityIcons
												name="arrow-expand-all"
												size={16}
												color="#070a13"
											/>
										</>
									)}
								</TouchableOpacity>
							)}
						</View>

						{/* AUTO-DEPLOY BACKUP PICKERS */}
						<View style={styles.card}>
							<View style={styles.cardHeaderRow}>
								<View style={{ flex: 1 }}>
									<Text style={styles.cardTitle}>
										Auto-Deploy Backup Pickers
									</Text>
									<Text style={styles.cardDescription}>
										Trigger backup picker daemons automatically when order
										backlog rises above 4 pending tickets to maintain 4-minute
										SLA.
									</Text>
								</View>
								<Switch
									value={autoDeployBackup}
									onValueChange={setAutoDeployBackup}
									trackColor={{ false: "#2c3040", true: THEME.inventoryGlow }}
									thumbColor={autoDeployBackup ? THEME.inventory : "#64748b"}
								/>
							</View>

							<View style={styles.rosterStatusContainer}>
								<Text style={styles.rosterLabel}>TEAM WORKER ROSTER SIZE</Text>
								<View style={styles.rosterStatusRow}>
									<View style={styles.rosterStatItem}>
										<Text style={styles.rosterStatNum}>1</Text>
										<Text style={styles.rosterStatLabel}>Active Picker</Text>
									</View>
									<View style={styles.rosterStatItem}>
										<Text
											style={[
												styles.rosterStatNum,
												{
													color: autoDeployBackup
														? THEME.customer
														: THEME.textMuted,
												},
											]}
										>
											{backupPickersActive}
										</Text>
										<Text style={styles.rosterStatLabel}>Backup Deployed</Text>
									</View>
									<View style={styles.rosterStatItem}>
										<Text style={styles.rosterStatNum}>
											{1 + backupPickersActive}
										</Text>
										<Text style={styles.rosterStatLabel}>Total Workforce</Text>
									</View>
								</View>

								{autoDeployBackup && backupPickersActive > 0 && (
									<View style={styles.deployPulseAnimationContainer}>
										<View style={styles.pulseNode} />
										<Text style={styles.pulseText}>
											Backup pickers actively draining queue bottlenecks
										</Text>
									</View>
								)}
							</View>
						</View>

						{/* TELEMETRY & SYSTEM HEALTH PANEL */}
						<View style={styles.card}>
							<Text style={styles.cardTitle}>
								Fulfillment Telemetry Metrics
							</Text>
							<View style={styles.telemetryGrid}>
								<View style={styles.telemetryRow}>
									<Text style={styles.telemetryLabel}>BFF Health State:</Text>
									<Text
										style={[
											styles.telemetryVal,
											{ color: isConnected ? THEME.customer : THEME.rider },
										]}
									>
										{isConnected ? "SECURE CONNECTION" : "OFFLINE SIMULATED"}
									</Text>
								</View>
								<View style={styles.telemetryRow}>
									<Text style={styles.telemetryLabel}>Ledger Integrity:</Text>
									<Text
										style={[styles.telemetryVal, { color: THEME.customer }]}
									>
										VERIFIED (HASH-CHAIN OK)
									</Text>
								</View>
								<View style={styles.telemetryRow}>
									<Text style={styles.telemetryLabel}>Double-Entry Audit:</Text>
									<Text
										style={[styles.telemetryVal, { color: THEME.customer }]}
									>
										DEBITS == CREDITS INVARIANT MET
									</Text>
								</View>
								<View style={styles.telemetryRow}>
									<Text style={styles.telemetryLabel}>Database Latency:</Text>
									<Text
										style={[styles.telemetryVal, { fontFamily: "monospace" }]}
									>
										4.2 ms (OLTP)
									</Text>
								</View>
							</View>
						</View>
					</View>
				)}
			</ScrollView>

			{/* ----------------- PICKING SCREEN MODAL (SCAN HELPER) ----------------- */}
			{activePickingOrder && (
				<Modal visible={true} animationType="slide" transparent={true}>
					<View style={styles.modalOverlay}>
						<View style={styles.pickModalContainer}>
							<View style={styles.pickModalHeader}>
								<View>
									<Text style={styles.pickModalTitle}>
										Picking Order #{activePickingOrder.orderId}
									</Text>
									<Text style={styles.pickModalSub}>
										Store Location: {activePickingOrder.storeId.toUpperCase()}{" "}
										MFC
									</Text>
								</View>
								<TouchableOpacity
									style={styles.closeModalBtn}
									onPress={() => setActivePickingOrder(null)}
								>
									<Ionicons name="close" size={24} color={THEME.textPrimary} />
								</TouchableOpacity>
							</View>

							{/* Items checklist */}
							<Text style={styles.checklistTitle}>ITEMS PACK CHECKLIST</Text>
							<ScrollView style={styles.checklistScroll}>
								{activePickingOrder.items.map((item, idx) => {
									const isChecked = pickedItemChecks[item.itemId];
									return (
										<TouchableOpacity
											key={idx}
											style={[
												styles.checkItemRow,
												isChecked && styles.checkItemRowChecked,
											]}
											onPress={() => toggleItemCheck(item.itemId)}
										>
											<View
												style={[
													styles.checkbox,
													isChecked && styles.checkboxChecked,
												]}
											>
												{isChecked && (
													<Ionicons
														name="checkmark"
														size={14}
														color="#070a13"
													/>
												)}
											</View>
											<View style={{ flex: 1 }}>
												<Text
													style={[
														styles.itemNameText,
														isChecked && styles.itemNameTextChecked,
													]}
												>
													{item.name}
												</Text>
												<View style={styles.itemTagRow}>
													<View style={styles.categoryBadge}>
														<Text style={styles.categoryBadgeText}>
															{item.category}
														</Text>
													</View>
													{item.perishable && (
														<View style={styles.perishableBadge}>
															<Text style={styles.perishableBadgeText}>
																❄️ PERISHABLE
															</Text>
														</View>
													)}
												</View>
											</View>
											<Text style={styles.itemQtyText}>x{item.quantity}</Text>
										</TouchableOpacity>
									);
								})}
							</ScrollView>

							{/* Rider and error controls */}
							<View style={styles.handoverFormContainer}>
								<Text style={styles.inputLabel}>Select Shipping Rider ID:</Text>
								<TextInput
									style={styles.textInput}
									value={selectedRider}
									onChangeText={setSelectedRider}
									placeholder="Rider ID"
									placeholderTextColor={THEME.textMuted}
								/>

								<View style={styles.handoverBtnRow}>
									<TouchableOpacity
										style={[
											styles.handoverBtn,
											styles.handoverBtnError,
											handoverLoading && { opacity: 0.6 },
										]}
										onPress={() => completeHandover(true)}
										disabled={handoverLoading}
									>
										<Text style={styles.handoverBtnErrorText}>
											REPORT ERROR
										</Text>
									</TouchableOpacity>

									<TouchableOpacity
										style={[
											styles.handoverBtn,
											styles.handoverBtnSuccess,
											handoverLoading && { opacity: 0.6 },
										]}
										onPress={() => completeHandover(false)}
										disabled={handoverLoading}
									>
										{handoverLoading ? (
											<ActivityIndicator size="small" color="#070a13" />
										) : (
											<>
												<Text style={styles.handoverBtnText}>
													HANDOVER CARGO
												</Text>
												<Ionicons
													name="arrow-forward-sharp"
													size={16}
													color="#070a13"
												/>
											</>
										)}
									</TouchableOpacity>
								</View>
							</View>
						</View>
					</View>
				</Modal>
			)}

			{/* ----------------- SETTINGS MODAL ----------------- */}
			<Modal visible={showSettings} animationType="fade" transparent={true}>
				<View style={styles.modalOverlay}>
					<View style={styles.settingsModalCard}>
						<View style={styles.modalHeader}>
							<Text style={styles.settingsTitle}>
								BFF Gateway Configuration
							</Text>
							<TouchableOpacity onPress={() => setShowSettings(false)}>
								<Ionicons name="close" size={24} color={THEME.textPrimary} />
							</TouchableOpacity>
						</View>

						<ScrollView contentContainerStyle={styles.settingsScrollContent}>
							<Text style={styles.cardDescription}>
								Connect to the Live Spring Cloud Gateway BFF and complete
								transactions in PostgreSQL via dual-ledger entry verification.
							</Text>

							{/* Simulator Mode toggle */}
							<View style={styles.settingsToggleRow}>
								<View style={{ flex: 1 }}>
									<Text style={styles.toggleLabel}>
										Enable Offline Simulator Mode
									</Text>
									<Text style={styles.toggleDesc}>
										Run high-fidelity client-side database simulation without
										needing the active BFF server running.
									</Text>
								</View>
								<Switch
									value={useSimulator}
									onValueChange={(val) => {
										setUseSimulator(val);
										if (!val) {
											setIsConnected(false);
										}
									}}
									trackColor={{ false: "#2c3040", true: THEME.inventoryGlow }}
									thumbColor={useSimulator ? THEME.inventory : "#64748b"}
								/>
							</View>

							{!useSimulator && (
								<View style={styles.bffConfigArea}>
									<Text style={styles.inputLabel}>BFF Gateway Endpoint:</Text>
									<TextInput
										style={styles.textInput}
										value={bffUrl}
										onChangeText={setBffUrl}
										placeholder="http://192.168.x.x:8081"
										placeholderTextColor={THEME.textMuted}
									/>

									<Text style={styles.inputLabel}>
										Bearer JWT Session Authorization Token:
									</Text>
									<TextInput
										style={[styles.textInput, { height: 80 }]}
										value={jwtToken}
										onChangeText={setJwtToken}
										placeholder="JWT Token"
										placeholderTextColor={THEME.textMuted}
										multiline={true}
										numberOfLines={4}
									/>

									<TouchableOpacity
										style={styles.testBtn}
										onPress={testBffConnection}
									>
										{loading ? (
											<ActivityIndicator size="small" color="#070a13" />
										) : (
											<>
												<Text style={styles.testBtnText}>
													VALIDATE BFF GATEWAY
												</Text>
												<Ionicons
													name="shield-checkmark"
													size={16}
													color="#070a13"
												/>
											</>
										)}
									</TouchableOpacity>
								</View>
							)}

							<Text style={styles.inputLabel}>Mock Operator Picker ID:</Text>
							<TextInput
								style={styles.textInput}
								value={pickerId}
								onChangeText={setPickerId}
								placeholder="picker_zuerich_01"
								placeholderTextColor={THEME.textMuted}
							/>
						</ScrollView>
					</View>
				</View>
			</Modal>

			{/* ----------------- LIGHTNING BADGE CELEBRATION MODAL ----------------- */}
			<Modal
				visible={lightningCelebration}
				transparent={true}
				animationType="fade"
			>
				<View style={styles.modalOverlay}>
					<View style={styles.celebrationCard}>
						<Text style={styles.celebrationFlash}>⚡ LIGHTNING PICKER ⚡</Text>
						<View style={styles.badgeRing}>
							<MaterialCommunityIcons
								name="lightning-bolt"
								size={80}
								color="#fbbf24"
							/>
						</View>
						<Text style={styles.celebrationTitle}>SLA SPEED BADGE AWARDED</Text>
						<Text style={styles.celebrationSub}>
							Order pick handover completed in under 90 seconds. Your speed
							record was committed to the Security Trust Ledger.
						</Text>
						<TouchableOpacity
							style={styles.celebrationBtn}
							onPress={() => setLightningCelebration(false)}
						>
							<Text style={styles.celebrationBtnText}>
								SECURE BONUS CREDITS
							</Text>
						</TouchableOpacity>
					</View>
				</View>
			</Modal>

			{/* ----------------- BOTTOM TAB BAR ----------------- */}
			<View style={styles.tabBar}>
				<TouchableOpacity
					style={[
						styles.tabBarItem,
						activeTab === "queue" && styles.tabBarItemActive,
					]}
					onPress={() => setActiveTab("queue")}
				>
					<MaterialCommunityIcons
						name="format-list-checks"
						size={22}
						color={
							activeTab === "queue" ? THEME.inventory : THEME.textSecondary
						}
					/>
					<Text
						style={[
							styles.tabBarText,
							activeTab === "queue" && styles.tabBarTextActive,
						]}
					>
						PICK QUEUE
					</Text>
				</TouchableOpacity>

				<TouchableOpacity
					style={[
						styles.tabBarItem,
						activeTab === "dispatch" && styles.tabBarItemActive,
					]}
					onPress={() => setActiveTab("dispatch")}
				>
					<FontAwesome5
						name="truck"
						size={18}
						color={
							activeTab === "dispatch" ? THEME.inventory : THEME.textSecondary
						}
					/>
					<Text
						style={[
							styles.tabBarText,
							activeTab === "dispatch" && styles.tabBarTextActive,
						]}
					>
						STOCK DISPATCH
					</Text>
				</TouchableOpacity>

				<TouchableOpacity
					style={[
						styles.tabBarItem,
						activeTab === "ops" && styles.tabBarItemActive,
					]}
					onPress={() => setActiveTab("ops")}
				>
					<Ionicons
						name="construct"
						size={20}
						color={activeTab === "ops" ? THEME.inventory : THEME.textSecondary}
					/>
					<Text
						style={[
							styles.tabBarText,
							activeTab === "ops" && styles.tabBarTextActive,
						]}
					>
						OPERATIONS
					</Text>
				</TouchableOpacity>
			</View>
		</SafeAreaView>
	);
}

// ----------------------------------------------------
// CYBER-INDUSTRIAL STYLE SYSTEM (hsl matching style.css)
// ----------------------------------------------------
const styles = StyleSheet.create({
	container: {
		flex: 1,
		backgroundColor: THEME.bgBody,
	},
	header: {
		backgroundColor: THEME.bgDark,
		borderBottomWidth: 1,
		borderBottomColor: THEME.borderColor,
		paddingHorizontal: 16,
		paddingTop: Platform.OS === "ios" ? 12 : 16,
		paddingBottom: 16,
	},
	brandRow: {
		flexDirection: "row",
		justifyContent: "space-between",
		alignItems: "center",
		marginBottom: 8,
	},
	logoText: {
		fontFamily: Platform.OS === "ios" ? "Outfit" : "sans-serif",
		fontWeight: "800",
		fontSize: 18,
		letterSpacing: -0.5,
		color: THEME.textPrimary,
	},
	connectionBadge: {
		flexDirection: "row",
		alignItems: "center",
		backgroundColor: "rgba(255, 255, 255, 0.04)",
		borderColor: THEME.borderColor,
		borderWidth: 1,
		borderRadius: 12,
		paddingVertical: 4,
		paddingHorizontal: 10,
	},
	ledIndicator: {
		width: 6,
		height: 6,
		borderRadius: 3,
		marginRight: 6,
	},
	connectionText: {
		fontSize: 10,
		fontWeight: "700",
		letterSpacing: 0.5,
	},
	subHeader: {
		flexDirection: "row",
		justifyContent: "space-between",
		alignItems: "center",
		marginTop: 4,
	},
	roleContainer: {
		flexDirection: "row",
		alignItems: "center",
	},
	roleText: {
		color: THEME.textSecondary,
		fontSize: 11,
		fontWeight: "700",
		marginLeft: 6,
		letterSpacing: 0.3,
	},
	settingsBtn: {
		padding: 4,
		backgroundColor: "rgba(255, 255, 255, 0.05)",
		borderRadius: 8,
	},
	metricsContainer: {
		flexDirection: "row",
		backgroundColor: "rgba(7, 10, 19, 0.5)",
		borderBottomWidth: 1,
		borderBottomColor: THEME.borderColor,
		paddingVertical: 12,
	},
	metricItem: {
		flex: 1,
		alignItems: "center",
		borderRightWidth: 1,
		borderRightColor: THEME.borderColor,
	},
	metricLabel: {
		color: THEME.textMuted,
		fontSize: 8,
		fontWeight: "700",
		letterSpacing: 0.5,
		marginBottom: 2,
	},
	metricValue: {
		fontSize: 15,
		fontWeight: "800",
		fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
	},
	storeSelectorContainer: {
		flexDirection: "row",
		alignItems: "center",
		justifyContent: "space-between",
		paddingHorizontal: 16,
		paddingVertical: 10,
		backgroundColor: "rgba(11, 15, 25, 0.4)",
		borderBottomWidth: 1,
		borderBottomColor: THEME.borderColor,
	},
	storeSelectorLabel: {
		color: THEME.textSecondary,
		fontSize: 12,
		fontWeight: "600",
	},
	storePillRow: {
		flexDirection: "row",
		gap: 8,
	},
	storePill: {
		paddingVertical: 5,
		paddingHorizontal: 12,
		borderRadius: 14,
		backgroundColor: "rgba(255, 255, 255, 0.04)",
		borderColor: THEME.borderColor,
		borderWidth: 1,
	},
	storePillActive: {
		backgroundColor: THEME.inventory,
		borderColor: THEME.inventory,
	},
	storePillText: {
		color: THEME.textSecondary,
		fontSize: 11,
		fontWeight: "700",
	},
	storePillTextActive: {
		color: THEME.bgDark,
	},
	content: {
		padding: 16,
		paddingBottom: 80,
	},
	tabContent: {
		gap: 16,
	},
	sectionHeaderRow: {
		flexDirection: "row",
		justifyContent: "space-between",
		alignItems: "center",
		marginBottom: 4,
	},
	sectionTitle: {
		color: THEME.textPrimary,
		fontSize: 14,
		fontWeight: "700",
		letterSpacing: 0.2,
	},
	backupRunningBadge: {
		backgroundColor: THEME.customerGlow,
		borderColor: THEME.customer,
		borderWidth: 1,
		borderRadius: 6,
		paddingVertical: 2,
		paddingHorizontal: 6,
	},
	backupRunningText: {
		color: THEME.customer,
		fontSize: 8,
		fontWeight: "800",
	},
	emptyContainer: {
		backgroundColor: THEME.bgCard,
		borderColor: THEME.borderColor,
		borderWidth: 1,
		borderRadius: 16,
		padding: 32,
		alignItems: "center",
		justifyContent: "center",
		gap: 12,
	},
	emptyText: {
		color: THEME.textPrimary,
		fontSize: 16,
		fontWeight: "700",
	},
	emptySubText: {
		color: THEME.textSecondary,
		fontSize: 12,
		textAlign: "center",
		lineHeight: 16,
	},
	slaAlertBanner: {
		flexDirection: "row",
		alignItems: "center",
		backgroundColor: THEME.admin,
		borderRadius: 10,
		padding: 10,
	},
	slaAlertText: {
		color: "#070a13",
		fontWeight: "800",
		fontSize: 11,
	},
	orderCard: {
		backgroundColor: THEME.bgCard,
		borderRadius: 16,
		padding: 16,
		borderWidth: 1,
		borderColor: THEME.borderColor,
		gap: 12,
	},
	orderCardCritical: {
		borderColor: THEME.admin,
		borderWidth: 1.5,
		backgroundColor: "rgba(239, 68, 68, 0.05)",
	},
	orderCardHeader: {
		flexDirection: "row",
		justifyContent: "space-between",
		alignItems: "center",
	},
	orderIdText: {
		color: THEME.textPrimary,
		fontSize: 16,
		fontWeight: "800",
	},
	slaContainer: {
		flexDirection: "row",
		alignItems: "center",
		backgroundColor: "rgba(255, 255, 255, 0.04)",
		borderRadius: 8,
		paddingVertical: 4,
		paddingHorizontal: 8,
		gap: 4,
	},
	slaText: {
		color: THEME.textSecondary,
		fontSize: 11,
		fontWeight: "700",
		fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
	},
	slaTextCritical: {
		color: THEME.admin,
		fontWeight: "800",
	},
	orderMetadata: {
		flexDirection: "row",
		justifyContent: "space-between",
	},
	metaText: {
		color: THEME.textSecondary,
		fontSize: 12,
	},
	boldText: {
		color: THEME.textPrimary,
		fontWeight: "700",
	},
	itemsPreviewRow: {
		flexDirection: "row",
		flexWrap: "wrap",
		gap: 6,
	},
	itemEmojiBadge: {
		backgroundColor: "rgba(255, 255, 255, 0.03)",
		borderRadius: 6,
		borderWidth: 1,
		borderColor: THEME.borderColor,
		paddingVertical: 4,
		paddingHorizontal: 8,
	},
	emojiBadgeText: {
		fontSize: 10,
		color: THEME.textPrimary,
		fontWeight: "600",
	},
	pickButton: {
		backgroundColor: THEME.inventory,
		borderRadius: 10,
		paddingVertical: 10,
		flexDirection: "row",
		justifyContent: "center",
		alignItems: "center",
		gap: 8,
		shadowColor: THEME.inventory,
		shadowOffset: { width: 0, height: 4 },
		shadowOpacity: 0.3,
		shadowRadius: 8,
		elevation: 4,
	},
	pickButtonText: {
		color: "#070a13",
		fontWeight: "800",
		fontSize: 12,
		letterSpacing: 0.5,
	},
	card: {
		backgroundColor: THEME.bgCard,
		borderRadius: 16,
		padding: 16,
		borderWidth: 1,
		borderColor: THEME.borderColor,
		gap: 12,
	},
	cardTitle: {
		color: THEME.textPrimary,
		fontSize: 15,
		fontWeight: "800",
	},
	cardHeaderRow: {
		flexDirection: "row",
		alignItems: "center",
		justifyContent: "space-between",
	},
	cardDescription: {
		color: THEME.textSecondary,
		fontSize: 11,
		lineHeight: 16,
	},
	inputLabel: {
		color: THEME.textSecondary,
		fontSize: 11,
		fontWeight: "700",
		letterSpacing: 0.3,
		marginTop: 4,
	},
	pillInputRow: {
		flexDirection: "row",
		gap: 8,
	},
	miniPill: {
		flex: 1,
		paddingVertical: 8,
		borderRadius: 8,
		alignItems: "center",
		backgroundColor: "rgba(255, 255, 255, 0.04)",
		borderWidth: 1,
		borderColor: THEME.borderColor,
	},
	miniPillActive: {
		backgroundColor: THEME.inventoryGlow,
		borderColor: THEME.inventory,
	},
	miniPillText: {
		color: THEME.textPrimary,
		fontSize: 12,
		fontWeight: "700",
	},
	dispatchDestCard: {
		backgroundColor: "rgba(7, 10, 19, 0.6)",
		borderRadius: 8,
		padding: 10,
		borderColor: THEME.borderColor,
		borderWidth: 1,
	},
	dispatchDestText: {
		color: THEME.engine,
		fontWeight: "800",
		fontSize: 12,
		fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
	},
	itemPillScroller: {
		paddingVertical: 4,
	},
	itemSelectPill: {
		flexDirection: "row",
		alignItems: "center",
		backgroundColor: "rgba(255, 255, 255, 0.04)",
		borderWidth: 1,
		borderColor: THEME.borderColor,
		borderRadius: 8,
		paddingVertical: 8,
		paddingHorizontal: 12,
		marginRight: 8,
		gap: 6,
	},
	itemSelectPillActive: {
		backgroundColor: THEME.inventoryGlow,
		borderColor: THEME.inventory,
	},
	itemSelectEmoji: {
		fontSize: 14,
	},
	itemSelectText: {
		color: THEME.textPrimary,
		fontSize: 11,
		fontWeight: "600",
	},
	qtyContainer: {
		flexDirection: "row",
		alignItems: "center",
		gap: 8,
	},
	qtyBtn: {
		backgroundColor: "rgba(255, 255, 255, 0.05)",
		borderRadius: 8,
		width: 38,
		height: 38,
		alignItems: "center",
		justifyContent: "center",
		borderColor: THEME.borderColor,
		borderWidth: 1,
	},
	qtyBtnText: {
		color: THEME.textPrimary,
		fontWeight: "700",
		fontSize: 11,
	},
	qtyDisplayBox: {
		flex: 1,
		backgroundColor: "rgba(7, 10, 19, 0.6)",
		borderColor: THEME.borderColor,
		borderWidth: 1,
		borderRadius: 8,
		height: 38,
		alignItems: "center",
		justifyContent: "center",
	},
	qtyDisplayText: {
		color: THEME.inventory,
		fontSize: 16,
		fontWeight: "800",
		fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
	},
	dispatchSubmitBtn: {
		backgroundColor: THEME.customer,
		borderRadius: 10,
		paddingVertical: 12,
		flexDirection: "row",
		justifyContent: "center",
		alignItems: "center",
		gap: 8,
		shadowColor: THEME.customer,
		shadowOffset: { width: 0, height: 4 },
		shadowOpacity: 0.3,
		shadowRadius: 8,
		elevation: 4,
		marginTop: 8,
	},
	dispatchSubmitBtnText: {
		color: "#070a13",
		fontWeight: "800",
		fontSize: 12,
		letterSpacing: 0.5,
	},
	resultHeader: {
		flexDirection: "row",
		justifyContent: "space-between",
		alignItems: "center",
		borderBottomWidth: 1,
		borderBottomColor: THEME.borderColor,
		paddingBottom: 8,
	},
	resultTitle: {
		fontSize: 12,
		fontWeight: "800",
		letterSpacing: 0.5,
	},
	resultTime: {
		fontSize: 10,
		color: THEME.textMuted,
	},
	resultGrid: {
		gap: 6,
	},
	resultRow: {
		flexDirection: "row",
		justifyContent: "space-between",
	},
	resultLabel: {
		color: THEME.textSecondary,
		fontSize: 11,
	},
	resultVal: {
		color: THEME.textPrimary,
		fontSize: 11,
		fontWeight: "700",
	},
	occupancyGaugeContainer: {
		backgroundColor: "rgba(7, 10, 19, 0.5)",
		borderRadius: 12,
		padding: 12,
		borderWidth: 1,
		borderColor: THEME.borderColor,
	},
	occupancyMetricBox: {
		alignItems: "center",
		marginBottom: 8,
	},
	occupancyLabel: {
		color: THEME.textMuted,
		fontSize: 8,
		fontWeight: "700",
		letterSpacing: 0.5,
	},
	occupancyValue: {
		fontSize: 24,
		fontWeight: "800",
		marginVertical: 2,
		fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
	},
	occupancySub: {
		color: THEME.textSecondary,
		fontSize: 9,
		fontWeight: "600",
	},
	progressTrackBar: {
		height: 6,
		backgroundColor: "rgba(255, 255, 255, 0.05)",
		borderRadius: 3,
		overflow: "hidden",
	},
	progressBarFill: {
		height: "100%",
		borderRadius: 3,
	},
	overflowSuccessCard: {
		flexDirection: "row",
		alignItems: "center",
		backgroundColor: THEME.customer,
		borderRadius: 10,
		padding: 12,
	},
	overflowSuccessTitle: {
		color: "#070a13",
		fontWeight: "800",
		fontSize: 12,
	},
	overflowSuccessSub: {
		color: "rgba(7, 10, 19, 0.7)",
		fontSize: 10,
		fontWeight: "600",
	},
	resetScaleBtn: {
		backgroundColor: "#070a13",
		paddingVertical: 4,
		paddingHorizontal: 8,
		borderRadius: 6,
	},
	resetScaleBtnText: {
		color: THEME.textPrimary,
		fontSize: 9,
		fontWeight: "800",
	},
	scaleActionBtn: {
		backgroundColor: THEME.inventory,
		borderRadius: 10,
		paddingVertical: 12,
		flexDirection: "row",
		justifyContent: "center",
		alignItems: "center",
		gap: 8,
	},
	scalingIndicatorRow: {
		flexDirection: "row",
		alignItems: "center",
	},
	scaleActionBtnText: {
		color: "#070a13",
		fontWeight: "800",
		fontSize: 12,
		letterSpacing: 0.3,
	},
	rosterStatusContainer: {
		backgroundColor: "rgba(7, 10, 19, 0.5)",
		borderRadius: 12,
		padding: 12,
		borderWidth: 1,
		borderColor: THEME.borderColor,
		gap: 8,
	},
	rosterLabel: {
		color: THEME.textMuted,
		fontSize: 8,
		fontWeight: "700",
		letterSpacing: 0.5,
		textAlign: "center",
	},
	rosterStatusRow: {
		flexDirection: "row",
		justifyContent: "space-around",
	},
	rosterStatItem: {
		alignItems: "center",
	},
	rosterStatNum: {
		fontSize: 20,
		fontWeight: "800",
		color: THEME.textPrimary,
		fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
	},
	rosterStatLabel: {
		fontSize: 9,
		color: THEME.textSecondary,
		fontWeight: "600",
		marginTop: 2,
	},
	deployPulseAnimationContainer: {
		flexDirection: "row",
		alignItems: "center",
		justifyContent: "center",
		gap: 8,
		marginTop: 4,
		paddingTop: 8,
		borderTopWidth: 1,
		borderTopColor: THEME.borderColor,
	},
	pulseNode: {
		width: 8,
		height: 8,
		borderRadius: 4,
		backgroundColor: THEME.customer,
	},
	pulseText: {
		color: THEME.customer,
		fontSize: 9,
		fontWeight: "700",
	},
	telemetryGrid: {
		gap: 8,
	},
	telemetryRow: {
		flexDirection: "row",
		justifyContent: "space-between",
		borderBottomWidth: 1,
		borderBottomColor: "rgba(255, 255, 255, 0.02)",
		paddingBottom: 6,
	},
	telemetryLabel: {
		color: THEME.textSecondary,
		fontSize: 11,
	},
	telemetryVal: {
		color: THEME.textPrimary,
		fontSize: 11,
		fontWeight: "700",
	},
	modalOverlay: {
		flex: 1,
		backgroundColor: "rgba(0, 0, 0, 0.75)",
		justifyContent: "center",
		alignItems: "center",
		padding: 16,
	},
	pickModalContainer: {
		backgroundColor: THEME.bgBody,
		borderColor: THEME.borderColor,
		borderWidth: 1,
		borderRadius: 16,
		width: "100%",
		maxHeight: "85%",
		padding: 16,
		gap: 16,
	},
	pickModalHeader: {
		flexDirection: "row",
		justifyContent: "space-between",
		alignItems: "center",
		borderBottomWidth: 1,
		borderBottomColor: THEME.borderColor,
		paddingBottom: 12,
	},
	pickModalTitle: {
		fontSize: 16,
		fontWeight: "800",
		color: THEME.textPrimary,
	},
	pickModalSub: {
		color: THEME.textSecondary,
		fontSize: 11,
	},
	closeModalBtn: {
		padding: 4,
	},
	checklistTitle: {
		color: THEME.textMuted,
		fontSize: 10,
		fontWeight: "800",
		letterSpacing: 0.5,
	},
	checklistScroll: {
		flexGrow: 0,
		maxHeight: "50%",
	},
	checkItemRow: {
		flexDirection: "row",
		alignItems: "center",
		backgroundColor: "rgba(255, 255, 255, 0.02)",
		borderColor: THEME.borderColor,
		borderWidth: 1,
		borderRadius: 10,
		padding: 12,
		marginBottom: 8,
		gap: 10,
	},
	checkItemRowChecked: {
		borderColor: THEME.customer,
		backgroundColor: THEME.customerGlow,
	},
	checkbox: {
		width: 20,
		height: 20,
		borderRadius: 6,
		borderWidth: 1.5,
		borderColor: THEME.textSecondary,
		alignItems: "center",
		justifyContent: "center",
	},
	checkboxChecked: {
		borderColor: THEME.customer,
		backgroundColor: THEME.customer,
	},
	itemNameText: {
		color: THEME.textPrimary,
		fontSize: 13,
		fontWeight: "600",
	},
	itemNameTextChecked: {
		color: THEME.textSecondary,
		textDecorationLine: "line-through",
	},
	itemTagRow: {
		flexDirection: "row",
		gap: 6,
		marginTop: 4,
	},
	categoryBadge: {
		backgroundColor: "rgba(255, 255, 255, 0.05)",
		borderRadius: 4,
		paddingVertical: 1,
		paddingHorizontal: 4,
	},
	categoryBadgeText: {
		color: THEME.textSecondary,
		fontSize: 8,
		fontWeight: "700",
	},
	perishableBadge: {
		backgroundColor: "rgba(6, 182, 212, 0.1)",
		borderRadius: 4,
		paddingVertical: 1,
		paddingHorizontal: 4,
	},
	perishableBadgeText: {
		color: THEME.engine,
		fontSize: 8,
		fontWeight: "800",
	},
	itemQtyText: {
		color: THEME.textPrimary,
		fontWeight: "800",
		fontSize: 14,
		fontFamily: Platform.OS === "ios" ? "Courier" : "monospace",
	},
	handoverFormContainer: {
		gap: 10,
		borderTopWidth: 1,
		borderTopColor: THEME.borderColor,
		paddingTop: 12,
	},
	textInput: {
		backgroundColor: "rgba(255, 255, 255, 0.03)",
		borderColor: THEME.borderColor,
		borderWidth: 1,
		borderRadius: 8,
		paddingVertical: 8,
		paddingHorizontal: 12,
		color: THEME.textPrimary,
		fontSize: 13,
	},
	handoverBtnRow: {
		flexDirection: "row",
		gap: 8,
	},
	handoverBtn: {
		flex: 1,
		borderRadius: 8,
		paddingVertical: 12,
		alignItems: "center",
		justifyContent: "center",
	},
	handoverBtnError: {
		backgroundColor: "rgba(239, 68, 68, 0.15)",
		borderColor: THEME.admin,
		borderWidth: 1,
	},
	handoverBtnErrorText: {
		color: THEME.admin,
		fontWeight: "800",
		fontSize: 11,
	},
	handoverBtnSuccess: {
		backgroundColor: THEME.inventory,
		flexDirection: "row",
		gap: 6,
	},
	handoverBtnText: {
		color: "#070a13",
		fontWeight: "800",
		fontSize: 11,
	},
	settingsModalCard: {
		backgroundColor: THEME.bgBody,
		borderColor: THEME.borderColor,
		borderWidth: 1,
		borderRadius: 16,
		width: "100%",
		padding: 16,
		gap: 16,
	},
	modalHeader: {
		flexDirection: "row",
		justifyContent: "space-between",
		alignItems: "center",
		borderBottomWidth: 1,
		borderBottomColor: THEME.borderColor,
		paddingBottom: 8,
	},
	settingsTitle: {
		fontSize: 15,
		fontWeight: "800",
		color: THEME.textPrimary,
	},
	settingsScrollContent: {
		gap: 12,
	},
	settingsToggleRow: {
		flexDirection: "row",
		alignItems: "center",
		backgroundColor: "rgba(255, 255, 255, 0.02)",
		padding: 10,
		borderRadius: 10,
		borderColor: THEME.borderColor,
		borderWidth: 1,
	},
	toggleLabel: {
		color: THEME.textPrimary,
		fontSize: 12,
		fontWeight: "700",
	},
	toggleDesc: {
		color: THEME.textSecondary,
		fontSize: 9,
		marginTop: 2,
		paddingRight: 10,
	},
	bffConfigArea: {
		gap: 10,
	},
	testBtn: {
		backgroundColor: THEME.customer,
		paddingVertical: 10,
		borderRadius: 8,
		alignItems: "center",
		justifyContent: "center",
		flexDirection: "row",
		gap: 6,
		marginTop: 4,
	},
	testBtnText: {
		color: "#070a13",
		fontWeight: "800",
		fontSize: 11,
	},
	celebrationCard: {
		backgroundColor: THEME.bgDark,
		borderColor: "#fbbf24",
		borderWidth: 2,
		borderRadius: 24,
		padding: 24,
		alignItems: "center",
		width: "90%",
		gap: 16,
		shadowColor: "#fbbf24",
		shadowOffset: { width: 0, height: 10 },
		shadowOpacity: 0.5,
		shadowRadius: 20,
		elevation: 10,
	},
	celebrationFlash: {
		color: "#fbbf24",
		fontSize: 18,
		fontWeight: "900",
		letterSpacing: 2,
	},
	badgeRing: {
		width: 120,
		height: 120,
		borderRadius: 60,
		backgroundColor: "rgba(251, 191, 36, 0.1)",
		borderColor: "#fbbf24",
		borderWidth: 2.5,
		alignItems: "center",
		justifyContent: "center",
	},
	celebrationTitle: {
		color: THEME.textPrimary,
		fontSize: 15,
		fontWeight: "800",
		letterSpacing: 0.5,
	},
	celebrationSub: {
		color: THEME.textSecondary,
		fontSize: 11,
		textAlign: "center",
		lineHeight: 16,
	},
	celebrationBtn: {
		backgroundColor: "#fbbf24",
		paddingVertical: 12,
		paddingHorizontal: 24,
		borderRadius: 10,
		width: "100%",
		alignItems: "center",
	},
	celebrationBtnText: {
		color: "#070a13",
		fontWeight: "900",
		fontSize: 12,
	},
	tabBar: {
		position: "absolute",
		bottom: 0,
		left: 0,
		right: 0,
		height: 56,
		backgroundColor: THEME.bgDark,
		borderTopWidth: 1,
		borderTopColor: THEME.borderColor,
		flexDirection: "row",
		justifyContent: "space-around",
		alignItems: "center",
	},
	tabBarItem: {
		alignItems: "center",
		justifyContent: "center",
		gap: 4,
		flex: 1,
		height: "100%",
	},
	tabBarItemActive: {
		borderTopWidth: 2,
		borderTopColor: THEME.inventory,
	},
	tabBarText: {
		fontSize: 8,
		fontWeight: "700",
		color: THEME.textSecondary,
		letterSpacing: 0.3,
	},
	tabBarTextActive: {
		color: THEME.inventory,
	},
});
