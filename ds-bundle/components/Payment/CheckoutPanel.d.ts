import type React from "react";

export interface CheckoutPanelProps {
	/** Order ID for tracking and display */
	orderId: string;
	/** Current order status (pending, payment, processing, approved, triage, failed) */
	orderStatus: string;
	/** User ID associated with the order */
	userId: string;
	/** Last trace ID for debugging/support */
	lastTraceId: string | null;
	/** Whether simulation mode is active */
	isSimulating: boolean;
	/** Simulation mode (AUTO or LOCAL_MOCK) */
	simulationMode: "AUTO" | "LOCAL_MOCK";
	/** Callback when simulation mode changes */
	onSimulationModeChange: (mode: "AUTO" | "LOCAL_MOCK") => void;
	/** Callback when checkout is initiated */
	onCheckout: () => void;
	/** Callback to reset the order */
	onResetOrder: () => void;
	/** Index of the field that was copied (for visual feedback) */
	copiedIndex: number | string | null;
	/** Callback when user copies a value */
	onCopy: (text: string, index: number | string) => void;
}

/**
 * Wholesale order checkout panel.
 * Displays order details, timeline, credit card, payment controls, and status banners.
 *
 * @example
 * ```tsx
 * <CheckoutPanel
 *   orderId="ORD-12345"
 *   orderStatus="payment"
 *   userId="user-123"
 *   lastTraceId="trace-abc"
 *   isSimulating={true}
 *   simulationMode="AUTO"
 *   onSimulationModeChange={(mode) => console.log(mode)}
 *   onCheckout={() => processPayment()}
 *   onResetOrder={() => resetOrder()}
 *   copiedIndex={null}
 *   onCopy={(text, idx) => clipboard.copy(text)}
 * />
 * ```
 */
export const CheckoutPanel: React.FC<CheckoutPanelProps>;
