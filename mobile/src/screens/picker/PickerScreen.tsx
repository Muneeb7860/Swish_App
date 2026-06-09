import {
	FontAwesome5,
	Ionicons,
	MaterialCommunityIcons,
} from "@expo/vector-icons";
import React, { useEffect, useState } from "react";
import {
	Alert,
	SafeAreaView,
	ScrollView,
	StatusBar,
	Text,
	TouchableOpacity,
	View,
} from "react-native";
import { DEFAULT_ITEMS, INITIAL_ORDERS, THEME } from "./constants";
import styles from "./styles";
import { DispatchResult, Order, PickerStats } from "./types";

import DispatchTab from "./components/DispatchTab";
import LightningModal from "./components/LightningModal";
import OpsTab from "./components/OpsTab";
import PickingModal from "./components/PickingModal";
import QueueTab from "./components/QueueTab";
import SettingsModal from "./components/SettingsModal";

export default function PickerScreen() {
	// Navigation Tabs
	const [activeTab, setActiveTab] = useState("queue");

	// Connection states
	const [useSimulator, setUseSimulator] = useState(true);
	const [bffUrl, setBffUrl] = useState("http://192.168.1.134:8081");
	const [jwtToken, setJwtToken] = useState(
		"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJwaWNrZXJfZGVtb24iLCJyb2xlcyI6IlBJQ0tFUiIsImlhdCI6MTc4MDAwMDAwMH0.signature",
	);
	const [isConnected, setIsConnected] = useState(false);
	const [showSettings, setShowSettings] = useState(false);
	const [loading, setLoading] = useState(false);

	// Picker Profile
	const [pickerId, setPickerId] = useState("picker_zuerich_01");
	const [selectedStore, setSelectedStore] = useState("central");
	const [pickerStats, setPickerStats] = useState<PickerStats>({
		pickedCount: 14,
		lightningBadges: 2,
		avgPickTime: 72,
		trustScore: 95,
	});

	// Dark Store Queue & Picking state
	const [orders, setOrders] = useState<Order[]>(INITIAL_ORDERS);
	const [activePickingOrder, setActivePickingOrder] = useState<Order | null>(
		null,
	);
	const [pickedItemChecks, setPickedItemChecks] = useState<
		Record<string, boolean>
	>({});
	const [selectedRider, setSelectedRider] = useState("rider_bern_12");
	const [pickerStartTime, setPickerStartTime] = useState<number | null>(null);
	const [handoverLoading, setHandoverLoading] = useState(false);
	const [lightningCelebration, setLightningCelebration] = useState(false);

	// Operations/Scaling states
	const [storeCapacity, setStoreCapacity] = useState(91);
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
	const [lastDispatchResult, setLastDispatchResult] =
		useState<DispatchResult | null>(null);

	// Real-time counter tickers
	useEffect(() => {
		const timer = setInterval(() => {
			// 1. Decrement order SLAs
			setOrders((prevOrders) =>
				prevOrders.map((order) => {
					const elapsed = Math.floor(
						(Date.now() - new Date(order.created_at).getTime()) / 1000,
					);
					return {
						...order,
						slaCountdownSec: Math.max(0, 240 - elapsed),
					};
				}),
			);

			// 2. If Simulator & Auto-Deploy Backup is ON, drain backlog slowly
			if (useSimulator && autoDeployBackup && orders.length > 0) {
				setBackupPickersActive(Math.ceil(orders.length / 3));
				if (Math.random() < 0.08) {
					setOrders((prev) => {
						if (prev.length === 0) return prev;
						const next = [...prev];
						const sorted = next.sort(
							(a, b) => a.slaCountdownSec - b.slaCountdownSec,
						);
						sorted.shift();
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

				setOrders((prev) => [
					...prev,
					{
						orderId: nextId,
						storeId: randomStore,
						status: "pending",
						slaCountdownSec: 240,
						totalAmount: parseFloat(total.toFixed(2)),
						created_at: new Date().toISOString(),
						items: orderItems,
					},
				]);

				setStoreCapacity((cap) => Math.min(100, cap + 1));
			}
		}, 1000);

		return () => clearInterval(timer);
	}, [useSimulator, autoDeployBackup, orders.length]);

	// Fetch queue when store changes and simulator is off
	useEffect(() => {
		if (!useSimulator) {
			fetchBffQueue();
		}
	}, [selectedStore, useSimulator]);

	// ─── BFF helpers ──────────────────────────────────────────────────────────

	const testBffConnection = async () => {
		setLoading(true);
		try {
			const response = await fetch(`${bffUrl}/api/admin/health`, {
				method: "GET",
				headers: { Authorization: `Bearer ${jwtToken}`, Accept: "application/json" },
			});
			if (response.ok) {
				setIsConnected(true);
				setUseSimulator(false);
				Alert.alert(
					"BFF Connection Secure",
					"BFF Gateway reached. Active JWT Session verified.",
				);
				setShowSettings(false);
				fetchBffQueue();
			} else {
				throw new Error(`BFF responded with code ${response.status}`);
			}
		} catch (err: any) {
			setIsConnected(false);
			Alert.alert(
				"Connection Failed",
				`Could not reach BFF Gateway at ${bffUrl}. Reverting to high-fidelity Offline Simulator.\n\nDetail: ${err.message}`,
			);
		} finally {
			setLoading(false);
		}
	};

	const fetchBffQueue = async () => {
		if (useSimulator) return;
		try {
			const response = await fetch(
				`${bffUrl}/api/inventory/picker/queue?storeId=${selectedStore}`,
				{
					method: "GET",
					headers: { Authorization: `Bearer ${jwtToken}`, Accept: "application/json" },
				},
			);
			if (response.ok) {
				const data = await response.json();
				setOrders(
					data.map((o: any) => ({
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
					})),
				);
			}
		} catch (err) {
			console.warn("Error fetching BFF Picker Queue:", err);
		}
	};

	// ─── Picking handlers ─────────────────────────────────────────────────────

	const startPicking = (order: Order) => {
		setActivePickingOrder(order);
		setPickedItemChecks({});
		setPickerStartTime(Date.now());
	};

	const toggleItemCheck = (itemId: string) => {
		setPickedItemChecks((prev) => ({ ...prev, [itemId]: !prev[itemId] }));
	};

	const completeHandover = async (containsError = false) => {
		if (!activePickingOrder) return;
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

	const processHandover = async (containsError: boolean) => {
		if (!activePickingOrder || pickerStartTime === null) return;
		setHandoverLoading(true);
		const duration = Math.round((Date.now() - pickerStartTime) / 1000);
		const isLightning = duration < 90;

		if (useSimulator) {
			setTimeout(() => {
				setHandoverLoading(false);
				setOrders((prev) =>
					prev.filter((o) => o.orderId !== activePickingOrder.orderId),
				);
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
							pickerId,
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
			} catch (err: any) {
				setHandoverLoading(false);
				Alert.alert(
					"BFF Handover Error",
					`Failed to register handover at BFF: ${err.message}`,
				);
			}
		}
	};

	// ─── Dispatch handler ─────────────────────────────────────────────────────

	const dispatchStockRequest = async () => {
		if (dispatchSource === dispatchTarget) {
			Alert.alert("Invalid Target", "Source and Target MFCs must be different.");
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
			setTimeout(() => {
				setDispatchLoading(false);
				const itemObj = DEFAULT_ITEMS.find((i) => i.id === dispatchItem);
				const result: DispatchResult = {
					status: "rebalanced",
					item: itemObj ? itemObj.name : dispatchItem,
					quantity: dispatchQty,
					fromStore: dispatchSource.toUpperCase() + " MFC",
					toStore: dispatchTarget.toUpperCase() + " MFC",
					transferTruckId: "TX-TRUCK-" + Math.floor(1000 + Math.random() * 9000),
					timestamp: new Date().toLocaleTimeString(),
				};
				setLastDispatchResult(result);
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
						toStore:
							(resData.toStore || dispatchTarget).toUpperCase() + " MFC",
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
			} catch (err: any) {
				setDispatchLoading(false);
				Alert.alert(
					"BFF Dispatch Error",
					`Failed to rebalance stock: ${err.message}`,
				);
			}
		}
	};

	// ─── Ops handlers ─────────────────────────────────────────────────────────

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
					setStoreCapacity(58);
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

	// ─── Derived ──────────────────────────────────────────────────────────────

	const filteredOrders = orders.filter((o) => o.storeId === selectedStore);

	// ─── Render ───────────────────────────────────────────────────────────────

	return (
		<SafeAreaView style={styles.container}>
			<StatusBar barStyle="light-content" backgroundColor={THEME.bgDark} />

			{/* HEADER */}
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

			{/* METRIC BAR */}
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

			{/* STORE SELECTOR */}
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

			{/* SCREEN CONTENT */}
			<ScrollView contentContainerStyle={styles.content}>
				{activeTab === "queue" && (
					<QueueTab
						filteredOrders={filteredOrders}
						autoDeployBackup={autoDeployBackup}
						backupPickersActive={backupPickersActive}
						onStartPicking={startPicking}
					/>
				)}
				{activeTab === "dispatch" && (
					<DispatchTab
						dispatchSource={dispatchSource}
						setDispatchSource={setDispatchSource}
						dispatchTarget={dispatchTarget}
						setDispatchTarget={setDispatchTarget}
						dispatchItem={dispatchItem}
						setDispatchItem={setDispatchItem}
						dispatchQty={dispatchQty}
						setDispatchQty={setDispatchQty}
						dispatchLoading={dispatchLoading}
						lastDispatchResult={lastDispatchResult}
						onDispatch={dispatchStockRequest}
					/>
				)}
				{activeTab === "ops" && (
					<OpsTab
						storeCapacity={storeCapacity}
						isOverflowBayDeployed={isOverflowBayDeployed}
						isScaling={isScaling}
						scalingProgress={scalingProgress}
						autoDeployBackup={autoDeployBackup}
						setAutoDeployBackup={setAutoDeployBackup}
						backupPickersActive={backupPickersActive}
						isConnected={isConnected}
						onExecuteVirtualScaling={executeVirtualScaling}
						onResetScale={() => {
							setIsOverflowBayDeployed(false);
							setStoreCapacity(91);
						}}
					/>
				)}
			</ScrollView>

			{/* PICKING MODAL */}
			{activePickingOrder && (
				<PickingModal
					order={activePickingOrder}
					pickedItemChecks={pickedItemChecks}
					selectedRider={selectedRider}
					setSelectedRider={setSelectedRider}
					handoverLoading={handoverLoading}
					onToggleItem={toggleItemCheck}
					onClose={() => setActivePickingOrder(null)}
					onCompleteHandover={completeHandover}
				/>
			)}

			{/* SETTINGS MODAL */}
			<SettingsModal
				visible={showSettings}
				useSimulator={useSimulator}
				setUseSimulator={(val) => {
					setUseSimulator(val);
					if (!val) setIsConnected(false);
				}}
				bffUrl={bffUrl}
				setBffUrl={setBffUrl}
				jwtToken={jwtToken}
				setJwtToken={setJwtToken}
				pickerId={pickerId}
				setPickerId={setPickerId}
				loading={loading}
				onTest={testBffConnection}
				onClose={() => setShowSettings(false)}
			/>

			{/* LIGHTNING CELEBRATION MODAL */}
			<LightningModal
				visible={lightningCelebration}
				onDismiss={() => setLightningCelebration(false)}
			/>

			{/* BOTTOM TAB BAR */}
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
						color={activeTab === "queue" ? THEME.inventory : THEME.textSecondary}
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
