import type React from "react";

interface NotificationEnvelope {
	id: string;
	type: string;
	timestamp: string;
	recipientId: string;
	priority?: string;
	correlationId?: string;
	payload?: {
		ai_status?: string;
		amount?: number;
		orderId?: string;
		status?: string;
		[key: string]: any;
	};
}

interface NotificationInboxProps {
	notifications: NotificationEnvelope[];
	onClear: () => void;
	copiedIndex: number | string | null;
	onCopy: (text: string, index: number | string) => void;
}

/**
 * Live push notification inbox sidebar.
 * Displays real-time notification cards with priority styling and trace IDs.
 */
const NotificationInbox: React.FC<NotificationInboxProps> = ({
	notifications,
	onClear,
	copiedIndex,
	onCopy,
}) => {
	return (
		<div className="glass-panel max-h-[700px] flex flex-col">
			{/* Header */}
			<div className="flex justify-between items-center mb-1">
				<h3
					className="m-0"
					style={{ fontSize: "var(--text-lg)", fontWeight: 700 }}
				>
					Live Push Inbox
				</h3>
				<div className="flex items-center gap-2">
					<span
						className="text-white font-bold rounded-full px-2 py-0.5"
						style={{
							fontSize: "10px",
							background: "var(--accent)",
						}}
					>
						{notifications.length}
					</span>
					{notifications.length > 0 && (
						<button
							className="btn-ghost"
							style={{ fontSize: "var(--text-xs)" }}
							onClick={onClear}
							type="button"
						>
							Clear
						</button>
					)}
				</div>
			</div>

			<p
				className="text-xs mt-0 mb-4 leading-relaxed"
				style={{ color: "var(--text-secondary)" }}
			>
				Messages received in real-time from the notification-engine via Gateway.
			</p>

			{/* Notification List */}
			<div className="notifications-list flex-1 overflow-y-auto flex flex-col gap-3 pr-1 stagger-enter">
				{notifications.length === 0 ? (
					<div className="empty-state">
						<div className="bell-icon">🔔</div>
						<p
							className="font-semibold text-sm mb-1"
							style={{ color: "var(--text-primary)" }}
						>
							No active notifications
						</p>
						<span style={{ fontSize: "10px", color: "var(--text-muted)" }}>
							Waiting for real-time transactions to trigger events...
						</span>
					</div>
				) : (
					notifications.map((notif, index) => (
						<div
							key={notif.id || index}
							className={`notification-card flex flex-col gap-1.5 ${
								notif.priority === "HIGH"
									? "priority-high"
									: notif.priority === "MEDIUM"
										? "priority-medium"
										: "priority-low"
							}`}
						>
							{/* Type and Time */}
							<div className="card-row flex justify-between items-center">
								<span className="type-badge">{notif.type}</span>
								<span className="time-ago">
									{new Date(notif.timestamp).toLocaleTimeString()}
								</span>
							</div>

							{/* Description */}
							<p className="notif-desc m-0">
								{notif.type === "ORDER_EVALUATED" &&
									`AI evaluation complete: ${notif.payload?.ai_status}`}
								{notif.type === "PAYMENT_CONFIRMED" &&
									`Mock payment of $${(notif.payload?.amount || 1250000).toLocaleString()} confirmed`}
								{notif.type === "PAYMENT_FAILED" &&
									`Stripe reported payment failure`}
								{![
									"ORDER_EVALUATED",
									"PAYMENT_CONFIRMED",
									"PAYMENT_FAILED",
								].includes(notif.type) && JSON.stringify(notif.payload || {})}
							</p>

							{/* Trace Row */}
							<div className="trace-row text-[10px] flex items-center gap-1.5">
								<span className="trace-label">trace:</span>
								<button
									type="button"
									className="trace-id"
									onClick={() => onCopy(notif.correlationId || "none", index)}
									style={{
										background: "none",
										border: "none",
										padding: 0,
										font: "inherit",
										color: "inherit",
										cursor: "pointer",
										textAlign: "left",
									}}
								>
									{copiedIndex === index
										? "Copied!"
										: `${(notif.correlationId || "none").substring(0, 15)}...`}
								</button>
							</div>
						</div>
					))
				)}
			</div>
		</div>
	);
};

export default NotificationInbox;
