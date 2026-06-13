import { Ionicons, MaterialCommunityIcons } from "@expo/vector-icons";
import React from "react";
import { Text, TouchableOpacity, View } from "react-native";
import { THEME } from "../constants";
import styles from "../styles";
import { Order } from "../types";

interface QueueTabProps {
	filteredOrders: Order[];
	autoDeployBackup: boolean;
	backupPickersActive: number;
	onStartPicking: (order: Order) => void;
}

export default function QueueTab({
	filteredOrders,
	autoDeployBackup,
	backupPickersActive,
	onStartPicking,
}: QueueTabProps) {
	return (
		<View style={styles.tabContent}>
			{/* SLA Alert banner */}
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
							style={[styles.orderCard, isCritical && styles.orderCardCritical]}
						>
							<View style={styles.orderCardHeader}>
								<Text style={styles.orderIdText}>Order #{order.orderId}</Text>

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
										{order.items.reduce((sum, i) => sum + i.quantity, 0)} units
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
								onPress={() => onStartPicking(order)}
							>
								<Text style={styles.pickButtonText}>INITIALIZE PICK SCAN</Text>
								<Ionicons name="barcode-outline" size={16} color="#070a13" />
							</TouchableOpacity>
						</View>
					);
				})
			)}
		</View>
	);
}
