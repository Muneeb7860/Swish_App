import { useQuery } from "@tanstack/react-query";
import * as Lucide from "lucide-react";
import React, {
	Suspense,
	useCallback,
	useEffect,
	useMemo,
	useRef,
	useState,
} from "react";
import MfaLoginPortal from "./components/MfaLoginPortal";
import RbacBlocker from "./components/RbacBlocker";
import RiderTrackingPanel from "./components/RiderTrackingPanel";
import SupportBot from "./components/SupportBot";
import { useEnvProfiles } from "./hooks/useEnvProfiles";
import { useStore } from "./store";
import { syncProductsFromFirebase } from "./firebaseSync";
import * as api from "./api/endpoints";

// Strict MFE Origin Whitelist Check to prevent module hijacking
const MFE_WHITELIST = (
	import.meta.env.VITE_MFE_WHITELIST || "localhost,127.0.0.1"
)
	.split(",")
	.map((host: string) => host.trim());

const verifyMfeOrigin = (importPromise: Promise<any>, remoteName: string) => {
	return importPromise.then((module) => {
		const scriptElements = Array.from(document.querySelectorAll("script"));
		const remoteScript = scriptElements.find(
			(s) => s.src && s.src.includes(remoteName),
		);
		if (remoteScript) {
			const url = new URL(remoteScript.src);
			if (
				!MFE_WHITELIST.includes(url.hostname) &&
				url.origin !== window.location.origin
			) {
				throw new Error(
					`Security Exception: Untrusted MFE Remote origin blocked: ${url.origin}`,
				);
			}
		} else {
			// Hardening: If remote script not explicitly found, scan all DOM scripts to prevent evasion.
			const untrustedScripts = scriptElements.filter((s) => {
				if (!s.src) return false;
				try {
					const url = new URL(s.src);
					return (
						!MFE_WHITELIST.includes(url.hostname) &&
						url.origin !== window.location.origin
					);
				} catch {
					return true;
				}
			});
			if (untrustedScripts.length > 0) {
				throw new Error(
					`Security Exception: Untrusted scripts detected in DOM: ${untrustedScripts.map((s) => s.src).join(", ")}`,
				);
			}
		}
		return module;
	});
};

// Lazy loaded remote Micro-Frontends with Whitelist Verification
const CustomerApp = React.lazy(() =>
	verifyMfeOrigin(import("customer/CustomerApp"), "customer"),
);
const RiderApp = React.lazy(() =>
	verifyMfeOrigin(import("rider/RiderApp"), "rider"),
);
const AdminPanel = React.lazy(() =>
	verifyMfeOrigin(import("admin/AdminPanel"), "admin"),
);
const BusinessApp = React.lazy(() =>
	verifyMfeOrigin(import("admin/BusinessApp"), "admin"),
);
const InventoryApp = React.lazy(() =>
	verifyMfeOrigin(import("admin/InventoryApp"), "admin"),
);
const SystemEngineRoom = React.lazy(() =>
	verifyMfeOrigin(import("admin/SystemEngineRoom"), "admin"),
);
const B2bDashboard = React.lazy(() =>
	verifyMfeOrigin(import("b2b/B2bDashboard"), "b2b"),
);

interface ErrorBoundaryProps {
	name: string;
	children: React.ReactNode;
}
interface ErrorBoundaryState {
	hasError: boolean;
	error: Error | null;
}
class LocalErrorBoundary extends React.Component<
	ErrorBoundaryProps,
	ErrorBoundaryState
> {
	constructor(props) {
		super(props);
		this.state = { hasError: false, error: null };
	}

	static getDerivedStateFromError(error) {
		return { hasError: true, error };
	}

	componentDidCatch(error, errorInfo) {
		console.error(
			`MFE Error Boundary caught failure for [${this.props.name}]:`,
			error,
			errorInfo,
		);
	}

	render() {
		if (this.state.hasError) {
			return (
				<div
					className="glass-card"
					style={{
						padding: "2.5rem",
						textAlign: "center",
						borderColor: "#ef4444",
						borderWidth: "1px",
						borderStyle: "dashed",
						borderRadius: "12px",
						background: "rgba(239, 68, 68, 0.02)",
					}}
				>
					<div
						style={{
							display: "flex",
							flexDirection: "column",
							alignItems: "center",
							gap: "0.85rem",
						}}
					>
						<div
							style={{
								color: "#ef4444",
								background: "rgba(239, 68, 68, 0.1)",
								padding: "0.6rem",
								borderRadius: "50%",
								display: "inline-flex",
							}}
						>
							<Lucide.AlertOctagon size={24} />
						</div>
						<h4
							style={{
								margin: 0,
								color: "#f8fafc",
								fontSize: "1.05rem",
								fontWeight: 800,
							}}
						>
							Micro-Frontend Load Failure
						</h4>
						<p
							style={{
								margin: 0,
								fontSize: "0.8rem",
								color: "#94a3b8",
								maxWidth: "440px",
								lineHeight: "1.4",
							}}
						>
							The federated remote panel [<strong>{this.props.name}</strong>]
							failed to load or experienced a runtime crash. Downstream systems
							and checkout capabilities remain operational.
						</p>
						{this.state.error && (
							<code
								style={{
									fontSize: "0.75rem",
									color: "#f87171",
									background: "rgba(0,0,0,0.2)",
									padding: "0.25rem 0.5rem",
									borderRadius: "4px",
								}}
							>
								{this.state.error.toString()}
							</code>
						)}
						<button
							onClick={() => {
								this.setState({ hasError: false, error: null });
								window.location.reload();
							}}
							style={{
								marginTop: "1rem",
								padding: "0.5rem 1.25rem",
								background: "rgba(239, 68, 68, 0.2)",
								border: "1px solid #ef4444",
								color: "#fca5a5",
								borderRadius: "8px",
								fontSize: "0.8rem",
								fontWeight: 600,
								cursor: "pointer",
								transition: "all 0.2s ease",
							}}
							onMouseOver={(e) => {
								e.currentTarget.style.background = "#ef4444";
								e.currentTarget.style.color = "#fff";
							}}
							onMouseOut={(e) => {
								e.currentTarget.style.background = "rgba(239, 68, 68, 0.2)";
								e.currentTarget.style.color = "#fca5a5";
							}}
						>
							Retry Loading
						</button>
					</div>
				</div>
			);
		}
		return this.props.children;
	}
}

// Default mock product catalog
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

