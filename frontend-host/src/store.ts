import { create, type StateCreator } from "zustand";

const INITIAL_PRODUCTS = [
	{
		id: "p1",
		name: "Organic Fresh Milk",
		price: 3.49,
		stock: 12,
		stockEast: 15,
		category: "Dairy & Eggs",
		emoji: "🥛",
		perishable: true,
	},
	{
		id: "p2",
		name: "Chiquita Bananas (1kg)",
		price: 1.99,
		stock: 18,
		stockEast: 20,
		category: "Fruits & Veggies",
		emoji: "🍌",
		perishable: false,
	},
	{
		id: "p3",
		name: "Fresh Hass Avocado (Pair)",
		price: 2.99,
		stock: 8,
		stockEast: 0,
		category: "Fruits & Veggies",
		emoji: "🥑",
		perishable: false,
	},
	{
		id: "p4",
		name: "Coca Cola Zero 6-Pack",
		price: 5.49,
		stock: 15,
		stockEast: 15,
		category: "Snacks & Drinks",
		emoji: "🥤",
		perishable: false,
	},
	{
		id: "p5",
		name: "Whole Wheat Sourdough",
		price: 4.29,
		stock: 6,
		stockEast: 8,
		category: "Bakery",
		emoji: "🍞",
		perishable: false,
	},
	{
		id: "p6",
		name: "Double Chocolate Muffins",
		price: 3.89,
		stock: 2,
		stockEast: 5,
		category: "Bakery",
		emoji: "🧁",
		perishable: false,
	},
	{
		id: "p7",
		name: "Free Range Eggs (Dozen)",
		price: 4.99,
		stock: 10,
		stockEast: 12,
		category: "Dairy & Eggs",
		emoji: "🥚",
		perishable: true,
	},
	{
		id: "p8",
		name: "Potato Chips (Sea Salt)",
		price: 2.49,
		stock: 25,
		stockEast: 30,
		category: "Snacks & Drinks",
		emoji: "🥔",
		perishable: false,
	},
];

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

export interface OrderItem {
	id: number | string;
	date: string;
	items: string;
	total: number;
	status: string;
	paymentMethod: string;
}

export interface UserSlice {
	activeRole: any;
	setActiveRole: (val: any) => void;
	customerWallet: any;
	setCustomerWallet: (val: any) => void;
	customerPoints: any;
	setCustomerPoints: (val: any) => void;
	riderWallet: any;
	setRiderWallet: (val: any) => void;
	merchantWallet: any;
	setMerchantWallet: (val: any) => void;
	customerOrderCount: any;
	setCustomerOrderCount: (val: any) => void;
	customerRefundCount: any;
	setCustomerRefundCount: (val: any) => void;
	customerTab: any;
	setCustomerTab: (val: any) => void;
	profileSubTab: any;
	setProfileSubTab: (val: any) => void;
	savedAddresses: any;
	setSavedAddresses: (val: any) => void;
	savedCards: any;
	setSavedCards: (val: any) => void;
	favorites: any;
	setFavorites: (val: any) => void;
	vipMember: any;
	setVipMember: (val: any) => void;
	vouchers: any;
	setVouchers: (val: any) => void;
	b2bDiscountActive: any;
	setB2bDiscountActive: (val: any) => void;
	customerTrustScore: any;
	setCustomerTrustScore: (val: any) => void;
	riderTrustScore: any;
	setRiderTrustScore: (val: any) => void;
	riderTrafficActive: any;
	setRiderTrafficActive: (val: any) => void;
	onboardingQueue: any;
	setOnboardingQueue: (val: any) => void;
	riderOnboardStatus: any;
	setRiderOnboardStatus: (val: any) => void;
	businessOnboardStatus: any;
	setBusinessOnboardStatus: (val: any) => void;
	gatewayOnboardStatus: any;
	setGatewayOnboardStatus: (val: any) => void;
	riderCoords: any;
	setRiderCoords: (val: any) => void;
}

export interface ProductSlice {
	products: any;
	setProducts: (val: any) => void;
	searchVolumeMap: any;
	setSearchVolumeMap: (val: any) => void;
}

