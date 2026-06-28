import { Modal } from "@swish/shared-ui";
import * as Lucide from "lucide-react";
import type React from "react";
import { useState } from "react";
import { createPortal } from "react-dom";
import { useAiStream } from "../hooks/useAiStream";

export interface Product {
	id: string;
	name: string;
	price: number;
	stock: number;
	stockEast: number;
	category: string;
	emoji: string;
	perishable: boolean;
}

export interface CartItem extends Product {
	qty: number;
}

export interface Voucher {
	code: string;
	value: number;
	desc: string;
}

export interface Order {
	id: number;
	items: string;
	total: number;
	progress: number;
	status: string;
	perishable: boolean;
	temperature: number | null;
	slaRemaining: number;
}

export interface SavedAddress {
	id: string;
	label: string;
	line1: string;
	city: string;
	zip: string;
}

export interface SavedCard {
	id: string;
	last4: string;
	brand: string;
	expiry: string;
}

export interface OrderHistoryItem {
	id: number;
	date: string;
	items: string;
	total: number;
	status: string;
}

export interface CustomerAppProps {
	products: Product[];
	cart: CartItem[];
	setCart: React.Dispatch<React.SetStateAction<CartItem[]>>;
	customerWallet: number;
	setCustomerWallet: React.Dispatch<React.SetStateAction<number>>;
	customerPoints: number;
	setCustomerPoints: React.Dispatch<React.SetStateAction<number>>;
	customerTab: string;
	setCustomerTab: React.Dispatch<React.SetStateAction<string>>;
	profileSubTab: string;
	setProfileSubTab: React.Dispatch<React.SetStateAction<string>>;
	savedAddresses?: SavedAddress[];
	savedCards?: SavedCard[];
	favorites?: string[];
	setFavorites?: React.Dispatch<React.SetStateAction<string[]>>;
	vipMember: boolean;
	vouchers: Voucher[];
	customerTrustScore: number;
	gdprTokenProbation: boolean;
	handleGdprPurge: () => void;
	orderHistory: OrderHistoryItem[];
	esgCheckbox: boolean;
	setEsgCheckbox: React.Dispatch<React.SetStateAction<boolean>>;
	tipAmount: number;
	setTipAmount: React.Dispatch<React.SetStateAction<number>>;
	handleCheckout: (method: string) => void;
	handleApplyVoucher?: (code: string) => void;
	voucherCode?: string;
	setVoucherCode?: React.Dispatch<React.SetStateAction<string>>;
	activeOrder: Order | null;
	generateCertificate: (role: string) => void;
	catalogLoading?: boolean;
}

