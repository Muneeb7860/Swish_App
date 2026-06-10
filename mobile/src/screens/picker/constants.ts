import { Dimensions } from "react-native";

export const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } =
	Dimensions.get("window");

// ----------------------------------------------------
// CYBER-INDUSTRIAL DESIGN TOKENS (HSL counterparts)
// ----------------------------------------------------
export const THEME = {
	bgDark: "#070a13",
	bgBody: "#0b0f19",
	bgCard: "rgba(17, 24, 39, 0.8)",
	bgCardHover: "rgba(31, 41, 55, 0.95)",
	borderColor: "rgba(255, 255, 255, 0.08)",

	customer: "#10b981",
	customerGlow: "rgba(16, 185, 129, 0.2)",

	rider: "#f59e0b",
	riderGlow: "rgba(245, 158, 11, 0.2)",

	inventory: "#3b82f6",
	inventoryGlow: "rgba(59, 130, 246, 0.2)",

	admin: "#ef4444",
	adminGlow: "rgba(239, 68, 68, 0.2)",

	engine: "#06b6d4",
	engineGlow: "rgba(6, 182, 212, 0.2)",

	textPrimary: "#f8fafc",
	textSecondary: "#94a3b8",
	textMuted: "#64748b",
};

// ----------------------------------------------------
// DEFAULT SEED DATA (MOCK / LOCAL SIMULATOR)
// ----------------------------------------------------
export const INITIAL_ORDERS = [
	{
		orderId: 3011,
		storeId: "central",
		status: "pending",
		slaCountdownSec: 185,
		totalAmount: 23.4,
		created_at: new Date(Date.now() - 55000).toISOString(),
		items: [
			{
				itemId: "milk",
				name: "Swiss Whole Milk 1L",
				quantity: 2,
				category: "Dairy",
				emoji: "🥛",
				perishable: true,
			},
			{
				itemId: "bread",
				name: "Artisan Sourdough Loaf",
				quantity: 1,
				category: "Bakery",
				emoji: "🍞",
				perishable: false,
			},
			{
				itemId: "apples",
				name: "Gala Apples 500g",
				quantity: 4,
				category: "Produce",
				emoji: "🍎",
				perishable: false,
			},
		],
	},
	{
		orderId: 3012,
		storeId: "central",
		status: "pending",
		slaCountdownSec: 238,
		totalAmount: 31.2,
		created_at: new Date(Date.now() - 20000).toISOString(),
		items: [
			{
				itemId: "chocolate",
				name: "Lindt Dark Chocolate 70%",
				quantity: 3,
				category: "Sweets",
				emoji: "🍫",
				perishable: false,
			},
			{
				itemId: "icecream",
				name: "Movenpick Vanilla 500ml",
				quantity: 2,
				category: "Frozen",
				emoji: "🍨",
				perishable: true,
			},
		],
	},
	{
		orderId: 3013,
		storeId: "central",
		status: "pending",
		slaCountdownSec: 45,
		totalAmount: 18.0,
		created_at: new Date(Date.now() - 195000).toISOString(),
		items: [
			{
				itemId: "cola",
				name: "Coca Cola Zero 6x330ml",
				quantity: 1,
				category: "Beverages",
				emoji: "🥤",
				perishable: false,
			},
			{
				itemId: "chips",
				name: "Zweifel Paprika Chips",
				quantity: 2,
				category: "Snacks",
				emoji: "🥔",
				perishable: false,
			},
		],
	},
	{
		orderId: 3014,
		storeId: "east",
		status: "pending",
		slaCountdownSec: 310,
		totalAmount: 29.5,
		created_at: new Date(Date.now() - 30000).toISOString(),
		items: [
			{
				itemId: "milk",
				name: "Swiss Whole Milk 1L",
				quantity: 1,
				category: "Dairy",
				emoji: "🥛",
				perishable: true,
			},
			{
				itemId: "eggs",
				name: "Organic Free Range Eggs x12",
				quantity: 1,
				category: "Dairy",
				emoji: "🥚",
				perishable: true,
			},
			{
				itemId: "cheese",
				name: "Gruyère AOP aged 200g",
				quantity: 2,
				category: "Dairy",
				emoji: "🧀",
				perishable: true,
			},
		],
	},
];

export const DEFAULT_ITEMS = [
	{ id: "milk", name: "Swiss Whole Milk 1L", emoji: "🥛", category: "Dairy" },
	{
		id: "bread",
		name: "Artisan Sourdough Loaf",
		emoji: "🍞",
		category: "Bakery",
	},
	{
		id: "apples",
		name: "Gala Apples 500g",
		emoji: "🍎",
		category: "Produce",
	},
	{
		id: "chocolate",
		name: "Lindt Dark Chocolate 70%",
		emoji: "🍫",
		category: "Sweets",
	},
	{
		id: "icecream",
		name: "Movenpick Vanilla 500ml",
		emoji: "🍨",
		category: "Frozen",
	},
	{
		id: "cola",
		name: "Coca Cola Zero 6x330ml",
		emoji: "🥤",
		category: "Beverages",
	},
	{
		id: "chips",
		name: "Zweifel Paprika Chips",
		emoji: "🥔",
		category: "Snacks",
	},
];