export interface OrderSlice {
	cart: any;
	setCart: (val: any) => void;
	activeOrder: any;
	setActiveOrder: (val: any) => void;
	orderHistory: any;
	setOrderHistory: (val: any) => void;
	activeStockTransfers: any;
	setActiveStockTransfers: (val: any) => void;
	tipAmount: any;
	setTipAmount: (val: any) => void;
	esgCheckbox: any;
	setEsgCheckbox: (val: any) => void;
	totalCo2Offset: any;
	setTotalCo2Offset: (val: any) => void;
	pickerSlaDuration: any;
	setPickerSlaDuration: (val: any) => void;
	pickerBadge: any;
	setPickerBadge: (val: any) => void;
	backupPickersCount: any;
	setBackupPickersCount: (val: any) => void;
	cartIdleTime: any;
	setCartIdleTime: (val: any) => void;
	pickingBacklogQueue: any;
	setPickingBacklogQueue: (val: any) => void;
	activePickingCongested: any;
	setActivePickingCongested: (val: any) => void;
}

export interface AppSlice {
	weather: any;
	setWeather: (val: any) => void;
	toasts: any;
	setToasts: (val: any) => void;
	botOpen: any;
	setBotOpen: (val: any) => void;
	botInputText: any;
	setBotInputText: (val: any) => void;
	botMessages: any;
	setBotMessages: (val: any) => void;
	theme: string;
	setTheme: (val: any) => void;
	language: string;
	setLanguage: (val: any) => void;
}


export interface AuthSlice {
	isAuthenticated: any;
	setIsAuthenticated: (val: any) => void;
	currentUserSession: any;
	setCurrentUserSession: (val: any) => void;
	mfaStep: any;
	setMfaStep: (val: any) => void;
	mfaRole: any;
	setMfaRole: (val: any) => void;
	mfaPassword: any;
	setMfaPassword: (val: any) => void;
	mfaOtpSentCode: any;
	setMfaOtpSentCode: (val: any) => void;
	mfaOtpInput: any;
	setMfaOtpInput: (val: any) => void;
	mfaMethod: any;
	setMfaMethod: (val: any) => void;
	totpSecretCode: any;
	setTotpSecretCode: (val: any) => void;
	totpTimer: any;
	setTotpTimer: (val: any) => void;
	sessionToken: any;
	setSessionToken: (val: any) => void;
	authToken: any;
	setAuthToken: (val: any) => void;
	gdprTokenProbation: any;
	setGdprTokenProbation: (val: any) => void;
	jwtFlash: any;
	setJwtFlash: (val: any) => void;
}

export interface SystemSlice {
	simulateTelemetryFraud: any;
	setSimulateTelemetryFraud: (val: any) => void;
	selectedCertRole: any;
	setSelectedCertRole: (val: any) => void;
	activeTrainingRole: any;
	setActiveTrainingRole: (val: any) => void;
	trainingProgress: any;
	setTrainingProgress: (val: any) => void;
	earnedCertifications: any;
	setEarnedCertifications: (val: any) => void;
	pickerTrustScore: any;
	setPickerTrustScore: (val: any) => void;
	wholesalerTrustScore: any;
	setWholesalerTrustScore: (val: any) => void;
	trustLogs: any;
	setTrustLogs: (val: any) => void;
	coldChainBreakdownActive: any;
	setColdChainBreakdownActive: (val: any) => void;
	wholesalerOutageActive: any;
	setWholesalerOutageActive: (val: any) => void;
	paymentOutageActive: any;
	setPaymentOutageActive: (val: any) => void;
	redisCrashActive: any;
	setRedisCrashActive: (val: any) => void;
	dbLatencyActive: any;
	setDbLatencyActive: (val: any) => void;
	centralCapacity: any;
	setCentralCapacity: (val: any) => void;
	eastCapacity: any;
	setEastCapacity: (val: any) => void;
	centralScalingCount: any;
	setCentralScalingCount: (val: any) => void;
	eastScalingCount: any;
	setEastScalingCount: (val: any) => void;
	hitlQueue: any;
	setHitlQueue: (val: any) => void;
	agentMetrics: any;
	setAgentMetrics: (val: any) => void;
	oltpWriteLatency: any;
	setOltpWriteLatency: (val: any) => void;
	olapSyncTimer: any;
	setOlapSyncTimer: (val: any) => void;
	vaultTimer: any;
	setVaultTimer: (val: any) => void;
	latencyHistory: any;
	setLatencyHistory: (val: any) => void;
	cacheHits: any;
	setCacheHits: (val: any) => void;
	cacheMisses: any;
	setCacheMisses: (val: any) => void;
	circuitBreakerTripped: any;
	setCircuitBreakerTripped: (val: any) => void;
	rateLimitActive: any;
	setRateLimitActive: (val: any) => void;
	kafkaLogs: any;
	setKafkaLogs: (val: any) => void;
	ledger: any;
	setLedger: (val: any) => void;
	certModalOpen: any;
	setCertModalOpen: (val: any) => void;
}

