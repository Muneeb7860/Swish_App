import { useCallback, useEffect, useRef, useState } from "react";
import { useStore } from "../store";
import { ApiClient, BASE_URL } from "./client";

export type ConnectionStatus =
	| "CONNECTED"
	| "CONNECTING"
	| "RECONNECTING"
	| "DISCONNECTED"
	| "ERROR";

export interface WebSocketOptions {
	userId: string;
	accessToken?: string;
	maxReconnectAttempts?: number;
	reconnectIntervalMin?: number;
	reconnectIntervalMax?: number;
	onMessage?: (payload: any) => void;
}

export interface UseResilientWebSocketResult {
	status: ConnectionStatus;
	notifications: any[];
	reconnectAttempts: number;
	reconnect: () => void;
	disconnect: () => void;
	clearNotifications: () => void;
	send: (msg: any) => void;
}

// -------------------------------------------------------------
// Global WebSocket Hook (Reused by MFEs via Host exposes)
// -------------------------------------------------------------
export const useResilientWebSocket = (
	url: string,
	options: WebSocketOptions,
): UseResilientWebSocketResult => {
	const {
		userId,
		accessToken,
		maxReconnectAttempts = 5,
		reconnectIntervalMin = 1000,
		reconnectIntervalMax = 30000,
	} = options;

	const [status, setStatus] = useState<ConnectionStatus>("DISCONNECTED");
	const [notifications, setNotifications] = useState<any[]>([]);
	const [reconnectAttempts, setReconnectAttempts] = useState(0);

	const wsRef = useRef<WebSocket | null>(null);
	const reconnectAttemptRef = useRef(0);
	const reconnectTimeoutRef = useRef<any>(null);
	const seenMessageIds = useRef<Set<string>>(new Set());

	const onMessageReceived = useRef(options.onMessage);
	useEffect(() => {
		onMessageReceived.current = options.onMessage;
	}, [options.onMessage]);

	const connect = useCallback(() => {
		if (ApiClient.isMockMode()) {
			setStatus("CONNECTED");
			return;
		}

		if (!userId) {
			console.warn("[WebSocket] Missing userId, cannot connect");
			setStatus("ERROR");
			return;
		}

		if (
			wsRef.current &&
			(wsRef.current.readyState === WebSocket.OPEN ||
				wsRef.current.readyState === WebSocket.CONNECTING)
		) {
			return;
		}

		if (reconnectAttemptRef.current > 0) {
			setStatus("RECONNECTING");
		} else {
			setStatus("CONNECTING");
		}

		const queryParams = new URLSearchParams();
		queryParams.append("userId", userId);
		if (accessToken) {
			queryParams.append("access_token", accessToken);
		}

		const wsUrl = `${url}?${queryParams.toString()}`;
		console.log(`[WebSocket] Connecting to ${url} for user ${userId}...`);

		let ws: WebSocket;
		try {
			ws = new WebSocket(wsUrl);
		} catch (err) {
			console.error("[WebSocket] Failed to instantiate WebSocket:", err);
			setStatus("ERROR");
			handleReconnect();
			return;
		}

		ws.onopen = () => {
			console.log(`[WebSocket] Connected successfully for user ${userId}`);
			setStatus("CONNECTED");
			reconnectAttemptRef.current = 0;
			setReconnectAttempts(0);
		};

		ws.onmessage = (event) => {
			try {
				const rawData = event.data;
				if (typeof rawData !== "string") {
					throw new Error("WebSocket message data is not a string");
				}
				if (rawData.length > 131072) {
					throw new Error("WebSocket payload size limit exceeded");
				}

				// JSON nesting depth check
				let depth = 0;
				for (let i = 0; i < rawData.length; i++) {
					const char = rawData[i];
					if (char === "{" || char === "[") {
						depth++;
						if (depth > 20) {
							throw new Error("WebSocket payload nesting depth limit exceeded");
						}
					} else if (char === "}" || char === "]") {
						depth--;
					}
				}

				const rawPayload = JSON.parse(rawData);

				// Sanitization helper
				const sanitizeValue = (val: any): any => {
					if (typeof val === "string") {
						return val
							.replace(/<script[^>]*>([\s\S]*?)<\/script>/gi, "")
							.replace(/<[^>]+>/g, "");
					}
					if (val !== null && typeof val === "object") {
						const cleanObj: any = Array.isArray(val) ? [] : {};
						for (const key in val) {
							if (Object.hasOwn(val, key)) {
								cleanObj[key] = sanitizeValue(val[key]);
							}
						}
						return cleanObj;
					}
					return val;
				};

				const payload = sanitizeValue(rawPayload);

				if (payload.type === "HEARTBEAT") {
					return;
				}

				if (payload.id) {
					if (seenMessageIds.current.has(payload.id)) {
						return;
					}
					seenMessageIds.current.add(payload.id);
					// Evict oldest elements if capacity exceeds 1000 items (sliding window protection)
					if (seenMessageIds.current.size > 1000) {
						const firstKey = seenMessageIds.current.keys().next().value;
						if (firstKey !== undefined) {
							seenMessageIds.current.delete(firstKey);
						}
					}
				}

				if (payload.type && payload.type !== "WELCOME") {
					setNotifications((prev) => [payload, ...prev].slice(0, 50));
				}

				if (onMessageReceived.current) {
					onMessageReceived.current(payload);
				}
			} catch (err: any) {
				console.error(
					"[WebSocket] Failed to validate/parse message:",
					err.message,
				);
			}
		};

		ws.onclose = (event) => {
			console.log(`[WebSocket] Connection closed. Code: ${event.code}`);
			wsRef.current = null;

			if (event.code === 1000 || event.code === 4003) {
				setStatus("DISCONNECTED");
				reconnectAttemptRef.current = 0;
				setReconnectAttempts(0);
				return;
			}

			handleReconnect();
		};

		ws.onerror = (err) => {
			console.error("[WebSocket] Error detected:", err);
			ws.close();
		};

		wsRef.current = ws;
	}, [
		url,
		userId,
		accessToken,
		maxReconnectAttempts,
		reconnectIntervalMin,
		reconnectIntervalMax,
	]);

	const handleReconnect = () => {
		if (reconnectAttemptRef.current < maxReconnectAttempts) {
			const backoff = reconnectIntervalMin * 2 ** reconnectAttemptRef.current;
			const jitter = Math.random() * 500;
			const delay = Math.min(backoff + jitter, reconnectIntervalMax);

			reconnectAttemptRef.current += 1;
			setReconnectAttempts(reconnectAttemptRef.current);
			setStatus("RECONNECTING");

			if (reconnectTimeoutRef.current)
				clearTimeout(reconnectTimeoutRef.current);
			reconnectTimeoutRef.current = setTimeout(() => {
				connect();
			}, delay);
		} else {
			setStatus("ERROR");
		}
	};

	const disconnect = useCallback(() => {
		if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
		reconnectAttemptRef.current = 0;
		setReconnectAttempts(0);

		if (wsRef.current) {
			wsRef.current.close(1000, "Manual disconnect");
			wsRef.current = null;
		}
		setStatus("DISCONNECTED");
	}, []);

	const reconnect = useCallback(() => {
		disconnect();
		connect();
	}, [disconnect, connect]);

	const send = useCallback((msg: any) => {
		if (ApiClient.isMockMode()) {
			console.log("[WebSocket Mock] Message sent:", msg);
			return;
		}
		if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
			wsRef.current.send(typeof msg === "string" ? msg : JSON.stringify(msg));
		} else {
			console.warn("[WebSocket] Cannot send, socket not open");
		}
	}, []);

	useEffect(() => {
		connect();
		return () => {
			if (reconnectTimeoutRef.current)
				clearTimeout(reconnectTimeoutRef.current);
			if (wsRef.current) {
				wsRef.current.close(1000, "Component unmounting");
				wsRef.current = null;
			}
		};
	}, [connect]);

	return {
		status,
		notifications,
		reconnectAttempts,
		reconnect,
		disconnect,
		clearNotifications: () => {
			seenMessageIds.current.clear();
			setNotifications([]);
		},
		send,
	};
};

