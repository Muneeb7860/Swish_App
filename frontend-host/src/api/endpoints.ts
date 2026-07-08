import { ApiClient } from "./client";
import * as mocks from "./mocks";
import type {
	AuditLogEntry,
	B2BInvoice,
	B2BRestockOrder,
	CatalogItem,
	ChaosFaultRequest,
	ChaosFaultResponse,
	ComplianceReport,
	HitlResolveRequest,
	HitlTicket,
	LedgerLine,
	LoginRequest,
	LoginResponse,
	LogoutResponse,
	MfaVerifyRequest,
	MfaVerifyResponse,
	OnboardingApplication,
	OnboardingApproveRequest,
	Order,
	OrderCheckoutRequest,
	Payment,
	PaymentRequest,
	PickerHandoverRequest,
	PickerHandoverResponse,
	RebalanceRequest,
	RebalanceResponse,
	RefundRequest,
	RefundResponse,
	RiderCourse,
	RiderOnboardRequest,
	RiderOnboardResponse,
} from "./types";

// ==========================================
// 1. AUTHENTICATION ENDPOINTS
// ==========================================

export const login = (data: LoginRequest): Promise<LoginResponse> => {
	return ApiClient.post<LoginResponse>(
		"/api/v1/auth/login",
		data,
		{ bypassMock: false },
		() => {
			const loginName =
				data.username || (data.email ? data.email.split("@")[0] : "");
			if (loginName === "admin") {
				return {
					mfaRequired: true,
					mfa_required: true,
					sessionToken: "mock-sess-admin",
					session_token: "mock-sess-admin",
				};
			} else if (loginName === "rider") {
				return {
					mfaRequired: true,
					mfa_required: true,
					sessionToken: "mock-sess-rider",
					session_token: "mock-sess-rider",
				};
			} else if (loginName === "inventory") {
				return {
					mfaRequired: true,
					mfa_required: true,
					sessionToken: "mock-sess-inventory",
					session_token: "mock-sess-inventory",
				};
			} else if (loginName === "wholesaler") {
				return {
					mfaRequired: true,
					mfa_required: true,
					sessionToken: "mock-sess-wholesaler",
					session_token: "mock-sess-wholesaler",
				};
			}
			return { token: "mock.jwt.token-bypass" };
		},
	);
};

export const verifyMfa = (
	data: MfaVerifyRequest,
): Promise<MfaVerifyResponse> => {
	return ApiClient.post<MfaVerifyResponse>(
		"/api/v1/auth/mfa/verify",
		data,
		{},
		() => ({ token: `mock.jwt.token-verified-${data.session_token}` }),
	);
};

export const logout = (): Promise<LogoutResponse> => {
	return ApiClient.post<LogoutResponse>("/api/v1/auth/logout", {}, {}, () => ({
		message: "Logged out successfully.",
	}));
};

// ==========================================
// 2. CUSTOMER ENDPOINTS
// ==========================================

export const getCatalog = (): Promise<CatalogItem[]> => {
	return ApiClient.get<CatalogItem[]>(
		"/api/customer/catalog",
		{},
		() => mocks.MOCK_PRODUCTS,
	);
};

export const placeOrder = (order: OrderCheckoutRequest): Promise<Order> => {
	return ApiClient.post<Order>("/api/customer/orders", order, {}, () => {
		const total =
			order.items.reduce((sum, item) => {
				const prod = mocks.MOCK_PRODUCTS.find(
					(p) => p.item_id === item.item_id,
				);
				return sum + (prod ? prod.price * item.quantity : 0);
			}, 0) + (order.tip_amount || 0);

		return {
			order_id: Math.floor(Math.random() * 9000) + 1000,
			customer_id: "CUST-Dave",
			store_id: "store-central",
			rider_id: "rid-1",
			total_amount: Number(total.toFixed(2)),
			weather_surcharge: 0.0,
			payment_method: order.payment_method,
			status: "pending",
			sla_countdown_sec: 180,
			bags_returned: order.bags_returned || 0,
			created_at: new Date().toISOString(),
		};
	});
};

export const getOrders = (): Promise<Order[]> => {
	return ApiClient.get<Order[]>(
		"/api/customer/orders",
		{},
		() => mocks.MOCK_ORDERS,
	);
};

