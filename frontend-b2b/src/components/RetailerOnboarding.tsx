import type React from "react";

interface RetailerOnboardingProps {
	// Registration form state
	retailerName: string;
	onRetailerNameChange: (value: string) => void;
	retailerEmail: string;
	onRetailerEmailChange: (value: string) => void;
	retailerStoreId: string;
	onRetailerStoreIdChange: (value: string) => void;
	billingTier: "BASIC" | "PRO" | "ENTERPRISE";
	onBillingTierChange: (value: "BASIC" | "PRO" | "ENTERPRISE") => void;

	// State
	currentRetailer: any;
	revealedApiKey: string | null;
	isRegistering: boolean;

	// Actions
	onRegister: (e: React.FormEvent) => void;
	onApproveGate: (gate: "ops" | "compliance" | "admin") => void;
	onReset: () => void;

	// Clipboard
	copiedIndex: number | string | null;
	onCopy: (text: string, index: number | string) => void;
}

/**
 * Retailer self-service onboarding panel with registration form,
 * 3-gate approval system, and API key reveal.
 */
const RetailerOnboarding: React.FC<RetailerOnboardingProps> = ({
	retailerName,
	onRetailerNameChange,
	retailerEmail,
	onRetailerEmailChange,
	retailerStoreId,
	onRetailerStoreIdChange,
	billingTier,
	onBillingTierChange,
	currentRetailer,
	revealedApiKey,
	isRegistering,
	onRegister,
	onApproveGate,
	onReset,
	copiedIndex,
	onCopy,
}) => {
	return (
		<div className="upgrade-glow-card p-6 flex flex-col gap-4 animate-fade-in">
			{/* Header */}
			<div
				className="flex justify-between items-center pb-3"
				style={{ borderBottom: "1px solid var(--border-default)" }}
			>
				<h3
					className="m-0 flex items-center gap-2"
					style={{ fontSize: "var(--text-lg)", fontWeight: 700 }}
				>
					🏢 Retailer Tenant Onboarding Portal
				</h3>
				{currentRetailer && (
					<RetailerStatusBadge status={currentRetailer.status} />
				)}
			</div>

			{!currentRetailer ? (
				/* Registration Form */
				<form onSubmit={onRegister} className="flex flex-col gap-4">
					<p
						className="text-xs mt-0 leading-relaxed"
						style={{ color: "var(--text-muted)" }}
					>
						Register your retail convenience-store network hub with Swish OS.
						Self-signup initiates a PENDING onboarding application that must
						pass 3 ops-gated compliance validations before activation.
					</p>

					<div className="grid grid-cols-1 md:grid-cols-2 gap-4">
						<div className="flex flex-col gap-1">
							<label
								htmlFor="retailer-name-input"
								className="text-xs font-semibold"
								style={{ color: "var(--text-muted)" }}
							>
								Retailer Entity Name
							</label>
							<input
								id="retailer-name-input"
								type="text"
								required
								placeholder="e.g. Valora Kiosk HB"
								className="input-field"
								value={retailerName}
								onChange={(e) => onRetailerNameChange(e.target.value)}
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label
								htmlFor="retailer-email-input"
								className="text-xs font-semibold"
								style={{ color: "var(--text-muted)" }}
							>
								Corporate Billing Email
							</label>
							<input
								id="retailer-email-input"
								type="email"
								required
								placeholder="ops@valora.ch"
								className="input-field"
								value={retailerEmail}
								onChange={(e) => onRetailerEmailChange(e.target.value)}
							/>
						</div>
					</div>

					<div className="grid grid-cols-1 md:grid-cols-2 gap-4">
						<div className="flex flex-col gap-1">
							<label
								htmlFor="retailer-store-id-input"
								className="text-xs font-semibold"
								style={{ color: "var(--text-muted)" }}
							>
								Assigned Store Hub ID
							</label>
							<input
								id="retailer-store-id-input"
								type="text"
								required
								placeholder="store-valora-01"
								className="input-field"
								value={retailerStoreId}
								onChange={(e) => onRetailerStoreIdChange(e.target.value)}
							/>
						</div>
						<div className="flex flex-col gap-1">
							<label
								htmlFor="retailer-tier-select"
								className="text-xs font-semibold"
								style={{ color: "var(--text-muted)" }}
							>
								Select Subscription Tier
							</label>
							<select
								id="retailer-tier-select"
								className="select-field"
								value={billingTier}
								onChange={(e) => onBillingTierChange(e.target.value as any)}
							>
								<option value="BASIC">
									BASIC ($1,000/mo - IoT Telemetry only)
								</option>
								<option value="PRO">
									PRO ($1,500/mo - Telemetry + Procurement)
								</option>
								<option value="ENTERPRISE">
									ENTERPRISE (SLA Guarantees + Audits)
								</option>
							</select>
						</div>
					</div>

					<button
						type="submit"
						disabled={isRegistering}
						className="w-full mt-2 btn-primary"
						style={{ padding: "var(--space-3) var(--space-5)" }}
					>
						{isRegistering ? (
							<>
								<span className="spinner" /> Registering Tenant...
							</>
						) : (
							"Register Retailer (Self-Service)"
						)}
					</button>
				</form>
			) : (
				/* Retailer Details + Gates */
				<div className="flex flex-col gap-4 text-sm animate-scale-in">
					{/* Retailer Info Card */}
					<div
						className="rounded-xl p-4 flex flex-col gap-2"
						style={{
							background: "var(--bg-glass)",
							border: "1px solid var(--border-default)",
						}}
					>
						<InfoRow
							label="Retailer tenantId"
							value={currentRetailer.retailerId}
							mono
						/>
						<InfoRow label="Corporate Name" value={currentRetailer.name} />
						<InfoRow
							label="Provisioned Store Hub"
							value={currentRetailer.storeId}
							mono
						/>
						<InfoRow
							label="Subscription Tier"
							value={currentRetailer.tier}
							accent
						/>
						{currentRetailer.billingAccountId && (
							<div
								style={{
									borderTop: "1px solid var(--border-subtle)",
									paddingTop: "var(--space-2)",
									marginTop: "var(--space-1)",
								}}
							>
								<InfoRow
									label="Billing Account ID"
									value={currentRetailer.billingAccountId}
									mono
								/>
							</div>
						)}
					</div>

					{/* 3-Gate Approval */}
					<div
						className="rounded-xl p-4 flex flex-col gap-3"
						style={{ border: "1px solid var(--border-default)" }}
					>
						<h4
							className="m-0"
							style={{
								fontSize: "var(--text-xs)",
								fontWeight: 700,
								letterSpacing: "var(--tracking-wider)",
							}}
						>
							ADMINISTRATIVE ONBOARDING GATES
						</h4>
						<p
							className="m-0 leading-relaxed"
							style={{ fontSize: "10px", color: "var(--text-muted)" }}
						>
							In accordance with corporate governance protocols, new retailers
							are reviewed across three validation checkpoints. Activating the
							final Admin Gate activates database records and triggers billing
							accounts.
						</p>

						<div className="grid grid-cols-1 sm:grid-cols-3 gap-2 mt-1">
							<GateCard
								name="Ops Vetting"
								approved={currentRetailer.approvalOps}
								onApprove={() => onApproveGate("ops")}
							/>
							<GateCard
								name="Compliance"
								approved={currentRetailer.approvalCompliance}
								onApprove={() => onApproveGate("compliance")}
							/>
							<GateCard
								name="Admin Gate"
								approved={currentRetailer.approvalAdmin}
								onApprove={() => onApproveGate("admin")}
							/>
						</div>

						{/* API Key Reveal */}
						{revealedApiKey && (
							<div className="api-key-card mt-2 flex flex-col gap-2 animate-slide-up">
								<span
									className="text-xs font-bold flex items-center gap-1.5"
									style={{ color: "var(--accent-hover)" }}
								>
									🔑 Secure API Authorization Key
								</span>
								<p
									className="m-0 leading-relaxed"
									style={{ fontSize: "10px", color: "var(--text-muted)" }}
								>
									This key will be hashed downstream. Record it securely to
									establish connection adapters.
								</p>
								<div
									className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 mt-1 px-3 py-2.5 rounded-lg text-xs font-mono"
									style={{
										background: "var(--bg-elevated)",
										border: "1px solid var(--border-default)",
										color: "var(--accent-hover)",
									}}
								>
									<code
										className="break-all flex-1"
										style={{ userSelect: "all" }}
									>
										{revealedApiKey}
									</code>
									<button
										className="btn-ghost whitespace-nowrap self-start sm:self-auto"
										style={{ fontSize: "10px" }}
										onClick={() => onCopy(revealedApiKey, "api-key")}
										type="button"
									>
										{copiedIndex === "api-key" ? "Copied!" : "Copy Key"}
									</button>
								</div>
							</div>
						)}
					</div>

					<button
						className="text-xs self-end underline"
						style={{
							color: "var(--text-muted)",
							background: "none",
							border: "none",
							cursor: "pointer",
						}}
						onClick={onReset}
						type="button"
					>
						Reset & Onboard New Retailer
					</button>
				</div>
			)}
		</div>
	);
};

