import {
	CatalogItem,
	Order,
	RefundResponse,
	Payment,
	RiderOnboardResponse,
	RiderCourse,
	PickerHandoverResponse,
	RebalanceResponse,
	B2BRestockOrder,
	B2BInvoice,
	OnboardingApplication,
	HitlTicket,
	LedgerLine,
	AuditLogEntry,
	ComplianceReport,
} from "./types";

export const MOCK_PRODUCTS: CatalogItem[] = [
	{
		item_id: "p1",
		name: "Organic Fresh Milk",
		price: 3.49,
		stock: 12,
		category: "Dairy & Eggs",
		emoji: "🥛",
		perishable: true,
	},
	{
		item_id: "p2",
		name: "Chiquita Bananas (1kg)",
		price: 1.99,
		stock: 18,
		category: "Fruits & Veggies",
		emoji: "🍌",
		perishable: false,
	},
	{
		item_id: "p3",
		name: "Fresh Hass Avocado (Pair)",
		price: 2.99,
		stock: 8,
		category: "Fruits & Veggies",
		emoji: "🥑",
		perishable: false,
	},
	{
		item_id: "p4",
		name: "Coca Cola Zero 6-Pack",
		price: 5.49,
		stock: 15,
		category: "Snacks & Drinks",
		emoji: "🥤",
		perishable: false,
	},
	{
		item_id: "p5",
		name: "Whole Wheat Sourdough",
		price: 4.29,
		stock: 6,
		category: "Bakery",
		emoji: "🍞",
		perishable: false,
	},
	{
		item_id: "p6",
		name: "Double Chocolate Muffins",
		price: 3.89,
		stock: 2,
		category: "Bakery",
		emoji: "🧁",
		perishable: false,
	},
	{
		item_id: "p7",
		name: "Free Range Eggs (Dozen)",
		price: 4.99,
		stock: 10,
		category: "Dairy & Eggs",
		emoji: "🥚",
		perishable: true,
	},
	{
		item_id: "p8",
		name: "Potato Chips (Sea Salt)",
		price: 2.49,
		stock: 25,
		category: "Snacks & Drinks",
		emoji: "🥔",
		perishable: false,
	},
];

export const MOCK_ORDERS: Order[] = [
	{
		order_id: 8901,
		customer_id: "CUST-Dave",
		store_id: "store-central",
		rider_id: "rid-1",
		total_amount: 8.97,
		weather_surcharge: 0.0,
		payment_method: "Wallet",
		status: "delivered",
		sla_countdown_sec: 0,
		bags_returned: 0,
		created_at: new Date(Date.now() - 86400000 * 2).toISOString(),
	},
	{
		order_id: 8710,
		customer_id: "CUST-Dave",
		store_id: "store-east",
		rider_id: "rid-2",
		total_amount: 9.28,
		weather_surcharge: 0.0,
		payment_method: "PayPal",
		status: "delivered",
		sla_countdown_sec: 0,
		bags_returned: 0,
		created_at: new Date(Date.now() - 86400000 * 5).toISOString(),
	},
];

export const MOCK_COURSES: RiderCourse[] = [
	{ course_id: "c-cold-1", course_name: "IoT Cold Chain Cargo Handling V2" },
	{ course_id: "c-safety-2", course_name: "Urban E-Bike Defensive Navigation" },
	{
		course_id: "c-customer-3",
		course_name: "High-Empathy Delivery Touchpoints",
	},
];

export const MOCK_ONBOARDING: OnboardingApplication[] = [
	{
		application_id: "rid-1",
		applicant_type: "rider",
		name: "Rider Dave",
		approval_ops: false,
		approval_compliance: false,
		approval_admin: false,
		created_at: new Date().toISOString(),
	},
	{
		application_id: "mer-1",
		applicant_type: "merchant",
		name: "FreshGrocer Store",
		approval_ops: false,
		approval_compliance: false,
		approval_admin: false,
		created_at: new Date().toISOString(),
	},
];

export const MOCK_HITL_TICKETS: HitlTicket[] = [
	{
		ticket_id: "HITL-101",
		type: "customer_refund",
		description: "Refund request for spoiled milk claims by Dave",
		amount: 8.97,
		status: "pending",
		created_at: new Date().toISOString(),
	},
	{
		ticket_id: "HITL-102",
		type: "rider_emergency",
		description: "Rider reported mechanical failure of transit vehicle",
		amount: 15.0,
		status: "pending",
		created_at: new Date().toISOString(),
	},
];

export const MOCK_LEDGER: LedgerLine[] = [
	{
		line_id: 1,
		entry_id: 100,
		account_type: "ASSETS",
		debit: 100.0,
		credit: 0,
	},
	{
		line_id: 2,
		entry_id: 101,
		account_type: "REVENUE",
		debit: 0,
		credit: 8.97,
	},
];

export const MOCK_AUDIT_LOGS: AuditLogEntry[] = [
	{
		timestamp: new Date().toISOString(),
		actor: "system",
		action: "INITIALIZE",
		details: "Security and compliance guardrails active",
	},
	{
		timestamp: new Date().toISOString(),
		actor: "admin",
		action: "CONFIG_UPDATE",
		details: "Rotated Vault keys for token signature verification",
	},
];

export const MOCK_COMPLIANCE: ComplianceReport = {
	gdpr_status: "COMPLIANT",
	pci_status: "COMPLIANT",
	last_audited: new Date().toISOString(),
	issues_found: 0,
};

export const MOCK_RESTOCKS: B2BRestockOrder[] = [
	{
		restock_order_id: 3001,
		store_id: "store-central",
		wholesaler_id: "wholesaler-zuri",
		invoice_amount: 150.0,
		is_fallback: false,
		status: "pending",
		created_at: new Date().toISOString(),
	},
	{
		restock_order_id: 3002,
		store_id: "store-east",
		wholesaler_id: "wholesaler-basel",
		invoice_amount: 45.0,
		is_fallback: true,
		status: "fulfilled",
		created_at: new Date(Date.now() - 3600000).toISOString(),
	},
];

export const MOCK_INVOICES: B2BInvoice[] = [
	{
		invoice_id: 4001,
		restock_order_id: 3002,
		wholesaler_id: "wholesaler-basel",
		amount: 45.0,
		status: "paid",
		created_at: new Date(Date.now() - 3600000).toISOString(),
	},
];