export type State = UserSlice &
	ProductSlice &
	OrderSlice &
	AppSlice &
	AuthSlice &
	SystemSlice;

export const createUserSlice: StateCreator<State, [], [], UserSlice> = (
	set,
) => ({
	activeRole: "customer",
	setActiveRole: (val) =>
		set(
			(state: any) =>
				({
					activeRole: typeof val === "function" ? val(state.activeRole) : val,
				}) as any,
		),
	customerWallet: 100.0,
	setCustomerWallet: (val) =>
		set(
			(state: any) =>
				({
					customerWallet:
						typeof val === "function" ? val(state.customerWallet) : val,
				}) as any,
		),
	customerPoints: 45,
	setCustomerPoints: (val) =>
		set(
			(state: any) =>
				({
					customerPoints:
						typeof val === "function" ? val(state.customerPoints) : val,
				}) as any,
		),
	riderWallet: 15.0,
	setRiderWallet: (val) =>
		set(
			(state: any) =>
				({
					riderWallet: typeof val === "function" ? val(state.riderWallet) : val,
				}) as any,
		),
	merchantWallet: 1542.8,
	setMerchantWallet: (val) =>
		set(
			(state: any) =>
				({
					merchantWallet:
						typeof val === "function" ? val(state.merchantWallet) : val,
				}) as any,
		),
	customerOrderCount: 2,
	setCustomerOrderCount: (val) =>
		set(
			(state: any) =>
				({
					customerOrderCount:
						typeof val === "function" ? val(state.customerOrderCount) : val,
				}) as any,
		),
	customerRefundCount: 0,
	setCustomerRefundCount: (val) =>
		set(
			(state: any) =>
				({
					customerRefundCount:
						typeof val === "function" ? val(state.customerRefundCount) : val,
				}) as any,
		),
	customerTab: "catalog",
	setCustomerTab: (val) =>
		set(
			(state: any) =>
				({
					customerTab: typeof val === "function" ? val(state.customerTab) : val,
				}) as any,
		),
	profileSubTab: "vip",
	setProfileSubTab: (val) =>
		set(
			(state: any) =>
				({
					profileSubTab:
						typeof val === "function" ? val(state.profileSubTab) : val,
				}) as any,
		),
	savedAddresses: [
		{
			id: "a1",
			label: "Home (Primary)",
			address: "Flat 402, Sunset Towers, Bangalore, Karnataka",
			coords: "12.971, 77.594",
		},
		{
			id: "a2",
			label: "Work (Google Office)",
			address: "Tower C, Google Signature Road, Bangalore, Karnataka",
			coords: "12.912, 77.621",
		},
	],
	setSavedAddresses: (val) =>
		set(
			(state: any) =>
				({
					savedAddresses:
						typeof val === "function" ? val(state.savedAddresses) : val,
				}) as any,
		),
	savedCards: [
		{
			id: "c1",
			bank: "Visa Premium Credit Card",
			number: "•••• •••• •••• 9823",
			expiry: "12/28",
		},
		{
			id: "c2",
			bank: "Mastercard Gold Debit",
			number: "•••• •••• •••• 4120",
			expiry: "05/29",
		},
	],
	setSavedCards: (val) =>
		set(
			(state: any) =>
				({
					savedCards: typeof val === "function" ? val(state.savedCards) : val,
				}) as any,
		),
	favorites: [
		"Organic Fresh Milk",
		"Chiquita Bananas (1kg)",
		"Fresh Hass Avocado (Pair)",
	],
	setFavorites: (val) =>
		set(
			(state: any) =>
				({
					favorites: typeof val === "function" ? val(state.favorites) : val,
				}) as any,
		),
	vipMember: true,
	setVipMember: (val) =>
		set(
			(state: any) =>
				({
					vipMember: typeof val === "function" ? val(state.vipMember) : val,
				}) as any,
		),
	vouchers: [
		{
			code: "SWISSWELCOME5",
			value: 5.0,
			minCart: 15.0,
			desc: "Get $5.00 cash voucher on your first grocery basket!",
		},
		{
			code: "FRESH10",
			value: 10.0,
			minCart: 30.0,
			desc: "Flat $10.00 discount coupon on organic dairy orders.",
		},
	],
	setVouchers: (val) =>
		set(
			(state: any) =>
				({
					vouchers: typeof val === "function" ? val(state.vouchers) : val,
				}) as any,
		),
	b2bDiscountActive: false,
	setB2bDiscountActive: (val) =>
		set(
			(state: any) =>
				({
					b2bDiscountActive:
						typeof val === "function" ? val(state.b2bDiscountActive) : val,
				}) as any,
		),
	customerTrustScore: 100,
	setCustomerTrustScore: (val) =>
		set(
			(state: any) =>
				({
					customerTrustScore:
						typeof val === "function" ? val(state.customerTrustScore) : val,
				}) as any,
		),
	riderTrustScore: 100,
	setRiderTrustScore: (val) =>
		set(
			(state: any) =>
				({
					riderTrustScore:
						typeof val === "function" ? val(state.riderTrustScore) : val,
				}) as any,
		),
	riderTrafficActive: false,
	setRiderTrafficActive: (val) =>
		set(
			(state: any) =>
				({
					riderTrafficActive:
						typeof val === "function" ? val(state.riderTrafficActive) : val,
				}) as any,
		),
	onboardingQueue: [
		{
			id: "rid-1",
			name: "Rider Dave",
			type: "rider",
			approvals: { l1: false, l2: false, l3: false },
		},
		{
			id: "mer-1",
			name: "FreshGrocer Store",
			type: "merchant",
			approvals: { l1: false, l2: false, l3: false },
		},
	],
	setOnboardingQueue: (val) =>
		set(
			(state: any) =>
				({
					onboardingQueue:
						typeof val === "function" ? val(state.onboardingQueue) : val,
				}) as any,
		),
	riderOnboardStatus: "unapplied",
	setRiderOnboardStatus: (val) =>
		set(
			(state: any) =>
				({
					riderOnboardStatus:
						typeof val === "function" ? val(state.riderOnboardStatus) : val,
				}) as any,
		),
	businessOnboardStatus: "unapplied",
	setBusinessOnboardStatus: (val) =>
		set(
			(state: any) =>
				({
					businessOnboardStatus:
						typeof val === "function" ? val(state.businessOnboardStatus) : val,
				}) as any,
		),
	gatewayOnboardStatus: "active",
	setGatewayOnboardStatus: (val) =>
		set(
			(state: any) =>
				({
					gatewayOnboardStatus:
						typeof val === "function" ? val(state.gatewayOnboardStatus) : val,
				}) as any,
		),
	riderCoords: null,
	setRiderCoords: (val) =>
		set(
			(state: any) =>
				({
					riderCoords: typeof val === "function" ? val(state.riderCoords) : val,
				}) as any,
		),
});

