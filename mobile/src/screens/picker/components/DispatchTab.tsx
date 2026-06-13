import { FontAwesome5 } from "@expo/vector-icons";
import React from "react";
import {
	ActivityIndicator,
	ScrollView,
	Text,
	TouchableOpacity,
	View,
} from "react-native";
import { DEFAULT_ITEMS, THEME } from "../constants";
import styles from "../styles";
import { DispatchResult } from "../types";

interface DispatchTabProps {
	dispatchSource: string;
	setDispatchSource: (v: string) => void;
	dispatchTarget: string;
	setDispatchTarget: (v: string) => void;
	dispatchItem: string;
	setDispatchItem: (v: string) => void;
	dispatchQty: number;
	setDispatchQty: (fn: (q: number) => number) => void;
	dispatchLoading: boolean;
	lastDispatchResult: DispatchResult | null;
	onDispatch: () => void;
}

export default function DispatchTab({
	dispatchSource,
	setDispatchSource,
	dispatchTarget,
	setDispatchTarget,
	dispatchItem,
	setDispatchItem,
	dispatchQty,
	setDispatchQty,
	dispatchLoading,
	lastDispatchResult,
	onDispatch,
}: DispatchTabProps) {
	return (
		<View style={styles.tabContent}>
			<View style={styles.card}>
				<Text style={styles.cardTitle}>
					Inter-Store Stock Balance Dispatcher
				</Text>
				<Text style={styles.cardDescription}>
					Dispatch autonomous trucks to rebalance product items from surplus
					zones to target fulfillment centers (PostgreSQL serializable ledger
					sync).
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
					onPress={onDispatch}
					disabled={dispatchLoading}
				>
					{dispatchLoading ? (
						<ActivityIndicator size="small" color="#070a13" />
					) : (
						<>
							<Text style={styles.dispatchSubmitBtnText}>
								DISPATCH TRANSFER VEHICLE
							</Text>
							<FontAwesome5 name="truck-loading" size={14} color="#070a13" />
						</>
					)}
				</TouchableOpacity>
			</View>

			{/* Last Dispatch Result Card */}
			{lastDispatchResult && (
				<View
					style={[styles.card, { borderColor: THEME.customer, borderWidth: 1 }]}
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
							<Text style={styles.resultVal}>{lastDispatchResult.item}</Text>
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
								{lastDispatchResult.fromStore} ➡️ {lastDispatchResult.toStore}
							</Text>
						</View>
					</View>
				</View>
			)}
		</View>
	);
}
