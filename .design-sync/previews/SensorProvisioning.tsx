import { SensorProvisioning } from "frontend-b2b";

// Sensor fields (sensorId / sensorType / status / calibrationStatus /
// integrityValid / lastCalibratedAt) are the ones the component reads; the
// retailer must be ACTIVE before provisioning is allowed, which is the gate
// worth showing alongside the populated fleet.

const handlers = {
	onSensorTypeChange: () => {},
	onProvision: () => {},
	onCalibrate: () => {},
	onVerifyIntegrity: () => {},
};

const activeRetailer = {
	retailerId: "RTL-408912",
	name: "Northgate Grocers",
	storeId: "store-northgate-01",
	tier: "ENTERPRISE",
	status: "ACTIVE",
};

const fleet = [
	{
		sensorId: "SNS-77120",
		retailerId: "RTL-408912",
		storeId: "store-northgate-01",
		sensorType: "TEMPERATURE",
		status: "ACTIVE",
		calibrationStatus: "SUCCESS",
		integrityValid: true,
		lastCalibratedAt: "2026-09-07T09:14:00.000Z",
	},
	{
		sensorId: "SNS-77121",
		retailerId: "RTL-408912",
		storeId: "store-northgate-01",
		sensorType: "HUMIDITY",
		status: "ACTIVE",
		calibrationStatus: "PENDING",
		integrityValid: true,
		lastCalibratedAt: null,
	},
	{
		sensorId: "SNS-77122",
		retailerId: "RTL-408912",
		storeId: "store-northgate-01",
		sensorType: "GPS",
		status: "ACTIVE",
		calibrationStatus: "SUCCESS",
		integrityValid: false,
		lastCalibratedAt: "2026-09-06T18:40:00.000Z",
	},
];

// No story for a missing/non-ACTIVE retailer: the component early-returns null
// (`currentRetailer?.status !== "ACTIVE"`), so that state has no render at all —
// it would only ever be a blank cell.

export const NoSensorsYet = () => (
	<SensorProvisioning
		currentRetailer={activeRetailer}
		sensorType="TEMPERATURE"
		sensorsList={[]}
		isProvisioning={false}
		{...handlers}
	/>
);

export const Provisioning = () => (
	<SensorProvisioning
		currentRetailer={activeRetailer}
		sensorType="HUMIDITY"
		sensorsList={fleet.slice(0, 1)}
		isProvisioning
		{...handlers}
	/>
);

export const ColdChainFleet = () => (
	<SensorProvisioning
		currentRetailer={activeRetailer}
		sensorType="GPS"
		sensorsList={fleet}
		isProvisioning={false}
		{...handlers}
	/>
);
