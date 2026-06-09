import { MaterialCommunityIcons } from "@expo/vector-icons";
import React from "react";
import { Modal, Text, TouchableOpacity, View } from "react-native";
import styles from "../styles";

interface LightningModalProps {
	visible: boolean;
	onDismiss: () => void;
}

export default function LightningModal({
	visible,
	onDismiss,
}: LightningModalProps) {
	return (
		<Modal visible={visible} transparent={true} animationType="fade">
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
						Order pick handover completed in under 90 seconds. Your speed record
						was committed to the Security Trust Ledger.
					</Text>
					<TouchableOpacity style={styles.celebrationBtn} onPress={onDismiss}>
						<Text style={styles.celebrationBtnText}>SECURE BONUS CREDITS</Text>
					</TouchableOpacity>
				</View>
			</View>
		</Modal>
	);
}
