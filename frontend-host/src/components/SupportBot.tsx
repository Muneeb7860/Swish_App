import * as Lucide from "lucide-react";
import React, { useEffect, useRef } from "react";

const parseInline = (text) => {
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

const parseMarkdown = (text) => {
	if (!text) return null;
	const lines = text.split("\n");
	return lines.map((line, idx) => {
		const trimmed = line.trim();
		if (trimmed.startsWith("### ")) {
			return (
				<h3
					key={idx}
					style={{ margin: "0.4rem 0", color: "#f8fafc", fontSize: "1rem" }}
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
						color: "#cbd5e1",
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

const TypewriterText = ({ text, onComplete }) => {
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

	return <>{parseMarkdown(displayed)}</>;
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
}) {
	const messagesEndRef = useRef<any>(null);
	const [lastStreamedIndex, setLastStreamedIndex] = React.useState(-1);

	useEffect(() => {
		if (messagesEndRef.current) {
			messagesEndRef.current.scrollIntoView({ behavior: "smooth" });
		}
	}, [botMessages, botOpen, isBotTyping]);

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
		setBotInputText("Where is my order?");
		if (triggerToast) {
			triggerToast('Voice recognized: "Where is my order?"', "system");
		}
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

			<div className="glass-card ai-bot-window">
				<div className="ai-bot-header">
					<div className="ai-bot-header-title">
						<Lucide.Bot size={18} />
						<span>
							{activeRole === "rider"
								? "Rider Operations Copilot"
								: activeRole === "inventory"
									? "Dark Store Picker Assistant"
									: "Swiss AI Help Center"}
						</span>
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

				<div className="ai-bot-input-area">
					<input
						id="input-support-bot"
						type="text"
						className="ai-bot-input"
						placeholder={
							activeRole === "rider"
								? "Report breakdown, traffic..."
								: activeRole === "inventory"
									? "Report damaged, spoiled shelf item..."
									: "Ask about orders, refunds..."
						}
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
