import { useState } from "react";
import RiderApp from "./components/RiderApp";
import "./index.css";

export default function App() {
	const [riderWallet, setRiderWallet] = useState(15.0);
	const [riderTrustScore, setRiderTrustScore] = useState(100);
	const [riderOnboardStatus, setRiderOnboardStatus] = useState("active"); // default to active for quick view
	const [activeOrder, setActiveOrder] = useState({
		id: 1234,
		items: "1x Banana, 2x Milk",
		total: 12.99,
		progress: 30,
		slaRemaining: 150,
		perishable: true,
		temperature: 5.5,
	});

	return (
		<div
			style={{
				padding: "2rem",
				background: "#0b0f19",
				minHeight: "100vh",
				color: "#fff",
			}}
		>
			<h2>Rider MFE (Standalone Dev Preview)</h2>
			<RiderApp
				riderWallet={riderWallet}
				riderTrustScore={riderTrustScore}
				riderOnboardStatus={riderOnboardStatus}
				setRiderOnboardStatus={setRiderOnboardStatus}
				activeOrder={activeOrder}
				generateCertificate={(role) => alert(`Mock Certificate for: ${role}`)}
				coldChainBreakdownActive={false}
				handleInjectDryIce={() =>
					setActiveOrder((prev) => ({ ...prev, temperature: 4.0 }))
				}
				handleApplyOnboard={() => alert("Mock onboard applied")}
				handleCompleteDelivery={(order, hash) =>
					alert(
						`Mock delivery complete for order #${order.id} with hash: ${hash}`,
					)
				}
				logKafka={(src, evt, m) =>
					console.log(`[MOCK LOG] ${src} | ${evt} | ${m}`)
				}
			/>
		</div>
	);
}