export default function CustomerApp({
	products,
	cart,
	setCart,
	customerWallet,
	customerPoints,
	customerTab,
	setCustomerTab,
	profileSubTab,
	setProfileSubTab,
	favorites = [],
	setFavorites,
	vipMember,
	vouchers,
	customerTrustScore,
	gdprTokenProbation,
	handleGdprPurge,
	orderHistory,
	esgCheckbox,
	setEsgCheckbox,
	tipAmount,
	setTipAmount,
	handleCheckout,
	generateCertificate,
	catalogLoading = false,
	setVoucherCode,
}: CustomerAppProps) {
	const [searchQuery, setSearchQuery] = useState("");
	const [showSubstitutionModal, setShowSubstitutionModal] = useState(false);
	const [subTargetItem, setSubTargetItem] = useState<Product | null>(null);
	const [activeCategory, setActiveCategory] = useState("All");
	const [activePromoFilter, setActivePromoFilter] = useState<string | null>(
		null,
	);

	// AI Shopping Planner Integration
	const { streamData, isStreaming, error, startStream } = useAiStream();
	const [aiPrompt, setAiPrompt] = useState("");
	const [aiPanelOpen, setAiPanelOpen] = useState(false);

	const MOCK_PRODUCTS: Product[] = [
		// Monsoon
		{
			id: "mock-umbrella",
			name: "Premium Golf Umbrella",
			price: 15.99,
			stock: 15,
			stockEast: 10,
			category: "Monsoon",
			emoji: "☔",
			perishable: false,
		},
		{
			id: "mock-raincoat",
			name: "Breathable Raincoat",
			price: 29.5,
			stock: 8,
			stockEast: 5,
			category: "Monsoon",
			emoji: "🧥",
			perishable: false,
		},
		{
			id: "mock-waterproof-bag",
			name: "Dry Bag Backpack",
			price: 19.99,
			stock: 12,
			stockEast: 8,
			category: "Monsoon",
			emoji: "🎒",
			perishable: false,
		},
		// Kids
		{
			id: "mock-blocks",
			name: "Wooden Building Blocks",
			price: 24.99,
			stock: 12,
			stockEast: 8,
			category: "Kids",
			emoji: "🧱",
			perishable: false,
		},
		{
			id: "mock-toy-train",
			name: "Magnetic Train Set",
			price: 18.5,
			stock: 14,
			stockEast: 10,
			category: "Kids",
			emoji: "🚂",
			perishable: false,
		},
		{
			id: "mock-diapers",
			name: "Eco Baby Diapers (Pack of 40)",
			price: 21.99,
			stock: 20,
			stockEast: 15,
			category: "Kids",
			emoji: "👶",
			perishable: false,
		},
		// Electronics
		{
			id: "mock-headphones",
			name: "ANC Wireless Headphones",
			price: 59.99,
			stock: 10,
			stockEast: 5,
			category: "Electronics",
			emoji: "🎧",
			perishable: false,
		},
		{
			id: "mock-charger",
			name: "Fast USB-C Charger Hub",
			price: 15.49,
			stock: 25,
			stockEast: 20,
			category: "Electronics",
			emoji: "🔌",
			perishable: false,
		},
		{
			id: "mock-batteries",
			name: "Rechargeable AA Batteries",
			price: 9.99,
			stock: 40,
			stockEast: 30,
			category: "Electronics",
			emoji: "🔋",
			perishable: false,
		},
		// Beauty
		{
			id: "mock-facewash",
			name: "Hydrating Aloe Facewash",
			price: 9.99,
			stock: 30,
			stockEast: 25,
			category: "Beauty",
			emoji: "🧼",
			perishable: false,
		},
		{
			id: "mock-lipstick",
			name: "Matte Crimson Lipstick",
			price: 14.99,
			stock: 18,
			stockEast: 15,
			category: "Beauty",
			emoji: "💄",
			perishable: false,
		},
		{
			id: "mock-serum",
			name: "Vitamin C Glow Serum",
			price: 18.0,
			stock: 15,
			stockEast: 12,
			category: "Beauty",
			emoji: "🧪",
			perishable: false,
		},
		// Decor
		{
			id: "mock-candle",
			name: "Scented Lavender Candle",
			price: 7.99,
			stock: 40,
			stockEast: 30,
			category: "Decor",
			emoji: "🕯️",
			perishable: false,
		},
		{
			id: "mock-fairylights",
			name: "Warm White Fairy Lights",
			price: 8.99,
			stock: 50,
			stockEast: 40,
			category: "Decor",
			emoji: "✨",
			perishable: false,
		},
		{
			id: "mock-cushion",
			name: "Velvet Throw Cushion",
			price: 12.5,
			stock: 20,
			stockEast: 15,
			category: "Decor",
			emoji: "🛋️",
			perishable: false,
		},
		// Gifting
		{
			id: "mock-giftpack",
			name: "Gourmet Swiss Chocolate Box",
			price: 29.99,
			stock: 15,
			stockEast: 10,
			category: "Gifting",
			emoji: "🎁",
			perishable: false,
		},
		{
			id: "mock-greeting",
			name: "Pop-up Laser Card",
			price: 4.99,
			stock: 100,
			stockEast: 90,
			category: "Gifting",
			emoji: "✉️",
			perishable: false,
		},
		{
			id: "mock-giftbag",
			name: "Premium Holographic Gift Bag",
			price: 3.5,
			stock: 60,
			stockEast: 50,
			category: "Gifting",
			emoji: "🛍️",
			perishable: false,
		},
		// Imported
		{
			id: "mock-matcha",
			name: "Ceremonial Kyoto Matcha",
			price: 24.0,
			stock: 10,
			stockEast: 7,
			category: "Imported",
			emoji: "🍵",
			perishable: false,
		},
		{
			id: "mock-truffleoil",
			name: "Italian White Truffle Oil",
			price: 28.5,
			stock: 14,
			stockEast: 10,
			category: "Imported",
			emoji: "🛢️",
			perishable: false,
		},
		{
			id: "mock-pasta",
			name: "Bronze Cut Penne Rigate",
			price: 5.99,
			stock: 35,
			stockEast: 30,
			category: "Imported",
			emoji: "🍝",
			perishable: false,
		},
		// Household Essentials
		{
			id: "mock-detergent",
			name: "Liquid Laundry Detergent",
			price: 14.99,
			stock: 20,
			stockEast: 15,
			category: "Household Essentials",
			emoji: "🧴",
			perishable: false,
		},
		{
			id: "mock-tissues",
			name: "Soft Face Tissues (Pack of 3)",
			price: 4.5,
			stock: 60,
			stockEast: 50,
			category: "Household Essentials",
			emoji: "🧻",
			perishable: false,
		},
		// Lifestyle
		{
			id: "mock-protein",
			name: "Vegan Protein Powder",
			price: 39.99,
			stock: 12,
			stockEast: 10,
			category: "Lifestyle",
			emoji: "💪",
			perishable: false,
		},
		{
			id: "mock-fitbar",
			name: "Organic Peanut Protein Bar",
			price: 2.99,
			stock: 80,
			stockEast: 70,
			category: "Lifestyle",
			emoji: "🍫",
			perishable: false,
		},
		{
			id: "brand-7622210416681",
			name: "Heudebert Biscottes",
			price: 4.92,
			stock: 70,
			stockEast: 60,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622210713803",
			name: "Belvita Petit Déjeuner Chocolat",
			price: 4.17,
			stock: 65,
			stockEast: 55,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622210713902",
			name: "Belvita Belvita original  - Biscuits petit déjeuner miel & pépites de chocolat",
			price: 5.37,
			stock: 74,
			stockEast: 64,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-3017760819121",
			name: "LU Chamonix",
			price: 4.59,
			stock: 73,
			stockEast: 63,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-6111031005576",
			name: "Oreo original  oreo",
			price: 2.17,
			stock: 21,
			stockEast: 11,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-3392460460409",
			name: "Heudebert Crackers heudebert",
			price: 1.81,
			stock: 26,
			stockEast: 16,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-3045140118502",
			name: "Milka Milka Tablette Noisette",
			price: 5.19,
			stock: 53,
			stockEast: 43,
			category: "Grocery & Kitchen",
			emoji: "🥛",
			perishable: false,
		},
		{
			id: "brand-7622201695323",
			name: "Mondelez Halls",
			price: 1.76,
			stock: 42,
			stockEast: 32,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622202028199",
			name: "Glico Mikado",
			price: 6.25,
			stock: 38,
			stockEast: 28,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-5410041161700",
			name: "Lu Grany moelleux, fruit des bois",
			price: 7.56,
			stock: 58,
			stockEast: 48,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622201515492",
			name: "Milka Milka aux noisettes",
			price: 3.86,
			stock: 30,
			stockEast: 20,
			category: "Grocery & Kitchen",
			emoji: "🥛",
			perishable: false,
		},
		{
			id: "brand-7622210726223",
			name: "Belvita Belvita petit déjeuner",
			price: 7.35,
			stock: 16,
			stockEast: 6,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622210438294",
			name: "Côté d'Or L'Original noir",
			price: 8.49,
			stock: 16,
			stockEast: 6,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622202024115",
			name: "Belin Crackers minizza tomate BELIN",
			price: 3.83,
			stock: 57,
			stockEast: 47,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-3017760268196",
			name: "Mondelez Feuilleté doré Collection LU",
			price: 3.36,
			stock: 40,
			stockEast: 30,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622202225512",
			name: "Cadbury Original Oreo",
			price: 8.98,
			stock: 63,
			stockEast: 53,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622202334009",
			name: "Cadbury dairy milk",
			price: 1.59,
			stock: 42,
			stockEast: 32,
			category: "Grocery & Kitchen",
			emoji: "🥛",
			perishable: false,
		},
		{
			id: "brand-09141209",
			name: "Cadbury Bourn Vita 500Gram Pauch",
			price: 5.91,
			stock: 59,
			stockEast: 49,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622210882974",
			name: "Cadbury Mini Eggs",
			price: 8.6,
			stock: 34,
			stockEast: 24,
			category: "Grocery & Kitchen",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622202219214",
			name: "Cadbury Brunch Choc Chip",
			price: 8.28,
			stock: 69,
			stockEast: 59,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622202216695",
			name: "Cadbury Gems",
			price: 5.1,
			stock: 32,
			stockEast: 22,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-5034660021537",
			name: "Cadbury Cadbury hot chocolate",
			price: 4.1,
			stock: 22,
			stockEast: 12,
			category: "Snacks & Drinks",
			emoji: "🍫",
			perishable: false,
		},
		{
			id: "brand-12111202",
			name: "Cadbury Minis Mix Eggs",
			price: 5.3,
			stock: 36,
			stockEast: 26,
			category: "Grocery & Kitchen",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622202219306",
			name: "Cadbury Brunch Bar",
			price: 8.94,
			stock: 26,
			stockEast: 16,
			category: "Snacks & Drinks",
			emoji: "🍫",
			perishable: false,
		},
		{
			id: "brand-7622201734862",
			name: "Cadbury Roses chocolate box",
			price: 7.58,
			stock: 59,
			stockEast: 49,
			category: "Snacks & Drinks",
			emoji: "🍫",
			perishable: false,
		},
		{
			id: "brand-7622202325960",
			name: "Cadbury Bournville Intense 70% Dark",
			price: 5.86,
			stock: 12,
			stockEast: 5,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7622202318078",
			name: "Cadbury 5 Star Chocolate Bar",
			price: 4.81,
			stock: 51,
			stockEast: 41,
			category: "Snacks & Drinks",
			emoji: "🍫",
			perishable: false,
		},
		{
			id: "brand-5000183501108",
			name: "Cadbury Milk Chocolate spread",
			price: 8.26,
			stock: 40,
			stockEast: 30,
			category: "Grocery & Kitchen",
			emoji: "🍫",
			perishable: false,
		},
		{
			id: "brand-9300617065920",
			name: "Cadbury OLD GOLD DARK CHOCOLATE",
			price: 7.57,
			stock: 51,
			stockEast: 41,
			category: "Snacks & Drinks",
			emoji: "🍫",
			perishable: false,
		},
		{
			id: "brand-7622202272639",
			name: "Cadbury Cadbury Dairy Milk",
			price: 7.24,
			stock: 34,
			stockEast: 24,
			category: "Grocery & Kitchen",
			emoji: "🥛",
			perishable: false,
		},
		{
			id: "brand-3387390123210",
			name: "Nestlé Chocapic",
			price: 6.78,
			stock: 73,
			stockEast: 63,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7613036669146",
			name: "Nestlé NESTLE CHOCAPIC BIO Céréales - Boite de 375g",
			price: 6.21,
			stock: 61,
			stockEast: 51,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-3033710073467",
			name: "Nestlé Chicorée & Café, RICORÉ® L'Original, Boîte de 100g",
			price: 2.02,
			stock: 65,
			stockEast: 55,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-8002270014901",
			name: "Nestlé S. Pellegrino Water",
			price: 7.43,
			stock: 52,
			stockEast: 42,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7613035040823",
			name: "Nestlé Nestlé dessert Noir",
			price: 3.66,
			stock: 25,
			stockEast: 15,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7613035676497",
			name: "Nestlé Chicorée & Café, RICORÉ® L'Original, Boîte de 260g",
			price: 5.87,
			stock: 31,
			stockEast: 21,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-3033710084913",
			name: "Maggi Arôme MAGGI - Bouteille 250g",
			price: 5.76,
			stock: 58,
			stockEast: 48,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-8445290133403",
			name: "Nestlé Nesquik",
			price: 3.53,
			stock: 48,
			stockEast: 38,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-3033710065783",
			name: "Nestlé Le Choco",
			price: 8.73,
			stock: 42,
			stockEast: 32,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7613035449626",
			name: "Nestlé NESTLE CHOCAPIC Céréales 645g",
			price: 8.27,
			stock: 49,
			stockEast: 39,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7613032655495",
			name: "Nestlé Chicorée & Café, RICORÉ® L'Original, Boîte de 260g",
			price: 3.64,
			stock: 35,
			stockEast: 25,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7613287431943",
			name: "Nestlé Nesquik Cocoa",
			price: 4.97,
			stock: 46,
			stockEast: 36,
			category: "Snacks & Drinks",
			emoji: "🍫",
			perishable: false,
		},
		{
			id: "brand-7613035530799",
			name: "Nestlé NESQUIK Moins de Sucres",
			price: 8.58,
			stock: 23,
			stockEast: 13,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7613287514325",
			name: "Nestlé Fitness Nature Céréales complètes",
			price: 8.47,
			stock: 52,
			stockEast: 42,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
		{
			id: "brand-7613032779566",
			name: "Nestlé chocapic",
			price: 1.56,
			stock: 42,
			stockEast: 32,
			category: "Snacks & Drinks",
			emoji: "📦",
			perishable: false,
		},
	];

	// Re-map backend products to match storefront shelves
	const normalizedBffProducts = products.map((p) => {
		let cat = p.category;
		if (
			p.category === "Dairy" ||
			p.category === "Produce" ||
			p.category === "Dairy & Eggs" ||
			p.category === "Fruits & Veggies" ||
			p.category === "Bakery"
		) {
			cat = "Grocery & Kitchen";
		} else if (p.category === "Sweets") {
			cat = "Snacks & Drinks";
		}
		return { ...p, category: cat };
	});

	const allProducts = [...normalizedBffProducts, ...MOCK_PRODUCTS];

	// Helper to parse matching products from streamed response (case-insensitive hybrid)
	const getMatchingProducts = (): Product[] => {
		if (!streamData) return [];
		const text = streamData.toLowerCase();
		return allProducts.filter((product) => {
			const name = product.name.toLowerCase();
			return (
				text.includes(name) ||
				(product.id === "p1" && text.includes("milk")) ||
				(product.id === "p2" && text.includes("banana")) ||
				(product.id === "p3" && text.includes("avocado")) ||
				(product.id === "p4" &&
					(text.includes("coke") ||
						text.includes("cola") ||
						text.includes("drinks") ||
						text.includes("soda"))) ||
				(product.id === "p5" &&
					(text.includes("sourdough") ||
						text.includes("bread") ||
						text.includes("bakery"))) ||
				(product.id === "p6" && text.includes("muffin")) ||
				(product.id === "p7" &&
					(text.includes("egg") || text.includes("eggs"))) ||
				(product.id === "p8" &&
					(text.includes("chip") || text.includes("chips")))
			);
		});
	};

	const matchingProducts = getMatchingProducts();

	const addMatchingToCart = () => {
		matchingProducts.forEach((product) => {
			addToCart(product);
		});
	};

	// Filter products by category/search
	const filteredProducts = allProducts.filter(
		(p) =>
			p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
			p.category.toLowerCase().includes(searchQuery.toLowerCase()),
	);

	const substitutionMap: Record<string, string> = {
		p1: "p7", // Milk -> Eggs
		p2: "p3", // Bananas -> Avocado
		p3: "p2", // Avocado -> Bananas
		p5: "p6", // Sourdough -> Muffins
		p6: "p5", // Muffins -> Sourdough
		p7: "p1", // Eggs -> Milk
		p8: "p4", // Chips -> Soda
	};

	const addToCart = (product: Product) => {
		if (product.stock < 5 && !showSubstitutionModal) {
			setSubTargetItem(product);
			setShowSubstitutionModal(true);
		}
		setCart((prev: CartItem[]) => {
			const existing = prev.find((item) => item.id === product.id);
			const currentQty = existing?.qty ?? 0;

			if (currentQty >= product.stock) {
				return prev;
			}

			if (existing) {
				return prev.map((item) =>
					item.id === product.id ? { ...item, qty: item.qty + 1 } : item,
				);
			}
			return [...prev, { ...product, qty: 1 }];
		});
	};

	const updateCartQty = (productId: string, newQty: number) => {
		if (newQty <= 0) {
			setCart((prev: CartItem[]) =>
				prev.filter((item) => item.id !== productId),
			);
			return;
		}
		const prod = allProducts.find((p) => p.id === productId);
		if (prod && newQty > prod.stock) {
			return;
		}
		setCart((prev: CartItem[]) =>
			prev.map((item) =>
				item.id === productId ? { ...item, qty: newQty } : item,
			),
		);
	};

	const removeFromCart = (itemId: string) => {
		setCart((prev: CartItem[]) => prev.filter((item) => item.id !== itemId));
	};

	// Calculations
	const cartSubtotal = cart.reduce(
		(sum, item) => sum + item.price * item.qty,
		0,
	);
	const esgRebate = esgCheckbox ? 0.5 : 0.0;
	const deliveryFee = 2.99;
	const appliedDiscount = 0.0;
	const totalCost = Math.max(
		0,
		cartSubtotal + deliveryFee + tipAmount - esgRebate - appliedDiscount,
	);

	const categories = [
		"All",
		"Monsoon",
		"Kids",
		"Electronics",
		"Beauty",
		"Decor",
		"Gifting",
		"Imported",
	];

	const PROMO_BANNERS = [
		{
			id: "fast",
			name: "Fast Corner",
			tag: "⚡ Under 10 Mins",
			desc: "Express delivery items",
			grad: "linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%)",
			border: "rgba(6, 182, 212, 0.4)",
		},
		{
			id: "deals",
			name: "Grab Deals",
			tag: "🏷️ Hot Prices",
			desc: "Best offers selected",
			grad: "linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%)",
			border: "rgba(139, 92, 246, 0.4)",
		},
		{
			id: "chill",
			name: "Chill Out",
			tag: "❄️ Cold Chain",
			desc: "Ice cream & cold drinks",
			grad: "linear-gradient(135deg, #0ea5e9 0%, #10b981 100%)",
			border: "rgba(14, 165, 233, 0.4)",
		},
		{
			id: "offer90",
			name: "Avail Offer 90%",
			tag: "💥 Clearout Sale",
			desc: "Up to 90% off deals",
			grad: "linear-gradient(135deg, #ef4444 0%, #f59e0b 100%)",
			border: "rgba(239, 68, 68, 0.4)",
		},
		{
			id: "nutrition",
			name: "Nutrition",
			tag: "🥗 Organic Choice",
			desc: "Protein & healthy fruits",
			grad: "linear-gradient(135deg, #10b981 0%, #84cc16 100%)",
			border: "rgba(16, 185, 129, 0.4)",
		},
	];

	const SPOTLIGHT_STORES = [
		{
			id: "store-1",
			name: "Zurich Dark Store Hub",
			dist: "0.8 km",
			time: "10-15 mins",
			rating: "4.9 ★",
			active: true,
			desc: "Primary Fulfillment Center",
		},
		{
			id: "store-2",
			name: "Geneva Micro-Fulfillment",
			dist: "120 km",
			time: "Out of range",
			rating: "4.7 ★",
			active: false,
			desc: "Secondary Center",
		},
		{
			id: "store-3",
			name: "Basel Alpine Express",
			dist: "85 km",
			time: "Out of range",
			rating: "4.8 ★",
			active: false,
			desc: "Coming Soon",
		},
	];

	// Filter based on active promo
	const getPromoFilteredProducts = (): Product[] => {
		if (activePromoFilter === "fast") {
			return allProducts.filter((p) => p.stock > 15 || p.perishable);
		}
		if (activePromoFilter === "deals") {
			return allProducts.filter(
				(p) => p.price > 25 || p.id === "mock-giftpack",
			);
		}
		if (activePromoFilter === "chill") {
			return allProducts.filter(
				(p) => p.perishable || p.category === "Snacks & Drinks",
			);
		}
		if (activePromoFilter === "offer90") {
			return allProducts.filter(
				(p) => p.id === "mock-greeting" || p.id === "mock-candle",
			);
		}
		if (activePromoFilter === "nutrition") {
			return allProducts.filter(
				(p) =>
					p.category === "Lifestyle" ||
					p.id === "item-4" ||
					p.id === "mock-facewash",
			);
		}
		return allProducts;
	};

	const renderProductCard = (p: Product) => {
		const cartItem = cart.find((item) => item.id === p.id);
		const isFavorite = favorites.includes(p.id);

		let discountBadge = null;
		if (p.id.startsWith("mock-")) {
			if (p.category === "Imported") {
				discountBadge = "Imported";
			} else if (p.category === "Monsoon") {
				discountBadge = "Monsoon Special";
			} else if (p.id === "mock-greeting" || p.id === "mock-candle") {
				discountBadge = "90% OFF";
			} else if (p.price > 30) {
				discountBadge = "Grab Deal";
			}
		} else if (p.perishable) {
			discountBadge = "Cold Chain";
		}

		return (
			<div
				key={p.id}
				className="product-card"
				style={{
					background: "var(--bg-card)",
					border: "1px solid var(--border-color)",
					borderRadius: "16px",
					padding: "1rem",
					position: "relative",
					display: "flex",
					flexDirection: "column",
					transition: "all 0.3s ease",
					boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
				}}
			>
				{discountBadge && (
					<span
						className="badge-perishable"
						style={{
							position: "absolute",
							top: "0.5rem",
							left: "0.5rem",
							background:
								discountBadge === "90% OFF"
									? "rgba(239, 68, 68, 0.9)"
									: "rgba(16, 185, 129, 0.9)",
							color: "#fff",
							padding: "0.15rem 0.4rem",
							borderRadius: "4px",
							fontSize: "0.65rem",
							fontWeight: "bold",
							zIndex: 5,
						}}
					>
						{discountBadge}
					</span>
				)}

				<button
					type="button"
					className="fav-btn"
					style={{
						position: "absolute",
						top: "0.5rem",
						right: "0.5rem",
						background: "rgba(15, 23, 42, 0.75)",
						border: "1px solid rgba(255, 255, 255, 0.1)",
						width: "28px",
						height: "28px",
						borderRadius: "50%",
						display: "flex",
						alignItems: "center",
						justifyContent: "center",
						cursor: "pointer",
						zIndex: 5,
						transition: "all 0.2s ease",
					}}
					onClick={(e) => {
						e.stopPropagation();
						if (setFavorites) {
							setFavorites((prev) =>
								prev.includes(p.id)
									? prev.filter((id) => id !== p.id)
									: [...prev, p.id],
							);
						}
					}}
				>
					<Lucide.Heart
						size={14}
						fill={isFavorite ? "var(--color-admin)" : "none"}
						stroke={isFavorite ? "var(--color-admin)" : "var(--text-secondary)"}
					/>
				</button>

				<div
					className="product-image-container"
					style={{
						height: "100px",
						borderRadius: "10px",
						background:
							"linear-gradient(135deg, rgba(255, 255, 255, 0.03) 0%, rgba(255, 255, 255, 0.01) 100%)",
						display: "flex",
						justifyContent: "center",
						alignItems: "center",
						fontSize: "2.5rem",
						marginBottom: "0.5rem",
						border: "1px solid var(--border-color)",
						position: "relative",
						overflow: "hidden",
					}}
				>
					<span>{p.emoji}</span>
				</div>

				<h4
					style={{
						fontWeight: 700,
						margin: "0.5rem 0 0.2rem 0",
						fontSize: "0.85rem",
						whiteSpace: "nowrap",
						overflow: "hidden",
						textOverflow: "ellipsis",
					}}
				>
					{p.name}
				</h4>
				<span
					style={{
						fontSize: "0.65rem",
						color: "var(--text-muted)",
						textTransform: "uppercase",
						letterSpacing: "0.05em",
					}}
				>
					{p.category}
				</span>

				<div
					className="product-price-row"
					style={{
						display: "flex",
						justifyContent: "space-between",
						alignItems: "center",
						marginTop: "auto",
						paddingTop: "0.75rem",
					}}
				>
					<span
						style={{
							fontWeight: 800,
							color: "var(--color-customer)",
							fontFamily: "var(--font-mono)",
							fontSize: "0.9rem",
						}}
					>
						${p.price.toFixed(2)}
					</span>

					{cartItem ? (
						<div
							style={{
								display: "flex",
								alignItems: "center",
								gap: "0.4rem",
								background: "rgba(16, 185, 129, 0.15)",
								border: "1px solid var(--color-customer)",
								borderRadius: "6px",
								padding: "0.2rem 0.4rem",
							}}
						>
							<button
								type="button"
								onClick={() => updateCartQty(p.id, cartItem.qty - 1)}
								className="qty-btn"
								aria-label="Decrease quantity"
							>
								-
							</button>
							<span
								style={{
									fontSize: "0.75rem",
									fontWeight: "bold",
									minWidth: "12px",
									textAlign: "center",
								}}
							>
								{cartItem.qty}
							</span>
							<button
								type="button"
								onClick={() => updateCartQty(p.id, cartItem.qty + 1)}
								className="qty-btn"
								aria-label="Increase quantity"
							>
								+
							</button>
						</div>
					) : (
						<button
							type="button"
							className="btn-add-cart add-cart-btn"
							onClick={() => addToCart(p)}
							style={{
								padding: "0.25rem 0.65rem",
								fontSize: "0.75rem",
								fontWeight: 700,
								border: "1px solid var(--color-customer)",
								borderRadius: "6px",
								cursor: "pointer",
								background: "rgba(16, 185, 129, 0.08)",
								color: "var(--color-customer)",
								transition: "all 0.2s ease",
							}}
						>
							+ Add
						</button>
					)}
				</div>

				<div
					style={{
						marginTop: "0.4rem",
						fontSize: "0.6rem",
						color: "var(--text-muted)",
					}}
				>
					{p.stock < 5 ? (
						<span style={{ color: "var(--color-admin)", fontWeight: "bold" }}>
							🔥 Only {p.stock} left!
						</span>
					) : (
						<span>Stock: {p.stock} units</span>
					)}
				</div>
			</div>
		);
	};

	const renderShelf = (
		title: string,
		emoji: string,
		shelfItems: Product[],
		emptyPlaceholder?: React.ReactNode,
	) => {
		if (shelfItems.length === 0 && !emptyPlaceholder) return null;
		return (
			<div
				key={title}
				className="shelf-section"
				style={{ marginBottom: "2.25rem" }}
			>
				<h3
					className="shelf-title"
					style={{
						fontSize: "1.05rem",
						fontWeight: 800,
						marginBottom: "0.85rem",
						display: "flex",
						alignItems: "center",
						gap: "0.5rem",
						color: "var(--text-primary)",
					}}
				>
					<span>{emoji}</span> {title}
				</h3>
				{shelfItems.length === 0 ? (
					emptyPlaceholder
				) : (
					<div
						className="shelf-scroll-row"
						style={{
							display: "flex",
							gap: "1rem",
							overflowX: "auto",
							paddingBottom: "0.75rem",
							scrollbarWidth: "thin",
						}}
					>
						{shelfItems.map((p) => renderProductCard(p))}
					</div>
				)}
			</div>
		);
	};

	return (
		<div className="customer-dashboard">
			<style>{`
        @keyframes slide-up-bounce {
          0% { transform: translate(-50%, 100%); opacity: 0; }
          60% { transform: translate(-50%, -10px); opacity: 1; }
          80% { transform: translate(-50%, 5px); }
          100% { transform: translate(-50%, 0); }
        }
        
        @keyframes pulse-glow {
          0% { text-shadow: 0 0 2px var(--color-admin), 0 0 4px var(--color-admin); }
          100% { text-shadow: 0 0 8px var(--color-admin), 0 0 16px var(--color-admin); }
        }

        .glowing-stock-counter {
          color: var(--color-admin);
          animation: pulse-glow 1s ease-in-out infinite alternate;
        }
        
        .floating-checkout-bar {
          position: fixed;
          bottom: 1.5rem;
          left: 50%;
          transform: translateX(-50%);
          width: 90%;
          max-width: 480px;
          background: rgba(11, 15, 25, 0.95);
          border: 1px solid var(--color-customer);
          box-shadow: 0 0 20px rgba(16, 185, 129, 0.3), inset 0 0 10px rgba(16, 185, 129, 0.1);
          border-radius: 12px;
          padding: 0.75rem 1.25rem;
          display: flex;
          justify-content: space-between;
          align-items: center;
          z-index: 1000;
          backdrop-filter: blur(10px);
          animation: slide-up-bounce 0.6s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
        }
      `}</style>
			<div className="customer-main-panel">
				{/* Navigation Tabs */}
				<div className="customer-navigation-tabs">
					<button
						type="button"
						className={`customer-tab-btn ${customerTab === "catalog" ? "active" : ""}`}
						onClick={() => setCustomerTab("catalog")}
					>
						Browse Store Catalog
					</button>
					<button
						type="button"
						className={`customer-tab-btn ${customerTab === "profile" ? "active" : ""}`}
						onClick={() => setCustomerTab("profile")}
					>
						My Profile Hub
					</button>
				</div>

				{customerTab === "catalog" ? (
					<div>
						{/* Search bar */}
						<div
							className="search-promo-header"
							style={{ display: "flex", gap: "1rem", marginBottom: "1.25rem" }}
						>
							<div style={{ flex: 1, position: "relative" }}>
								<Lucide.Search
									size={16}
									style={{
										position: "absolute",
										left: "10px",
										top: "12px",
										color: "var(--text-muted)",
									}}
								/>
								<input
									type="text"
									className="search-input"
									style={{ paddingLeft: "2.25rem" }}
									placeholder="Search organic grocery, monsoon essentials, kids toys, electronics..."
									value={searchQuery}
									onChange={(e) => {
										setSearchQuery(e.target.value);
										setActiveCategory("All");
										setActivePromoFilter(null);
									}}
								/>
							</div>
						</div>

						{/* AI Shopping Assistant Widget */}
						<div
							className="glass-card"
							style={{
								padding: "1.25rem",
								marginBottom: "1.25rem",
								borderLeft: "4px solid var(--color-customer)",
								background: "rgba(255,255,255,0.01)",
								borderRadius: "12px",
								transition: "all 0.3s ease",
							}}
						>
							<button
								type="button"
								style={{
									display: "flex",
									justifyContent: "space-between",
									alignItems: "center",
									cursor: "pointer",
									background: "none",
									border: "none",
									width: "100%",
									padding: 0,
									fontFamily: "inherit",
									color: "inherit",
									textAlign: "left",
								}}
								onClick={() => setAiPanelOpen(!aiPanelOpen)}
							>
								<h3
									style={{
										margin: 0,
										fontSize: "0.95rem",
										fontWeight: 800,
										color: "var(--color-customer)",
										display: "flex",
										alignItems: "center",
										gap: "0.5rem",
									}}
								>
									<Lucide.Sparkles
										size={16}
										className="animate-pulse"
										style={{ color: "var(--color-customer)" }}
									/>
									✨ Swiss AI Smart Recipe & Shopping Assistant
								</h3>
								<span
									className="btn-secondary-glow"
									style={{
										padding: "0.2rem 0.5rem",
										fontSize: "0.7rem",
										border: "none",
									}}
								>
									{aiPanelOpen ? "Hide Assistant" : "Show Assistant"}
								</span>
							</button>

							{aiPanelOpen && (
								<div
									style={{
										marginTop: "1rem",
										display: "flex",
										flexDirection: "column",
										gap: "0.75rem",
									}}
								>
									<p
										style={{
											fontSize: "0.75rem",
											color: "var(--text-muted)",
											margin: 0,
										}}
									>
										Describe what you want to cook or buy, and Swiss AI will
										plan your meal and suggest matching catalog items!
									</p>

									<div style={{ display: "flex", gap: "0.5rem" }}>
										<input
											type="text"
											className="search-input"
											style={{
												flex: 1,
												padding: "0.5rem 0.75rem",
												fontSize: "0.8rem",
											}}
											placeholder="e.g. I want to cook spaghetti carbonara, or make avocado toast with fresh milk..."
											value={aiPrompt}
											onChange={(e) => setAiPrompt(e.target.value)}
											onKeyDown={(e) => {
												if (e.key === "Enter" && !isStreaming) {
													startStream("/api/ai/orchestrate", aiPrompt);
												}
											}}
											disabled={isStreaming}
										/>
										<button
											type="button"
											className="btn-primary-glow"
											style={{
												background: "var(--color-customer)",
												color: "white",
												border: "none",
												padding: "0.5rem 1rem",
												fontSize: "0.8rem",
												cursor: "pointer",
											}}
											onClick={() =>
												startStream("/api/ai/orchestrate", aiPrompt)
											}
											disabled={isStreaming || !aiPrompt.trim()}
										>
											{isStreaming ? "Planning..." : "Plan Meal"}
										</button>
									</div>

									{error && (
										<div
											style={{
												color: "var(--color-admin)",
												fontSize: "0.75rem",
												marginTop: "0.25rem",
											}}
										>
											⚠️ Error: {error}
										</div>
									)}

									{streamData && (
										<div
											style={{
												background: "rgba(0,0,0,0.2)",
												padding: "0.75rem",
												borderRadius: "8px",
												border: "1px solid rgba(255,255,255,0.05)",
												fontSize: "0.8rem",
												lineHeight: "1.4",
												color: "var(--text-primary)",
												maxHeight: "200px",
												overflowY: "auto",
												whiteSpace: "pre-wrap",
												position: "relative",
												overflowWrap: "anywhere",
												wordBreak: "break-word",
											}}
										>
											{streamData}
											{isStreaming && <span className="ai-type-cursor" />}
										</div>
									)}

									{matchingProducts.length > 0 && (
										<div
											style={{
												marginTop: "0.5rem",
												padding: "0.75rem",
												background: "rgba(16, 185, 129, 0.03)",
												border: "1px dashed rgba(16, 185, 129, 0.2)",
												borderRadius: "8px",
												display: "flex",
												flexDirection: "column",
												gap: "0.5rem",
											}}
										>
											<div
												style={{
													display: "flex",
													justifyContent: "space-between",
													alignItems: "center",
												}}
											>
												<span
													style={{
														fontSize: "0.75rem",
														fontWeight: "bold",
														color: "var(--color-customer)",
													}}
												>
													🎯 Matching Catalog Products Found (
													{matchingProducts.length})
												</span>
												<button
													type="button"
													className="btn-primary-glow"
													style={{
														background: "var(--color-customer)",
														color: "white",
														border: "none",
														padding: "0.25rem 0.65rem",
														fontSize: "0.7rem",
														cursor: "pointer",
													}}
													onClick={addMatchingToCart}
												>
													🛒 Add All to Cart
												</button>
											</div>
											<div
												style={{
													display: "flex",
													gap: "0.5rem",
													flexWrap: "wrap",
												}}
											>
												{matchingProducts.map((p) => (
													<button
														type="button"
														key={p.id}
														style={{
															background: "rgba(255,255,255,0.03)",
															border: "1px solid rgba(255,255,255,0.05)",
															borderRadius: "6px",
															padding: "0.25rem 0.5rem",
															fontSize: "0.7rem",
															display: "flex",
															alignItems: "center",
															gap: "0.25rem",
															cursor: "pointer",
															color: "inherit",
															fontFamily: "inherit",
														}}
														onClick={() => addToCart(p)}
													>
														<span>{p.emoji}</span>
														<strong>{p.name}</strong>
														<span style={{ color: "var(--color-customer)" }}>
															${p.price.toFixed(2)}
														</span>
													</button>
												))}
											</div>
										</div>
									)}
								</div>
							)}
						</div>

						{/* Dynamic Category Shortcuts */}
						<div style={{ marginBottom: "1.5rem" }}>
							<span
								style={{
									fontSize: "0.65rem",
									fontWeight: 700,
									color: "var(--text-muted)",
									textTransform: "uppercase",
									letterSpacing: "0.05em",
									display: "block",
									marginBottom: "0.5rem",
								}}
							>
								Quick Category Shortcuts
							</span>
							<div
								className="category-pills"
								style={{
									display: "flex",
									gap: "0.5rem",
									overflowX: "auto",
									paddingBottom: "0.4rem",
									scrollbarWidth: "none",
								}}
							>
								{categories.map((cat) => (
									<button
										type="button"
										key={cat}
										className={`category-pill ${activeCategory === cat ? "active" : ""}`}
										onClick={() => {
											setActiveCategory(cat);
											setActivePromoFilter(null);
											setSearchQuery("");
										}}
										style={{
											background:
												activeCategory === cat
													? "rgba(16, 185, 129, 0.15)"
													: "rgba(255, 255, 255, 0.02)",
											border: `1px solid ${activeCategory === cat ? "var(--color-customer)" : "var(--border-color)"}`,
											color:
												activeCategory === cat
													? "var(--color-customer)"
													: "var(--text-secondary)",
											padding: "0.4rem 1rem",
											borderRadius: "9999px",
											fontSize: "0.75rem",
											fontWeight: 600,
											cursor: "pointer",
											whiteSpace: "nowrap",
											transition: "all 0.2s ease",
										}}
									>
										{cat === "All" ? "🌐 All Products" : cat}
									</button>
								))}
							</div>
						</div>

						{/* Interactive Promotional Banners */}
						<div style={{ marginBottom: "1.75rem" }}>
							<span
								style={{
									fontSize: "0.65rem",
									fontWeight: 700,
									color: "var(--text-muted)",
									textTransform: "uppercase",
									letterSpacing: "0.05em",
									display: "block",
									marginBottom: "0.5rem",
								}}
							>
								Hot Offers & Corners
							</span>
							<div
								style={{
									display: "grid",
									gridTemplateColumns: "repeat(5, 1fr)",
									gap: "0.75rem",
								}}
							>
								{PROMO_BANNERS.map((banner) => {
									const isActive = activePromoFilter === banner.id;
									return (
										<button
											type="button"
											key={banner.id}
											className={`promo-banner-card ${isActive ? "active" : ""}`}
											onClick={() => {
												setActivePromoFilter(isActive ? null : banner.id);
												setActiveCategory("All");
												setSearchQuery("");
											}}
											style={{
												background: banner.grad,
												border: `1px solid ${isActive ? "#fff" : banner.border}`,
												boxShadow: isActive
													? "0 0 15px rgba(255,255,255,0.2)"
													: "none",
												borderRadius: "12px",
												padding: "0.75rem",
												color: "#fff",
												display: "flex",
												flexDirection: "column",
												justifyContent: "space-between",
												minHeight: "85px",
												cursor: "pointer",
												transition: "all 0.2s ease",
												textAlign: "left",
											}}
										>
											<div>
												<div style={{ fontSize: "0.75rem", fontWeight: 800 }}>
													{banner.tag}
												</div>
												<div
													style={{
														fontSize: "0.6rem",
														opacity: 0.8,
														marginTop: "0.15rem",
														lineHeight: 1.2,
													}}
												>
													{banner.desc}
												</div>
											</div>
											<div
												style={{
													display: "flex",
													justifyContent: "space-between",
													alignItems: "center",
													marginTop: "0.5rem",
												}}
											>
												<span
													style={{
														fontSize: "0.65rem",
														fontWeight: 700,
														textTransform: "uppercase",
													}}
												>
													{banner.name}
												</span>
												<Lucide.ChevronRight size={12} />
											</div>
										</button>
									);
								})}
							</div>
						</div>

						{/* Selected Filter Indicator Banner */}
						{(activePromoFilter !== null || activeCategory !== "All") && (
							<div
								className="weather-sla-banner"
								style={{
									background: "rgba(16, 185, 129, 0.08)",
									border: "1px solid rgba(16, 185, 129, 0.2)",
									padding: "0.65rem 1rem",
									borderRadius: "10px",
									marginBottom: "1.25rem",
									display: "flex",
									justifyContent: "space-between",
									alignItems: "center",
								}}
							>
								<span
									style={{
										fontSize: "0.8rem",
										color: "var(--color-customer)",
										fontWeight: "bold",
									}}
								>
									🎯 Filter Active: Showing{" "}
									{activePromoFilter
										? PROMO_BANNERS.find((b) => b.id === activePromoFilter)
												?.name
										: activeCategory}{" "}
									items
								</span>
								<button
									type="button"
									onClick={() => {
										setActivePromoFilter(null);
										setActiveCategory("All");
									}}
									style={{
										background: "rgba(255, 255, 255, 0.05)",
										border: "1px solid var(--border-color)",
										color: "var(--text-primary)",
										padding: "0.25rem 0.5rem",
										borderRadius: "6px",
										fontSize: "0.7rem",
										cursor: "pointer",
									}}
								>
									Clear Filter
								</button>
							</div>
						)}

						{/* Catalog Content (Skeleton / Search Results / Filtered View / Shelves) */}
						{catalogLoading ? (
							<div className="product-shelf-grid">
								{Array.from({ length: 8 }).map((_, i) => (
									<div
										// biome-ignore lint/suspicious/noArrayIndexKey: skeleton elements are static placeholder cards
										key={i}
										className="product-card"
										style={{ cursor: "default" }}
									>
										<div className="skeleton-image skeleton-shimmer" />
										<div className="skeleton-text medium skeleton-shimmer" />
										<div
											style={{
												display: "flex",
												justifyContent: "space-between",
												alignItems: "center",
												marginTop: "1rem",
											}}
										>
											<div
												className="skeleton-text short skeleton-shimmer"
												style={{ margin: 0, height: 16, width: "40%" }}
											/>
											<div
												className="skeleton-shimmer"
												style={{ width: 40, height: 24, borderRadius: 8 }}
											/>
										</div>
									</div>
								))}
							</div>
						) : searchQuery.trim() !== "" ? (
							filteredProducts.length === 0 ? (
								<div
									style={{
										textAlign: "center",
										padding: "3rem",
										color: "var(--text-muted)",
									}}
								>
									<Lucide.SearchCheck
										size={32}
										style={{ opacity: 0.3, marginBottom: "0.5rem" }}
									/>
									<p>No products found matching "{searchQuery}"</p>
								</div>
							) : (
								<div className="product-shelf-grid">
									{filteredProducts.map((p) => renderProductCard(p))}
								</div>
							)
						) : activePromoFilter !== null ? (
							getPromoFilteredProducts().length === 0 ? (
								<div
									style={{
										textAlign: "center",
										padding: "3rem",
										color: "var(--text-muted)",
									}}
								>
									<p>No products match this deal right now.</p>
								</div>
							) : (
								<div className="product-shelf-grid">
									{getPromoFilteredProducts().map((p) => renderProductCard(p))}
								</div>
							)
						) : activeCategory !== "All" ? (
							allProducts.filter((p) => p.category === activeCategory)
								.length === 0 ? (
								<div
									style={{
										textAlign: "center",
										padding: "3rem",
										color: "var(--text-muted)",
									}}
								>
									<p>No products in category "{activeCategory}"</p>
								</div>
							) : (
								<div className="product-shelf-grid">
									{allProducts
										.filter((p) => p.category === activeCategory)
										.map((p) => renderProductCard(p))}
								</div>
							)
						) : (
							/* Default E-Commerce Shelves View */
							<div>
								{renderShelf(
									"Grocery & Kitchen",
									"🥑",
									allProducts.filter((p) => p.category === "Grocery & Kitchen"),
								)}
								{renderShelf(
									"Snacks & Drinks",
									"🍿",
									allProducts.filter((p) => p.category === "Snacks & Drinks"),
								)}
								{renderShelf(
									"Beauty & Personal Care",
									"🧴",
									allProducts.filter(
										(p) =>
											p.category === "Beauty & Personal Care" ||
											p.category === "Beauty",
									),
								)}
								{renderShelf(
									"Household Essentials",
									"🧻",
									allProducts.filter(
										(p) => p.category === "Household Essentials",
									),
								)}
								{renderShelf(
									"Featured This Week",
									"✨",
									allProducts.filter(
										(p) =>
											p.id === "item-2" ||
											p.id === "item-3" ||
											p.id === "mock-matcha" ||
											p.id === "mock-umbrella" ||
											p.id === "mock-giftpack",
									),
								)}
								{renderShelf(
									"Your Wishlist",
									"❤️",
									allProducts.filter((p) => favorites.includes(p.id)),
									<div
										style={{
											background: "rgba(255,255,255,0.01)",
											border: "1px dashed var(--border-color)",
											borderRadius: "12px",
											padding: "1.5rem",
											textAlign: "center",
											color: "var(--text-muted)",
											fontSize: "0.8rem",
											marginBottom: "2.25rem",
										}}
									>
										Your wishlist is empty. Tap the ❤️ icon on products to save
										them here!
									</div>,
								)}
								<div
									className="shelf-section"
									style={{ marginBottom: "2.25rem" }}
								>
									<h3
										className="shelf-title"
										style={{
											fontSize: "1.05rem",
											fontWeight: 800,
											marginBottom: "0.85rem",
											display: "flex",
											alignItems: "center",
											gap: "0.5rem",
											color: "var(--text-primary)",
										}}
									>
										<span>🏪</span> Stores in Spotlight
									</h3>
									<div
										className="shelf-scroll-row"
										style={{
											display: "flex",
											gap: "1rem",
											overflowX: "auto",
											paddingBottom: "0.75rem",
											scrollbarWidth: "thin",
										}}
									>
										{SPOTLIGHT_STORES.map((store) => (
											<div
												key={store.id}
												className="glass-card"
												style={{
													flex: "0 0 240px",
													padding: "1rem",
													borderLeft: store.active
														? "3px solid var(--color-customer)"
														: "1px solid var(--border-color)",
													opacity: store.active ? 1 : 0.6,
													display: "flex",
													flexDirection: "column",
													gap: "0.25rem",
												}}
											>
												<div
													style={{
														display: "flex",
														justifyContent: "space-between",
														alignItems: "center",
													}}
												>
													<strong
														style={{
															fontSize: "0.85rem",
															color: store.active
																? "var(--text-primary)"
																: "var(--text-secondary)",
														}}
													>
														{store.name}
													</strong>
													{store.active && (
														<span
															style={{
																background: "rgba(16, 185, 129, 0.12)",
																color: "var(--color-customer)",
																fontSize: "0.6rem",
																fontWeight: "bold",
																padding: "0.1rem 0.35rem",
																borderRadius: "4px",
															}}
														>
															OPEN
														</span>
													)}
												</div>
												<span
													style={{
														fontSize: "0.7rem",
														color: "var(--text-muted)",
													}}
												>
													{store.desc}
												</span>
												<div
													style={{
														display: "flex",
														gap: "0.5rem",
														marginTop: "0.5rem",
														fontSize: "0.7rem",
														fontWeight: "bold",
													}}
												>
													<span style={{ color: "var(--color-rider)" }}>
														📍 {store.dist}
													</span>
													<span style={{ color: "var(--color-engine)" }}>
														⏱️ {store.time}
													</span>
													<span style={{ color: "gold" }}>{store.rating}</span>
												</div>
											</div>
										))}
									</div>
								</div>
								{renderShelf(
									"Your Lifestyle",
									"🏃",
									allProducts.filter((p) => p.category === "Lifestyle"),
								)}
							</div>
						)}
					</div>
				) : (
					<div
						className="profile-hub-layout"
						style={{ display: "flex", gap: "1.25rem" }}
					>
						{/* Sidebar nav */}
						<div
							style={{
								width: "220px",
								display: "flex",
								flexDirection: "column",
								gap: "0.35rem",
							}}
						>
							<button
								type="button"
								className={`profile-nav-item ${profileSubTab === "vip" ? "active" : ""}`}
								onClick={() => setProfileSubTab("vip")}
							>
								<Lucide.Crown size={14} /> VIP Club Membership
							</button>
							<button
								type="button"
								className={`profile-nav-item ${profileSubTab === "orders" ? "active" : ""}`}
								onClick={() => setProfileSubTab("orders")}
							>
								<Lucide.ClipboardList size={14} /> Orders History & Purge
							</button>
							<button
								type="button"
								className={`profile-nav-item ${profileSubTab === "vouchers" ? "active" : ""}`}
								onClick={() => setProfileSubTab("vouchers")}
							>
								<Lucide.Tag size={14} /> My Discount Vouchers
							</button>
							<button
								type="button"
								className={`profile-nav-item ${profileSubTab === "rewards" ? "active" : ""}`}
								onClick={() => setProfileSubTab("rewards")}
							>
								<Lucide.Award size={14} /> Academy Course Certs
							</button>
						</div>

						{/* Sub-view Content */}
						<div style={{ flex: 1 }}>
							{profileSubTab === "vip" && (
								<div
									className={vipMember ? "vip-card-glow" : "glass-card"}
									style={{
										padding: "1.5rem",
										borderLeft: vipMember
											? "none"
											: "4px solid var(--color-customer)",
										display: "flex",
										flexDirection: "column",
										gap: "1.25rem",
									}}
								>
									<div
										style={{
											display: "flex",
											justifyContent: "space-between",
											alignItems: "center",
										}}
									>
										<h3
											style={{
												display: "flex",
												alignItems: "center",
												gap: "0.5rem",
												fontWeight: 800,
												margin: 0,
											}}
											className={vipMember ? "vip-gold-text" : ""}
										>
											<Lucide.Crown
												size={20}
												style={{
													color: vipMember ? "gold" : "var(--text-muted)",
												}}
											/>
											Swiss Q-Commerce VIP Hub
										</h3>
										<div
											style={{
												display: "flex",
												alignItems: "center",
												gap: "0.35rem",
												padding: "0.25rem 0.65rem",
												background: "rgba(16, 185, 129, 0.12)",
												border: "1px solid rgba(16, 185, 129, 0.25)",
												borderRadius: "20px",
												color: "var(--color-customer)",
												fontSize: "0.75rem",
												fontWeight: 700,
											}}
										>
											<Lucide.Award size={13} />
											<span>{customerPoints} Points</span>
										</div>
									</div>

									<div
										style={{
											display: "grid",
											gridTemplateColumns: "1fr 1fr",
											gap: "1rem",
										}}
									>
										{/* Trust Score Bento */}
										<div
											className="glass-card"
											style={{
												padding: "0.85rem",
												background: "rgba(255,255,255,0.01)",
												borderRadius: "12px",
												display: "flex",
												flexDirection: "column",
												gap: "0.4rem",
											}}
										>
											<span
												style={{
													fontSize: "0.6rem",
													color: "var(--text-muted)",
													fontWeight: 700,
													textTransform: "uppercase",
													letterSpacing: "0.05em",
												}}
											>
												Trust Shield Rating
											</span>
											<div
												style={{
													display: "flex",
													alignItems: "center",
													gap: "0.5rem",
												}}
											>
												{customerTrustScore >= 85 ? (
													<Lucide.ShieldCheck
														size={20}
														style={{ color: "var(--color-customer)" }}
													/>
												) : (
													<Lucide.ShieldAlert
														size={20}
														style={{ color: "var(--color-admin)" }}
													/>
												)}
												<span
													style={{
														fontSize: "1.25rem",
														fontWeight: 800,
														fontFamily: "var(--font-mono)",
														color:
															customerTrustScore >= 85
																? "var(--color-customer)"
																: "var(--color-admin)",
													}}
												>
													{customerTrustScore}
													<span
														style={{
															fontSize: "0.8rem",
															color: "var(--text-muted)",
															fontWeight: 500,
														}}
													>
														/100
													</span>
												</span>
											</div>
											{/* Small progress meter */}
											<div
												style={{
													height: "4px",
													background: "rgba(255,255,255,0.05)",
													borderRadius: "2px",
													overflow: "hidden",
												}}
											>
												<div
													style={{
														height: "100%",
														background:
															customerTrustScore >= 85
																? "var(--color-customer)"
																: "var(--color-admin)",
														width: `${customerTrustScore}%`,
														transition: "width 0.5s ease",
													}}
												/>
											</div>
										</div>

										{/* Membership status card */}
										<div
											className="glass-card"
											style={{
												padding: "0.85rem",
												background: "rgba(255,255,255,0.01)",
												borderRadius: "12px",
												display: "flex",
												flexDirection: "column",
												gap: "0.4rem",
											}}
										>
											<span
												style={{
													fontSize: "0.65rem",
													color: "var(--text-muted)",
													fontWeight: 700,
													textTransform: "uppercase",
													letterSpacing: "0.05em",
												}}
											>
												Membership Tier
											</span>
											<div
												style={{
													display: "flex",
													alignItems: "center",
													gap: "0.5rem",
												}}
											>
												<Lucide.Zap
													size={18}
													style={{
														color: vipMember ? "gold" : "var(--text-muted)",
													}}
												/>
												<span
													style={{
														fontSize: "0.95rem",
														fontWeight: 800,
														color: vipMember ? "gold" : "var(--text-primary)",
													}}
												>
													{vipMember ? "VIP Premium" : "Standard Tier"}
												</span>
											</div>
											<span
												style={{
													fontSize: "0.65rem",
													color: "var(--text-muted)",
													lineHeight: 1.2,
												}}
											>
												{vipMember
													? "✓ Unlimited free delivery on orders above $15"
													: "Upgrade by earning loyalty points on purchases"}
											</span>
										</div>
									</div>

									{gdprTokenProbation && (
										<div
											className="chaos-banner"
											style={{
												margin: 0,
												padding: "0.75rem 1rem",
												borderRadius: "10px",
												border: "1px solid rgba(245, 158, 11, 0.3)",
												background: "rgba(245, 158, 11, 0.08)",
												color: "var(--color-rider)",
											}}
										>
											<Lucide.AlertTriangle size={16} />
											<div style={{ fontSize: "0.75rem", lineHeight: 1.3 }}>
												<strong>GDPR Probation Active:</strong> Your trust
												rating is temporarily capped. Complete 3 successful
												deliveries to clear compliance logs.
											</div>
										</div>
									)}
								</div>
							)}

							{profileSubTab === "orders" && (
								<div className="glass-card" style={{ padding: "1rem" }}>
									<div
										style={{
											display: "flex",
											justifyContent: "space-between",
											alignItems: "center",
											marginBottom: "1rem",
										}}
									>
										<h3 style={{ fontWeight: 800 }}>
											Past Purchase Statements
										</h3>
										<button
											type="button"
											className="btn-secondary-glow"
											style={{
												color: "var(--color-admin)",
												borderColor: "var(--color-admin)",
											}}
											onClick={handleGdprPurge}
										>
											Purge History (GDPR Art. 17)
										</button>
									</div>
									{orderHistory.length === 0 ? (
										<p
											style={{
												color: "var(--text-muted)",
												fontSize: "0.75rem",
											}}
										>
											No purchase statements found. Placed orders will display
											here.
										</p>
									) : (
										<div
											style={{
												display: "flex",
												flexDirection: "column",
												gap: "0.5rem",
											}}
										>
											{orderHistory.map((historyItem: OrderHistoryItem) => (
												<div
													key={historyItem.id}
													style={{
														display: "flex",
														justifyContent: "space-between",
														padding: "0.5rem",
														borderBottom: "1px solid var(--border-color)",
														fontSize: "0.8rem",
													}}
												>
													<div>
														<strong>Order #{historyItem.id}</strong> (
														{historyItem.date})
														<div
															style={{
																color: "var(--text-muted)",
																fontSize: "0.7rem",
															}}
														>
															{historyItem.items}
														</div>
													</div>
													<div style={{ textAlign: "right" }}>
														<strong>${historyItem.total.toFixed(2)}</strong>
														<div
															style={{
																color: "var(--color-customer)",
																fontSize: "0.7rem",
															}}
														>
															{historyItem.status}
														</div>
													</div>
												</div>
											))}
										</div>
									)}
								</div>
							)}

							{profileSubTab === "vouchers" && (
								<div className="voucher-ticket-row">
									{vouchers.map((v) => (
										<div key={v.code} className="voucher-ticket">
											<div
												style={{
													display: "flex",
													flexDirection: "column",
													gap: "0.25rem",
												}}
											>
												<div>
													<span className="voucher-code">{v.code}</span>
													<span
														style={{
															fontSize: "0.75rem",
															color: "var(--text-secondary)",
															marginLeft: "0.5rem",
														}}
													>
														(Flat ${v.value.toFixed(2)} Off)
													</span>
												</div>
												<p
													style={{
														fontSize: "0.7rem",
														color: "var(--text-muted)",
														margin: 0,
													}}
												>
													{v.desc}
												</p>
											</div>
											<button
												type="button"
												className="btn-secondary-glow"
												style={{
													fontSize: "0.7rem",
													padding: "0.3rem 0.65rem",
													cursor: "pointer",
													border: "1px solid var(--border-color)",
													borderRadius: "6px",
												}}
												onClick={() => {
													if (setVoucherCode) setVoucherCode(v.code);
												}}
											>
												Apply
											</button>
										</div>
									))}
								</div>
							)}

							{profileSubTab === "rewards" && (
								<div className="glass-card" style={{ padding: "1rem" }}>
									<h3 style={{ fontWeight: 800, marginBottom: "0.5rem" }}>
										Swiss Training Academy Certificates
									</h3>
									<p
										style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}
									>
										Complete roles training to generate downloadable canvas
										credentials.
									</p>
									<button
										type="button"
										className="btn-primary-glow"
										style={{
											background: "var(--color-customer)",
											color: "#ffffff",
											border: "none",
											padding: "0.5rem 1rem",
											marginTop: "0.5rem",
										}}
										onClick={() => generateCertificate("customer")}
									>
										View Customer Loyalty Certificate
									</button>
								</div>
							)}
						</div>
					</div>
				)}
			</div>

			{/* Cart Drawer Panel (Right Side) */}
			<div className="customer-cart-drawer">
				<h3
					style={{
						display: "flex",
						alignItems: "center",
						gap: "0.4rem",
						fontWeight: 800,
						borderBottom: "1px solid var(--border-color)",
						paddingBottom: "0.5rem",
						marginBottom: "0.75rem",
					}}
				>
					<Lucide.ShoppingCart size={18} className="event-customer" />
					Shopping Cart (
					<span
						key={cart.reduce((sum, item) => sum + item.qty, 0)}
						className="scale-pop-animation"
						style={{ display: "inline-block" }}
					>
						{cart.reduce((sum, item) => sum + item.qty, 0)}
					</span>
					)
				</h3>

				{cart.length === 0 ? (
					<div
						style={{
							display: "flex",
							flexDirection: "column",
							alignItems: "center",
							justifyContent: "center",
							height: "200px",
							color: "var(--text-muted)",
						}}
					>
						<Lucide.ShoppingBag
							size={32}
							style={{ opacity: 0.3, marginBottom: "0.5rem" }}
						/>
						<span style={{ fontSize: "0.75rem" }}>Cart is empty</span>
					</div>
				) : (
					<div
						style={{
							display: "flex",
							flexDirection: "column",
							height: "calc(100% - 40px)",
							justifyContent: "space-between",
						}}
					>
						<div
							style={{ overflowY: "auto", flex: 1, paddingRight: "0.25rem" }}
						>
							{cart.map((item) => (
								<div
									key={item.id}
									className="cart-item-row"
									style={{
										display: "flex",
										justifyContent: "space-between",
										alignItems: "center",
										padding: "0.5rem 0",
										borderBottom: "1px solid rgba(255,255,255,0.03)",
									}}
								>
									<div>
										<span style={{ fontSize: "0.8rem", fontWeight: 700 }}>
											{item.name}
										</span>
										<div
											style={{ fontSize: "0.7rem", color: "var(--text-muted)" }}
										>
											{item.qty}x ${item.price.toFixed(2)}
										</div>
									</div>
									<button
										type="button"
										className="cart-remove-btn"
										onClick={() => removeFromCart(item.id)}
									>
										<Lucide.Trash size={12} />
									</button>
								</div>
							))}
						</div>

						{/* Cart Calculations and checkout */}
						<div
							style={{
								borderTop: "1px solid var(--border-color)",
								paddingTop: "0.75rem",
								marginTop: "0.5rem",
							}}
						>
							{/* Tip Select */}
							<div style={{ marginBottom: "0.75rem" }}>
								<span
									style={{
										fontSize: "0.65rem",
										fontWeight: 700,
										color: "var(--text-secondary)",
									}}
								>
									ADD RIDER TIP
								</span>
								<div
									style={{
										display: "flex",
										gap: "0.25rem",
										marginTop: "0.25rem",
									}}
								>
									{[0, 2, 5, 10].map((tip) => (
										<button
											type="button"
											key={tip}
											className={`btn-secondary-glow`}
											style={{
												flex: 1,
												padding: "0.25rem",
												fontSize: "0.7rem",
												background:
													tipAmount === tip
														? "rgba(16, 185, 129, 0.1)"
														: "transparent",
												borderColor:
													tipAmount === tip
														? "var(--color-customer)"
														: "var(--border-color)",
											}}
											onClick={() => setTipAmount(tip)}
										>
											{tip === 0 ? "No Tip" : `$${tip}`}
										</button>
									))}
								</div>
							</div>

							{/* ESG Bag Checkbox */}
							<div
								className="chaos-switch-row"
								style={{
									marginBottom: "0.75rem",
									padding: "0.4rem 0",
									borderBottom: "1px solid rgba(255,255,255,0.03)",
								}}
							>
								<div className="chaos-switch-info">
									<label
										htmlFor="esg-bags"
										style={{
											fontSize: "0.75rem",
											fontWeight: 700,
											color: "var(--text-secondary)",
											cursor: "pointer",
											display: "flex",
											alignItems: "center",
											gap: "0.3rem",
										}}
									>
										<span>🌳 Return bags offset rebate</span>
									</label>
									<span
										style={{ fontSize: "0.65rem", color: "var(--text-muted)" }}
									>
										$0.50 cash rebate applied to total cost
									</span>
								</div>
								<div style={{ position: "relative" }}>
									<input
										type="checkbox"
										id="esg-bags"
										className="switch-input-customer"
										checked={esgCheckbox}
										onChange={(e) => setEsgCheckbox(e.target.checked)}
									/>
									<label
										htmlFor="esg-bags"
										className="switch-label"
										aria-label="Toggle bags offset rebate"
									/>
								</div>
							</div>

							{/* Invoice calculation summary */}
							<div
								style={{
									display: "flex",
									flexDirection: "column",
									gap: "0.3rem",
									fontSize: "0.75rem",
									marginBottom: "0.75rem",
								}}
							>
								<div
									style={{ display: "flex", justifyContent: "space-between" }}
								>
									<span style={{ color: "var(--text-muted)" }}>Subtotal:</span>
									<span>${cartSubtotal.toFixed(2)}</span>
								</div>
								<div
									style={{ display: "flex", justifyContent: "space-between" }}
								>
									<span style={{ color: "var(--text-muted)" }}>
										Delivery Fee:
									</span>
									<span>${deliveryFee.toFixed(2)}</span>
								</div>
								{esgCheckbox && (
									<div
										style={{
											display: "flex",
											justifyContent: "space-between",
											color: "var(--color-customer)",
										}}
									>
										<span>Paper Bag Rebate:</span>
										<span>-$0.50</span>
									</div>
								)}
								{tipAmount > 0 && (
									<div
										style={{ display: "flex", justifyContent: "space-between" }}
									>
										<span style={{ color: "var(--text-muted)" }}>
											Rider Tip:
										</span>
										<span>${tipAmount.toFixed(2)}</span>
									</div>
								)}
								<div
									style={{
										display: "flex",
										justifyContent: "space-between",
										borderTop: "1px dashed var(--border-color)",
										paddingTop: "0.3rem",
										fontSize: "0.85rem",
										fontWeight: 800,
									}}
								>
									<span>Total cost:</span>
									<span style={{ color: "var(--color-customer)" }}>
										${totalCost.toFixed(2)}
									</span>
								</div>
							</div>

							{/* Checkout Gateways fallbacks */}
							<div
								style={{
									display: "flex",
									flexDirection: "column",
									gap: "0.4rem",
								}}
							>
								<button
									type="button"
									id="btn-checkout-wallet"
									className="btn-primary-glow scale-pop-animation"
									key={totalCost}
									style={{
										width: "100%",
										border: "none",
										background: "var(--color-customer)",
										color: "#ffffff",
										padding: "0.5rem",
										cursor: "pointer",
									}}
									onClick={() => handleCheckout("Wallet")}
								>
									Pay via Wallet (Balance: ${customerWallet.toFixed(2)})
								</button>
								<button
									type="button"
									className="btn-secondary-glow"
									style={{
										width: "100%",
										padding: "0.4rem",
										cursor: "pointer",
									}}
									onClick={() => handleCheckout("Swipe")}
								>
									Pay via Swipe Instant Gateway
								</button>
							</div>
						</div>
					</div>
				)}
			</div>

			{cart.length > 0 && (
				<div className="floating-checkout-bar">
					<div style={{ display: "flex", flexDirection: "column" }}>
						<span
							style={{
								fontSize: "0.65rem",
								color: "var(--text-muted)",
								textTransform: "uppercase",
								letterSpacing: "0.05em",
								fontWeight: 600,
							}}
						>
							Quick Checkout
						</span>
						<span
							style={{
								fontSize: "1rem",
								fontWeight: 800,
								color: "var(--color-customer)",
							}}
						>
							Total: ${totalCost.toFixed(2)}
						</span>
					</div>
					<div style={{ display: "flex", gap: "0.5rem" }}>
						<button
							type="button"
							className="btn-primary-glow scale-pop-animation"
							key={totalCost}
							style={{
								background: "var(--color-customer)",
								color: "#ffffff",
								border: "none",
								padding: "0.4rem 0.8rem",
								cursor: "pointer",
								borderRadius: "6px",
								fontSize: "0.75rem",
								fontWeight: 700,
							}}
							onClick={() => handleCheckout("Wallet")}
						>
							Pay Wallet (${customerWallet.toFixed(2)})
						</button>
						<button
							type="button"
							className="btn-secondary-glow"
							style={{
								padding: "0.4rem 0.8rem",
								cursor: "pointer",
								borderRadius: "6px",
								fontSize: "0.75rem",
							}}
							onClick={() => handleCheckout("Swipe")}
						>
							Swipe Pay
						</button>
					</div>
				</div>
			)}
			{showSubstitutionModal &&
				subTargetItem &&
				createPortal(
					<Modal
						isOpen
						zIndex={2000}
						maxWidth={420}
						accentColor="var(--color-customer)"
						title={
							<>
								<Lucide.Sparkles size={18} />
								Stock Alert: Running Low!
							</>
						}
						onClose={() => {
							setShowSubstitutionModal(false);
							setSubTargetItem(null);
						}}
						actions={
							<button
								type="button"
								className="btn-secondary-glow"
								style={{
									flex: 1,
									padding: "0.5rem",
									fontSize: "0.8rem",
									cursor: "pointer",
								}}
								onClick={() => {
									setShowSubstitutionModal(false);
									setSubTargetItem(null);
								}}
							>
								Keep Original
							</button>
						}
					>
						<p
							style={{
								fontSize: "0.8rem",
								color: "var(--text-secondary)",
								marginBottom: "1.25rem",
							}}
						>
							<strong>
								{subTargetItem.emoji} {subTargetItem.name}
							</strong>{" "}
							is in high demand (Only {subTargetItem.stock} left!). Would you
							like to select a high-availability backup substitute to ensure
							your delivery isn't delayed?
						</p>

						{(() => {
							const subId = substitutionMap[subTargetItem.id];
							const substitute = allProducts.find((p) => p.id === subId);
							if (!substitute) return null;

							return (
								<div
									style={{
										background: "rgba(255,255,255,0.03)",
										border: "1px solid rgba(255,255,255,0.05)",
										borderRadius: "10px",
										padding: "1rem",
										marginBottom: "1.5rem",
										display: "flex",
										alignItems: "center",
										gap: "1rem",
										justifyContent: "space-between",
									}}
								>
									<div
										style={{
											display: "flex",
											alignItems: "center",
											gap: "0.75rem",
										}}
									>
										<span style={{ fontSize: "2rem" }}>{substitute.emoji}</span>
										<div style={{ textAlign: "left" }}>
											<div style={{ fontSize: "0.85rem", fontWeight: "bold" }}>
												{substitute.name}
											</div>
											<div
												style={{
													fontSize: "0.7rem",
													color: "var(--text-muted)",
												}}
											>
												{substitute.category} • ${substitute.price.toFixed(2)}
											</div>
										</div>
									</div>
									<button
										type="button"
										className="btn-primary-glow"
										style={{
											background: "var(--color-customer)",
											color: "#ffffff",
											padding: "0.4rem 0.8rem",
											fontSize: "0.75rem",
											border: "none",
											borderRadius: "6px",
											cursor: "pointer",
										}}
										onClick={() => {
											setCart((prev) => {
												const targetInCart = prev.find(
													(item) => item.id === subTargetItem.id,
												);
												if (targetInCart) {
													const filtered = prev.filter(
														(item) => item.id !== subTargetItem.id,
													);
													const subInCart = filtered.find(
														(item) => item.id === substitute.id,
													);
													if (subInCart) {
														return filtered.map((item) =>
															item.id === substitute.id
																? {
																		...item,
																		qty: item.qty + targetInCart.qty,
																	}
																: item,
														);
													}
													return [
														...filtered,
														{ ...substitute, qty: targetInCart.qty },
													];
												}
												return prev;
											});
											setShowSubstitutionModal(false);
											setSubTargetItem(null);
										}}
									>
										Swap Item
									</button>
								</div>
							);
						})()}
					</Modal>,
					document.body,
				)}
		</div>
	);
}