export const requestRefund = (
	id: number,
	request: RefundRequest,
): Promise<RefundResponse> => {
	return ApiClient.post<RefundResponse>(
		`/api/customer/orders/${id}/refund`,
		request,
		{},
		() => ({
			status: "pending_admin_approval",
			message: "AI Audit flagged low-medium confidence. Route to HITL queue.",
			ticket_id: `HITL-${id}`,
		}),
	);
};

export const getLedger = (): Promise<LedgerLine[]> => {
	return ApiClient.get<LedgerLine[]>(
		"/api/customer/ledger",
		{},
		() => mocks.MOCK_LEDGER,
	);
};

export const gdprPurge = (): Promise<{
	status: string;
	probationary_trust_score: number;
}> => {
	return ApiClient.post<{ status: string; probationary_trust_score: number }>(
		"/api/customer/profile/purge",
		{},
		{},
		() => ({
			status: "purged",
			probationary_trust_score: 75,
		}),
	);
};

// ==========================================
// 3. PAYMENTS ENDPOINTS
// ==========================================

export const createPayment = (payment: PaymentRequest): Promise<Payment> => {
	return ApiClient.post<Payment>("/api/payments", payment, {}, () => ({
		payment_id: Math.floor(Math.random() * 90000) + 10000,
		order_id: payment.order_id,
		customer_id: payment.customer_id,
		amount: payment.amount,
		currency: "CHF",
		payment_method: payment.payment_method,
		status: "authorized",
		idempotency_key: `idem-${Date.now()}`,
		created_at: new Date().toISOString(),
	}));
};

export const getPayments = (customerId: string): Promise<Payment[]> => {
	return ApiClient.get<Payment[]>(
		`/api/payments?customerId=${customerId}`,
		{},
		() => [],
	);
};

export const capturePayment = (id: number): Promise<Payment> => {
	return ApiClient.post<Payment>(`/api/payments/${id}/capture`, {}, {}, () => ({
		payment_id: id,
		order_id: 8901,
		customer_id: "CUST-Dave",
		amount: 8.97,
		currency: "CHF",
		payment_method: "Wallet",
		status: "captured",
		idempotency_key: `idem-${id}`,
		created_at: new Date().toISOString(),
		captured_at: new Date().toISOString(),
	}));
};

export const compensatePayment = (id: number): Promise<any> => {
	return ApiClient.post<any>(`/api/payments/${id}/compensate`, {}, {}, () => ({
		status: "compensated",
	}));
};

// ==========================================
// 4. RIDER ENDPOINTS
// ==========================================

export const submitOnboard = (
	onboard: RiderOnboardRequest,
): Promise<RiderOnboardResponse> => {
	return ApiClient.post<RiderOnboardResponse>(
		"/api/rider/onboard",
		onboard,
		{},
		() => ({
			application_id: `app-${Math.random().toString(36).substring(2, 7)}`,
		}),
	);
};

export const getCourses = (): Promise<RiderCourse[]> => {
	return ApiClient.get<RiderCourse[]>(
		"/api/rider/academy/courses",
		{},
		() => mocks.MOCK_COURSES,
	);
};

export const completeCourse = (id: string): Promise<any> => {
	return ApiClient.post<any>(
		`/api/rider/academy/courses/${id}/complete`,
		{},
		{},
		() => ({ status: "completed" }),
	);
};

export const injectCoolant = (
	id: number,
): Promise<{ message: string; new_temperature: number }> => {
	return ApiClient.post<{ message: string; new_temperature: number }>(
		`/api/rider/orders/${id}/coolant`,
		{},
		{},
		() => ({
			message: "Coolant injected. Perishable cargo temperature reset.",
			new_temperature: 4.0,
		}),
	);
};

export const confirmDelivery = (
	id: number,
	request: { pin?: string; photoUrl?: string },
): Promise<any> => {
	return ApiClient.post<any>(
		`/api/rider/orders/${id}/deliver`,
		request,
		{},
		() => ({ status: "delivered", message: "Delivery confirmed." }),
	);
};

export const rejectDelivery = (
	id: number,
	request: { reason: string; photoUrl: string },
): Promise<any> => {
	return ApiClient.post<any>(
		`/api/rider/orders/${id}/reject`,
		request,
		{},
		() => ({ status: "rejected", message: "Delivery rejected at door." }),
	);
};