export const createProductSlice: StateCreator<State, [], [], ProductSlice> = (
	set,
) => ({
	products: INITIAL_PRODUCTS,
	setProducts: (val) =>
		set(
			(state: any) =>
				({
					products: typeof val === "function" ? val(state.products) : val,
				}) as any,
		),
	searchVolumeMap: {},
	setSearchVolumeMap: (val) =>
		set(
			(state: any) =>
				({
					searchVolumeMap:
						typeof val === "function" ? val(state.searchVolumeMap) : val,
				}) as any,
		),
});

export const createOrderSlice: StateCreator<State, [], [], OrderSlice> = (
	set,
) => ({
	cart: [],
	setCart: (val) =>
		set(
			(state: any) =>
				({ cart: typeof val === "function" ? val(state.cart) : val }) as any,
		),
	activeOrder: null,
	setActiveOrder: (val) =>
		set(
			(state: any) =>
				({
					activeOrder: typeof val === "function" ? val(state.activeOrder) : val,
				}) as any,
		),
	orderHistory: [
		{
			id: 8901,
			date: "May 24",
			items: "2x Organic Milk, 1x Bananas",
			total: 8.97,
			status: "delivered",
			paymentMethod: "Wallet",
		},
		{
			id: 8710,
			date: "May 20",
			items: "1x Wheat Sourdough, 1x Free Range Eggs",
			total: 9.28,
			status: "delivered",
			paymentMethod: "PayPal",
		},
	],
	setOrderHistory: (val) =>
		set(
			(state: any) =>
				({
					orderHistory:
						typeof val === "function" ? val(state.orderHistory) : val,
				}) as any,
		),
	activeStockTransfers: [],
	setActiveStockTransfers: (val) =>
		set(
			(state: any) =>
				({
					activeStockTransfers:
						typeof val === "function" ? val(state.activeStockTransfers) : val,
				}) as any,
		),
	tipAmount: 0,
	setTipAmount: (val) =>
		set(
			(state: any) =>
				({
					tipAmount: typeof val === "function" ? val(state.tipAmount) : val,
				}) as any,
		),
	esgCheckbox: false,
	setEsgCheckbox: (val) =>
		set(
			(state: any) =>
				({
					esgCheckbox: typeof val === "function" ? val(state.esgCheckbox) : val,
				}) as any,
		),
	totalCo2Offset: 1250,
	setTotalCo2Offset: (val) =>
		set(
			(state: any) =>
				({
					totalCo2Offset:
						typeof val === "function" ? val(state.totalCo2Offset) : val,
				}) as any,
		),
	pickerSlaDuration: 3.2,
	setPickerSlaDuration: (val) =>
		set(
			(state: any) =>
				({
					pickerSlaDuration:
						typeof val === "function" ? val(state.pickerSlaDuration) : val,
				}) as any,
		),
	pickerBadge: "Standard",
	setPickerBadge: (val) =>
		set(
			(state: any) =>
				({
					pickerBadge: typeof val === "function" ? val(state.pickerBadge) : val,
				}) as any,
		),
	backupPickersCount: 0,
	setBackupPickersCount: (val) =>
		set(
			(state: any) =>
				({
					backupPickersCount:
						typeof val === "function" ? val(state.backupPickersCount) : val,
				}) as any,
		),
	cartIdleTime: 0,
	setCartIdleTime: (val) =>
		set(
			(state: any) =>
				({
					cartIdleTime:
						typeof val === "function" ? val(state.cartIdleTime) : val,
				}) as any,
		),
	pickingBacklogQueue: 0,
	setPickingBacklogQueue: (val) =>
		set(
			(state: any) =>
				({
					pickingBacklogQueue:
						typeof val === "function" ? val(state.pickingBacklogQueue) : val,
				}) as any,
		),
	activePickingCongested: false,
	setActivePickingCongested: (val) =>
		set(
			(state: any) =>
				({
					activePickingCongested:
						typeof val === "function" ? val(state.activePickingCongested) : val,
				}) as any,
		),
});

