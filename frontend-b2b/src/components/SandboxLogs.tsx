import type React from "react";

interface LogEntry {
	text: string;
	type: "info" | "success" | "warning" | "error";
	time: string;
}

interface SandboxLogsProps {
	logs: LogEntry[];
	maxHeight?: string;
	emptyMessage?: string;
}

const LOG_COLORS: Record<string, string> = {
	success: "var(--success)",
	warning: "var(--warning)",
	error: "var(--error)",
	info: "var(--text-secondary)",
};

/**
 * Shared log viewer component for sandbox simulation output.
 * Color-coded log entries with timestamps and auto-scroll.
 */
const SandboxLogs: React.FC<SandboxLogsProps> = ({
	logs,
	maxHeight = "180px",
	emptyMessage = 'No logs generated. Click "Pay" to start checkout events.',
}) => {
	return (
		<div className="flex flex-col">
			<h4
				className="m-0 mb-2"
				style={{
					fontSize: "var(--text-xs)",
					fontWeight: 700,
					color: "var(--text-muted)",
					letterSpacing: "var(--tracking-wider)",
					textTransform: "uppercase",
				}}
			>
				Sandbox Simulation Logs
			</h4>
			<div
				className="logs-output p-3 overflow-y-auto flex flex-col gap-1.5"
				style={{ height: maxHeight }}
			>
				{logs.length === 0 ? (
					<div
						className="text-center py-12 italic"
						style={{ color: "var(--text-muted)" }}
					>
						{emptyMessage}
					</div>
				) : (
					logs.map((log) => (
						<div
							key={`${log.time}-${log.text.substring(0, 20)}`}
							className="leading-relaxed"
						>
							<span className="mr-2" style={{ color: "var(--text-muted)" }}>
								[{log.time}]
							</span>
							<span
								style={{
									color: LOG_COLORS[log.type] || "var(--text-secondary)",
									fontWeight: log.type !== "info" ? 500 : 400,
								}}
							>
								{log.text}
							</span>
						</div>
					))
				)}
			</div>
		</div>
	);
};

export default SandboxLogs;