export const recordRiderTelemetry = (
	id: number,
	request: { latitude: number; longitude: number; temperature: number },
): Promise<any> => {
	return ApiClient.post<any>(
		`/api/rider/orders/${id}/telemetry`,
		request,
		{},
		() => ({ status: "success", orderId: id }),
	);
};

// ==========================================
// 5. INVENTORY / PICKER ENDPOINTS
// ==========================================

export const getPickerQueue = (): Promise<Order[]> => {
	return ApiClient.get<Order[]>("/api/inventory/picker/queue", {}, () =>
		mocks.MOCK_ORDERS.filter(
			(o) => o.status === "picking" || o.status === "pending",
		),
	);
};

export const handoverItem = (
	data: PickerHandoverRequest,
): Promise<PickerHandoverResponse> => {
	return ApiClient.post<PickerHandoverResponse>(
		"/api/inventory/picker/handover",
		data,
		{},
		() => ({
			status: "success",
			lightning_bonus_awarded: data.duration_seconds < 4.0,
			replacement_order_dispatched: data.contains_packing_error,
		}),
	);
};

export const rebalanceInventory = (
	data: RebalanceRequest,
): Promise<RebalanceResponse> => {
	return ApiClient.post<RebalanceResponse>(
		"/api/inventory/rebalance",
		data,
		{},
		() => ({
			message: "MFC stock rebalancing initiated.",
			transfer_truck_id: "TRK-9831",
		}),
	);
};

// ==========================================
// 6. WHOLESALER ENDPOINTS
// ==========================================

export const getRestocks = (): Promise<B2BRestockOrder[]> => {
	return ApiClient.get<B2BRestockOrder[]>(
		"/api/wholesaler/restocks",
		{},
		() => mocks.MOCK_RESTOCKS,
	);
};

export const fulfillRestock = (
	id: number,
): Promise<{ status: string; invoice_amount: number }> => {
	return ApiClient.post<{ status: string; invoice_amount: number }>(
		`/api/wholesaler/restocks/${id}/fulfill`,
		{},
		{},
		() => ({
			status: "fulfilled",
			invoice_amount: 150.0,
		}),
	);
};

export const getInvoices = (): Promise<B2BInvoice[]> => {
	return ApiClient.get<B2BInvoice[]>(
		"/api/wholesaler/invoices",
		{},
		() => mocks.MOCK_INVOICES,
	);
};

// ==========================================
// 7. ADMIN ENDPOINTS
// ==========================================

export const getChaosFaults = (): Promise<ChaosFaultResponse[]> => {
	return ApiClient.get<ChaosFaultResponse[]>(
		"/api/admin/chaos/active",
		{},
		() => [{ fault_id: 101, status: "active" }],
	);
};

export const injectChaosFault = (
	data: ChaosFaultRequest,
): Promise<ChaosFaultResponse> => {
	// The path in AdminController is PostMapping("/chaos/faults") with InjectFaultRequest
	// So we call "/api/admin/chaos/faults" with body { faultType: data.fault_type, details: data.action }
	const body = {
		faultType: data.fault_type,
		details: data.action,
	};
	return ApiClient.post<ChaosFaultResponse>(
		"/api/admin/chaos/faults",
		body,
		{},
		() => ({
			fault_id: Math.floor(Math.random() * 1000),
			status: data.action === "inject" ? "active" : "resolved",
		}),
	);
};

export const resolveChaosFault = (id: number): Promise<any> => {
	return ApiClient.post<any>(`/api/admin/chaos/${id}/resolve`, {}, {}, () => ({
		status: "resolved",
	}));
};

export const getOnboardQueue = (): Promise<OnboardingApplication[]> => {
	return ApiClient.get<OnboardingApplication[]>(
		"/api/admin/onboard/queue",
		{},
		() => mocks.MOCK_ONBOARDING,
	);
};

