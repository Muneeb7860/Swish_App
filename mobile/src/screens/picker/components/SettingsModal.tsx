import { Ionicons } from "@expo/vector-icons";
import React from "react";
import {
	ActivityIndicator,
	Modal,
	ScrollView,
	Switch,
	Text,
	TextInput,
	TouchableOpacity,
	View,
} from "react-native";
import { THEME } from "../constants";
import styles from "../styles";

interface SettingsModalProps {
	visible: boolean;
	useSimulator: boolean;
	setUseSimulator: (v: boolean) => void;
	bffUrl: string;
	setBffUrl: (v: string) => void;
	jwtToken: string;
	setJwtToken: (v: string) => void;
	pickerId: string;
	setPickerId: (v: string) => void;
	loading: boolean;
	onTest: () => void;
	onClose: () => void;
}

export default function SettingsModal({
	visible,
	useSimulator,
	setUseSimulator,
	bffUrl,
	setBffUrl,
	jwtToken,
	setJwtToken,
	pickerId,
	setPickerId,
	loading,
	onTest,
	onClose,
}: SettingsModalProps) {
	return (
		<Modal visible={visible} animationType="fade" transparent={true}>
			<View style={styles.modalOverlay}>
				<View style={styles.settingsModalCard}>
					<View style={styles.modalHeader}>
						<Text style={styles.settingsTitle}>BFF Gateway Configuration</Text>
						<TouchableOpacity onPress={onClose}>
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

								<TouchableOpacity style={styles.testBtn} onPress={onTest}>
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
	);
}