export const createAppSlice: StateCreator<State, [], [], AppSlice> = (set) => ({
	weather: "Sunny",
	setWeather: (val) =>
		set(
			(state: any) =>
				({
					weather: typeof val === "function" ? val(state.weather) : val,
				}) as any,
		),
	toasts: [],
	setToasts: (val) =>
		set(
			(state: any) =>
				({
					toasts: typeof val === "function" ? val(state.toasts) : val,
				}) as any,
		),
	botOpen: false,
	setBotOpen: (val) =>
		set(
			(state: any) =>
				({
					botOpen: typeof val === "function" ? val(state.botOpen) : val,
				}) as any,
		),
	botInputText: "",
	setBotInputText: (val) =>
		set(
			(state: any) =>
				({
					botInputText:
						typeof val === "function" ? val(state.botInputText) : val,
				}) as any,
		),
	botMessages: [
		{
			sender: "bot",
			text: "Hi! I am SwissBot, your AI support assistant. Need help with checkouts, orders, refunds, or shelf updates?",
		},
	],
	setBotMessages: (val) =>
		set(
			(state: any) =>
				({
					botMessages: typeof val === "function" ? val(state.botMessages) : val,
				}) as any,
		),
	theme: (() => {
		if (typeof window !== "undefined") {
			const saved = localStorage.getItem("swish-theme");
			if (saved) return saved;
		}
		const hour = new Date().getHours();
		return hour >= 7 && hour < 19 ? "light" : "dark";
	})(),
	setTheme: (val) =>
		set(
			(state: any) => {
				const nextTheme = typeof val === "function" ? val(state.theme) : val;
				if (typeof window !== "undefined") {
					localStorage.setItem("swish-theme", nextTheme);
					document.documentElement.setAttribute("data-theme", nextTheme);
					if (nextTheme === "light") {
						document.body.classList.add("light-theme");
					} else {
						document.body.classList.remove("light-theme");
					}
				}
				return { theme: nextTheme };
			}
		),
	language: (() => {
		if (typeof window !== "undefined") {
			const saved = localStorage.getItem("swish-language");
			if (saved) return saved;
		}
		return "en";
	})(),
	setLanguage: (val) =>
		set(
			(state: any) => {
				const nextLang = typeof val === "function" ? val(state.language) : val;
				if (typeof window !== "undefined") {
					localStorage.setItem("swish-language", nextLang);
				}
				return { language: nextLang };
			}
		),
});


