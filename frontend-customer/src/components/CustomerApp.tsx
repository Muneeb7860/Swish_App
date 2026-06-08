import * as Lucide from "lucide-react";
import type React from "react";
import { useState } from "react";
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
}

export default function CustomerApp({
	products,
	cart,
	setCart,
	customerWallet,
	setCustomerWallet,
	customerPoints,
	setCustomerPoints,
	customerTab,
	setCustomerTab,
	profileSubTab,
	setProfileSubTab,
	savedAddresses = [],
	savedCards = [],
	favorites = [],
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
	activeOrder,
	generateCertificate,
}: CustomerAppProps) {
	const [searchQuery, setSearchQuery] = useState("");
	const [showSubstitutionModal, setShowSubstitutionModal] = useState(false);
	const [subTargetItem, setSubTargetItem] = useState<Product | null>(null);

	// AI Shopping Planner Integration
	const { streamData, isStreaming, error, startStream } = useAiStream();
	const [aiPrompt, setAiPrompt] = useState("");
	const [aiPanelOpen, setAiPanelOpen] = useState(false);

	// Helper to parse matching products from streamed response (case-insensitive hybrid)
	const getMatchingProducts = (): Product[] => {
		if (!streamData) return [];
		const text = streamData.toLowerCase();
		return products.filter((product) => {
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
	const filteredProducts = products.filter(
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
		// Show substitution modal for low-stock items (only once per item)
		if (product.stock < 5 && !showSubstitutionModal) {
			setSubTargetItem(product);
			setShowSubstitutionModal(true);
		}
		setCart((prev: CartItem[]) => {
			const existing = prev.find((item) => item.id === product.id);
			const currentQty = existing?.qty ?? 0;

			// Guard: never allow cart quantity to exceed available stock
			if (currentQty >= product.stock) {
				return prev; // silently cap — stock counter in UI already shows the limit
			}

			if (existing) {
				return prev.map((item) =>
					item.id === product.id ? { ...item, qty: item.qty + 1 } : item,
				);
			}
			return [...prev, { ...product, qty: 1 }];
		});
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
	const appliedDiscount = 0.0; // Simplified
	const totalCost = Math.max(
		0,
		cartSubtotal + deliveryFee + tipAmount - esgRebate - appliedDiscount,
	);

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
				<div
					style={{
						display: "flex",
						gap: "0.5rem",
						background: "rgba(255,255,255,0.02)",
						padding: "0.25rem",
						borderRadius: "10px",
						width: "fit-content",
						border: "1px solid var(--border-color)",
						marginBottom: "1rem",
					}}
				>
					<button
						className="btn-secondary-glow"
						style={{
							background:
								customerTab === "catalog"
									? "rgba(255,255,255,0.08)"
									: "transparent",
							border: "none",
							fontSize: "0.8rem",
							padding: "0.4rem 1rem",
							cursor: "pointer",
						}}
						onClick={() => setCustomerTab("catalog")}
					>
						Browse Store Catalog
					</button>
					<button
						className="btn-secondary-glow"
						style={{
							background:
								customerTab === "profile"
									? "rgba(255,255,255,0.08)"
									: "transparent",
							border: "none",
							fontSize: "0.8rem",
							padding: "0.4rem 1rem",
							cursor: "pointer",
						}}
						onClick={() => setCustomerTab("profile")}
					>
						My Profile Hub
					</button>
				</div>

				{customerTab === "catalog" ? (
					<div>
						{/* Search and Promotions */}
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
									placeholder="Search organic grocery, fresh dairy, bakery..."
									value={searchQuery}
									onChange={(e) => setSearchQuery(e.target.value)}
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
							<div
								style={{
									display: "flex",
									justifyContent: "space-between",
									alignItems: "center",
									cursor: "pointer",
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
								<button
									className="btn-secondary-glow"
									style={{
										padding: "0.2rem 0.5rem",
										fontSize: "0.7rem",
										border: "none",
										cursor: "pointer",
									}}
								>
									{aiPanelOpen ? "Hide Assistant" : "Show Assistant"}
								</button>
							</div>

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
											}}
										>
											{streamData}
											{isStreaming && (
												<span
													className="animate-ping"
													style={{
														color: "var(--color-customer)",
														fontWeight: "bold",
														marginLeft: "2px",
													}}
												>
													▋
												</span>
											)}
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
													className="btn-primary-glow"
													style={{
														background: "var(--color-customer)",
														color: "white",
														border: "none",
														padding: "0.25rem 0.6rem",
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
													<div
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
														}}
														onClick={() => addToCart(p)}
													>
														<span>{p.emoji}</span>
														<strong>{p.name}</strong>
														<span style={{ color: "var(--color-customer)" }}>
															${p.price.toFixed(2)}
														</span>
													</div>
												))}
											</div>
										</div>
									)}
								</div>
							)}
						</div>

						{/* Product Shelf Grid */}
						<div className="product-shelf-grid">
							{filteredProducts.map((p) => (
								<div key={p.id} className="product-card">
									{p.perishable && (
										<span className="badge-perishable">
											Cold Chain Perishable
										</span>
									)}
									<div className="product-emoji-row">
										<span style={{ fontSize: "2rem" }}>{p.emoji}</span>
										<button
											key={cart.find((item) => item.id === p.id)?.qty || 0}
											className={`add-cart-btn ${cart.find((item) => item.id === p.id) ? "scale-pop-animation" : ""}`}
											onClick={() => addToCart(p)}
										>
											<Lucide.Plus size={16} />
										</button>
									</div>
									<h4 style={{ fontWeight: 700, margin: "0.5rem 0 0.2rem 0" }}>
										{p.name}
									</h4>
									<span
										style={{ fontSize: "0.7rem", color: "var(--text-muted)" }}
									>
										{p.category}
									</span>
									<div
										className="product-price-row"
										style={{
											display: "flex",
											justifyContent: "space-between",
											alignItems: "center",
											marginTop: "0.75rem",
										}}
									>
										<span
											style={{
												fontWeight: 800,
												color: "var(--color-customer)",
											}}
										>
											${p.price.toFixed(2)}
										</span>
										{p.stock < 5 ? (
											<span
												className="glowing-stock-counter"
												style={{ fontSize: "0.75rem", fontWeight: "bold" }}
											>
												🔥 Only {p.stock} left!
											</span>
										) : (
											<span
												style={{
													fontSize: "0.65rem",
													color: "var(--text-muted)",
												}}
											>
												Stock: {p.stock} units
											</span>
										)}
									</div>
								</div>
							))}
						</div>
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
								className={`profile-nav-item ${profileSubTab === "vip" ? "active" : ""}`}
								onClick={() => setProfileSubTab("vip")}
							>
								<Lucide.Crown size={14} /> VIP Club Membership
							</button>
							<button
								className={`profile-nav-item ${profileSubTab === "orders" ? "active" : ""}`}
								onClick={() => setProfileSubTab("orders")}
							>
								<Lucide.ClipboardList size={14} /> Orders History & Purge
							</button>
							<button
								className={`profile-nav-item ${profileSubTab === "vouchers" ? "active" : ""}`}
								onClick={() => setProfileSubTab("vouchers")}
							>
								<Lucide.Tag size={14} /> My Discount Vouchers
							</button>
							<button
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
									className="glass-card"
									style={{
										padding: "1rem",
										borderLeft: "4px solid var(--color-customer)",
									}}
								>
									<h3
										style={{
											display: "flex",
											alignItems: "center",
											gap: "0.5rem",
											fontWeight: 800,
										}}
									>
										<Lucide.Crown size={20} style={{ color: "gold" }} />
										Swiss Q-Commerce VIP Hub
									</h3>
									<div
										style={{
											display: "flex",
											flexDirection: "column",
											gap: "0.5rem",
											marginTop: "1rem",
										}}
									>
										<div>
											<strong>Trust Shield rating:</strong>{" "}
											<span
												style={{
													color:
														customerTrustScore >= 85
															? "var(--color-customer)"
															: "var(--color-admin)",
												}}
											>
												{customerTrustScore}/100
											</span>
										</div>
										{gdprTokenProbation && (
											<div
												style={{
													color: "var(--color-admin)",
													fontSize: "0.75rem",
												}}
											>
												⚠️ Your account is currently under GDPR probation. Please
												complete 3 successful orders to restore score.
											</div>
										)}
										<div>
											<strong>VIP Member status:</strong>{" "}
											{vipMember
												? "Active (Free delivery on orders > $15)"
												: "Inactive"}
										</div>
										<div>
											<strong>Loyalty Points balance:</strong> {customerPoints}{" "}
											Points
										</div>
									</div>
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
														<strong>Order #{historyItem.id}</strong> ({historyItem.date})
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
								<div
									style={{
										display: "flex",
										flexDirection: "column",
										gap: "0.75rem",
									}}
								>
									{vouchers.map((v) => (
										<div
											key={v.code}
											className="glass-card"
											style={{
												padding: "0.75rem",
												borderLeft: "3px solid var(--color-customer)",
											}}
										>
											<strong>{v.code}</strong> (Flat ${v.value.toFixed(2)} Off)
											<p
												style={{
													fontSize: "0.7rem",
													color: "var(--text-muted)",
													margin: "0.2rem 0 0 0",
												}}
											>
												{v.desc}
											</p>
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
								style={{
									display: "flex",
									alignItems: "center",
									gap: "0.5rem",
									marginBottom: "0.75rem",
								}}
							>
								<input
									type="checkbox"
									id="esg-bags"
									checked={esgCheckbox}
									onChange={(e) => setEsgCheckbox(e.target.checked)}
									style={{
										accentColor: "var(--color-customer)",
										cursor: "pointer",
									}}
								/>
								<label
									htmlFor="esg-bags"
									style={{
										fontSize: "0.7rem",
										color: "var(--text-secondary)",
										cursor: "pointer",
									}}
								>
									🌳 Return bags for $0.50 cash rebate offset
								</label>
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
			{showSubstitutionModal && subTargetItem && (
				<div className="cert-modal-overlay" style={{ zIndex: 2000 }}>
					<div
						className="cert-modal-content"
						style={{
							maxWidth: "420px",
							padding: "1.5rem",
							textAlign: "center",
						}}
					>
						<h4
							style={{
								color: "var(--color-customer)",
								fontWeight: 800,
								margin: "0 0 0.5rem 0",
								display: "flex",
								alignItems: "center",
								gap: "0.5rem",
								justifyContent: "center",
							}}
						>
							<Lucide.Sparkles size={18} />
							Stock Alert: Running Low!
						</h4>
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
							const substitute = products.find((p) => p.id === subId);
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
																? { ...item, qty: item.qty + targetInCart.qty }
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

						<div style={{ display: "flex", gap: "0.5rem" }}>
							<button
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
						</div>
					</div>
				</div>
			)}
		</div>
	);
}
