import { useCallback, useState } from "react";
import { buildRequestHeaders } from "../utils/api";

interface LogEntry {
	text: string;
	type: "info" | "success" | "warning" | "error";
	time: string;
}

interface UseOrderSimulationOptions {
	gatewayUrl: string;
	accessToken: string;
	userId: string;
	addLog: (message: string, type?: LogEntry["type"]) => void;
}

/**
 * Custom hook encapsulating all checkout/payment simulation logic.
 * Handles API calls with local mock fallback, order state management.
 */
export function useOrderSimulation({
	gatewayUrl,
	accessToken,
	userId,
	addLog,
}: UseOrderSimulationOptions) {
	const [orderId, setOrderId] = useState(
		`ORD-${Math.floor(100000 + Math.random() * 900000)}`,
	);
	const [orderStatus, setOrderStatus] = useState("PENDING");
	const [lastTraceId, setLastTraceId] = useState<string | null>(null);
	const [isSimulating, setIsSimulating] = useState(false);
	const [simulationMode, setSimulationMode] = useState<"AUTO" | "LOCAL_MOCK">(
		"AUTO",
	);

	const getHeaders = useCallback(
		(custom: Record<string, string> = {}) =>
			buildRequestHeaders(accessToken, custom),
		[accessToken],
	);

	const runLocalMock = useCallback(
		(_idempotencyKey: string, traceId: string) => {
			setTimeout(() => {
				addLog(`[Local Mock] Payment succeeded.`, "success");
				setOrderStatus("PROCESSING");

				window.dispatchEvent(
					new CustomEvent("ws-packet-received", {
						detail: {
							id: `mock-msg-${Math.random()}`,
							type: "PAYMENT_CONFIRMED",
							timestamp: new Date().toISOString(),
							recipientId: userId,
							correlationId: traceId,
							payload: { status: "CONFIRMED", orderId },
						},
					}),
				);

				setTimeout(() => {
					const isApproved = Math.random() > 0.3;
					const statusOutcome = isApproved ? "APPROVED" : "HUMAN_TRIAGE";

					setOrderStatus(statusOutcome);
					addLog(
						`[Local Mock] AI Evaluation complete: ${statusOutcome}`,
						"success",
					);

					window.dispatchEvent(
						new CustomEvent("ws-packet-received", {
							detail: {
								id: `mock-msg-${Math.random()}`,
								type: "ORDER_EVALUATED",
								timestamp: new Date().toISOString(),
								recipientId: userId,
								correlationId: traceId,
								payload: { ai_status: statusOutcome, orderId },
							},
						}),
					);
				}, 3000);
			}, 1500);
			setIsSimulating(false);
		},
		[userId, orderId, addLog],
	);

	const handleCheckout = useCallback(async () => {
		setIsSimulating(true);
		setOrderStatus("PAYMENT_PROCESSING");
		const idempotencyKey = `idemp-${Math.floor(Math.random() * 100000000)}`;
		const traceId = `trace-${Math.floor(Math.random() * 100000000)}`;

		addLog(
			`Initiating checkout. Order: ${orderId}, Idempotency: ${idempotencyKey}`,
			"info",
		);

		if (simulationMode === "LOCAL_MOCK") {
			runLocalMock(idempotencyKey, traceId);
			return;
		}

		try {
			addLog(`Calling Gateway: POST /api/v1/checkout/intents...`, "info");
			const intentRes = await fetch(`${gatewayUrl}/api/v1/checkout/intents`, {
				method: "POST",
				headers: getHeaders({ "X-Idempotency-Key": idempotencyKey }),
				body: JSON.stringify({
					customerId: userId,
					orderId: orderId,
					amount: 1250000,
				}),
			});

			if (!intentRes.ok) {
				throw new Error(`Intent API returned status ${intentRes.status}`);
			}

			const intentData = await intentRes.json();
			addLog(
				`PaymentIntent created. ID: ${intentData.paymentId}. Client Secret acquired.`,
				"success",
			);

			addLog(`Calling Gateway: POST /api/webhooks/payments/stripe...`, "info");
			const webhookRes = await fetch(
				`${gatewayUrl}/api/webhooks/payments/stripe`,
				{
					method: "POST",
					headers: {
						"Content-Type": "application/json",
						"Stripe-Signature": "mock_sig_for_dev",
					},
					body: JSON.stringify({
						id: `evt_${Math.floor(Math.random() * 100000)}`,
						type: "payment_intent.succeeded",
						paymentIntentId: intentData.paymentId,
						correlationId: traceId,
					}),
				},
			);

			if (!webhookRes.ok) {
				throw new Error(`Webhook API returned status ${webhookRes.status}`);
			}

			addLog(
				`Simulated Stripe Webhook dispatched successfully. Waiting for WebSocket message...`,
				"success",
			);
			setLastTraceId(traceId);
		} catch (error: any) {
			addLog(
				`API Connection Failed: ${error.message}. Falling back to Local Mock.`,
				"warning",
			);
			runLocalMock(idempotencyKey, traceId);
		} finally {
			setIsSimulating(false);
		}
	}, [
		orderId,
		simulationMode,
		gatewayUrl,
		userId,
		addLog,
		getHeaders,
		runLocalMock,
	]);

	const resetOrder = useCallback(() => {
		setOrderId(`ORD-${Math.floor(100000 + Math.random() * 900000)}`);
		setOrderStatus("PENDING");
		setLastTraceId(null);
		addLog("Order state reset.", "info");
	}, [addLog]);

	return {
		orderId,
		orderStatus,
		setOrderStatus,
		lastTraceId,
		setLastTraceId,
		isSimulating,
		simulationMode,
		setSimulationMode,
		handleCheckout,
		resetOrder,
	};
}