/* ── Sub-components ── */

function RetailerStatusBadge({ status }: { status: string }) {
	const isActive = status === "ACTIVE";
	const isPending = status === "PENDING";

	return (
		<span
			className="px-2 py-0.5 rounded font-bold"
			style={{
				fontSize: "10px",
				background: isActive
					? "var(--success-muted)"
					: isPending
						? "var(--warning-muted)"
						: "var(--bg-muted)",
				color: isActive
					? "var(--success)"
					: isPending
						? "var(--warning)"
						: "var(--text-muted)",
				border: `1px solid ${
					isActive
						? "rgba(16, 185, 129, 0.2)"
						: isPending
							? "rgba(245, 158, 11, 0.2)"
							: "var(--border-default)"
				}`,
			}}
		>
			{status}
		</span>
	);
}

function InfoRow({
	label,
	value,
	mono,
	accent,
}: {
	label: string;
	value: string;
	mono?: boolean;
	accent?: boolean;
}) {
	return (
		<div className="flex justify-between text-xs">
			<span style={{ color: "var(--text-muted)" }}>{label}:</span>
			<span
				className={mono ? "font-mono" : ""}
				style={{
					fontWeight: accent ? 600 : mono ? 500 : 500,
					color: accent ? "var(--accent-hover)" : "var(--text-primary)",
				}}
			>
				{value}
			</span>
		</div>
	);
}

function GateCard({
	name,
	approved,
	onApprove,
}: {
	name: string;
	approved: boolean;
	onApprove: () => void;
}) {
	return (
		<div
			className={`gate-card ${approved ? "gate-card--approved" : "gate-card--pending"}`}
		>
			<span
				className="text-xs font-bold block mb-1.5"
				style={{ color: approved ? "var(--success)" : "var(--text-muted)" }}
			>
				{name}
			</span>
			{approved ? (
				<span
					className="font-semibold"
					style={{ fontSize: "10px", color: "var(--success)" }}
				>
					Approved ✅
				</span>
			) : (
				<button
					className="btn-ghost"
					style={{
						fontSize: "10px",
						color: "var(--accent-hover)",
						borderColor: "rgba(99, 102, 241, 0.2)",
						background: "var(--accent-muted)",
					}}
					onClick={onApprove}
					type="button"
				>
					Approve
				</button>
			)}
		</div>
	);
}

export default RetailerOnboarding;
