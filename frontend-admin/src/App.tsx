import { useState } from "react";
import AdminPanel from "./components/AdminPanel";
import AdminLogin from "./components/AdminLogin";
import BusinessApp from "./components/BusinessApp";
import InventoryApp from "./components/InventoryApp";
import SystemEngineRoom from "./components/SystemEngineRoom";
import { useHitlConsole } from "./hooks/useHitlConsole";
import "./index.css";

export default function App() {
	const [tab, setTab] = useState("admin"); // admin, business, inventory, system
	const hitl = useHitlConsole();
	const [products, setProducts] = useState([
		{
			id: "p1",
			name: "Fresh Milk",
			price: 3.49,
			stock: 12,
			stockEast: 15,
			category: "Dairy & Eggs",
			emoji: "🥛",
			perishable: true,
		},
		{
			id: "p2",
			name: "Bananas",
			price: 1.99,
			stock: 18,
			stockEast: 20,
			category: "Fruits & Veggies",
			emoji: "🍌",
			perishable: false,
		},
	]);

	return (
		<div
			style={{
				padding: "2rem",
				background: "#0b0f19",
				minHeight: "100vh",
				color: "#fff",
			}}
		>
			<h2>Admin MFE (Standalone Dev Preview)</h2>

			<div style={{ display: "flex", gap: "0.5rem", marginBottom: "1.5rem" }}>
				{["admin", "business", "inventory", "system"].map((t) => (
					<button
						key={t}
						onClick={() => setTab(t)}
						style={{
							padding: "0.5rem 1rem",
							background: tab === t ? "#3b82f6" : "rgba(255,255,255,0.05)",
							border: "1px solid rgba(255,255,255,0.1)",
							color: "#fff",
							borderRadius: "4px",
							cursor: "pointer",
						}}
					>
						{t.toUpperCase()}
					</button>
				))}
			</div>

			{tab === "admin" && !hitl.authed && (
				<AdminLogin onLogin={hitl.login} />
			)}

			{tab === "admin" && hitl.authed && (
				<>
					<div
						style={{
							display: "flex",
							alignItems: "center",
							gap: "0.75rem",
							marginBottom: "1rem",
							fontSize: "0.78rem",
						}}
					>
						<span style={{ color: "#34d399" }}>● Supervisor session active</span>
						<button
							onClick={() => hitl.refresh()}
							style={{
								padding: "0.3rem 0.7rem",
								background: "rgba(255,255,255,0.06)",
								border: "1px solid rgba(255,255,255,0.15)",
								color: "#fff",
								borderRadius: 4,
								cursor: "pointer",
							}}
						>
							↻ Refresh queue
						</button>
						<button
							onClick={hitl.logout}
							style={{
								padding: "0.3rem 0.7rem",
								background: "rgba(255,255,255,0.06)",
								border: "1px solid rgba(255,255,255,0.15)",
								color: "#fff",
								borderRadius: 4,
								cursor: "pointer",
							}}
						>
							Sign out
						</button>
						{hitl.error && <span style={{ color: "#f87171" }}>⚠ {hitl.error}</span>}
					</div>

					<AdminPanel
						coldChainBreakdownActive={false}
						setColdChainBreakdownActive={() => {}}
						wholesalerOutageActive={false}
						setWholesalerOutageActive={() => {}}
						paymentOutageActive={false}
						setPaymentOutageActive={() => {}}
						redisCrashActive={false}
						setRedisCrashActive={() => {}}
						dbLatencyActive={false}
						setDbLatencyActive={() => {}}
						riderTrafficActive={false}
						setRiderTrafficActive={() => {}}
						simulateTelemetryFraud={false}
						setSimulateTelemetryFraud={() => {}}
						onboardingQueue={[]}
						handleApproveOnboard={() => {}}
						hitlQueue={hitl.queue}
						handleReleaseHitl={hitl.approve}
						handleVoidHitl={hitl.voidTicket}
						handleAdjustHitl={hitl.adjust}
						hitlLoading={hitl.loading}
					/>
				</>
			)}

			{tab === "business" && (
				<BusinessApp
					products={products}
					merchantWallet={1542.8}
					ledger={[]}
					trustLogs={[]}
					customerTrustScore={100}
					riderTrustScore={100}
					pickerTrustScore={100}
					wholesalerTrustScore={100}
					centralCapacity={120}
					eastCapacity={120}
					centralScalingCount={0}
					eastScalingCount={0}
					handleScaleCapacity={() => {}}
					downloadRegulatoryReport={() => alert("Mock Download")}
				/>
			)}

			{tab === "inventory" && (
				<InventoryApp
					products={products}
					setProducts={setProducts}
					pickerTrustScore={100}
					pickerBadge="Standard"
					activeOrder={null}
					activeStockTransfers={[]}
					handleBalanceStores={() => alert("Mock balance")}
					handlePickerCheckItem={() => {}}
					handlePickerHandover={() => {}}
					handleDeployBackupPicker={() => {}}
					pickingBacklogQueue={0}
					activePickingCongested={false}
				/>
			)}

			{tab === "system" && (
				<SystemEngineRoom
					rateLimitActive={false}
					dbLatencyActive={false}
					redisCrashActive={false}
					paymentOutageActive={false}
					riderTrafficActive={false}
					circuitBreakerTripped={false}
					activeProfile={{
						logLevel: "debug",
						dbLatencyDefault: 4,
						mfaRequired: false,
					}}
					oltpWriteLatency={4}
					olapSyncTimer={0}
					jwtFlash={false}
					vaultTimer={15}
					latencyHistory={[4, 5, 4, 4]}
					cacheHits={10}
					cacheMisses={1}
					kafkaLogs={[]}
				/>
			)}
		</div>
	);
}
