import { Ionicons, MaterialCommunityIcons } from "@expo/vector-icons";
import React from "react";
import {
	ActivityIndicator,
	Switch,
	Text,
	TouchableOpacity,
	View,
} from "react-native";
import { THEME } from "../constants";
import styles from "../styles";

interface OpsTabProps {
	storeCapacity: number;
	isOverflowBayDeployed: boolean;
	isScaling: boolean;
	scalingProgress: number;
	autoDeployBackup: boolean;
	setAutoDeployBackup: (v: boolean) => void;
	backupPickersActive: number;
	isConnected: boolean;
	onExecuteVirtualScaling: () => void;
	onResetScale: () => void;
}

export default function OpsTab({
	storeCapacity,
	isOverflowBayDeployed,
	isScaling,
	scalingProgress,
	autoDeployBackup,
	setAutoDeployBackup,
	backupPickersActive,
	isConnected,
	onExecuteVirtualScaling,
	onResetScale,
}: OpsTabProps) {
	return (
		<View style={styles.tabContent}>
			{/* CAPACITY LIMITS PANEL */}
			<View style={styles.card}>
				<Text style={styles.cardTitle}>
					Dark Store Capacity & Virtual Scaling
				</Text>
				<Text style={styles.cardDescription}>
					Monitor physical storage occupancy. Deploy manual-scale virtual overflow
					warehouse bays to avoid order checkouts rejecting due to congestion.
				</Text>

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
							<Text style={styles.overflowSuccessTitle}>VIRTUAL BAY ACTIVE</Text>
							<Text style={styles.overflowSuccessSub}>
								+300 sq meters added. Congestion mitigated.
							</Text>
						</View>
						<TouchableOpacity style={styles.resetScaleBtn} onPress={onResetScale}>
							<Text style={styles.resetScaleBtnText}>RESET</Text>
						</TouchableOpacity>
					</View>
				) : (
					<TouchableOpacity
						style={[styles.scaleActionBtn, isScaling && { opacity: 0.7 }]}
						onPress={onExecuteVirtualScaling}
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
						<Text style={styles.cardTitle}>Auto-Deploy Backup Pickers</Text>
						<Text style={styles.cardDescription}>
							Trigger backup picker daemons automatically when order backlog rises
							above 4 pending tickets to maintain 4-minute SLA.
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
										color: autoDeployBackup ? THEME.customer : THEME.textMuted,
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
				<Text style={styles.cardTitle}>Fulfillment Telemetry Metrics</Text>
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
						<Text style={[styles.telemetryVal, { color: THEME.customer }]}>
							VERIFIED (HASH-CHAIN OK)
						</Text>
					</View>
					<View style={styles.telemetryRow}>
						<Text style={styles.telemetryLabel}>Double-Entry Audit:</Text>
						<Text style={[styles.telemetryVal, { color: THEME.customer }]}>
							DEBITS == CREDITS INVARIANT MET
						</Text>
					</View>
					<View style={styles.telemetryRow}>
						<Text style={styles.telemetryLabel}>Database Latency:</Text>
						<Text style={[styles.telemetryVal, { fontFamily: "monospace" }]}>
							4.2 ms (OLTP)
						</Text>
					</View>
				</View>
			</View>
		</View>
	);
}