export const createAuthSlice: StateCreator<State, [], [], AuthSlice> = (
	set,
) => ({
	isAuthenticated: false,
	setIsAuthenticated: (val) =>
		set(
			(state: any) =>
				({
					isAuthenticated:
						typeof val === "function" ? val(state.isAuthenticated) : val,
				}) as any,
		),
	currentUserSession: null,
	setCurrentUserSession: (val) =>
		set(
			(state: any) =>
				({
					currentUserSession:
						typeof val === "function" ? val(state.currentUserSession) : val,
				}) as any,
		),
	mfaStep: "credentials",
	setMfaStep: (val) =>
		set(
			(state: any) =>
				({
					mfaStep: typeof val === "function" ? val(state.mfaStep) : val,
				}) as any,
		),
	mfaRole: "customer",
	setMfaRole: (val) =>
		set(
			(state: any) =>
				({
					mfaRole: typeof val === "function" ? val(state.mfaRole) : val,
				}) as any,
		),
	mfaPassword: "",
	setMfaPassword: (val) =>
		set(
			(state: any) =>
				({
					mfaPassword: typeof val === "function" ? val(state.mfaPassword) : val,
				}) as any,
		),
	mfaOtpSentCode: null,
	setMfaOtpSentCode: (val) =>
		set(
			(state: any) =>
				({
					mfaOtpSentCode:
						typeof val === "function" ? val(state.mfaOtpSentCode) : val,
				}) as any,
		),
	mfaOtpInput: "",
	setMfaOtpInput: (val) =>
		set(
			(state: any) =>
				({
					mfaOtpInput: typeof val === "function" ? val(state.mfaOtpInput) : val,
				}) as any,
		),
	mfaMethod: "sms",
	setMfaMethod: (val) =>
		set(
			(state: any) =>
				({
					mfaMethod: typeof val === "function" ? val(state.mfaMethod) : val,
				}) as any,
		),
	totpSecretCode: "",
	setTotpSecretCode: (val) =>
		set(
			(state: any) =>
				({
					totpSecretCode:
						typeof val === "function" ? val(state.totpSecretCode) : val,
				}) as any,
		),
	totpTimer: 30,
	setTotpTimer: (val) =>
		set(
			(state: any) =>
				({
					totpTimer: typeof val === "function" ? val(state.totpTimer) : val,
				}) as any,
		),
	sessionToken: "",
	setSessionToken: (val) =>
		set(
			(state: any) =>
				({
					sessionToken:
						typeof val === "function" ? val(state.sessionToken) : val,
				}) as any,
		),
	authToken: (() => {
		const token = localStorage.getItem("jwt_token") || "";
		const jwtRegex = /^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_+/=]+$/;
		const isMockToken = token.startsWith("mock.");
		return isMockToken || jwtRegex.test(token) ? token : "";
	})(),
	setAuthToken: (val) =>
		set((state: any) => {
			const rawToken = typeof val === "function" ? val(state.authToken) : val;
			const jwtRegex = /^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_+/=]+$/;
			const isMockToken =
				typeof rawToken === "string" && rawToken.startsWith("mock.");
			const cleanToken = isMockToken || jwtRegex.test(rawToken) ? rawToken : "";
			if (cleanToken) {
				localStorage.setItem("jwt_token", cleanToken);
			} else {
				localStorage.removeItem("jwt_token");
			}
			return { authToken: cleanToken } as any;
		}),
	gdprTokenProbation: false,
	setGdprTokenProbation: (val) =>
		set(
			(state: any) =>
				({
					gdprTokenProbation:
						typeof val === "function" ? val(state.gdprTokenProbation) : val,
				}) as any,
		),
	jwtFlash: false,
	setJwtFlash: (val) =>
		set(
			(state: any) =>
				({
					jwtFlash: typeof val === "function" ? val(state.jwtFlash) : val,
				}) as any,
		),
});