// -------------------------------------------------------------
// Real-Time Order Tracking Socket (Class-based Event Listener)
// -------------------------------------------------------------
type OrderStatusCallback = (status: string, metadata?: any) => void;

export class OrderStatusSocket {
	private static listeners: Map<number, Set<OrderStatusCallback>> = new Map();
	private static ws: WebSocket | null = null;
	private static simulatedTimers: Map<number, any> = new Map();
	private static reconnectAttempts: Map<number, number> = new Map();

	public static subscribe(
		orderId: number,
		callback: OrderStatusCallback,
	): () => void {
		if (!OrderStatusSocket.listeners.has(orderId)) {
			OrderStatusSocket.listeners.set(orderId, new Set());
		}
		OrderStatusSocket.listeners.get(orderId)!.add(callback);

		// Initialize socket connection or simulated flow
		OrderStatusSocket.initForOrder(orderId);

		// Return unsubscribe function
		return () => {
			const orderListeners = OrderStatusSocket.listeners.get(orderId);
			if (orderListeners) {
				orderListeners.delete(callback);
				if (orderListeners.size === 0) {
					OrderStatusSocket.listeners.delete(orderId);
					OrderStatusSocket.cleanupOrder(orderId);
				}
			}
		};
	}

	private static trigger(orderId: number, status: string, metadata?: any) {
		const orderListeners = OrderStatusSocket.listeners.get(orderId);
		if (orderListeners) {
			for (const cb of orderListeners) {
				try {
					cb(status, metadata);
				} catch (e) {
					console.error("Error invoking order status listener callback", e);
				}
			}
		}
	}

