import * as Lucide from "lucide-react";
import React, { useEffect, useRef } from "react";

const BOT_TRANSLATIONS: Record<string, Record<string, string>> = {
	en: {
		headerTitleCustomer: "Swiss AI Help Center",
		headerTitleRider: "Rider Operations Copilot",
		headerTitleInventory: "Dark Store Picker Assistant",
		placeholderCustomer: "Ask about orders, refunds...",
		placeholderRider: "Report breakdown, traffic...",
		placeholderInventory: "Report damaged, spoiled shelf item...",
		actionTrack: "Track Order",
		actionRefund: "Request Refund",
		actionBreakdown: "Report Breakdown",
		actionSpoiled: "Report Damaged",
		agentConsult: "Consult AI Agent",
		botTyping: "Agent is typing...",
	},
	fr: {
		headerTitleCustomer: "Centre d'Aide Swiss AI",
		headerTitleRider: "Copilote Livreur",
		headerTitleInventory: "Assistant Inventaire Magasin",
		placeholderCustomer: "Posez vos questions sur vos commandes, remboursements...",
		placeholderRider: "Signaler une panne, du trafic...",
		placeholderInventory: "Signaler un article endommagé ou périmé...",
		actionTrack: "Suivre la commande",
		actionRefund: "Demander un remboursement",
		actionBreakdown: "Signaler une panne",
		actionSpoiled: "Signaler un produit gâté",
		agentConsult: "Consulter l'Agent AI",
		botTyping: "L'agent écrit...",
	},
	de: {
		headerTitleCustomer: "Swiss AI Service-Center",
		headerTitleRider: "Fahrer Operations Copilot",
		headerTitleInventory: "Dunkellager Kommissionierer-Hilfe",
		placeholderCustomer: "Fragen zu Bestellungen, Rückerstattungen...",
		placeholderRider: "Panne, Stau melden...",
		placeholderInventory: "Beschädigte oder verdorbene Ware melden...",
		actionTrack: "Bestellung verfolgen",
		actionRefund: "Erstattung anfordern",
		actionBreakdown: "Panne melden",
		actionSpoiled: "Defekte Ware melden",
		agentConsult: "AI-Agent konsultieren",
		botTyping: "Agent schreibt...",
	},
	it: {
		headerTitleCustomer: "Centro Assistenza Swiss AI",
		headerTitleRider: "Copilota Operazioni Rider",
		headerTitleInventory: "Assistente Inventario Magazzino",
		placeholderCustomer: "Chiedi su ordini, rimborsi...",
		placeholderRider: "Segnala guasto, traffico...",
		placeholderInventory: "Segnala articolo danneggiato o scaduto...",
		actionTrack: "Traccia Ordine",
		actionRefund: "Richiedi Rimborso",
		actionBreakdown: "Segnala Guasto",
		actionSpoiled: "Segnala Prodotto Danneggiato",
		agentConsult: "Consulta Agente AI",
		botTyping: "L'agente sta scrivendo...",
	},
	ar: {
		headerTitleCustomer: "مركز دعم الذكاء الاصطناعي السويسري",
		headerTitleRider: "مساعد عمليات السائق بالذكاء الاصطناعي",
		headerTitleInventory: "مساعد أمين المخزن",
		placeholderCustomer: "اسأل عن الطلبات، المبالغ المستردة...",
		placeholderRider: "أبلغ عن عطل، حركة المرور...",
		placeholderInventory: "الإبلاغ عن عنصر تالف أو منتهي الصلاحية...",
		actionTrack: "تتبع الطلب",
		actionRefund: "طلب استرداد الأموال",
		actionBreakdown: "الإبلاغ عن عطل",
		actionSpoiled: "الإبلاغ عن منتج تالف",
		agentConsult: "استشارة وكيل الذكاء الاصطناعي",
		botTyping: "الوكيل يكتب الآن...",
	},
};

const parseInline = (text: string) => {
	if (!text) return "";
	const parts = text.split(/\*\*([\s\S]*?)\*\*/g);
	return parts.map((part, i) => {
		if (i % 2 === 1) {
			return (
				<strong key={i} style={{ color: "#38bdf8" }}>
					{part}
				</strong>
			);
		}
		return part;
	});
};

