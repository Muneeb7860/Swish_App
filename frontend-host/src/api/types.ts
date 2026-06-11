export interface LoginRequest {
	username?: string;
	email?: string;
	password?: string;
}

export interface LoginResponse {
	mfa_required?: boolean;
	mfaRequired?: boolean;
	session_token?: string;
	sessionToken?: string;
	token?: string;
}

export interface MfaVerifyRequest {
	session_token: string;
	code: string;
}

export interface MfaVerifyResponse {
	token?: string;
}

export interface LogoutResponse {
	message?: string;
}

export interface CatalogItem {
	item_id: string;
	name: string;
	price: number;
	stock: number;
	category: string;
	emoji: string;
	perishable: boolean;
}

export interface OrderCheckoutItemRequest {
	item_id: string;
	quantity: number;
}

export interface OrderCheckoutRequest {
	items: OrderCheckoutItemRequest[];
	payment_method: "Wallet" | "Swipe" | "PayPal" | "Paytm" | "Cash on Delivery";
	tip_amount?: number;
	bags_returned?: number;
}

export interface Order {
	order_id: number;
	customer_id: string;
	store_id: string;
	rider_id: string;
	total_amount: number;
	weather_surcharge: number;
	payment_method: string;
	status: string;
	sla_countdown_sec: number;
	bags_returned: number;
	created_at: string;
}

export interface RefundRequest {
	claim_reason: string;
	customer_latitude: number;
	customer_longitude: number;
	photo_exif_timestamp: string;
}

export interface RefundResponse {
	status: "approved" | "pending_admin_approval" | "rejected";
	message: string;
	ticket_id: string;
}

export interface PaymentRequest {
	order_id: number;
	customer_id: string;
	amount: number;
	payment_method: "Wallet" | "Swipe" | "PayPal" | "Paytm" | "Cash on Delivery" | "Direct Debit" | "Bank Transfer";
}

export interface Payment {
	payment_id: number;
	order_id: number;
	customer_id: string;
	amount: number;
	currency: string;
	payment_method: string;
	status: string;
	idempotency_key: string;
	external_reference?: string;
	created_at: string;
	captured_at?: string;
	refunded_at?: string;
}

export interface RiderOnboardRequest {
	full_name: string;
	vehicle_type: "E-Bike" | "Scooter";
	driver_license_base64: string;
}

export interface RiderOnboardResponse {
	application_id: string;
}

export interface RiderCourse {
	course_id: string;
	course_name: string;
}

export interface PickerHandoverRequest {
	order_id: number;
	duration_seconds: number;
	contains_packing_error: boolean;
}

export interface PickerHandoverResponse {
	status: string;
	lightning_bonus_awarded: boolean;
	replacement_order_dispatched: boolean;
}

export interface RebalanceRequest {
	source_store_id: string;
	target_store_id: string;
	item_id: string;
	quantity: number;
}

export interface RebalanceResponse {
	message: string;
	transfer_truck_id: string;
}

export interface B2BRestockOrder {
	restock_order_id: number;
	store_id: string;
	wholesaler_id: string;
	invoice_amount: number;
	is_fallback: boolean;
	status: string;
	created_at: string;
}

export interface B2BInvoice {
	invoice_id: number;
	restock_order_id: number;
	wholesaler_id: string;
	amount: number;
	status: string;
	created_at: string;
}

export interface ChaosFaultRequest {
	fault_type: "cold_chain_breakdown" | "wholesaler_outage" | "traffic_congestion";
	action: "inject" | "resolve";
}

export interface ChaosFaultResponse {
	fault_id: number;
	status: string;
}

export interface OnboardingApplication {
	application_id: string;
	applicant_type: string;
	name: string;
	approval_ops: boolean;
	approval_compliance: boolean;
	approval_admin: boolean;
	created_at: string;
}

export interface OnboardingApproveRequest {
	approve: boolean;
	validator_role: "Ops" | "Compliance" | "Admin";
}

export interface HitlTicket {
	ticket_id: string;
	type: string;
	description: string;
	amount: number;
	status: string;
	created_at: string;
}

export interface HitlResolveRequest {
	action: "approve" | "void";
}

export interface LedgerLine {
	line_id: number;
	entry_id: number;
	account_type: string;
	debit: number;
	credit: number;
}

export interface AuditLogEntry {
	id?: string;
	timestamp: string;
	actor: string;
	action: string;
	details: string;
}

export interface ComplianceReport {
	gdpr_status: string;
	pci_status: string;
	last_audited: string;
	issues_found: number;
}