	private static initForOrder(orderId: number) {
		if (ApiClient.isMockMode()) {
			// Start simulated status update sequence:
			// pending (10s) -> picking (15s) -> shipped (15s) -> delivered (done)
			console.log(
				`[WebSocket Mock] Starting order tracker simulation for #${orderId}`,
			);
			if (OrderStatusSocket.simulatedTimers.has(orderId)) return;

			OrderStatusSocket.trigger(orderId, "pending");

			const timer = setTimeout(() => {
				OrderStatusSocket.trigger(orderId, "picking");
				const t2 = setTimeout(() => {
					OrderStatusSocket.trigger(orderId, "shipped", {
						temperature: 4.2,
						riderCoords: { lat: 12.971, lng: 77.594 },
						riderName: "Rider Dave",
					});
					const t3 = setTimeout(() => {
						OrderStatusSocket.trigger(orderId, "delivered");
					}, 20000);
					OrderStatusSocket.simulatedTimers.set(orderId, t3);
				}, 15000);
				OrderStatusSocket.simulatedTimers.set(orderId, t2);
			}, 10000);

			OrderStatusSocket.simulatedTimers.set(orderId, timer);
		} else {
			// Real WebSocket connection to backend gateway
			if (OrderStatusSocket.ws) return;
			const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
			const wsHost = BASE_URL_HOST(BASE_URL);
			const url = `${wsProtocol}//${wsHost}/api/ws/orders`;

			try {
				OrderStatusSocket.ws = new WebSocket(url);
				OrderStatusSocket.ws.onopen = () => {
					OrderStatusSocket.reconnectAttempts.set(orderId, 0);
				};
				OrderStatusSocket.ws.onmessage = (event) => {
					const data = JSON.parse(event.data);
					if (data && data.orderId) {
						OrderStatusSocket.trigger(data.orderId, data.status, data.metadata);
					}
				};
				OrderStatusSocket.ws.onclose = () => {
					OrderStatusSocket.ws = null;
					const attempts =
						OrderStatusSocket.reconnectAttempts.get(orderId) || 0;
					if (attempts < 5) {
						OrderStatusSocket.reconnectAttempts.set(orderId, attempts + 1);
						console.warn(
							`[WebSocket] Order status reconnect attempt #${attempts + 1}/5 in 3000ms`,
						);
						setTimeout(() => OrderStatusSocket.initForOrder(orderId), 3000);
					} else {
						console.error(
							"[WebSocket] Order status reconnect: Max attempts (5) reached. Stopping reconnection.",
						);
					}
				};
			} catch (e) {
				console.error(
					"[WebSocket] Failed to connect to real-time order service",
					e,
				);
			}
		}
	}

	private static cleanupOrder(orderId: number) {
		OrderStatusSocket.reconnectAttempts.delete(orderId);
		if (OrderStatusSocket.simulatedTimers.has(orderId)) {
			clearTimeout(OrderStatusSocket.simulatedTimers.get(orderId));
			OrderStatusSocket.simulatedTimers.delete(orderId);
			console.log(
				`[WebSocket Mock] Cleaned up order simulation for #${orderId}`,
			);
		}
		if (OrderStatusSocket.listeners.size === 0 && OrderStatusSocket.ws) {
			OrderStatusSocket.ws.close();
			OrderStatusSocket.ws = null;
		}
	}
}

const BASE_URL_HOST = (url: string) => {
	try {
		return new URL(url).host;
	} catch (e) {
		return "localhost:8080";
	}
};