const parseMarkdown = (text: string) => {
	if (!text) return null;
	const lines = text.split("\n");
	return lines.map((line, idx) => {
		const trimmed = line.trim();
		if (trimmed.startsWith("### ")) {
			return (
				<h3
					key={idx}
					style={{ margin: "0.4rem 0", color: "var(--text-primary)", fontSize: "1rem" }}
				>
					{parseInline(trimmed.slice(4))}
				</h3>
			);
		}
		if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
			return (
				<li
					key={idx}
					style={{
						marginLeft: "1rem",
						listStyleType: "disc",
						color: "var(--text-secondary)",
					}}
				>
					{parseInline(trimmed.slice(2))}
				</li>
			);
		}
		return (
			<p
				key={idx}
				style={{ margin: "0.25rem 0", minHeight: "1em", lineHeight: "1.4" }}
			>
				{parseInline(line)}
			</p>
		);
	});
};

const isSafeUrl = (url: string | null | undefined): boolean => {
	if (!url) return false;
	const cleanUrl = url.trim().toLowerCase();
	if (
		cleanUrl.startsWith("/") ||
		cleanUrl.startsWith("./") ||
		cleanUrl.startsWith("../")
	) {
		return true;
	}
	try {
		const parsed = new URL(url);
		return ["http:", "https:"].includes(parsed.protocol);
	} catch {
		return false;
	}
};

const TypewriterText = ({ text, onComplete }: { text: string; onComplete: () => void }) => {
	const [displayed, setDisplayed] = React.useState("");

	React.useEffect(() => {
		let i = 0;
		setDisplayed("");
		const timer = setInterval(() => {
			setDisplayed((prev) => prev + text.charAt(i));
			i++;
			if (i >= text.length) {
				clearInterval(timer);
				if (onComplete) onComplete();
			}
		}, 8); // Fast typing speed
		return () => clearInterval(timer);
	}, [text, onComplete]);

	const isComplete = displayed.length === text.length;

	return (
		<>
			{parseMarkdown(displayed)}
			{!isComplete && <span className="ai-type-cursor" />}
		</>
	);
};

