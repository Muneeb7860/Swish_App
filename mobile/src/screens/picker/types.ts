export interface OrderItem {
	itemId: string;
	name: string;
	quantity: number;
	category: string;
	emoji: string;
	perishable: boolean;
}

export interface Order {
	orderId: number;
	storeId: string;
	status: string;
	slaCountdownSec: number;
	totalAmount: number;
	created_at: string;
	items: OrderItem[];
}

export interface PickerStats {
	pickedCount: number;
	lightningBadges: number;
	avgPickTime: number;
	trustScore: number;
}

export interface DispatchResult {
	status: string;
	item: string;
	quantity: number;
	fromStore: string;
	toStore: string;
	transferTruckId: string;
	timestamp: string;
}

export interface DefaultItem {
	id: string;
	name: string;
	emoji: string;
	category: string;
}