export default function App() {
	const {
		activeRole,
		setActiveRole,
		weather,
		setWeather,
		merchantWallet,
		setMerchantWallet,
		customerOrderCount,
		setCustomerOrderCount,
		customerRefundCount,
		setCustomerRefundCount,
		customerTab,
		setCustomerTab,
		profileSubTab,
		setProfileSubTab,
		favorites,
		setFavorites,
		vipMember,
		setVipMember,
		isAuthenticated,
		setIsAuthenticated,
		currentUserSession,
		setCurrentUserSession,
		mfaStep,
		setMfaStep,
		mfaRole,
		setMfaRole,
		mfaPassword,
		setMfaPassword,
		mfaOtpSentCode,
		setMfaOtpSentCode,
		mfaOtpInput,
		setMfaOtpInput,
		mfaMethod,
		setMfaMethod,
		totpSecretCode,
		setTotpSecretCode,
		totpTimer,
		setTotpTimer,
		sessionToken,
		setSessionToken,
		authToken,
		setAuthToken,
		searchVolumeMap,
		setSearchVolumeMap,
		activeStockTransfers,
		setActiveStockTransfers,
		tipAmount,
		setTipAmount,
		esgCheckbox,
		setEsgCheckbox,
		totalCo2Offset,
		setTotalCo2Offset,
		pickerSlaDuration,
		setPickerSlaDuration,
		pickerBadge,
		setPickerBadge,
		backupPickersCount,
		setBackupPickersCount,
		cartIdleTime,
		setCartIdleTime,
		simulateTelemetryFraud,
		setSimulateTelemetryFraud,
		pickingBacklogQueue,
		setPickingBacklogQueue,
		activePickingCongested,
		setActivePickingCongested,
		selectedCertRole,
		setSelectedCertRole,
		activeTrainingRole,
		setActiveTrainingRole,
		trainingProgress,
		setTrainingProgress,
		earnedCertifications,
		setEarnedCertifications,
		b2bDiscountActive,
		setB2bDiscountActive,
		customerTrustScore,
		setCustomerTrustScore,
		riderTrustScore,
		setRiderTrustScore,
		pickerTrustScore,
		setPickerTrustScore,
		wholesalerTrustScore,
		setWholesalerTrustScore,
		coldChainBreakdownActive,
		setColdChainBreakdownActive,
		wholesalerOutageActive,
		setWholesalerOutageActive,
		paymentOutageActive,
		setPaymentOutageActive,
		redisCrashActive,
		setRedisCrashActive,
		dbLatencyActive,
		setDbLatencyActive,
		riderTrafficActive,
		setRiderTrafficActive,
		centralCapacity,
		setCentralCapacity,
		eastCapacity,
		setEastCapacity,
		centralScalingCount,
		setCentralScalingCount,
		eastScalingCount,
		setEastScalingCount,
		gdprTokenProbation,
		setGdprTokenProbation,
		riderOnboardStatus,
		setRiderOnboardStatus,
		businessOnboardStatus,
		setBusinessOnboardStatus,
		gatewayOnboardStatus,
		setGatewayOnboardStatus,
		hitlQueue,
		setHitlQueue,
		agentMetrics,
		setAgentMetrics,
		oltpWriteLatency,
		setOltpWriteLatency,
		olapSyncTimer,
		setOlapSyncTimer,
		jwtFlash,
		setJwtFlash,
		vaultTimer,
		setVaultTimer,
		latencyHistory,
		setLatencyHistory,
		cacheHits,
		setCacheHits,
		cacheMisses,
		setCacheMisses,
		circuitBreakerTripped,
		setCircuitBreakerTripped,
		rateLimitActive,
		setRateLimitActive,
		toasts,
		setToasts,
		botOpen,
		setBotOpen,
		botInputText,
		setBotInputText,
		certModalOpen,
		setCertModalOpen,
		riderCoords,
		setRiderCoords,
		products,
		setProducts,
		cart,
		setCart,
		activeOrder,
		setActiveOrder,
		orderHistory,
		setOrderHistory,
		customerWallet,
		setCustomerWallet,
		customerPoints,
		setCustomerPoints,
		riderWallet,
		setRiderWallet,
		botMessages,
		setBotMessages,
		kafkaLogs,
		setKafkaLogs,
		ledger,
		setLedger,
		trustLogs,
		setTrustLogs,
		onboardingQueue,
		setOnboardingQueue,
		savedAddresses,
		setSavedAddresses,
		savedCards,
		setSavedCards,
		vouchers,
		setVouchers,
	} = useStore();
	const {
		envProfiles,
		setEnvProfiles,
		activeProfileKey,
		setActiveProfileKey,
		activeProfile,
	} = useEnvProfiles();
	const [catalogLoading, setCatalogLoading] = useState(false);
	const [hitlLoading, setHitlLoading] = useState(false);
	const [isBotTyping, setIsBotTyping] = useState(false);
	// customer, rider, business, inventory, admin

	useEffect(() => {
		// Programmatic preloading of all Micro-Frontends remote entries in the background
		// to warm up cache and reduce tabs switching latency.
		const preloadMfes = async () => {
			try {
				await Promise.allSettled([
					verifyMfeOrigin(import("customer/CustomerApp"), "customer"),
					verifyMfeOrigin(import("rider/RiderApp"), "rider"),
					verifyMfeOrigin(import("admin/AdminPanel"), "admin"),
					verifyMfeOrigin(import("admin/BusinessApp"), "admin"),
					verifyMfeOrigin(import("admin/InventoryApp"), "admin"),
					verifyMfeOrigin(import("admin/SystemEngineRoom"), "admin"),
					verifyMfeOrigin(import("b2b/B2bDashboard"), "b2b"),
				]);
				console.log("Preloading of Micro-Frontends complete.");
			} catch (e) {
				console.warn("Failed to preload MFEs in background:", e);
			}
		};
		// Delay slightly to prioritize the initial mount and load of the host page
		const timer = setTimeout(preloadMfes, 1500);
		return () => clearTimeout(timer);
	}, []);

	useEffect(() => {
		// Start real-time Firebase syncing
		syncProductsFromFirebase().catch((err) =>
			console.error("Firebase sync error:", err),
		);

		// Global cursor-tracking glow effect for glass-cards and glow-cards
		const handleGlobalMouseMove = (e: MouseEvent) => {
			const cards = document.querySelectorAll(
				".glow-card, .glass-card, .mfa-card, .auth-glass-form, .auth-bento-card",
			);
			cards.forEach((card: any) => {
				const rect = card.getBoundingClientRect();
				const x = e.clientX - rect.left;
				const y = e.clientY - rect.top;
				card.style.setProperty("--mouse-x", `${x}px`);
				card.style.setProperty("--mouse-y", `${y}px`);
			});
		};

		window.addEventListener("mousemove", handleGlobalMouseMove);
		return () => {
			window.removeEventListener("mousemove", handleGlobalMouseMove);
		};
	}, []);

	useEffect(() => {
		if (isAuthenticated) {
			setCatalogLoading(true);
			api
				.getCatalog()
				.then((items) => {
					const mapped = items.map((item) => ({
						id: item.item_id,
						name: item.name,
						price: item.price,
						stock: item.stock,
						stockEast: Math.max(0, item.stock - 2),
						category: item.category,
						emoji: item.emoji,
						perishable: item.perishable,
					}));
					setProducts(mapped);
				})
				.catch((err) => {
					console.error("Failed to load catalog from BFF:", err);
				})
				.finally(() => {
					setCatalogLoading(false);
				});
		}
	}, [isAuthenticated, setProducts]);

	const ROLES = ["customer", "rider", "inventory", "business", "admin"];
	const handleRoleChange = (newRole: string) => {
		if (newRole === activeRole) return;
		if (!(document as any).startViewTransition) {
			setActiveRole(newRole);
			return;
		}

		const oldIndex = ROLES.indexOf(activeRole);
		const newIndex = ROLES.indexOf(newRole);
		const direction = newIndex > oldIndex ? "forward" : "backward";

		(document as any).startViewTransition({
			update: () => {
				setActiveRole(newRole);
			},
			types: [direction],
		});
	};

	// Sunny, Heavy Rain, Thunderstorm

	// Financials

	// --- SWIGGY-STYLE USER PROFILE HUB STATES ---
	// catalog, profile
	// vip, vouchers, rewards, orders, saved, addresses, payments, statements

	// --- AUTHENTICATION & MFA SECURITY SYSTEM ---
	// { role: null }
	// 'credentials', 'otp'
	// 'sms', 'totp'

	// --- ADVANCED Q-COMMERCE AUTOMATION STATES ---
	// in grams
	// customer, rider, picker, b2b

	// Trust Scores

	// Chaos & Resilience Extension States

	// Virtual Capacity & Scaling States

	// Onboarding applications

	// unapplied, pending, active

	// HITL Queue

	// Telemetry Metrics

	// Logs

	const canvasRef = useRef<any>(null);
	const riderTimerRef = useRef<any>(null);
	const sseRef = useRef<any>(null); // Holds the active EventSource for rider telemetry SSE

	// Live rider GPS coordinates streamed via SSE from BFF /api/telemetry/stream/{orderId}
	// { lat, lng, temperature, timestamp }

	// Teardown SSE connection cleanly
	const closeSseStream = () => {
		if (sseRef.current) {
			(sseRef.current as any).close();
			sseRef.current = null;
		}
	};

	// Cleanup on unmount
	useEffect(
		() => () => {
			closeSseStream();
			clearInterval(riderTimerRef.current);
		},
		[],
	);

	// Helper log triggers
	const triggerToast = (msg, borderType = "system") => {
		const id = Date.now() + Math.random();
		setToasts((prev) => [...prev, { id, msg, borderType }]);
		setTimeout(() => {
			setToasts((prev) => prev.filter((t) => t.id !== id));
		}, 4500);
	};

	const logKafka = (source, event, meta) => {
		if (activeProfile.logLevel === "error") {
			if (
				source !== "admin" &&
				!event.includes("error") &&
				!event.includes("fail") &&
				!event.includes("limit")
			)
				return;
		} else if (activeProfile.logLevel === "info") {
			if (event.includes("autocomplete") || event.includes("keystroke")) return;
		}

		setKafkaLogs((prev) =>
			[
				...prev,
				{
					id: "L-" + Date.now() + "-" + Math.random(),
					time: new Date().toLocaleTimeString(),
					event: `${event.toUpperCase()}`,
					source,
					meta,
				},
			].slice(-40),
		); // Keep last 40 logs
	};

	const logLedger = (type, ref, desc, debit, credit) => {
		setLedger((prev) => [
			...prev,
			{
				id: "TX-" + Date.now() + "-" + Math.random(),
				time: new Date().toLocaleTimeString(),
				type,
				ref,
				desc,
				debit,
				credit,
			},
		]);
	};

	const logTrust = (actor, event, delta, current) => {
		setTrustLogs((prev) => [
			...prev,
			{
				id: "TL-" + Date.now(),
				time: new Date().toLocaleTimeString(),
				actor,
				event,
				delta,
				current,
			},
		]);
		logKafka(
			"system",
			`${actor}.trust_score_changed`,
			`${event}. Delta: ${delta >= 0 ? "+" : ""}${delta}. Score: ${current}`,
		);
	};

	const updateCustomerTrust = (delta, event) => {
		setCustomerTrustScore((prev) => {
			const next = Math.max(0, Math.min(100, prev + delta));
			logTrust("customer", event, delta, next);
			return next;
		});
	};

	const updateRiderTrust = (delta, event) => {
		setRiderTrustScore((prev) => {
			const next = Math.max(0, Math.min(100, prev + delta));
			logTrust("rider", event, delta, next);
			return next;
		});
	};

	const updatePickerTrust = (delta, event) => {
		setPickerTrustScore((prev) => {
			const next = Math.max(0, Math.min(100, prev + delta));
			logTrust("picker", event, delta, next);
			return next;
		});
	};

	const updateWholesalerTrust = (delta, event) => {
		setWholesalerTrustScore((prev) => {
			const next = Math.max(0, Math.min(100, prev + delta));
			logTrust("wholesaler", event, delta, next);
			return next;
		});
	};

	// Dynamic system timers effects
	useEffect(() => {
		let timer;
		if (!isAuthenticated) {
			const generateCode = () => {
				const timeStep = Math.floor(Date.now() / 30000);
				const code = String(((timeStep * 1337) % 900000) + 100000);
				setTotpSecretCode(code);
			};
			generateCode();
			timer = setInterval(() => {
				generateCode();
				setTotpTimer(30);
			}, 30000);
		}
		return () => clearInterval(timer);
	}, [isAuthenticated]);

	useEffect(() => {
		let timer;
		if (!isAuthenticated) {
			timer = setInterval(() => {
				setTotpTimer((t) => (t <= 1 ? 30 : t - 1));
			}, 1000);
		}
		return () => clearInterval(timer);
	}, [isAuthenticated]);

	// Telemetry loop effect
	useEffect(() => {
		const interval = setInterval(() => {
			setOltpWriteLatency(() => {
				const base = dbLatencyActive ? 180 : activeProfile?.dbLatencyDefault;
				return base + Math.floor(Math.random() * 8);
			});
			setVaultTimer((t) => (t <= 1 ? 15 : t - 1));
			setJwtFlash((f) => !f);
		}, 1500);
		return () => clearInterval(interval);
	}, [dbLatencyActive, activeProfile?.dbLatencyDefault]);

	useEffect(() => {
		const interval = setInterval(() => {
			setOlapSyncTimer((t) => {
				if (t >= 7) {
					logKafka(
						"business",
						"olap.sync_complete",
						"OLAP analytics database synced successfully. ETL pipeline flushed.",
					);
					return 0;
				}
				return t + 1;
			});
		}, 1000);
		return () => clearInterval(interval);
	}, []);

	const { data: metricsData } = useQuery({
		queryKey: ["agentMetrics"],
		queryFn: () =>
			fetch(`${import.meta.env.VITE_API_BASE_URL}/api/agent/metrics`, {
				headers: { Authorization: `Bearer ${authToken}` },
			}).then((res) => {
				if (!res.ok) throw new Error("Metrics offline");
				return res.json();
			}),
		refetchInterval: 4000,
		enabled: !!authToken,
	});

	useEffect(() => {
		if (metricsData && typeof metricsData.dailyCost === "number") {
			setAgentMetrics(metricsData);
		}
	}, [metricsData, setAgentMetrics]);

	// MFA Handlers
	const handleMfaSendOtp = () => {
		if (!mfaPassword) {
			triggerToast("Please enter password", "admin");
			return;
		}

		const payload = {
			email: `${mfaRole}@swish.local`,
			password: mfaPassword,
		};

		api
			.login(payload)
			.then((data) => {
				if (data.mfaRequired) {
					setSessionToken(data.sessionToken);
					setMfaStep("otp");
					logKafka(
						"system",
						"auth.mfa_otp_sent",
						`MFA requested. Session token generated. Check backend stdout console for PIN.`,
					);
					triggerToast(
						`MFA verification required. Please check Spring Boot console for OTP code.`,
						"system",
					);
				} else {
					// Direct login
					setIsAuthenticated(true);
					setCurrentUserSession({ role: mfaRole });
					setActiveRole(mfaRole);
					const jwtRegex =
						/^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_+/=]+$/;
					const tokenToSet =
						data.token && jwtRegex.test(data.token) ? data.token : "";
					setAuthToken(tokenToSet);
					if (tokenToSet) {
						localStorage.setItem("jwt_token", tokenToSet);
					}
					logKafka(
						"system",
						"auth.success",
						`Authenticated successfully via backend for role: ${mfaRole}`,
					);
					triggerToast(`Welcome back, authorized ${mfaRole}!`, "customer");
					setMfaPassword("");
				}
			})
			.catch((err) => {
				logKafka(
					"system",
					"auth.failure",
					`Failed login credentials check: ${err.message}`,
				);
				triggerToast(`Login Error: ${err.message}`, "admin");
			});
	};

	const handleMfaVerify = () => {
		api
			.verifyMfa({
				session_token: sessionToken,
				code: mfaOtpInput,
			})
			.then((data) => {
				setIsAuthenticated(true);
				setCurrentUserSession({ role: mfaRole });
				setActiveRole(mfaRole);
				const jwtRegex = /^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_+/=]+$/;
				const tokenToSet =
					data.token && jwtRegex.test(data.token) ? data.token : "";
				setAuthToken(tokenToSet);
				if (tokenToSet) {
					localStorage.setItem("jwt_token", tokenToSet);
				}
				logKafka(
					"system",
					"auth.success",
					`MFA authenticated successfully via backend for role: ${mfaRole}`,
				);
				triggerToast(`Welcome back, authorized ${mfaRole}!`, "customer");
				setMfaStep("credentials");
				setMfaPassword("");
				setMfaOtpInput("");
			})
			.catch((err) => {
				logKafka(
					"system",
					"auth.failure",
					`Failed MFA verification for role: ${mfaRole}. Error: ${err.message}`,
				);
				triggerToast(
					`Invalid Passcode: ${err.message}. (Note: Backend uses SMS OTP printed in stdout console)`,
					"admin",
				);
			});
	};

	const handleLogout = () => {
		setIsAuthenticated(false);
		setCurrentUserSession(null);
		setMfaStep("credentials");
		setAuthToken("");
		localStorage.removeItem("jwt_token");
		logKafka(
			"system",
			"auth.session_terminated",
			"Session locked. User signed out.",
		);
		triggerToast("Session locked successfully.", "system");
	};

	// Onboarding Handlers
	const handleApplyOnboard = (type, name) => {
		api
			.submitOnboard({
				full_name: name,
				vehicle_type: "E-Bike",
				driver_license_base64: "demo-license-data-base64",
			})
			.then((res) => {
				const id =
					res.application_id ||
					`app-${Math.random().toString(36).substring(2, 7)}`;
				setOnboardingQueue((prev) => [
					...prev,
					{
						id,
						name,
						type,
						approvals: { l1: false, l2: false, l3: false },
					},
				]);
				logKafka(
					"admin",
					"onboard.applied",
					`${name} submitted onboarding application (${id}).`,
				);
			})
			.catch((err) => {
				triggerToast(`Failed to submit onboarding: ${err.message}`, "admin");
			});
	};

	const handleApproveOnboard = (appId, level) => {
		const roleMap: Record<string, "Ops" | "Compliance" | "Admin"> = {
			l1: "Ops",
			l2: "Compliance",
			l3: "Admin",
		};
		const validatorRole = roleMap[level] || "Ops";

		api
			.approveOnboard(appId, { approve: true, validator_role: validatorRole })
			.then(() => {
				setOnboardingQueue((prev) =>
					prev.map((app) => {
						if (app.id === appId) {
							const nextApprovals = { ...app.approvals, [level]: true };
							const active =
								nextApprovals.l1 && nextApprovals.l2 && nextApprovals.l3;

							if (active) {
								if (app.type === "rider") {
									setRiderOnboardStatus("active");
									triggerToast(
										"Onboarding Complete: Rider Dave is now ACTIVE!",
										"rider",
									);
									logKafka(
										"admin",
										"rider.onboarded",
										`Rider Dave approved across all 3 layers. System dispatch credentials generated.`,
									);
								} else if (app.type === "merchant") {
									setBusinessOnboardStatus("active");
									triggerToast(
										"Onboarding Complete: Merchant is now ACTIVE!",
										"business",
									);
									logKafka(
										"admin",
										"merchant.onboarded",
										"FreshGrocer Store verified. Catalog sync activated.",
									);
								}
							} else {
								logKafka(
									"admin",
									`onboard.l${level.slice(1)}_approved`,
									`Approved Level ${level.slice(1)} check for ${app.name}`,
								);
							}
							return { ...app, approvals: nextApprovals };
						}
						return app;
					}),
				);
			})
			.catch((err) => {
				triggerToast(`Failed to approve onboarding: ${err.message}`, "admin");
			});
	};

	// Interactive Checkout
	const handleCheckout = (paymentMethod) => {
		if (cart.length === 0) {
			triggerToast("Cart is empty", "admin");
			return;
		}

		const subtotal = cart.reduce((sum, item) => sum + item.price * item.qty, 0);
		const rebate = esgCheckbox ? 0.5 : 0.0;
		const finalAmount = Math.max(0, subtotal + 2.99 + tipAmount - rebate);

		if (paymentMethod === "Wallet" && customerWallet < finalAmount) {
			triggerToast("Insufficient Wallet balance!", "admin");
			return;
		}

		logKafka(
			"customer",
			"order.checkout_triggered",
			`Checkout requested for ${cart.length} items. Total: $${finalAmount.toFixed(2)}.`,
		);

		const orderItems = cart.map((item) => ({
			item_id: item.id,
			quantity: item.qty,
		}));

		const orderRequest = {
			items: orderItems,
			payment_method: paymentMethod,
			tip_amount: tipAmount,
			bags_returned: 0,
		};

		api
			.placeOrder(orderRequest)
			.then((orderResponse) => {
				const orderId = orderResponse.order_id;
				const paymentRequest = {
					order_id: orderId,
					customer_id: "CUST-Dave",
					amount: finalAmount,
					payment_method: paymentMethod,
				};

				return api
					.createPayment(paymentRequest)
					.then((paymentResponse) => {
						return api.capturePayment(paymentResponse.payment_id);
					})
					.then(() => {
						if (paymentMethod === "Wallet") {
							setCustomerWallet((w) => w - finalAmount);
							setMerchantWallet((w) => w + finalAmount);
							logLedger(
								"customer",
								"CUST-WALLET-PAY",
								`Purchased groceries via Wallet`,
								0,
								finalAmount,
							);
						} else {
							setMerchantWallet((w) => w + finalAmount);
							logLedger(
								"customer",
								"GATEWAY-CHARGE",
								`Swipe API charge authorization success`,
								0,
								finalAmount,
							);
						}

						const itemsDescription = cart
							.map((item) => `${item.qty}x ${item.name}`)
							.join(", ");
						const hasPerishables = cart.some((item) => item.perishable);

						const nextOrder = {
							id: orderId,
							items: itemsDescription,
							total: finalAmount,
							progress: 0,
							status: "picking",
							perishable: hasPerishables,
							temperature: hasPerishables ? 4.0 : null,
							slaRemaining: activePickingCongested ? 280 : 180,
						};

						setActiveOrder(nextOrder);
						setCart([]);
						setCustomerOrderCount((c) => c + 1);

						setPickingBacklogQueue((q) => {
							const nextQ = q + 1;
							if (nextQ > 1) {
								setActivePickingCongested(true);
								setPickerSlaDuration(6.8);
							}
							return nextQ;
						});

						logKafka(
							"inventory",
							"order.received",
							`Order #${nextOrder.id} dispatched to picker queue.`,
						);
						triggerToast(
							`Order #${nextOrder.id} placed successfully!`,
							"customer",
						);
					});
			})
			.catch((err) => {
				console.error("Checkout failed:", err);
				if (paymentOutageActive) {
					logKafka(
						"system",
						"gateway.outage",
						"Swipe gateway timeout! Initiating fallback chain Swipe ➔ PayPal...",
					);
					triggerToast(
						"Swipe down! Falling back to PayPal gateway...",
						"system",
					);
					logLedger(
						"customer",
						"GATEWAY-FAILOVER",
						"Swipe timed out. Charged via PayPal",
						0,
						finalAmount,
					);
				} else {
					triggerToast(`Checkout failed: ${err.message}`, "admin");
				}
			});
	};

	const startTransitTracking = (orderId: number) => {
		// ── SSE: Subscribe to live BFF telemetry stream with Exponential Backoff ──
		const connectSseWithBackoff = (targetOrderId, attempt = 0) => {
			closeSseStream();
			const sseUrl = `/api/telemetry/stream/${targetOrderId}`;
			const es = new EventSource(sseUrl);
			sseRef.current = es;

			es.onopen = () => {
				logKafka(
					"system",
					"sse.connected",
					`EventSource connected to rider telemetry stream for Order #${targetOrderId}`,
				);
			};

			es.addEventListener("telemetry-update", (event: any) => {
				try {
					const dataStr = event.data;
					if (typeof dataStr !== "string") {
						throw new Error("Invalid telemetry event data type");
					}
					// Size Gating: Max 50KB payload
					if (dataStr.length > 51200) {
						throw new Error("Telemetry payload size limit exceeded");
					}
					// JSON Nesting Depth Guard: Max 20 levels
					let depth = 0;
					for (let i = 0; i < dataStr.length; i++) {
						const char = dataStr[i];
						if (char === "{" || char === "[") {
							depth++;
							if (depth > 20) {
								throw new Error(
									"Telemetry payload nesting depth limit exceeded",
								);
							}
						} else if (char === "}" || char === "]") {
							depth--;
						}
					}

					const tick = JSON.parse(dataStr);
					const latVal =
						typeof tick.latitude === "number"
							? tick.latitude
							: parseFloat(tick.latitude);
					const lngVal =
						typeof tick.longitude === "number"
							? tick.longitude
							: parseFloat(tick.longitude);
					const tempVal =
						typeof tick.temperature === "number"
							? tick.temperature
							: parseFloat(tick.temperature);

					if (isNaN(latVal) || isNaN(lngVal) || isNaN(tempVal)) {
						throw new Error("Invalid numeric value in telemetry update");
					}

					const rawTimestamp = tick.timestamp || new Date().toISOString();
					const cleanTimestamp = String(rawTimestamp).replace(/<[^>]+>/g, "");

					setRiderCoords({
						lat: latVal,
						lng: lngVal,
						temperature: tempVal,
						timestamp: cleanTimestamp,
						thermalBreachActive: !!tick.thermalBreachActive,
					});
					logKafka(
						"rider",
						"sse.tick_received",
						`Lat ${latVal.toFixed(4)} Lng ${lngVal.toFixed(4)} Temp ${tempVal.toFixed(1)}°C (Breach: ${!!tick.thermalBreachActive})`,
					);
				} catch (parseErr: any) {
					console.warn(
						"[SSE] Telemetry tick validation/parse error:",
						parseErr.message,
					);
				}
			});

			es.onerror = () => {
				closeSseStream();
				if (attempt < 5) {
					const delay = 2 ** attempt * 1000;
					logKafka(
						"system",
						"sse.reconnecting",
						`SSE stream dropped. Retrying in ${delay}ms (Attempt ${attempt + 1}/5)...`,
					);
					setTimeout(() => {
						connectSseWithBackoff(targetOrderId, attempt + 1);
					}, delay);
				} else {
					logKafka(
						"system",
						"sse.failed",
						"SSE reconnection max attempts reached. Falling back to fetch-based telemetry.",
					);
				}
			};
		};

		connectSseWithBackoff(orderId, 0);
		// ── End SSE ──────────────────────────────────────────────────────────────

		riderTimerRef.current = setInterval(() => {
			setActiveOrder((prev) => {
				if (!prev) return null;
				if (prev.progress >= 100) {
					clearInterval(riderTimerRef.current as any);
					return {
						...prev,
						status: "arrived",
						progress: 100,
					};
				}

				const nextProgress = prev.progress + 10;
				let nextTemp = prev.temperature;

				if (prev.perishable && coldChainBreakdownActive) {
					nextTemp = prev.temperature + 1.8;
					if (nextTemp >= 12.0) {
						clearInterval(riderTimerRef.current as any);
						handlePerishablesSpoiled(prev);
						return null;
					}
				}

				// Fetch-based telemetry ingestion (push to BFF gateway)
				const lat = 47.3769 + nextProgress * 0.0001;
				const lng = 8.5417 + nextProgress * 0.0001;
				api
					.ingestTelemetryTick({
						orderId: prev.id,
						latitude: lat,
						longitude: lng,
						temperature: nextTemp || 0.0,
						dryIceInjected: false,
					})
					.then((res) => {
						if (res && res.alertTriggered) {
							// Handle potential circuit breaker state if simulated
						}
					})
					.catch((err) =>
						console.log("BFF Telemetry link offline:", err.message),
					);

				return {
					...prev,
					progress: nextProgress,
					temperature: nextTemp,
					slaRemaining: Math.max(0, prev.slaRemaining - 10),
				};
			});
		}, 1000);
	};

	const handlePickerHandover = () => {
		if (!activeOrder) return;

		const orderId = activeOrder.id;
		const duration = pickerSlaDuration;

		api
			.handoverItem({
				order_id: orderId,
				duration_seconds: duration,
				contains_packing_error: false,
			})
			.then((res) => {
				setActiveOrder((prev) =>
					prev ? { ...prev, status: "transit" } : null,
				);
				logKafka(
					"rider",
					"order.dispatched",
					`Order #${orderId} loaded on delivery cargo. Transit started.`,
				);
				triggerToast(`Order #${orderId} handed over to Rider Dave!`, "rider");

				setPickingBacklogQueue((q) => {
					const nextQ = Math.max(0, q - 1);
					if (nextQ <= 1) {
						setActivePickingCongested(false);
						setPickerSlaDuration(2.1);
					}
					return nextQ;
				});

				if (res.lightning_bonus_awarded) {
					setPickerBadge("Lightning Picker");
					setMerchantWallet((w) => w - 1.0);
					logLedger(
						"system",
						"PICKER-BONUS",
						"Lightning Picker badge speed bonus paid",
						1.0,
						0,
					);
					updatePickerTrust(10, "Lightning picking SLA completed");
					triggerToast("Picker awarded Lightning speed bonus!", "inventory");
				} else {
					setPickerBadge("Standard");
					updatePickerTrust(5, "Picking completed on time");
				}

				startTransitTracking(orderId);
			})
			.catch((err) => {
				triggerToast(`Picker handover failed: ${err.message}`, "inventory");
			});
	};

	const handleOrderDeliveryComplete = (order, podHash = "") => {
		// Close SSE stream — order lifecycle complete
		closeSseStream();
		setRiderCoords(null);

		api
			.confirmDelivery(order.id, { pin: "1234", photoUrl: "mock-pod-photo" })
			.then(() => {
				const tipPaid = tipAmount;
				if (tipPaid > 0) {
					setRiderWallet((w) => w + tipPaid);
					setCustomerWallet((w) => w - tipPaid);
					logLedger(
						"customer",
						"RIDER-TIP-DEBIT",
						`Rider Dave coordinate tip payout`,
						tipPaid,
						0,
					);
					logKafka(
						"rider",
						"wallet.tip_received",
						`Rider Dave received tip: $${tipPaid.toFixed(2)}.`,
					);
				}

				setRiderWallet((w) => w + 5.0);
				updateRiderTrust(5, "Order delivered successfully");
				updateCustomerTrust(5, "Order completed without issue");

				setOrderHistory((prev) => [
					{
						id: order.id,
						date: "Today",
						items: order.items,
						total: order.total,
						status: "delivered",
						paymentMethod: "Wallet",
						podHash: podHash,
					},
					...prev,
				]);

				logKafka(
					"rider",
					"order.delivered",
					`Order #${order.id} delivered. PoD Handshake Hash: ${podHash || "N/A"}`,
				);
				triggerToast(
					`Order #${order.id} delivered! PoD Hash: ${podHash ? podHash.substring(0, 10) + "..." : "N/A"}`,
					"customer",
				);
				setActiveOrder(null);
				setTipAmount(0);
			})
			.catch((err) => {
				triggerToast(`Delivery confirmation failed: ${err.message}`, "rider");
			});
	};

	const handlePerishablesSpoiled = (order) => {
		// Close SSE stream — order terminated due to spoilage
		closeSseStream();
		setRiderCoords(null);

		updateRiderTrust(-30, "Perishable cargo spoilage breach");
		logLedger(
			"system",
			"COLD-BREACH-DEBIT",
			`Perishable write-off debit`,
			order.total,
			0,
		);
		logKafka(
			"system",
			"coldchain.telemetry_failure",
			`Order #${order.id} cargo spoiled! Temperature exceeded 12.0°C limit. Delivery canceled.`,
		);
		triggerToast(
			"CRITICAL: Cargo spoiled in transit! Order canceled.",
			"admin",
		);
		setActiveOrder(null);
		setTipAmount(0);
	};

	const handleInjectDryIce = (orderId?: number) => {
		const id = orderId || activeOrder?.id;
		if (!id) return;
		setMerchantWallet((w) => w - 2.0);
		logLedger(
			"system",
			"DRY-ICE-DEBIT",
			"Rider manual coolant mitigation applied",
			2.0,
			0,
		);

		api
			.injectDryIce(id)
			.then((res) => {
				if (res && res.status === 503) {
					setCircuitBreakerTripped(true);
					triggerToast(
						"BFF Gateway: Downstream service offline. Circuit Breaker TRIPPED.",
						"admin",
					);
					logKafka(
						"system",
						"gateway.circuit_breaker_tripped",
						"Coolant action blocked. Circuit Breaker active.",
					);
				}
			})
			.catch((err) => console.log("BFF Telemetry link offline:", err.message));

		setActiveOrder((prev) => {
			if (!prev) return null;
			return { ...prev, temperature: 4.0 };
		});
		logKafka(
			"rider",
			"telemetry.mitigation_applied",
			"Coolant injected: Perishable temperature reset to 4.0°C.",
		);
		triggerToast("Dry ice coolant injected successfully!", "rider");
	};

	const handleDeployBackupPicker = () => {
		setMerchantWallet((w) => w - 10.0);
		logLedger(
			"system",
			"WAGE-DEBIT",
			"Deployed backup dark store picker",
			10.0,
			0,
		);
		setBackupPickersCount((c) => c + 1);
		setActivePickingCongested(false);
		setPickerSlaDuration(2.1);
		triggerToast("Backup picker deployed: backlog queue cleared!", "inventory");
	};

	const handleBalanceStores = () => {
		const centralStock = products.reduce((sum, p) => sum + p.stock, 0);
		const eastStock = products.reduce((sum, p) => sum + p.stockEast, 0);
		const delta = Math.abs(centralStock - eastStock);

		if (delta <= 3) {
			triggerToast("Stock levels already balanced across stores.", "inventory");
			return;
		}

		const direction =
			centralStock > eastStock ? "Central ➔ East" : "East ➔ Central";
		const tr = {
			id: Date.now(),
			itemName: `Inter-Store Inventory Rebalancing (${direction})`,
			progress: 0,
		};

		setActiveStockTransfers((prev) => [...prev, tr]);
		logKafka(
			"inventory",
			"balancing.dispatched",
			`Imbalance delta of ${delta} units detected. Transfer truck dispatched.`,
		);

		let trProgress = 0;
		const interval = setInterval(() => {
			trProgress += 20;
			setActiveStockTransfers((prev) =>
				prev.map((t) => (t.id === tr.id ? { ...t, progress: trProgress } : t)),
			);
			if (trProgress >= 100) {
				clearInterval(interval);
				setActiveStockTransfers((prev) => prev.filter((t) => t.id !== tr.id));

				setProducts((prev) =>
					prev.map((p) => {
						const totalItem = p.stock + p.stockEast;
						const avg = Math.floor(totalItem / 2);
						return { ...p, stock: avg, stockEast: totalItem - avg };
					}),
				);

				logKafka(
					"inventory",
					"balancing.completed",
					"Stock levels balanced and synced successfully across MFC locations.",
				);
				triggerToast("Rebalancing complete: inventory matched.", "inventory");
			}
		}, 1000);
	};

	const handleGdprPurge = () => {
		if (
			window.confirm(
				"CRITICAL WARNING: This action will permanently erase your personal order history and user profile data under GDPR Article 17 (Right to Erasure). Double-entry financial ledgers will remain intact for regulatory accounting compliance. Continue?",
			)
		) {
			setOrderHistory([]);
			setCustomerOrderCount(0);
			setCustomerRefundCount(0);
			// We DO NOT modify or anonymize the ledger, preserving double-entry accounting compliance.
			setGdprTokenProbation(true);
			updateCustomerTrust(
				-customerTrustScore + 75,
				"GDPR Purge Probationary Score Applied (Token Anonymization)",
			);
			logKafka(
				"customer",
				"gdpr.data_purge",
				"GDPR ARTICLE 17 PURGE: Erased customer order history and profile details. Immutable financial ledgers retained intact.",
			);
			triggerToast(
				"GDPR Data Purge complete: Personal history wiped.",
				"admin",
			);
		}
	};

	const handleScaleCapacity = (store) => {
		const isCentral = store === "Central";
		const count = isCentral ? centralScalingCount : eastScalingCount;
		const fee =
			count === 0 ? 15.0 : count === 1 ? 25.0 : count === 2 ? 35.0 : 0;

		if (fee === 0 || count >= 3) {
			triggerToast(`${store} Capacity max scaled!`, "admin");
			return;
		}

		setMerchantWallet((w) => w - fee);
		logLedger(
			"system",
			"SYS-SCALING-DEBIT",
			"Merchant rented virtual overflow warehouse bay due to capacity bottleneck",
			fee,
			0,
		);

		const nextCount = count + 1;
		if (isCentral) {
			setCentralCapacity((c) => c + 40);
			setCentralScalingCount(nextCount);
		} else {
			setEastCapacity((e) => e + 40);
			setEastScalingCount(nextCount);
		}
		logKafka(
			"system",
			"capacity_scaled",
			`${store} MFC Scaled: Rented overflow bay. Capacity increased by +40.`,
		);
		triggerToast(
			`MANUAL SCALE: ${store} capacity expanded! (Charged $${fee})`,
			"inventory",
		);
	};

	const fetchHitlQueues = useCallback(() => {
		setHitlLoading(true);
		const fetchB2b = api
			.getB2bHitlQueue()
			.then((data) => {
				if (!Array.isArray(data)) return [];
				return data.map((item) => ({
					id: `b2b-${item.id}`,
					originalId: item.id,
					type: "b2b_funds",
					desc: `Restock Order #${item.restockOrderId} override request for wholesaler ${item.wholesalerId}`,
					amount: item.amount,
					actionData: {
						restockOrderId: item.restockOrderId,
						wholesalerId: item.wholesalerId,
					},
				}));
			})
			.catch(() => []);

		const fetchGeneral = api
			.getHitlQueue()
			.then((data) => {
				if (!Array.isArray(data)) return [];
				return data.map((item) => ({
					id: item.ticket_id || item.ticketId,
					originalId: item.ticket_id || item.ticketId,
					type: item.type,
					desc: item.description,
					amount: item.amount,
					actionData: {
						customerId: item.customer_id || item.customer?.customerId,
						orderId: item.order_id || item.order?.orderId,
					},
				}));
			})
			.catch(() => []);

		Promise.all([fetchB2b, fetchGeneral])
			.then(([b2bList, generalList]) => {
				const merged = [...b2bList, ...generalList];
				setHitlQueue(merged);
			})
			.catch(() => {})
			.finally(() => {
				setHitlLoading(false);
			});
	}, [setHitlQueue]);

	useEffect(() => {
		fetchHitlQueues();
		if (activeRole === "admin") {
			const timer = setInterval(fetchHitlQueues, 10000);
			return () => clearInterval(timer);
		}
	}, [activeRole, fetchHitlQueues]);

	const handleReleaseHitl = (ticket) => {
		if (ticket.id && ticket.id.toString().startsWith("b2b-")) {
			const realId = ticket.originalId;
			api
				.approveB2bOverride(realId, {
					operator: "swissadmin",
					reason: "B2B Restock approved via Admin console",
				})
				.then(() => {
					triggerToast("B2B Restock order approved and released!", "admin");
					setMerchantWallet((w) => w - ticket.amount);
					logLedger(
						"system",
						"RESTOCK-FUND-RELEASE",
						`Restocked inventory: approved release to wholesaler`,
						ticket.amount,
						0,
					);
					logKafka(
						"system",
						"hitl.authorized",
						`Admin authorized B2B Funds: $${ticket.amount.toFixed(2)} transferred to wholesaler.`,
					);
					fetchHitlQueues();
				})
				.catch(() =>
					triggerToast("Failed to approve B2B restock override.", "admin"),
				);
		} else {
			api
				.resolveHitl(ticket.id, { action: "approve" })
				.then(() => {
					triggerToast("HITL ticket approved successfully.", "admin");
					setCustomerWallet((w) => w + ticket.amount);
					setMerchantWallet((w) => w - ticket.amount);
					logLedger(
						"system",
						"CUSTOMER-REFUND",
						"Approved support bot customer refund request",
						ticket.amount,
						0,
					);
					logKafka(
						"system",
						"hitl.authorized",
						`Admin authorized Support Bot refund of $${ticket.amount.toFixed(2)} to customer.`,
					);
					fetchHitlQueues();
				})
				.catch((err) =>
					triggerToast(
						`Failed to approve HITL ticket: ${err.message}`,
						"admin",
					),
				);
		}
	};

	const handleVoidHitl = (ticket) => {
		if (ticket.id && ticket.id.toString().startsWith("b2b-")) {
			const realId = ticket.originalId;
			api
				.rejectB2bOverride(realId, {
					operator: "swissadmin",
					reason: "B2B Restock rejected via Admin console",
				})
				.then(() => {
					triggerToast("B2B Restock order rejected and canceled.", "admin");
					logKafka(
						"admin",
						"hitl.voided",
						`Admin VOIDED B2B procurement request for restock order.`,
					);
					fetchHitlQueues();
				})
				.catch(() =>
					triggerToast("Failed to reject B2B restock override.", "admin"),
				);
		} else {
			api
				.resolveHitl(ticket.id, { action: "void" })
				.then(() => {
					triggerToast("HITL ticket rejected successfully.", "admin");
					logKafka(
						"admin",
						"hitl.voided",
						"Admin VOIDED AI Bot customer refund request.",
					);
					fetchHitlQueues();
				})
				.catch((err) =>
					triggerToast(`Failed to reject HITL ticket: ${err.message}`, "admin"),
				);
		}
	};

	const runRulesEngine = (text, attachmentUrl) => {
		setTimeout(() => {
			let botResponse = `I received: "${text}". How can I help resolve this operational request?`;

			if (activeRole === "rider") {
				if (
					text.toLowerCase().includes("breakdown") ||
					text.toLowerCase().includes("accident") ||
					attachmentUrl
				) {
					botResponse = `🚨 EMERGENCY DISPATCH: Vehicle breakdown/accident detected. Automatically routing backup rider to salvage cargo. Transit SLA paused. Incident report logged.`;
					updateRiderTrust(-5, "Breakdown reported in transit");
					setHitlQueue((prev) => [
						...prev,
						{
							id: "HITL-" + Date.now(),
							type: "rider_emergency",
							desc: "Emergency dispatch rider salvage fee",
							amount: 15.0,
							actionData: { orderId: activeOrder?.id },
						},
					]);
					logKafka(
						"rider",
						"incident_reported",
						"Emergency breakdown logged. Salvage protocols initiated.",
					);
				} else if (
					text.toLowerCase().includes("traffic") ||
					text.toLowerCase().includes("blocked") ||
					text.toLowerCase().includes("delay")
				) {
					botResponse = `🚦 TRAFFIC ALERT: Congestion detected on primary route. Alternative GPS route generated: Take Expressway bypass. Verify perishable seal.`;
					logKafka(
						"rider",
						"rerouted",
						"Expressway navigation bypass loaded in Rider GPS.",
					);
				}
			} else if (activeRole === "inventory" || activeRole === "admin") {
				if (
					text.toLowerCase().includes("damaged") ||
					text.toLowerCase().includes("spoiled")
				) {
					botResponse = `🍌 ITEM DAMAGE LOGGED: Damaged produce reported. Auto-dispatched a B2B replenishment reorder for safety. Damaged items written off.`;
					setProducts((prev) =>
						prev.map((p) =>
							p.id === "p2" ? { ...p, stock: Math.max(0, p.stock - 2) } : p,
						),
					);
					logLedger(
						"system",
						"SHELF-DAMAGE",
						"Spoiled bananas stock write-off",
						4.5,
						0,
					);
					logKafka(
						"inventory",
						"item_damaged",
						"Bananas written off due to shelf damage. Triggering B2B reorder.",
					);
				} else if (
					text.toLowerCase().includes("congested") ||
					text.toLowerCase().includes("backlog")
				) {
					botResponse = `📦 PICKER BACKLOG CONGESTION: Queue delays detected. Recommend deploying the Dark Store operations backup picker to restore standard speeds.`;
				}
			} else {
				if (text.toLowerCase().includes("refund")) {
					if (customerTrustScore < 65) {
						botResponse = `⛔ REFUND REFUSED: Your Customer Trust Score is below the 65-point safety threshold. Account flagged.`;
					} else if (gdprTokenProbation) {
						botResponse = `⛔ REFUND REFUSED: Your account is currently under GDPR Anonymization Probation. Automated bot refunds are blocked until completing 3 successful orders.`;
					} else if (simulateTelemetryFraud) {
						botResponse = `⛔ REFUND BLOCKED: Telemetry Correlation Audit Failed. Rider GPS logs indicate delivery took place at coordinates (12.971, 77.594) but customer profile claims geofence breach. Furthermore, uploaded photo EXIF metadata indicates timestamp mismatch. Refund request rejected.`;
						updateCustomerTrust(-25, "Telemetry check fraud alert");
						logKafka(
							"system",
							"fraud.telemetry_failed",
							"Fraud Shield Check: Telemetry correlation mismatch. GPS / EXIF validation failed. Rejected refund request for Customer.",
						);
					} else {
						botResponse = `✅ REFUND REQUESTED: Telemetry and EXIF audits passed. Your refund request of $8.97 has been submitted. Awaiting Admin HITL approval.`;
						setHitlQueue((prev) => [
							...prev,
							{
								id: "HITL-" + Date.now(),
								type: "customer_refund",
								desc: "AI Support Bot customer order refund",
								amount: 8.97,
								actionData: { customerId: "CUST-Dave" },
							},
						]);
						triggerToast(
							"Support Bot filed refund request. Awaiting HITL authorization.",
							"system",
						);
					}
				}
			}

			setBotMessages((prev) => [...prev, { sender: "bot", text: botResponse }]);
			setIsBotTyping(false);
		}, 1000);
	};

	const handleSendBotMessage = (attachmentUrl = null) => {
		const text = botInputText.trim();
		if (!text && !attachmentUrl) return;

		setBotMessages((prev) => [
			...prev,
			{ sender: "user", text: text || "Vision Image Uploaded", attachmentUrl },
		]);
		setBotInputText("");
		setIsBotTyping(true);

		if (activeRole === "customer") {
			// Connect directly to the Spring Boot Agentic backend via the BFF Gateway
			fetch(`${import.meta.env.VITE_API_BASE_URL}/api/agent/chat`, {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					Authorization: authToken ? `Bearer ${authToken}` : "",
				},
				body: JSON.stringify({
					message: text,
					conversationId: "CONV-CUST-Dave",
					customerId: "CUST-Dave",
				}),
			})
				.then(async (res) => {
					if (res.status === 503) {
						setCircuitBreakerTripped(true);
						triggerToast(
							"BFF Gateway: Agent service offline. Serving rule assistant fallback.",
							"system",
						);
						setBotMessages((prev) => [
							...prev,
							{
								sender: "bot",
								text: "🤖 [OFFLINE MODE] Hi! The Swiss Q-Commerce server is currently offline. Running local rule-based safety responses.",
							},
						]);
						runRulesEngine(text, attachmentUrl);
						return null;
					}
					if (!res.ok) {
						throw new Error("API error");
					}
					return res.json();
				})
				.then((data) => {
					if (!data) return;
					let reply = data.reply;
					logKafka(
						"system",
						"agent.message_processed",
						`Agent conversation processed. Cost: $${data.tokenCost.toFixed(5)}`,
					);

					if (data.hitlStatus) {
						reply += `\n\n[HITL ESCALATION: Request routed to human operator. Ticket ID: ${data.ticketId}]`;
						triggerToast(
							`Low confidence score (${Math.round(data.confidenceScore * 100)}%). Escalated ticket ${data.ticketId} to HITL queue.`,
							"system",
						);
						logKafka(
							"system",
							"agent.hitl_escalated",
							`HITL Ticket generated: ${data.ticketId}. Reason: Confidence score low.`,
						);

						// Re-fetch administrative queue to refresh the Admin view
						fetchHitlQueues();
					}
					setBotMessages((prev) => [...prev, { sender: "bot", text: reply }]);
					setIsBotTyping(false);
				})

				.catch(() => {
					// Transparent fallback to local rule-based system if gateway/backend is not serving the agent
					runRulesEngine(text, attachmentUrl);
				});
		} else {
			runRulesEngine(text, attachmentUrl);
		}
	};

	const downloadRegulatoryReport = () => {
		let csvContent = "data:text/csv;charset=utf-8,";
		csvContent += "ID,Time,Type,Ref Code,Description,Debit,Credit\n";
		ledger.forEach((l) => {
			csvContent += `"${l.id}","${l.time}","${l.type}","${l.ref}","${l.desc}","${l.debit.toFixed(2)}","${l.credit.toFixed(2)}"\n`;
		});
		const encodedUri = encodeURI(csvContent);
		const link = document.createElement("a");
		link.setAttribute("href", encodedUri);
		link.setAttribute("download", "swiss-audit-ledger.csv");
		document.body.appendChild(link);
		link.click();
		document.body.removeChild(link);
		triggerToast("OLAP CSV Regulatory Export download successful.", "business");
	};

	const generateCertificate = (role) => {
		setSelectedCertRole(role);
		setCertModalOpen(true);
		setTimeout(() => {
			const canvas = canvasRef.current;
			if (!canvas) return;
			const ctx = canvas.getContext("2d");
			ctx.fillStyle = "#070a13";
			ctx.fillRect(0, 0, 560, 360);

			ctx.strokeStyle =
				role === "rider"
					? "#f59e0b"
					: role === "picker"
						? "#3b82f6"
						: role === "b2b"
							? "#a855f7"
							: "#10b981";
			ctx.lineWidth = 10;
			ctx.strokeRect(20, 20, 520, 320);

			ctx.fillStyle = "#ffffff";
			ctx.font = "bold 24px sans-serif";
			ctx.textAlign = "center";
			ctx.fillText("SWISS QUICK COMMERCE ACADEMY", 280, 80);

			ctx.fillStyle = "var(--text-secondary)";
			ctx.font = "14px sans-serif";
			ctx.fillText(
				"This certifies that the user has completed course requirements for",
				280,
				130,
			);

			ctx.fillStyle = ctx.strokeStyle;
			ctx.font = "bold 20px sans-serif";
			const roleTitle =
				role.toUpperCase() === "RIDER"
					? "IoT COLD CHAIN LOGISTICS EXPERT"
					: role.toUpperCase() === "PICKER"
						? "SLA PICKER COMPLIANCE SPECIALIST"
						: role.toUpperCase() === "B2B"
							? "CERTIFIED WHOLESALE REPLENISHMENT LEADER"
							: "LOYAL GREEN EXPRESS BUYER";
			ctx.fillText(roleTitle, 280, 180);

			ctx.fillStyle = "#ffffff";
			ctx.font = "12px monospace";
			ctx.fillText(`Certificate ID: SEC-ACAD-${Date.now()}`, 280, 240);
			ctx.fillText(
				"HashiCorp Vault Sign Key: SHA256-VAULT-AUTHENTIC",
				280,
				260,
			);

			ctx.fillStyle = "rgba(255,255,255,0.03)";
			ctx.font = "bold 80px sans-serif";
			ctx.fillText("VERIFIED", 280, 220);
		}, 100);
	};

	const handleDownloadCert = () => {
		const canvas = canvasRef.current;
		if (!canvas) return;
		const url = canvas.toDataURL("image/png");
		const link = document.createElement("a");
		link.href = url;
		link.download = `swiss-cert-${selectedCertRole}.png`;
		document.body.appendChild(link);
		link.click();
		document.body.removeChild(link);
	};

	const hasRoleAccess = (targetRole) => {
		if (!isAuthenticated) return false;
		if (currentUserSession?.role === "admin") return true;
		return currentUserSession?.role === targetRole;
	};

	return (
		<div
			className="app-container"
			data-active-role={activeRole}
			style={{ minHeight: "100vh", display: "flex", flexDirection: "column" }}
		>
			<style>{`
        /* Bot Message window animation */
        @keyframes scale-up-bot {
          from {
            opacity: 0;
            transform: scale(0.9) translateY(20px);
          }
          to {
            opacity: 1;
            transform: scale(1) translateY(0);
          }
        }
        
        .ai-bot-window {
          animation: scale-up-bot 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards !important;
        }

        /* Bot Messages slide-in */
        @keyframes message-slide-in {
          from {
            opacity: 0;
            transform: translateY(10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }

        .ai-message {
          animation: message-slide-in 0.25s ease-out forwards;
        }

        /* Cart drawer entry and state updates transition */
        @keyframes drawer-slide-in {
          from {
            opacity: 0;
            transform: translateX(30px);
          }
          to {
            opacity: 1;
            transform: translateX(0);
          }
        }

        .customer-cart-drawer {
          animation: drawer-slide-in 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
          transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }

        /* Scale pop micro-animation for updates */
        @keyframes scale-pop {
          0% { transform: scale(1); }
          50% { transform: scale(1.15); }
          100% { transform: scale(1); }
        }

        .scale-pop-animation {
          animation: scale-pop 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
        }
      `}</style>

			<div className="toast-container">
				{toasts.map((t) => (
					<div key={t.id} className={`toast toast-border-${t.borderType}`}>
						<Lucide.Bell size={16} className={`event-${t.borderType}`} />
						<div>{t.msg}</div>
					</div>
				))}
			</div>

			<MfaLoginPortal
				isAuthenticated={isAuthenticated}
				activeProfile={activeProfile}
				mfaStep={mfaStep}
				setMfaStep={setMfaStep}
				mfaRole={mfaRole}
				setMfaRole={setMfaRole}
				mfaPassword={mfaPassword}
				setMfaPassword={setMfaPassword}
				mfaOtpInput={mfaOtpInput}
				setMfaOtpInput={setMfaOtpInput}
				mfaMethod={mfaMethod}
				setMfaMethod={setMfaMethod}
				totpSecretCode={totpSecretCode}
				totpTimer={totpTimer}
				handleMfaSendOtp={handleMfaSendOtp}
				handleMfaVerify={handleMfaVerify}
			/>

			<header className="app-header">
				<div className="brand-section">
					<div className="brand-logo">
						<Lucide.Zap size={22} fill="currentColor" />
						<h1
							style={{
								margin: 0,
								padding: 0,
								fontSize: "inherit",
								fontWeight: "inherit",
								display: "inline-flex",
								alignItems: "center",
							}}
						>
							swiss_App
						</h1>
					</div>
					<span className="brand-badge">
						Quick Commerce Architecture V1.0 (Federated MFEs)
					</span>

					<div
						style={{
							marginLeft: "1rem",
							display: "inline-flex",
							alignItems: "center",
							gap: "0.4rem",
							background: "rgba(255,255,255,0.03)",
							padding: "0.2rem 0.5rem",
							borderRadius: "6px",
							border: "1px solid var(--border-color)",
						}}
					>
						<Lucide.Globe size={12} className="event-system" />
						<select
							id="select-env-profile"
							value={activeProfileKey}
							onChange={(e) => {
								const newProfile = e.target.value;
								setActiveProfileKey(newProfile);
								triggerToast(
									`ENVIRONMENT SWITCHED: Loaded config for [${newProfile.toUpperCase()}] profile.`,
									"system",
								);
								logKafka(
									"system",
									"profile.switched",
									`Switched environment profile to ${newProfile.toUpperCase()}. Loaded variables: Rate Limit = ${envProfiles[newProfile].rateLimit} req/s, Base Latency = ${envProfiles[newProfile].dbLatencyDefault}ms, MFA = ${envProfiles[newProfile].mfaRequired ? "Required" : "Bypassed"}.`,
								);
							}}
							style={{
								background: "transparent",
								color: "var(--text-primary)",
								border: "none",
								fontSize: "0.7rem",
								fontWeight: "bold",
								fontFamily: "var(--font-sans)",
								outline: "none",
								cursor: "pointer",
							}}
						>
							<option
								value="development"
								style={{ background: "#0b0f19", color: "#ffffff" }}
							>
								development
							</option>
							<option
								value="staging"
								style={{ background: "#0b0f19", color: "#ffffff" }}
							>
								staging
							</option>
							<option
								value="production"
								style={{ background: "#0b0f19", color: "#ffffff" }}
							>
								production
							</option>
						</select>
					</div>
				</div>

				<nav className="role-navigation">
					<button
						aria-label="Customer Tab"
						id="tab-customer"
						data-role="customer"
						className={`role-tab ${activeRole === "customer" ? "active" : ""}`}
						onClick={() => handleRoleChange("customer")}
					>
						<Lucide.ShoppingBag size={15} />
						<span>Customer Super App</span>
					</button>
					<button
						aria-label="Rider Tab"
						id="tab-rider"
						data-role="rider"
						className={`role-tab ${activeRole === "rider" ? "active" : ""}`}
						onClick={() => handleRoleChange("rider")}
					>
						<Lucide.Bike size={15} />
						<span>Rider Light</span>
					</button>
					<button
						aria-label="Inventory Tab"
						id="tab-inventory"
						data-role="inventory"
						className={`role-tab ${activeRole === "inventory" ? "active" : ""}`}
						onClick={() => handleRoleChange("inventory")}
					>
						<Lucide.Package size={15} />
						<span>Dark Store Inventory</span>
					</button>
					<button
						aria-label="Business Tab"
						id="tab-business"
						data-role="business"
						className={`role-tab ${activeRole === "business" ? "active" : ""}`}
						onClick={() => handleRoleChange("business")}
					>
						<Lucide.BarChart3 size={15} />
						<span>B2B Retailer Hub</span>
					</button>
					<button
						aria-label="Admin Tab"
						id="tab-admin"
						data-role="admin"
						className={`role-tab ${activeRole === "admin" ? "active" : ""}`}
						onClick={() => handleRoleChange("admin")}
					>
						<Lucide.ShieldCheck size={15} />
						<span>System Admin</span>
					</button>
					{isAuthenticated && (
						<button
							aria-label="Logout"
							className="role-tab"
							data-role="admin"
							style={{
								color: "var(--color-admin)",
								borderColor: "rgba(239, 68, 68, 0.2)",
							}}
							onClick={handleLogout}
						>
							<Lucide.LogOut size={15} />
							<span>Lock Cockpit</span>
						</button>
					)}
				</nav>
			</header>

			{activeOrder && (
				<div
					style={{
						display: "flex",
						alignItems: "center",
						justifyContent: "space-between",
						background: "rgba(15, 23, 42, 0.9)",
						borderBottom: "1px solid rgba(59, 130, 246, 0.25)",
						padding: "0.4rem 1.25rem",
						backdropFilter: "blur(10px)",
						animation: "fadeIn 0.5s ease-out",
						position: "relative",
						zIndex: 10,
					}}
				>
					<div style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
						<Lucide.Zap
							size={14}
							className="animate-pulse"
							style={{ color: "#fbbf24" }}
						/>
						<div>
							<span
								style={{
									fontSize: "0.7rem",
									fontWeight: 800,
									color: "#f8fafc",
									letterSpacing: "0.05em",
								}}
							>
								⚡ ACTIVE SLA COUNTDOWN
							</span>
							<span
								style={{
									fontSize: "0.65rem",
									color: "#94a3b8",
									marginLeft: "0.5rem",
								}}
							>
								Order #{activeOrder.id} • Status:{" "}
								<strong style={{ color: "#10b981" }}>
									{activeOrder.status.toUpperCase()}
								</strong>
							</span>
						</div>
					</div>

					<div style={{ display: "flex", alignItems: "center", gap: "0.4rem" }}>
						<span
							style={{
								fontSize: "0.7rem",
								fontWeight: "bold",
								fontFamily: "monospace",
								color: activeOrder.slaRemaining < 30 ? "#ef4444" : "#fbbf24",
							}}
						>
							{activeOrder.slaRemaining}s remaining
						</span>
						<svg width="20" height="20" viewBox="0 0 24 24">
							<circle
								cx="12"
								cy="12"
								r="10"
								fill="none"
								stroke="rgba(255,255,255,0.05)"
								strokeWidth="2.5"
							/>
							<circle
								cx="12"
								cy="12"
								r="10"
								fill="none"
								stroke={activeOrder.slaRemaining < 30 ? "#ef4444" : "#fbbf24"}
								strokeWidth="2.5"
								strokeDasharray="63"
								strokeDashoffset={
									63 -
									(Math.min(
										activeOrder.slaRemaining,
										activeOrder.status === "picking"
											? activePickingCongested
												? 280
												: 180
											: 180,
									) /
										(activeOrder.status === "picking"
											? activePickingCongested
												? 280
												: 180
											: 180)) *
										63
								}
								strokeLinecap="round"
								transform="rotate(-90 12 12)"
								style={{
									transition: "stroke-dashoffset 1s linear, stroke 0.3s ease",
								}}
							/>
						</svg>
					</div>
				</div>
			)}

			<main className="cockpit-main-layout">
				<section className="workspace-main-panel">
					{/* ── Live Rider Tracking Panel (Global — visible on all tabs during transit) ── */}
					<RiderTrackingPanel
						activeOrder={activeOrder}
						riderCoords={riderCoords}
						onInjectDryIce={handleInjectDryIce}
					/>

					<Suspense
						fallback={
							<div
								className="glass-card"
								style={{ padding: "3rem", textAlign: "center" }}
							>
								<Lucide.Loader2 size={28} className="spin" />
								<p
									style={{
										color: "var(--text-secondary)",
										marginTop: "0.8rem",
									}}
								>
									Loading Federated Micro-Frontend...
								</p>
							</div>
						}
					>
						{activeRole === "customer" &&
							(hasRoleAccess("customer") ? (
								<LocalErrorBoundary name="Customer App">
									<CustomerApp
										products={products}
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
										savedAddresses={savedAddresses}
										savedCards={savedCards}
										favorites={favorites}
										vipMember={vipMember}
										vouchers={vouchers}
										customerTrustScore={customerTrustScore}
										gdprTokenProbation={gdprTokenProbation}
										handleGdprPurge={handleGdprPurge}
										orderHistory={orderHistory}
										esgCheckbox={esgCheckbox}
										setEsgCheckbox={setEsgCheckbox}
										tipAmount={tipAmount}
										setTipAmount={setTipAmount}
										handleCheckout={handleCheckout}
										activeOrder={activeOrder}
										generateCertificate={generateCertificate}
										catalogLoading={catalogLoading}
									/>
								</LocalErrorBoundary>
							) : (
								<RbacBlocker
									targetRole="customer"
									currentUserSession={currentUserSession}
									handleLogout={handleLogout}
									logKafka={logKafka}
									triggerToast={triggerToast}
								/>
							))}

						{activeRole === "rider" &&
							(hasRoleAccess("rider") ? (
								<LocalErrorBoundary name="Rider App">
									<RiderApp
										riderWallet={riderWallet}
										riderTrustScore={riderTrustScore}
										riderOnboardStatus={riderOnboardStatus}
										setRiderOnboardStatus={setRiderOnboardStatus}
										activeOrder={activeOrder}
										generateCertificate={generateCertificate}
										coldChainBreakdownActive={coldChainBreakdownActive}
										handleInjectDryIce={handleInjectDryIce}
										handleApplyOnboard={handleApplyOnboard}
										handleCompleteDelivery={handleOrderDeliveryComplete}
										logKafka={logKafka}
									/>
								</LocalErrorBoundary>
							) : (
								<RbacBlocker
									targetRole="rider"
									currentUserSession={currentUserSession}
									handleLogout={handleLogout}
									logKafka={logKafka}
									triggerToast={triggerToast}
								/>
							))}

						{activeRole === "inventory" &&
							(hasRoleAccess("inventory") ? (
								<LocalErrorBoundary name="Inventory App">
									<InventoryApp
										products={products}
										setProducts={setProducts}
										pickerTrustScore={pickerTrustScore}
										pickerBadge={pickerBadge}
										activeOrder={activeOrder}
										activeStockTransfers={activeStockTransfers}
										handleBalanceStores={handleBalanceStores}
										handlePickerCheckItem={null}
										handlePickerHandover={handlePickerHandover}
										handleDeployBackupPicker={handleDeployBackupPicker}
										pickingBacklogQueue={pickingBacklogQueue}
										activePickingCongested={activePickingCongested}
									/>
								</LocalErrorBoundary>
							) : (
								<RbacBlocker
									targetRole="inventory"
									currentUserSession={currentUserSession}
									handleLogout={handleLogout}
									logKafka={logKafka}
									triggerToast={triggerToast}
								/>
							))}

						{activeRole === "business" &&
							(hasRoleAccess("business") ? (
								<LocalErrorBoundary name="B2B Retailer Hub">
									<B2bDashboard />
								</LocalErrorBoundary>
							) : (
								<RbacBlocker
									targetRole="business"
									currentUserSession={currentUserSession}
									handleLogout={handleLogout}
									logKafka={logKafka}
									triggerToast={triggerToast}
								/>
							))}

						{activeRole === "admin" &&
							(hasRoleAccess("admin") ? (
								<LocalErrorBoundary name="Admin Panel">
									<AdminPanel
										coldChainBreakdownActive={coldChainBreakdownActive}
										setColdChainBreakdownActive={setColdChainBreakdownActive}
										wholesalerOutageActive={wholesalerOutageActive}
										setWholesalerOutageActive={setWholesalerOutageActive}
										paymentOutageActive={paymentOutageActive}
										setPaymentOutageActive={setPaymentOutageActive}
										redisCrashActive={redisCrashActive}
										setRedisCrashActive={setRedisCrashActive}
										dbLatencyActive={dbLatencyActive}
										setDbLatencyActive={setDbLatencyActive}
										riderTrafficActive={riderTrafficActive}
										setRiderTrafficActive={setRiderTrafficActive}
										simulateTelemetryFraud={simulateTelemetryFraud}
										setSimulateTelemetryFraud={setSimulateTelemetryFraud}
										onboardingQueue={onboardingQueue}
										handleApproveOnboard={handleApproveOnboard}
										hitlQueue={hitlQueue}
										handleReleaseHitl={handleReleaseHitl}
										handleVoidHitl={handleVoidHitl}
										hitlLoading={hitlLoading}
									/>
								</LocalErrorBoundary>
							) : (
								<RbacBlocker
									targetRole="admin"
									currentUserSession={currentUserSession}
									handleLogout={handleLogout}
									logKafka={logKafka}
									triggerToast={triggerToast}
								/>
							))}
					</Suspense>
				</section>

				<LocalErrorBoundary name="System Control Room">
					<Suspense
						fallback={
							<div className="engine-room-loading">
								Loading Telemetry Control Room...
							</div>
						}
					>
						<SystemEngineRoom
							rateLimitActive={rateLimitActive}
							dbLatencyActive={dbLatencyActive}
							redisCrashActive={redisCrashActive}
							paymentOutageActive={paymentOutageActive}
							riderTrafficActive={riderTrafficActive}
							circuitBreakerTripped={circuitBreakerTripped}
							activeProfile={activeProfile}
							oltpWriteLatency={oltpWriteLatency}
							olapSyncTimer={olapSyncTimer}
							jwtFlash={jwtFlash}
							vaultTimer={vaultTimer}
							latencyHistory={latencyHistory}
							cacheHits={cacheHits}
							cacheMisses={cacheMisses}
							kafkaLogs={kafkaLogs}
							agentMetrics={agentMetrics}
						/>
					</Suspense>
				</LocalErrorBoundary>
			</main>

			{certModalOpen && (
				<div className="cert-modal-overlay">
					<div className="cert-modal-content">
						<div
							style={{
								width: "100%",
								display: "flex",
								justifyContent: "space-between",
								alignItems: "center",
							}}
						>
							<h4
								style={{
									fontWeight: 800,
									color: "var(--color-business)",
									display: "flex",
									alignItems: "center",
									gap: "0.4rem",
								}}
							>
								<Lucide.Sparkles size={18} />
								Swiss Loyalty Certificate Desk
							</h4>
							<button
								aria-label="Close"
								className="ai-bot-close-btn"
								onClick={() => setCertModalOpen(false)}
							>
								<Lucide.X size={18} />
							</button>
						</div>

						<canvas
							ref={canvasRef as any}
							width="560"
							height="360"
							className="cert-canvas"
						/>

						<div className="cert-modal-actions">
							<button
								aria-label="Download Certificate"
								className="btn-primary-glow"
								style={{
									background: "var(--color-business)",
									color: "#ffffff",
									cursor: "pointer",
								}}
								onClick={handleDownloadCert}
							>
								Download Certificate (.PNG)
							</button>
							<button
								aria-label="Dismiss Modal"
								className="btn-secondary-glow"
								style={{ cursor: "pointer" }}
								onClick={() => setCertModalOpen(false)}
							>
								Dismiss Desk
							</button>
						</div>
					</div>
				</div>
			)}

			<SupportBot
				botOpen={botOpen}
				setBotOpen={setBotOpen}
				botMessages={botMessages}
				botInputText={botInputText}
				setBotInputText={setBotInputText}
				handleSendBotMessage={handleSendBotMessage}
				triggerToast={triggerToast}
				activeRole={activeRole}
				isBotTyping={isBotTyping}
			/>
		</div>
	);
}