export default function SupportBot({
	botOpen,
	setBotOpen,
	botMessages,
	botInputText,
	setBotInputText,
	handleSendBotMessage,
	triggerToast,
	activeRole,
	isBotTyping,
	theme = "dark",
	setTheme,
	language = "en",
	setLanguage,
}: {
	botOpen: boolean;
	setBotOpen: (val: boolean) => void;
	botMessages: any[];
	botInputText: string;
	setBotInputText: (val: string) => void;
	handleSendBotMessage: (attachmentUrl?: string | null) => void;
	triggerToast?: (msg: string, type?: string) => void;
	activeRole: string;
	isBotTyping: boolean;
	theme?: string;
	setTheme?: (val: string) => void;
	language?: string;
	setLanguage?: (val: string) => void;
}) {
	const messagesEndRef = useRef<any>(null);
	const [lastStreamedIndex, setLastStreamedIndex] = React.useState(-1);

	useEffect(() => {
		if (messagesEndRef.current) {
			messagesEndRef.current.scrollIntoView({ behavior: "smooth" });
		}
	}, [botMessages, isBotTyping]);

	if (!botOpen) {
		return (
			<div
				id="btn-support-bot-open"
				className="ai-bot-toggle-btn"
				onClick={() => setBotOpen(true)}
			>
				<Lucide.Bot size={24} />
			</div>
		);
	}

	const handleAttachPhoto = () => {
		if (triggerToast) {
			triggerToast(
				"Simulated camera capture: expired_milk.png uploaded.",
				"system",
			);
		}
		if (handleSendBotMessage) {
			handleSendBotMessage(
				"https://images.unsplash.com/photo-1550583724-b2692b85b150?auto=format&fit=crop&q=80&w=200",
			);
		}
	};

	const handleVoiceInput = () => {
		setBotInputText(language === "fr" ? "Où est ma commande ?" : language === "de" ? "Wo ist meine Bestellung?" : language === "it" ? "Dov'è il mio ordine?" : "Where is my order?");
		if (triggerToast) {
			triggerToast(language === "fr" ? 'Voix reconnue: "Où est ma commande ?"' : language === "de" ? 'Sprache erkannt: "Wo ist meine Bestellung?"' : language === "it" ? 'Voce riconosciuta: "Dov\'è il mio ordine?"' : 'Voice recognized: "Where is my order?"', "system");
		}
	};

	const currentTrans = BOT_TRANSLATIONS[language] || BOT_TRANSLATIONS.en;

	const getHeaderTitle = () => {
		if (activeRole === "rider") return currentTrans.headerTitleRider;
		if (activeRole === "inventory") return currentTrans.headerTitleInventory;
		return currentTrans.headerTitleCustomer;
	};

	const getInputPlaceholder = () => {
		if (activeRole === "rider") return currentTrans.placeholderRider;
		if (activeRole === "inventory") return currentTrans.placeholderInventory;
		return currentTrans.placeholderCustomer;
	};

	return (
		<div>
			<div
				id="btn-support-bot-open"
				className="ai-bot-toggle-btn"
				onClick={() => setBotOpen(false)}
			>
				<Lucide.MessageSquareDashed size={24} />
			</div>

			<div className="glass-card ai-bot-window" dir={language === "ar" ? "rtl" : "ltr"}>
				<div className="ai-bot-header">
					<div className="ai-bot-header-title">
						<Lucide.Bot size={18} />
						<span>{getHeaderTitle()}</span>
					</div>

					<div style={{ display: "flex", alignItems: "center", gap: "0.4rem", marginRight: "0.5rem" }}>
						{/* Day/Night theme toggle within chatbot */}
						{setTheme && (
							<button
								aria-label="Toggle Theme"
								onClick={() => setTheme(theme === "light" ? "dark" : "light")}
								style={{
									background: "transparent",
									border: "none",
									color: "var(--text-primary)",
									cursor: "pointer",
									display: "inline-flex",
									padding: "0.2rem",
									borderRadius: "4px"
								}}
								title="Switch Day/Night Theme"
							>
								{theme === "light" ? <Lucide.Moon size={14} /> : <Lucide.Sun size={14} />}
							</button>
						)}

						{/* Inline Language selector inside chatbot */}
						{setLanguage && (
							<select
								value={language}
								onChange={(e) => setLanguage(e.target.value)}
								style={{
									background: "rgba(255,255,255,0.05)",
									color: "var(--text-primary)",
									border: "1px solid var(--border-default)",
									borderRadius: "4px",
									fontSize: "0.65rem",
									fontWeight: "bold",
									outline: "none",
									cursor: "pointer",
									padding: "0.1rem 0.2rem",
								}}
							>
								<option value="en" style={{ background: "var(--bg-surface)", color: "var(--text-primary)" }}>EN</option>
								<option value="de" style={{ background: "var(--bg-surface)", color: "var(--text-primary)" }}>DE</option>
								<option value="fr" style={{ background: "var(--bg-surface)", color: "var(--text-primary)" }}>FR</option>
								<option value="it" style={{ background: "var(--bg-surface)", color: "var(--text-primary)" }}>IT</option>
								<option value="ar" style={{ background: "var(--bg-surface)", color: "var(--text-primary)" }}>AR</option>
							</select>
						)}
					</div>

					<button
						aria-label="Button"
						id="btn-support-bot-close"
						className="ai-bot-close-btn"
						onClick={() => setBotOpen(false)}
					>
						<Lucide.X size={16} />
					</button>
				</div>

				<div className="ai-bot-messages">
					{botMessages.map((msg, idx) => {
						const isLast = idx === botMessages.length - 1;
						const isBot = msg.sender === "bot";
						const shouldStream = isLast && isBot && idx > lastStreamedIndex;

						return (
							<div
								key={idx}
								className={`ai-message ${msg.sender === "user" ? "ai-message-user" : "ai-message-bot"}`}
							>
								{shouldStream ? (
									<TypewriterText
										text={msg.text}
										onComplete={() => setLastStreamedIndex(idx)}
									/>
								) : (
									parseMarkdown(msg.text)
								)}
								{msg.attachmentUrl && isSafeUrl(msg.attachmentUrl) && (
									<img
										src={msg.attachmentUrl}
										className="ai-message-attachment"
										alt="Simulated vision report upload"
									/>
								)}
							</div>
						);
					})}
					{isBotTyping && (
						<div className="ai-message ai-message-bot typing-indicator-bubble">
							<span className="dot"></span>
							<span className="dot"></span>
							<span className="dot"></span>
						</div>
					)}
					<div ref={messagesEndRef} />
				</div>

				{/* Quick Actions Panel */}
				<div
					style={{
						display: "flex",
						gap: "0.4rem",
						padding: "0.4rem 0.75rem",
						overflowX: "auto",
						borderTop: "1px solid var(--border-default)",
						background: "var(--bg-elevated)",
						scrollbarWidth: "none"
					}}
				>
					{activeRole === "customer" && (
						<>
							<button
								onClick={() => {
									setBotInputText(currentTrans.actionTrack);
									setTimeout(() => handleSendBotMessage(), 50);
								}}
								style={{ flexShrink: 0, background: "var(--accent-muted)", color: "var(--accent)", border: "1px solid var(--border-glow)", borderRadius: "12px", padding: "0.25rem 0.6rem", fontSize: "0.7rem", fontWeight: "600", cursor: "pointer" }}
							>
								{currentTrans.actionTrack}
							</button>
							<button
								onClick={() => {
									setBotInputText(currentTrans.actionRefund);
									setTimeout(() => handleSendBotMessage(), 50);
								}}
								style={{ flexShrink: 0, background: "var(--accent-muted)", color: "var(--accent)", border: "1px solid var(--border-glow)", borderRadius: "12px", padding: "0.25rem 0.6rem", fontSize: "0.7rem", fontWeight: "600", cursor: "pointer" }}
							>
								{currentTrans.actionRefund}
							</button>
						</>
					)}
					{activeRole === "rider" && (
						<>
							<button
								onClick={() => {
									setBotInputText(currentTrans.actionBreakdown);
									setTimeout(() => handleSendBotMessage(), 50);
								}}
								style={{ flexShrink: 0, background: "var(--accent-muted)", color: "var(--accent)", border: "1px solid var(--border-glow)", borderRadius: "12px", padding: "0.25rem 0.6rem", fontSize: "0.7rem", fontWeight: "600", cursor: "pointer" }}
							>
								{currentTrans.actionBreakdown}
							</button>
						</>
					)}
					{(activeRole === "inventory" || activeRole === "admin") && (
						<>
							<button
								onClick={() => {
									setBotInputText(currentTrans.actionSpoiled);
									setTimeout(() => handleSendBotMessage(), 50);
								}}
								style={{ flexShrink: 0, background: "var(--accent-muted)", color: "var(--accent)", border: "1px solid var(--border-glow)", borderRadius: "12px", padding: "0.25rem 0.6rem", fontSize: "0.7rem", fontWeight: "600", cursor: "pointer" }}
							>
								{currentTrans.actionSpoiled}
							</button>
						</>
					)}
					<button
						onClick={() => {
							setBotInputText(currentTrans.agentConsult);
							setTimeout(() => handleSendBotMessage(), 50);
						}}
						style={{ flexShrink: 0, background: "var(--bg-muted)", color: "var(--text-secondary)", border: "1px solid var(--border-default)", borderRadius: "12px", padding: "0.25rem 0.6rem", fontSize: "0.7rem", fontWeight: "600", cursor: "pointer" }}
					>
						{currentTrans.agentConsult}
					</button>
				</div>

				<div className="ai-bot-input-area">
					<input
						id="input-support-bot"
						type="text"
						className="ai-bot-input"
						placeholder={getInputPlaceholder()}
						value={botInputText}
						onChange={(e) => setBotInputText(e.target.value)}
						onKeyDown={(e) => e.key === "Enter" && handleSendBotMessage()}
					/>

					<button
						aria-label="Button"
						id="btn-support-bot-attach"
						className="ai-bot-action-btn"
						title="Attach photo (Simulated)"
						onClick={handleAttachPhoto}
					>
						<Lucide.Image size={16} />
					</button>

					<button
						aria-label="Button"
						id="btn-support-bot-voice"
						className="ai-bot-action-btn"
						title="Send voice message (Simulated)"
						onClick={handleVoiceInput}
					>
						<Lucide.Mic size={16} />
					</button>

					<button
						aria-label="Button"
						id="btn-support-bot-send"
						className="ai-bot-action-btn"
						onClick={() => handleSendBotMessage()}
					>
						<Lucide.Send size={16} />
					</button>
				</div>
			</div>
		</div>
	);
}
