import { Ionicons } from "@expo/vector-icons";
import React from "react";
import {
	ActivityIndicator,
	Modal,
	ScrollView,
	Text,
	TextInput,
	TouchableOpacity,
	View,
} from "react-native";
import { THEME } from "../constants";
import styles from "../styles";
import { Order } from "../types";

interface PickingModalProps {
	order: Order;
	pickedItemChecks: Record<string, boolean>;
	selectedRider: string;
	setSelectedRider: (v: string) => void;
	handoverLoading: boolean;
	onToggleItem: (itemId: string) => void;
	onClose: () => void;
	onCompleteHandover: (containsError: boolean) => void;
}

export default function PickingModal({
	order,
	pickedItemChecks,
	selectedRider,
	setSelectedRider,
	handoverLoading,
	onToggleItem,
	onClose,
	onCompleteHandover,
}: PickingModalProps) {
	return (
		<Modal visible={true} animationType="slide" transparent={true}>
			<View style={styles.modalOverlay}>
				<View style={styles.pickModalContainer}>
					<View style={styles.pickModalHeader}>
						<View>
							<Text style={styles.pickModalTitle}>
								Picking Order #{order.orderId}
							</Text>
							<Text style={styles.pickModalSub}>
								Store Location: {order.storeId.toUpperCase()} MFC
							</Text>
						</View>
						<TouchableOpacity style={styles.closeModalBtn} onPress={onClose}>
							<Ionicons name="close" size={24} color={THEME.textPrimary} />
						</TouchableOpacity>
					</View>

					{/* Items checklist */}
					<Text style={styles.checklistTitle}>ITEMS PACK CHECKLIST</Text>
					<ScrollView style={styles.checklistScroll}>
						{order.items.map((item, idx) => {
							const isChecked = pickedItemChecks[item.itemId];
							return (
								<TouchableOpacity
									key={idx}
									style={[
										styles.checkItemRow,
										isChecked && styles.checkItemRowChecked,
									]}
									onPress={() => onToggleItem(item.itemId)}
								>
									<View
										style={[
											styles.checkbox,
											isChecked && styles.checkboxChecked,
										]}
									>
										{isChecked && (
											<Ionicons name="checkmark" size={14} color="#070a13" />
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

					{/* Rider and handover controls */}
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
								onPress={() => onCompleteHandover(true)}
								disabled={handoverLoading}
							>
								<Text style={styles.handoverBtnErrorText}>REPORT ERROR</Text>
							</TouchableOpacity>

							<TouchableOpacity
								style={[
									styles.handoverBtn,
									styles.handoverBtnSuccess,
									handoverLoading && { opacity: 0.6 },
								]}
								onPress={() => onCompleteHandover(false)}
								disabled={handoverLoading}
							>
								{handoverLoading ? (
									<ActivityIndicator size="small" color="#070a13" />
								) : (
									<>
										<Text style={styles.handoverBtnText}>HANDOVER CARGO</Text>
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
	);
}
