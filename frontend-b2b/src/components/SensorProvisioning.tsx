import type React from "react";

interface SensorProvisioningProps {
	currentRetailer: any;
	sensorType: "TEMPERATURE" | "HUMIDITY" | "GPS";
	onSensorTypeChange: (type: "TEMPERATURE" | "HUMIDITY" | "GPS") => void;
	sensorsList: any[];
	isProvisioning: boolean;
	onProvision: (e: React.FormEvent) => void;
	onCalibrate: (sensorId: string) => void;
	onVerifyIntegrity: (sensorId: string) => void;
}

/**
 * IoT sensor device provisioning panel.
 * Allows provisioning new sensors and managing existing ones (calibrate, verify chain).
 */
const SensorProvisioning: React.FC<SensorProvisioningProps> = ({
	currentRetailer,
	sensorType,
	onSensorTypeChange,
	sensorsList,
	isProvisioning,
	onProvision,
	onCalibrate,
	onVerifyIntegrity,
}) => {
	if (currentRetailer?.status !== "ACTIVE") return null;

	return (
		<div className="upgrade-glow-card p-6 flex flex-col gap-4 animate-slide-up">
			<h3
				className="m-0 flex items-center gap-2 pb-3"
				style={{
					fontSize: "var(--text-lg)",
					fontWeight: 700,
					borderBottom: "1px solid var(--border-default)",
				}}
			>
				📡 IoT Sensor Device Provisioning
			</h3>

			{/* Provision Form */}
			<form
				onSubmit={onProvision}
				className="flex flex-col sm:flex-row items-end gap-4"
			>
				<div className="flex flex-col gap-1 flex-1">
					<label
						htmlFor="sensor-type-select"
						className="text-xs font-semibold"
						style={{ color: "var(--text-muted)" }}
					>
						Provision Device Type
					</label>
					<select
						id="sensor-type-select"
						className="select-field"
						value={sensorType}
						onChange={(e) => onSensorTypeChange(e.target.value as any)}
					>
						<option value="TEMPERATURE">
							Cold-chain Temperature Sensor (TimescaleDB)
						</option>
						<option value="HUMIDITY">
							Warehouse Ambient Humidity Sensor (RH%)
						</option>
						<option value="GPS">Rider Fleet GPS Coordinate Tracker</option>
					</select>
				</div>
				<button
					type="submit"
					disabled={isProvisioning}
					className="btn-primary whitespace-nowrap"
					style={{ height: 40 }}
				>
					{isProvisioning ? (
						<>
							<span className="spinner" /> Provisioning...
						</>
					) : (
						"Provision Device"
					)}
				</button>
			</form>

			{/* Provisioned Devices List */}
			<div className="flex flex-col mt-2">
				<h4
					className="m-0 mb-3"
					style={{
						fontSize: "var(--text-xs)",
						fontWeight: 700,
						letterSpacing: "var(--tracking-wider)",
					}}
				>
					ACTIVE STORE HUB SENSORS ({sensorsList.length})
				</h4>

				{sensorsList.length === 0 ? (
					<div
						className="text-center py-8 text-xs italic rounded-xl"
						style={{
							color: "var(--text-muted)",
							background: "var(--bg-glass)",
							border: "1px dashed var(--border-default)",
						}}
					>
						No active sensors provisioned for store hub{" "}
						{currentRetailer.storeId} yet.
					</div>
				) : (
					<div className="flex flex-col gap-3 stagger-enter">
						{sensorsList.map((sensor) => (
							<SensorCard
								key={sensor.sensorId}
								sensor={sensor}
								onCalibrate={onCalibrate}
								onVerifyIntegrity={onVerifyIntegrity}
							/>
						))}
					</div>
				)}
			</div>
		</div>
	);
};

/* ── Sensor Card Sub-component ── */

function SensorCard({
	sensor,
	onCalibrate,
	onVerifyIntegrity,
}: {
	sensor: any;
	onCalibrate: (id: string) => void;
	onVerifyIntegrity: (id: string) => void;
}) {
	return (
		<div className="sensor-card flex flex-col gap-2 animate-scale-in">
			{/* Header Row */}
			<div className="flex justify-between items-start">
				<div className="flex flex-col gap-0.5">
					<span
						className="text-xs font-bold flex items-center gap-1.5"
						style={{ color: "var(--text-primary)" }}
					>
						📟 {sensor.sensorId}
						<span
							className="font-semibold font-mono rounded-full px-1.5 py-0.5"
							style={{
								fontSize: "10px",
								background: "var(--accent-muted)",
								color: "var(--accent-hover)",
							}}
						>
							{sensor.sensorType}
						</span>
					</span>
					<span
						className="font-mono"
						style={{ fontSize: "10px", color: "var(--text-muted)" }}
					>
						Hub Owner: {sensor.retailerId} | Store: {sensor.storeId}
					</span>
				</div>
				<div className="flex items-center gap-2">
					<span
						className="status-dot status-dot--connected"
						style={{ width: 6, height: 6 }}
					/>
					<span
						className="text-xs font-medium capitalize"
						style={{ color: "var(--success)" }}
					>
						{sensor.status.toLowerCase()}
					</span>
				</div>
			</div>

			{/* Actions Row */}
			<div
				className="flex flex-col sm:flex-row justify-between items-start sm:items-center pt-2 mt-1 gap-2 text-xs"
				style={{ borderTop: "1px solid var(--border-subtle)" }}
			>
				<div className="flex flex-col gap-0.5">
					<span
						className="uppercase tracking-wider"
						style={{ fontSize: "9px", color: "var(--text-muted)" }}
					>
						Last Calibration
					</span>
					<span
						className="font-semibold"
						style={{ color: "var(--text-secondary)" }}
					>
						{sensor.lastCalibratedAt
							? new Date(sensor.lastCalibratedAt).toLocaleString()
							: "Never"}
					</span>
				</div>

				<div className="flex gap-2 self-end">
					<button
						className="btn-ghost"
						style={{
							fontSize: "10px",
							color: "var(--accent-hover)",
							borderColor: "rgba(99, 102, 241, 0.15)",
							background: "rgba(99, 102, 241, 0.05)",
						}}
						onClick={() => onCalibrate(sensor.sensorId)}
						type="button"
					>
						Calibrate
					</button>
					<button
						className="btn-ghost"
						style={{
							fontSize: "10px",
							color: "var(--success)",
							borderColor: "rgba(16, 185, 129, 0.15)",
							background: "rgba(16, 185, 129, 0.05)",
						}}
						onClick={() => onVerifyIntegrity(sensor.sensorId)}
						type="button"
					>
						Verify Chain
					</button>
				</div>
			</div>

			{/* Status Tags */}
			<div
				className="flex gap-4 mt-1 pt-1.5 font-mono"
				style={{
					borderTop: "1px solid var(--border-subtle)",
					fontSize: "9px",
					color: "var(--text-muted)",
				}}
			>
				<span>
					Calibration status:{" "}
					<strong
						style={{
							color:
								sensor.calibrationStatus === "SUCCESS"
									? "var(--success)"
									: "var(--warning)",
							fontWeight: 700,
						}}
					>
						{sensor.calibrationStatus}
					</strong>
				</span>
				<span>
					SHA-256 chain integrity:{" "}
					<strong
						style={{
							color: sensor.integrityValid ? "var(--success)" : "var(--error)",
							fontWeight: 700,
						}}
					>
						{sensor.integrityValid ? "VALID (SECURE)" : "INVALID"}
					</strong>
				</span>
			</div>
		</div>
	);
}

export default SensorProvisioning;