export const approveOnboard = (
	id: string,
	data: OnboardingApproveRequest,
): Promise<OnboardingApplication> => {
	const body = {
		gate: data.validator_role,
	};
	return ApiClient.post<OnboardingApplication>(
		`/api/admin/onboard/queue/${id}/approve`,
		body,
		{},
		() => ({
			application_id: id,
			applicant_type: "rider",
			name: "Rider Dave",
			approval_ops: data.validator_role === "Ops" ? data.approve : false,
			approval_compliance:
				data.validator_role === "Compliance" ? data.approve : false,
			approval_admin: data.validator_role === "Admin" ? data.approve : false,
			created_at: new Date().toISOString(),
		}),
	);
};

export const getHitlQueue = (): Promise<HitlTicket[]> => {
	return ApiClient.get<HitlTicket[]>(
		"/api/admin/hitl/queue",
		{},
		() => mocks.MOCK_HITL_TICKETS,
	);
};

export const resolveHitl = (
	id: string,
	data: HitlResolveRequest,
): Promise<HitlTicket> => {
	const body = {
		decision: data.action === "approve" ? "approve" : "reject",
		reason: "Resolved via Admin panel",
	};
	return ApiClient.post<HitlTicket>(
		`/api/admin/hitl/queue/${id}/resolve`,
		body,
		{},
		() => ({
			ticket_id: id,
			type: "customer_refund",
			description: "Refund request resolved",
			amount: 8.97,
			status: data.action === "approve" ? "approved" : "voided",
			created_at: new Date().toISOString(),
		}),
	);
};

// ==========================================
// 8. SECURITY & COMPLIANCE ENDPOINTS
// ==========================================

export const getAuditLog = (): Promise<AuditLogEntry[]> => {
	return ApiClient.get<AuditLogEntry[]>(
		"/api/security/audit",
		{},
		() => mocks.MOCK_AUDIT_LOGS,
	);
};

export const rotateVaultKey = (): Promise<{ status: string }> => {
	return ApiClient.post<{ status: string }>(
		"/api/security/vault/rotate",
		{},
		{},
		() => ({ status: "rotated" }),
	);
};

export const getCompliance = (): Promise<ComplianceReport> => {
	return ApiClient.get<ComplianceReport>(
		"/api/security/compliance",
		{},
		() => mocks.MOCK_COMPLIANCE,
	);
};

// ==========================================
// 9. TELEMETRY ENDPOINTS
// ==========================================

export const injectDryIce = (orderId: number): Promise<any> => {
	return ApiClient.post<any>(
		`/api/telemetry/${orderId}/dry-ice`,
		{},
		{},
		() => ({
			orderId,
			temperature: 4.0,
			dryIceInjected: true,
			thermalBreachActive: false,
			message: "Dry ice cargo cooling completed.",
		}),
	);
};

export const ingestTelemetryTick = (data: {
	orderId: number;
	latitude: number;
	longitude: number;
	temperature: number;
	dryIceInjected?: boolean;
}): Promise<any> => {
	return ApiClient.post<any>("/api/telemetry/tick", data, {}, () => ({
		orderId: data.orderId,
		latitude: data.latitude,
		longitude: data.longitude,
		temperature: data.temperature,
		dryIceInjected: !!data.dryIceInjected,
		timestamp: new Date().toISOString(),
		alertTriggered: data.temperature > 8.0,
		thermalBreachActive: data.temperature > 8.0,
	}));
};

// ==========================================
// 10. B2B GOVERNANCE ENDPOINTS
// ==========================================

export const getB2bHitlQueue = (): Promise<any[]> => {
	return ApiClient.get<any[]>("/api/governance/hitl", {}, () => [
		{
			id: 991,
			restockOrderId: 3001,
			wholesalerId: "wholesaler-zuri",
			amount: 150.0,
		},
	]);
};

export const approveB2bOverride = (
	id: number,
	request: { operator: string; reason: string },
): Promise<any> => {
	return ApiClient.post<any>(
		`/api/governance/hitl/${id}/approve`,
		request,
		{},
		() => ({
			approvalId: id,
			status: "APPROVED",
			message: "B2B restock transaction successfully overridden and released.",
		}),
	);
};

export const rejectB2bOverride = (
	id: number,
	request: { operator: string; reason: string },
): Promise<any> => {
	return ApiClient.post<any>(
		`/api/governance/hitl/${id}/reject`,
		request,
		{},
		() => ({
			approvalId: id,
			status: "REJECTED",
			message: "B2B restock transaction override rejected. Order canceled.",
		}),
	);
};
