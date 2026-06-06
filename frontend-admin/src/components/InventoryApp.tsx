import * as Lucide from "lucide-react";
import React, { useState } from "react";

export default function InventoryApp({
	products,
	setProducts,
	pickerTrustScore,
	pickerBadge,
	activeOrder,
	activeStockTransfers,
	handleBalanceStores,
	handlePickerCheckItem,
	handlePickerHandover,
	handleDeployBackupPicker,
	pickingBacklogQueue,
	activePickingCongested,
}) {
	const [checkedItems, setCheckedItems] = useState({});

	const handleToggleCheck = (itemId) => {
		setCheckedItems((prev) => {
			const next = { ...prev, [itemId]: !prev[itemId] };
			if (handlePickerCheckItem) {
				handlePickerCheckItem(itemId, next[itemId]);
			}
			return next;
		});
	};

	const getActiveOrderItems = () => {
		if (!activeOrder) return [];
		// Extract items from order description
		// E.g. "2x Organic Milk, 1x Bananas" -> [{ id: 'p1', name: 'Organic Milk', qty: 2 }]
		const rawItems = activeOrder.items.split(", ");
		return rawItems.map((item, idx) => {
			const parts = item.split("x ");
			const qty = parseInt(parts[0], 10) || 1;
			const name = parts[1] || item;
			return {
				id: `item-${idx}`,
				name,
				qty,
				checked: checkedItems[`item-${idx}`] || false,
			};
		});
	};

	const orderItems = getActiveOrderItems();
	const allItemsChecked =
		orderItems.length > 0 && orderItems.every((item) => item.checked);

	return (
		<div
			className="inventory-dashboard"
			style={{ display: "flex", gap: "1.25rem" }}
		>
			{/* Picker Operations Checklist */}
			<div
				style={{
					flex: 2,
					display: "flex",
					flexDirection: "column",
					gap: "1.25rem",
				}}
			>
				<div
					className="glass-card"
					style={{
						padding: "1rem",
						borderLeft: "3px solid var(--color-inventory)",
					}}
				>
					<div
						style={{
							display: "flex",
							justifyContent: "space-between",
							alignItems: "center",
						}}
					>
						<h3 style={{ fontWeight: 800 }}>Dark Store Picker Operations</h3>
						<div style={{ display: "flex", gap: "1rem", fontSize: "0.8rem" }}>
							<span>
								⭐ Picker Accuracy:{" "}
								<strong style={{ color: "var(--color-inventory)" }}>
									{pickerTrustScore}/100
								</strong>
							</span>
							<span>
								Speed Badge:{" "}
								<strong style={{ color: "var(--color-inventory)" }}>
									{pickerBadge}
								</strong>
							</span>
						</div>
					</div>
				</div>

				{/* Picking tasks */}
				{!activeOrder || activeOrder.status !== "picking" ? (
					<div
						className="glass-card"
						style={{
							padding: "2rem",
							textAlign: "center",
							color: "var(--text-muted)",
						}}
					>
						<Lucide.ClipboardCheck
							size={32}
							style={{ opacity: 0.3, marginBottom: "0.5rem" }}
						/>
						<span style={{ fontSize: "0.75rem" }}>
							Dark Store queue is currently clear. Awaiting checkout
							dispatches...
						</span>
					</div>
				) : (
					<div className="glass-card" style={{ padding: "1.25rem" }}>
						<div
							style={{
								display: "flex",
								justifyContent: "space-between",
								alignItems: "center",
								marginBottom: "0.75rem",
							}}
						>
							<h4 style={{ fontWeight: 700, color: "var(--color-inventory)" }}>
								ACTIVE PICKING JOB: Order #{activeOrder.id}
							</h4>
							{activePickingCongested && (
								<span
									className="warning-flag"
									style={{ animation: "pulse 1s infinite" }}
								>
									⚠️ QUEUE CONGESTED
								</span>
							)}
						</div>

						{/* Check list */}
						<div
							style={{
								display: "flex",
								flexDirection: "column",
								gap: "0.5rem",
								margin: "1rem 0",
							}}
						>
							{orderItems.map((item) => (
								<div
									key={item.id}
									style={{
										display: "flex",
										alignItems: "center",
										gap: "0.5rem",
										padding: "0.5rem",
										background: "rgba(255,255,255,0.01)",
										border: "1px solid var(--border-color)",
										borderRadius: "6px",
									}}
								>
									<input
										type="checkbox"
										id={item.id}
										checked={item.checked}
										onChange={() => handleToggleCheck(item.id)}
										style={{
											accentColor: "var(--color-inventory)",
											cursor: "pointer",
										}}
									/>
									<label
										htmlFor={item.id}
										style={{
											fontSize: "0.8rem",
											color: item.checked
												? "var(--text-muted)"
												: "var(--text-primary)",
											textDecoration: item.checked ? "line-through" : "none",
											cursor: "pointer",
										}}
									>
										{item.qty}x {item.name}
									</label>
								</div>
							))}
						</div>

						{allItemsChecked && (
							<button
								id="btn-picker-handover"
								className="btn-primary-glow"
								style={{
									background: "var(--color-inventory)",
									color: "#ffffff",
									border: "none",
									padding: "0.5rem 1rem",
									width: "100%",
									cursor: "pointer",
									fontWeight: "bold",
								}}
								onClick={() => {
									setCheckedItems({});
									handlePickerHandover();
								}}
							>
								Handover Cargo to Dispatch Rider
							</button>
						)}
					</div>
				)}

				{/* Store capacity and balancing */}
				<div className="glass-card" style={{ padding: "1rem" }}>
					<div
						style={{
							display: "flex",
							justifyContent: "space-between",
							alignItems: "center",
							marginBottom: "0.75rem",
						}}
					>
						<h4 style={{ fontWeight: 800 }}>Dark Store Inventory Imbalances</h4>
						<button
							className="btn-secondary-glow"
							style={{ fontSize: "0.75rem", cursor: "pointer" }}
							onClick={handleBalanceStores}
						>
							Balance Stores Inventory
						</button>
					</div>

					{activeStockTransfers.length > 0 && (
						<div
							style={{
								display: "flex",
								flexDirection: "column",
								gap: "0.5rem",
								marginTop: "1rem",
							}}
						>
							{activeStockTransfers.map((tr) => (
								<div
									key={tr.id}
									style={{
										background: "rgba(255,255,255,0.01)",
										border: "1px solid var(--border-color)",
										padding: "0.5rem",
										borderRadius: "6px",
									}}
								>
									<span
										style={{
											fontSize: "0.7rem",
											color: "var(--text-secondary)",
										}}
									>
										INTER-STORE TRANSFER TRUCK: {tr.itemName}
									</span>
									<div
										style={{
											background: "#020408",
											height: "6px",
											borderRadius: "99px",
											marginTop: "0.25rem",
											overflow: "hidden",
										}}
									>
										<div
											style={{
												background: "var(--color-inventory)",
												height: "100%",
												width: `${tr.progress}%`,
											}}
										/>
									</div>
								</div>
							))}
						</div>
					)}
				</div>
			</div>

			{/* Sidebar Controls */}
			<div
				style={{
					width: "280px",
					display: "flex",
					flexDirection: "column",
					gap: "1.25rem",
				}}
			>
				{/* Picking Queue Management */}
				<div className="glass-card" style={{ padding: "1rem" }}>
					<h4 style={{ fontWeight: 800, marginBottom: "0.5rem" }}>
						Picking Queue Manager
					</h4>
					<div
						style={{
							fontSize: "0.75rem",
							display: "flex",
							flexDirection: "column",
							gap: "0.5rem",
							margin: "0.75rem 0",
						}}
					>
						<div>
							Current Queue Backlog:{" "}
							<strong style={{ color: "var(--color-inventory)" }}>
								{pickingBacklogQueue} orders
							</strong>
						</div>
						<div>
							Picking Mode:{" "}
							<span
								style={{
									color: activePickingCongested
										? "var(--color-admin)"
										: "var(--color-customer)",
								}}
							>
								{activePickingCongested ? "CONGESTED (6.8s)" : "CLEAR (2.1s)"}
							</span>
						</div>
					</div>
					{activePickingCongested && (
						<button
							id="btn-deploy-backup"
							className="btn-primary-glow"
							style={{
								background: "var(--color-inventory)",
								color: "#ffffff",
								border: "none",
								padding: "0.5rem",
								width: "100%",
								fontSize: "0.75rem",
								cursor: "pointer",
								fontWeight: "bold",
							}}
							onClick={handleDeployBackupPicker}
						>
							Deploy Backup Picker ($10.00 Wage)
						</button>
					)}
				</div>
			</div>
		</div>
	);
}
