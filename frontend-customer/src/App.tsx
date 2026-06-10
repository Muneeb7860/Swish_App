import { useState } from "react";
import AuthGate from "./components/AuthGate";
import CustomerApp from "./components/CustomerApp";
import "./index.css";

const MOCK_PRODUCTS = [
	{
		id: "p1",
		name: "[MOCK] Fresh Milk",
		price: 3.49,
		stock: 12,
		category: "Dairy & Eggs",
		emoji: "🥛",
		perishable: true,
	},
	{
		id: "p2",
		name: "[MOCK] Bananas (1kg)",
		price: 1.99,
		stock: 18,
		category: "Fruits & Veggies",
		emoji: "🍌",
		perishable: false,
	},
];

interface AuthSession {
	token: string;
	sessionId: string;
}

export default function App() {
	const [session, setSession] = useState<AuthSession | null>(null);
	const [cart, setCart] = useState([]);
	const [customerWallet, setCustomerWallet] = useState(100.0);
	const [customerPoints, setCustomerPoints] = useState(45);
	const [customerTab, setCustomerTab] = useState("catalog");
	const [profileSubTab, setProfileSubTab] = useState("vip");
	const [esgCheckbox, setEsgCheckbox] = useState(false);
	const [tipAmount, setTipAmount] = useState(0);

	const handleCheckout = (method) => {
		alert(`Mock checkout triggered via: ${method}`);
		setCart([]);
	};

	if (!session) {
		return <AuthGate onAuth={setSession} />;
	}

	return (
		<div
			style={{
				padding: "2rem",
				background: "#0b0f19",
				minHeight: "100vh",
				color: "#fff",
			}}
		>
			<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
				<h2 style={{ margin: 0 }}>Customer MFE (Standalone Dev Preview)</h2>
				<button
					type="button"
					onClick={async () => {
						await fetch(`${import.meta.env.VITE_API_URL ?? "http://localhost:8080"}/api/v1/auth/logout`, {
							method: "POST",
							headers: { "X-Session-Id": session.sessionId },
						}).catch(() => {});
						setSession(null);
					}}
					style={{
						padding: "0.4rem 1rem",
						borderRadius: 8,
						border: "1px solid #2d3754",
						background: "transparent",
						color: "#8b9abf",
						cursor: "pointer",
						fontSize: "0.85rem",
					}}
				>
					Log out
				</button>
			</div>
			<CustomerApp
				{...({} as any)}
				products={MOCK_PRODUCTS}
				cart={cart}
				setCart={setCart}
				customerWallet={customerWallet}
				setCustomerWallet={setCustomerWallet}
				customerPoints={customerPoints}
				setCustomerPoints={setCustomerPoints}
				customerTab={customerTab}
				setCustomerTab={setCustomerTab}
				profileSubTab={profileSubTab}
				setProfileSubTab={setProfileSubTab}
				savedAddresses={[]}
				savedCards={[]}
				favorites={[]}
				vipMember={true}
				vouchers={[]}
				customerTrustScore={100}
				gdprTokenProbation={false}
				handleGdprPurge={() => alert("Mock purge")}
				orderHistory={[]}
				esgCheckbox={esgCheckbox}
				setEsgCheckbox={setEsgCheckbox}
				tipAmount={tipAmount}
				setTipAmount={setTipAmount}
				handleCheckout={handleCheckout}
				activeOrder={null}
				generateCertificate={(role) => alert(`Mock Certificate for: ${role}`)}
			/>
		</div>
	);
}