export const createSystemSlice: StateCreator<State, [], [], SystemSlice> = (
	set,
) => ({
	simulateTelemetryFraud: false,
	setSimulateTelemetryFraud: (val) =>
		set(
			(state: any) =>
				({
					simulateTelemetryFraud:
						typeof val === "function" ? val(state.simulateTelemetryFraud) : val,
				}) as any,
		),
	selectedCertRole: "customer",
	setSelectedCertRole: (val) =>
		set(
			(state: any) =>
				({
					selectedCertRole:
						typeof val === "function" ? val(state.selectedCertRole) : val,
				}) as any,
		),
	activeTrainingRole: null,
	setActiveTrainingRole: (val) =>
		set(
			(state: any) =>
				({
					activeTrainingRole:
						typeof val === "function" ? val(state.activeTrainingRole) : val,
				}) as any,
		),
	trainingProgress: 0,
	setTrainingProgress: (val) =>
		set(
			(state: any) =>
				({
					trainingProgress:
						typeof val === "function" ? val(state.trainingProgress) : val,
				}) as any,
		),
	earnedCertifications: [],
	setEarnedCertifications: (val) =>
		set(
			(state: any) =>
				({
					earnedCertifications:
						typeof val === "function" ? val(state.earnedCertifications) : val,
				}) as any,
		),
	pickerTrustScore: 100,
	setPickerTrustScore: (val) =>
		set(
			(state: any) =>
				({
					pickerTrustScore:
						typeof val === "function" ? val(state.pickerTrustScore) : val,
				}) as any,
		),
	wholesalerTrustScore: 100,
	setWholesalerTrustScore: (val) =>
		set(
			(state: any) =>
				({
					wholesalerTrustScore:
						typeof val === "function" ? val(state.wholesalerTrustScore) : val,
				}) as any,
		),
	trustLogs: [
		{
			id: "T0",
			time: new Date().toLocaleTimeString(),
			actor: "system",
			event: "Security trust systems initialized",
			delta: 0,
			current: 100,
		},
	],
	setTrustLogs: (val) =>
		set(
			(state: any) =>
				({
					trustLogs: typeof val === "function" ? val(state.trustLogs) : val,
				}) as any,
		),
	coldChainBreakdownActive: false,
	setColdChainBreakdownActive: (val) =>
		set(
			(state: any) =>
				({
					coldChainBreakdownActive:
						typeof val === "function"
							? val(state.coldChainBreakdownActive)
							: val,
				}) as any,
		),
	wholesalerOutageActive: false,
	setWholesalerOutageActive: (val) =>
		set(
			(state: any) =>
				({
					wholesalerOutageActive:
						typeof val === "function" ? val(state.wholesalerOutageActive) : val,
				}) as any,
		),
	paymentOutageActive: false,
	setPaymentOutageActive: (val) =>
		set(
			(state: any) =>
				({
					paymentOutageActive:
						typeof val === "function" ? val(state.paymentOutageActive) : val,
				}) as any,
		),
	redisCrashActive: false,
	setRedisCrashActive: (val) =>
		set(
			(state: any) =>
				({
					redisCrashActive:
						typeof val === "function" ? val(state.redisCrashActive) : val,
				}) as any,
		),
	dbLatencyActive: false,
	setDbLatencyActive: (val) =>
		set(
			(state: any) =>
				({
					dbLatencyActive:
						typeof val === "function" ? val(state.dbLatencyActive) : val,
				}) as any,
		),
	centralCapacity: 120,
	setCentralCapacity: (val) =>
		set(
			(state: any) =>
				({
					centralCapacity:
						typeof val === "function" ? val(state.centralCapacity) : val,
				}) as any,
		),
	eastCapacity: 120,
	setEastCapacity: (val) =>
		set(
			(state: any) =>
				({
					eastCapacity:
						typeof val === "function" ? val(state.eastCapacity) : val,
				}) as any,
		),
	centralScalingCount: 0,
	setCentralScalingCount: (val) =>
		set(
			(state: any) =>
				({
					centralScalingCount:
						typeof val === "function" ? val(state.centralScalingCount) : val,
				}) as any,
		),
	eastScalingCount: 0,
	setEastScalingCount: (val) =>
		set(
			(state: any) =>
				({
					eastScalingCount:
						typeof val === "function" ? val(state.eastScalingCount) : val,
				}) as any,
		),
	hitlQueue: [],
	setHitlQueue: (val) =>
		set(
			(state: any) =>
				({
					hitlQueue: typeof val === "function" ? val(state.hitlQueue) : val,
				}) as any,
		),
	agentMetrics: {
		dailyCost: 0.0,
		hourlyRequestCount: 0,
		dailyBudgetLimit: 5.0,
		hourlyRequestLimit: 100,
	},
	setAgentMetrics: (val) =>
		set(
			(state: any) =>
				({
					agentMetrics:
						typeof val === "function" ? val(state.agentMetrics) : val,
				}) as any,
		),
	oltpWriteLatency: 4,
	setOltpWriteLatency: (val) =>
		set(
			(state: any) =>
				({
					oltpWriteLatency:
						typeof val === "function" ? val(state.oltpWriteLatency) : val,
				}) as any,
		),
	olapSyncTimer: 0,
	setOlapSyncTimer: (val) =>
		set(
			(state: any) =>
				({
					olapSyncTimer:
						typeof val === "function" ? val(state.olapSyncTimer) : val,
				}) as any,
		),
	vaultTimer: 15,
	setVaultTimer: (val) =>
		set(
			(state: any) =>
				({
					vaultTimer: typeof val === "function" ? val(state.vaultTimer) : val,
				}) as any,
		),
	latencyHistory: [4, 6, 4, 5, 8, 4, 4],
	setLatencyHistory: (val) =>
		set(
			(state: any) =>
				({
					latencyHistory:
						typeof val === "function" ? val(state.latencyHistory) : val,
				}) as any,
		),
	cacheHits: 14,
	setCacheHits: (val) =>
		set(
			(state: any) =>
				({
					cacheHits: typeof val === "function" ? val(state.cacheHits) : val,
				}) as any,
		),
	cacheMisses: 2,
	setCacheMisses: (val) =>
		set(
			(state: any) =>
				({
					cacheMisses: typeof val === "function" ? val(state.cacheMisses) : val,
				}) as any,
		),
	circuitBreakerTripped: false,
	setCircuitBreakerTripped: (val) =>
		set(
			(state: any) =>
				({
					circuitBreakerTripped:
						typeof val === "function" ? val(state.circuitBreakerTripped) : val,
				}) as any,
		),
	rateLimitActive: false,
	setRateLimitActive: (val) =>
		set(
			(state: any) =>
				({
					rateLimitActive:
						typeof val === "function" ? val(state.rateLimitActive) : val,
				}) as any,
		),
	kafkaLogs: [
		{
			id: "L0",
			time: new Date().toLocaleTimeString(),
			event: "KAFKA EVENT CONSOLE ACTIVE",
			source: "system",
			meta: "Connected to broker-1:9092. Subscribed to telemetry.events",
		},
	],
	setKafkaLogs: (val) =>
		set(
			(state: any) =>
				({
					kafkaLogs: typeof val === "function" ? val(state.kafkaLogs) : val,
				}) as any,
		),
	ledger: [
		{
			id: "TX0",
			time: new Date().toLocaleTimeString(),
			type: "system",
			ref: "SYS-INIT",
			desc: "Simulated payment processing backend initialized",
			debit: 0,
			credit: 0,
		},
	],
	setLedger: (val) =>
		set(
			(state: any) =>
				({
					ledger: typeof val === "function" ? val(state.ledger) : val,
				}) as any,
		),
	certModalOpen: false,
	setCertModalOpen: (val) =>
		set(
			(state: any) =>
				({
					certModalOpen:
						typeof val === "function" ? val(state.certModalOpen) : val,
				}) as any,
		),
});

export const useStore = create<State>()((...a) => ({
	...createUserSlice(...a),
	...createProductSlice(...a),
	...createOrderSlice(...a),
	...createAppSlice(...a),
	...createAuthSlice(...a),
	...createSystemSlice(...a),
}));
