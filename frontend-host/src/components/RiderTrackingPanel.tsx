import * as Lucide from "lucide-react";
import React, { useEffect, useState } from "react";

/**
 * Enterprise Blueprint: Live Rider Tracking Panel
 *
 * Renders a real-time GPS coordinate display with animated rider position,
 * temperature gauge, and delivery progress. Subscribes to the `riderCoords`
 * state populated by the SSE EventSource in the parent App.
 *
 * Visible only when activeOrder.status === 'transit'.
 */
export default function RiderTrackingPanel({
	activeOrder,
	riderCoords,
	onInjectDryIce,
}: any) {
	const [tickFlash, setTickFlash] = useState(false);
	const [prevCoords, setPrevCoords] = useState<any>(null);
	const [tempHistory, setTempHistory] = useState<
		{ temp: number; time: number }[]
	>([]);
	const [injecting, setInjecting] = useState(false);

	const handleActionClick = async () => {
		setInjecting(true);
		await onInjectDryIce(activeOrder.id);
		setInjecting(false);
	};

	// Reset history on new order transit boot
	useEffect(() => {
		if (activeOrder?.status === "transit") {
			setTempHistory([
				{ temp: activeOrder.temperature ?? 4.0, time: Date.now() },
			]);
		}
	}, [activeOrder?.id, activeOrder?.status]);

	// Flash animation and history tracking on every new tick
	useEffect(() => {
		if (riderCoords) {
			setPrevCoords(riderCoords);
			setTickFlash(true);
			setTempHistory((prev) => {
				const val = riderCoords.temperature ?? activeOrder?.temperature ?? 4.0;
				const next = [...prev, { temp: val, time: Date.now() }];
				return next.slice(-6); // Keep last 6 ticks
			});
			const timer = setTimeout(() => setTickFlash(false), 400);
			return () => clearTimeout(timer);
		}
	}, [riderCoords?.lat, riderCoords?.lng]);

	if (!activeOrder || activeOrder.status !== "transit") return null;

	const coords = riderCoords || prevCoords;
	const progress = activeOrder.progress || 0;
	const temperature = activeOrder.temperature ?? coords?.temperature ?? 0;
	const tempColor =
		temperature > 10 ? "#ef4444" : temperature > 7 ? "#f59e0b" : "#10b981";
	const tempLabel =
		temperature > 10 ? "CRITICAL" : temperature > 7 ? "WARNING" : "SAFE";

	// SVG mini-map: simple route visualization
	const riderX = 30 + (progress / 100) * 240;
	const riderY = 50 + Math.sin((progress / 100) * Math.PI) * -15;

	const getTrendPath = () => {
		if (tempHistory.length < 2) return "";
		const minVal = 0;
		const maxVal = 15;
		const points = tempHistory.map((h, i) => {
			const x = (i / (tempHistory.length - 1)) * 80 + 10;
			const normalizedY = (h.temp - minVal) / (maxVal - minVal);
			const y = 35 - normalizedY * 25; // Scale to 10-35 range
			return `${x},${y}`;
		});
		return `M ${points.join(" L ")}`;
	};

	const trendPath = getTrendPath();
	const fillPath = trendPath
		? `${trendPath} L ${((tempHistory.length - 1) / (tempHistory.length - 1)) * 80 + 10},35 L 10,35 Z`
		: "";

	return (
		<div
			className="glow-card"
			data-glow-theme="rider"
			style={{
				background:
					"linear-gradient(135deg, rgba(15,23,42,0.95) 0%, rgba(30,41,59,0.9) 100%)",
				border: "1px solid rgba(59,130,246,0.2)",
				borderRadius: "16px",
				padding: "1.25rem",
				margin: "0 0 1rem 0",
				backdropFilter: "blur(20px)",
				position: "relative",
				overflow: "hidden",
				animation: "fadeIn 0.5s ease-out",
			}}
		>
			{/* Ambient glow */}
			<div
				style={{
					position: "absolute",
					top: "-50%",
					right: "-20%",
					width: "300px",
					height: "300px",
					background:
						"radial-gradient(circle, rgba(59,130,246,0.08) 0%, transparent 70%)",
					pointerEvents: "none",
				}}
			/>

			{/* Header */}
			<div
				style={{
					display: "flex",
					justifyContent: "space-between",
					alignItems: "center",
					marginBottom: "1rem",
					position: "relative",
					zIndex: 1,
				}}
			>
				<div style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
					<div
						style={{
							background: "rgba(59,130,246,0.15)",
							borderRadius: "10px",
							padding: "0.45rem",
							display: "inline-flex",
							animation: tickFlash ? "pulse 0.4s ease" : "none",
						}}
					>
						<Lucide.Navigation size={18} style={{ color: "#3b82f6" }} />
					</div>
					<div>
						<div
							style={{
								fontSize: "0.85rem",
								fontWeight: 800,
								color: "#f8fafc",
								letterSpacing: "-0.02em",
							}}
						>
							LIVE RIDER TRACKING
						</div>
						<div
							style={{
								fontSize: "0.65rem",
								color: "#64748b",
								fontWeight: 500,
							}}
						>
							Order #{activeOrder.id} • SSE Stream Active
						</div>
					</div>
				</div>

				<div
					style={{
						display: "flex",
						alignItems: "center",
						gap: "0.4rem",
						background: coords ? "rgba(16,185,129,0.1)" : "rgba(239,68,68,0.1)",
						padding: "0.25rem 0.6rem",
						borderRadius: "20px",
						border: `1px solid ${coords ? "rgba(16,185,129,0.3)" : "rgba(239,68,68,0.3)"}`,
					}}
				>
					<div
						style={{
							width: "6px",
							height: "6px",
							borderRadius: "50%",
							background: coords ? "#10b981" : "#ef4444",
							animation: coords ? "pulse 2s infinite" : "none",
						}}
					/>
					<span
						style={{
							fontSize: "0.6rem",
							fontWeight: 700,
							color: coords ? "#10b981" : "#ef4444",
							textTransform: "uppercase",
							letterSpacing: "0.05em",
						}}
					>
						{coords ? "LIVE" : "AWAITING SIGNAL"}
					</span>
				</div>
			</div>

			{/* Thermal Breach Warning Banner */}
			{riderCoords?.thermalBreachActive && (
				<div
					style={{
						background: "rgba(239, 68, 68, 0.15)",
						border: "1px solid rgba(239, 68, 68, 0.4)",
						borderRadius: "12px",
						padding: "0.8rem 1rem",
						marginBottom: "1rem",
						display: "flex",
						alignItems: "center",
						justifyContent: "space-between",
						gap: "1rem",
						animation: "pulseGlow 2s infinite",
						zIndex: 2,
						position: "relative",
					}}
				>
					<div style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
						<Lucide.AlertTriangle size={18} style={{ color: "#ef4444" }} />
						<div>
							<div
								style={{
									fontSize: "0.8rem",
									fontWeight: 800,
									color: "#ef4444",
								}}
							>
								THERMAL BREACH WARNING
							</div>
							<div
								style={{
									fontSize: "0.65rem",
									color: "#fca5a5",
									fontWeight: 500,
								}}
							>
								Temperature has exceeded limit for &gt;3 minutes. Cargo at risk!
							</div>
						</div>
					</div>
					<button
						onClick={handleActionClick}
						disabled={injecting}
						style={{
							background: "#ef4444",
							color: "#ffffff",
							border: "none",
							borderRadius: "8px",
							padding: "0.4rem 0.8rem",
							fontSize: "0.7rem",
							fontWeight: 700,
							cursor: injecting ? "not-allowed" : "pointer",
							display: "flex",
							alignItems: "center",
							gap: "0.4rem",
							transition: "background 0.2s",
							boxShadow: "0 0 10px rgba(239,68,68,0.4)",
							opacity: injecting ? 0.7 : 1,
						}}
						onMouseOver={(e) => (e.currentTarget.style.background = "#dc2626")}
						onMouseOut={(e) => (e.currentTarget.style.background = "#ef4444")}
					>
						{injecting ? (
							<Lucide.Loader2
								size={12}
								style={{ animation: "spin 1s linear infinite" }}
							/>
						) : (
							<Lucide.Thermometer size={12} />
						)}
						Inject Coolant ($2.00)
					</button>
				</div>
			)}

			{/* Main grid: Map + Coords + Temp */}
			<div
				style={{
					display: "grid",
					gridTemplateColumns: "1fr 1fr 1fr",
					gap: "0.8rem",
					position: "relative",
					zIndex: 1,
				}}
			>
				{/* SVG Mini Route Map */}
				<div
					style={{
						background: "rgba(15,23,42,0.6)",
						borderRadius: "12px",
						border: "1px solid rgba(148,163,184,0.08)",
						padding: "0.6rem",
						display: "flex",
						flexDirection: "column",
						alignItems: "center",
					}}
				>
					<div
						style={{
							fontSize: "0.55rem",
							color: "#64748b",
							fontWeight: 700,
							textTransform: "uppercase",
							letterSpacing: "0.1em",
							marginBottom: "0.4rem",
						}}
					>
						ROUTE VISUALIZATION
					</div>
					<svg
						width="280"
						height="85"
						viewBox="0 0 300 100"
						style={{ maxWidth: "100%" }}
					>
						{/* Route path */}
						<path
							d="M 30 50 Q 90 20 150 50 Q 210 80 270 50"
							fill="none"
							stroke="rgba(59,130,246,0.2)"
							strokeWidth="3"
							className="animated-route-line"
						/>
						{/* Progress overlay */}
						<path
							d="M 30 50 Q 90 20 150 50 Q 210 80 270 50"
							fill="none"
							stroke="#3b82f6"
							strokeWidth="3"
							strokeDasharray={`${progress * 2.6},1000`}
							className="animated-route-line"
						/>
						{/* Store pin */}
						<circle
							cx="30"
							cy="50"
							r="6"
							fill="rgba(139,92,246,0.8)"
							stroke="#8b5cf6"
							strokeWidth="1.5"
						/>
						<text
							x="30"
							y="72"
							textAnchor="middle"
							fill="#8b5cf6"
							fontSize="7"
							fontWeight="700"
						>
							STORE
						</text>
						{/* Customer pin */}
						<circle
							cx="270"
							cy="50"
							r="6"
							fill="rgba(16,185,129,0.8)"
							stroke="#10b981"
							strokeWidth="1.5"
						/>
						<text
							x="270"
							y="72"
							textAnchor="middle"
							fill="#10b981"
							fontSize="7"
							fontWeight="700"
						>
							DROP
						</text>
						{/* Rider dot */}
						<g>
							<circle cx={riderX} cy={riderY} r="8" fill="rgba(245,158,11,0.2)">
								<animate
									attributeName="r"
									values="8;14;8"
									dur="2s"
									repeatCount="indefinite"
								/>
								<animate
									attributeName="opacity"
									values="1;0.3;1"
									dur="2s"
									repeatCount="indefinite"
								/>
							</circle>
							<circle
								cx={riderX}
								cy={riderY}
								r="12"
								fill="none"
								stroke="#f59e0b"
								strokeWidth="1.5"
								className="ripple-circle"
							/>
							<circle
								cx={riderX}
								cy={riderY}
								r="5"
								fill="#f59e0b"
								stroke="#fbbf24"
								strokeWidth="1.5"
							/>
						</g>
						{/* Progress label */}
						<text
							x={riderX}
							y={riderY - 14}
							textAnchor="middle"
							fill="#fbbf24"
							fontSize="8"
							fontWeight="800"
						>
							{progress}%
						</text>
					</svg>
				</div>

				{/* GPS Coordinates Panel */}
				<div
					style={{
						background: "rgba(15,23,42,0.6)",
						borderRadius: "12px",
						border: "1px solid rgba(148,163,184,0.08)",
						padding: "0.8rem",
						display: "flex",
						flexDirection: "column",
						justifyContent: "center",
						gap: "0.5rem",
					}}
				>
					<div
						style={{
							fontSize: "0.55rem",
							color: "#64748b",
							fontWeight: 700,
							textTransform: "uppercase",
							letterSpacing: "0.1em",
						}}
					>
						GPS COORDINATES
					</div>

					<div style={{ display: "flex", alignItems: "center", gap: "0.4rem" }}>
						<Lucide.MapPin size={12} style={{ color: "#3b82f6" }} />
						<span
							style={{ fontSize: "0.65rem", color: "#94a3b8", fontWeight: 600 }}
						>
							LAT
						</span>
						<span
							style={{
								fontFamily: "var(--font-mono, monospace)",
								fontSize: "0.85rem",
								color: "#f8fafc",
								fontWeight: 700,
								marginLeft: "auto",
								transition: "color 0.3s",
								...(tickFlash ? { color: "#3b82f6" } : {}),
							}}
						>
							{coords ? Number(coords.lat).toFixed(6) : "—"}
						</span>
					</div>

					<div style={{ display: "flex", alignItems: "center", gap: "0.4rem" }}>
						<Lucide.MapPin size={12} style={{ color: "#8b5cf6" }} />
						<span
							style={{ fontSize: "0.65rem", color: "#94a3b8", fontWeight: 600 }}
						>
							LNG
						</span>
						<span
							style={{
								fontFamily: "var(--font-mono, monospace)",
								fontSize: "0.85rem",
								color: "#f8fafc",
								fontWeight: 700,
								marginLeft: "auto",
								transition: "color 0.3s",
								...(tickFlash ? { color: "#8b5cf6" } : {}),
							}}
						>
							{coords ? Number(coords.lng).toFixed(6) : "—"}
						</span>
					</div>

					<div
						style={{
							fontSize: "0.55rem",
							color: "#475569",
							fontWeight: 500,
							marginTop: "0.15rem",
							fontFamily: "var(--font-mono, monospace)",
						}}
					>
						{coords?.timestamp
							? `Last tick: ${new Date(coords.timestamp).toLocaleTimeString()}`
							: "Waiting for telemetry…"}
					</div>
				</div>

				{/* Temperature Gauge */}
				<div
					style={{
						background: "rgba(15,23,42,0.6)",
						borderRadius: "12px",
						border: `1px solid ${temperature > 8 ? "rgba(239,68,68,0.2)" : "rgba(148,163,184,0.08)"}`,
						padding: "0.8rem",
						display: "flex",
						flexDirection: "column",
						alignItems: "center",
						justifyContent: "center",
						gap: "0.35rem",
					}}
				>
					<div
						style={{
							fontSize: "0.55rem",
							color: "#64748b",
							fontWeight: 700,
							textTransform: "uppercase",
							letterSpacing: "0.1em",
						}}
					>
						COLD CHAIN SENSOR
					</div>

					{/* Circular temperature gauge */}
					<div style={{ position: "relative", width: "60px", height: "60px" }}>
						<svg width="60" height="60" viewBox="0 0 60 60">
							<circle
								cx="30"
								cy="30"
								r="24"
								fill="none"
								stroke="rgba(148,163,184,0.1)"
								strokeWidth="4"
							/>
							<circle
								cx="30"
								cy="30"
								r="24"
								fill="none"
								stroke={tempColor}
								strokeWidth="4"
								strokeDasharray={`${Math.min(temperature / 15, 1) * 150} 150`}
								strokeLinecap="round"
								transform="rotate(-90 30 30)"
								style={{
									transition: "stroke-dasharray 0.5s ease, stroke 0.3s ease",
								}}
							/>
						</svg>
						<div
							style={{
								position: "absolute",
								inset: 0,
								display: "flex",
								flexDirection: "column",
								alignItems: "center",
								justifyContent: "center",
							}}
						>
							<span
								style={{
									fontSize: "1.1rem",
									fontWeight: 800,
									color: tempColor,
									fontFamily: "var(--font-mono, monospace)",
									transition: "color 0.3s ease",
								}}
							>
								{Number(temperature).toFixed(1)}
							</span>
							<span
								style={{
									fontSize: "0.5rem",
									color: "#64748b",
									fontWeight: 600,
								}}
							>
								°C
							</span>
						</div>
					</div>

					<div
						style={{
							display: "flex",
							alignItems: "center",
							gap: "0.3rem",
							background: `${tempColor}15`,
							padding: "0.15rem 0.45rem",
							borderRadius: "12px",
							border: `1px solid ${tempColor}30`,
						}}
					>
						<Lucide.Thermometer size={10} style={{ color: tempColor }} />
						<span
							style={{
								fontSize: "0.55rem",
								fontWeight: 700,
								color: tempColor,
								textTransform: "uppercase",
								letterSpacing: "0.05em",
							}}
						>
							{tempLabel}
						</span>
					</div>

					{/* Real-time Temperature Trend Graph */}
					{tempHistory.length >= 2 && (
						<div
							style={{
								width: "100%",
								marginTop: "0.4rem",
								borderTop: "1px solid rgba(255,255,255,0.05)",
								paddingTop: "0.4rem",
								display: "flex",
								flexDirection: "column",
								alignItems: "center",
							}}
						>
							<div
								style={{
									fontSize: "0.5rem",
									color: "#64748b",
									fontWeight: 700,
									marginBottom: "0.2rem",
									textTransform: "uppercase",
								}}
							>
								Trend
							</div>
							<svg width="80" height="30" style={{ overflow: "visible" }}>
								<defs>
									<linearGradient id="tempGlowGrad" x1="0" y1="0" x2="0" y2="1">
										<stop offset="0%" stopColor={tempColor} stopOpacity="0.3" />
										<stop
											offset="100%"
											stopColor={tempColor}
											stopOpacity="0.0"
										/>
									</linearGradient>
								</defs>
								{fillPath && <path d={fillPath} fill="url(#tempGlowGrad)" />}
								{trendPath && (
									<path
										d={trendPath}
										fill="none"
										stroke={tempColor}
										strokeWidth="1.5"
									/>
								)}
								{tempHistory.map((h, i) => {
									const x = (i / (tempHistory.length - 1)) * 80 + 10;
									const normalizedY = (h.temp - 0) / (15 - 0);
									const y = 35 - normalizedY * 25;
									return (
										<circle key={i} cx={x} cy={y} r="2" fill={tempColor} />
									);
								})}
							</svg>
						</div>
					)}
				</div>
			</div>

			{/* Progress bar */}
			<div
				style={{
					marginTop: "0.8rem",
					position: "relative",
					zIndex: 1,
				}}
			>
				<div
					style={{
						display: "flex",
						justifyContent: "space-between",
						alignItems: "center",
						marginBottom: "0.3rem",
					}}
				>
					<span
						style={{ fontSize: "0.6rem", color: "#64748b", fontWeight: 600 }}
					>
						<Lucide.Bike
							size={10}
							style={{ display: "inline", marginRight: "4px" }}
						/>
						Rider Dave • En Route
					</span>
					<span
						style={{
							fontSize: "0.65rem",
							fontWeight: 800,
							color: "#3b82f6",
							fontFamily: "var(--font-mono, monospace)",
						}}
					>
						{progress}% COMPLETE
					</span>
				</div>
				<div
					style={{
						width: "100%",
						height: "6px",
						borderRadius: "3px",
						background: "rgba(148,163,184,0.1)",
						overflow: "hidden",
					}}
				>
					<div
						style={{
							height: "100%",
							borderRadius: "3px",
							background: `linear-gradient(90deg, #3b82f6, #8b5cf6, #06b6d4)`,
							width: `${progress}%`,
							transition: "width 0.8s cubic-bezier(0.4, 0, 0.2, 1)",
							boxShadow: "0 0 12px rgba(59,130,246,0.4)",
						}}
					/>
				</div>
				<div
					style={{
						display: "flex",
						justifyContent: "space-between",
						marginTop: "0.2rem",
					}}
				>
					<span style={{ fontSize: "0.5rem", color: "#475569" }}>
						SLA: {activeOrder.slaRemaining ?? "—"}s remaining
					</span>
					<span style={{ fontSize: "0.5rem", color: "#475569" }}>
						{activeOrder.perishable
							? "🧊 Perishable Cargo"
							: "📦 Standard Cargo"}
					</span>
				</div>
			</div>

			<style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(-8px); }
          to { opacity: 1; transform: translateY(0); }
        }
        @keyframes pulse {
          0%, 100% { transform: scale(1); }
          50% { transform: scale(1.15); }
        }
        @keyframes pulseGlow {
          0%, 100% { box-shadow: 0 0 5px rgba(239, 68, 68, 0.2); }
          50% { box-shadow: 0 0 15px rgba(239, 68, 68, 0.4); }
        }
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
		</div>
	);
}
