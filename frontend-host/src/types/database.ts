// Core Database Entities for Swish App Quick-Commerce Ecosystem

export interface User {
  _id: string;                    // "usr_xxxx"
  roles: Array<"CUSTOMER" | "RIDER" | "MERCHANT" | "ADMIN">;
  email: string;
  phone: string;
  name: string;
  createdAt: string;              // ISO String
  
  // Role-Specific Sub-Profiles
  customerProfile?: {
    isSubscribed: boolean;        // Active platform subscription (e.g., Swish VIP)
    subscriptionExpiry?: string;
    walletBalance: number;        // Preloaded funds for instant refunds
    defaultAddress: Address;
  };
  
  riderProfile?: {
    status: "OFFLINE" | "ONLINE" | "ON_TRIP";
    vehicleType: "BICYCLE" | "MOTORCYCLE" | "ELECTRIC_SCOOTER";
    activeShiftId?: string;       // References RiderShifts
    cashCollectedLimit: number;   // Max COD amount before block (e.g., $100 / ₹5000)
    currentCashInHand: number;    // Tracks active COD cash collected
    leasedGear: {
      bagScanned: boolean;
      bagQrCode?: string;
      apparelProvided: boolean;
    };
  };
  
  merchantProfile?: {
    associatedStoreIds: string[]; // Stores managed by this merchant
  };
}

export interface Address {
  street: string;
  city: string;
  postalCode: string;
  coordinates: { lat: number; lng: number };
  geoHash: string;
}

export interface Store {
  _id: string;                    // "store_xxxx"
  merchantId: string;             // References Users._id
  name: string;
  type: "DARK_STORE" | "LOCAL_RETAIL";
  status: "OPEN" | "CLOSED" | "MAINTENANCE";
  location: Address;
  operatingHours: { open: string; close: string }; // "08:00", "22:00"
  
  // IoT Cold Chain & Maintenance
  maintenanceStatus: {
    freezerTempCelsius: number;   // Updated by IoT ping
    chillerTempCelsius: number;   // Updated by IoT ping
    lastSanitizationAudit: string; // ISO String
    hasActiveAlert: boolean;
  };
}

export interface Product {
  _id: string;                    // "prod_xxxx"
  sku: string;                    // "COKE-ZERO-500ML"
  name: string;
  emoji: string;
  category: string;               // "Dairy", "Beverages", etc.
  isPerishable: boolean;          // Triggers minimum cart value checks
  globalMrp: number;
  taxRatePercent: number;         // Dynamic taxation (e.g., 5%, 18%)
}

export interface StoreInventory {
  productId: string;              // References Products._id
  totalStockCount: number;        // Sum of unexpired batch counts
  aisleLocation: string;          // e.g., "Aisle B - Shelf 3" (for WMS 90s pick optimization)
  retailPrice: number;            // Local store specific pricing (overrides global MRP if local shop)
  discountPercentage: number;     // Dynamic perishable discounting (FEFO)
  
  // Expiry Batch Tracking
  batches: Array<{
    batchId: string;              // "batch_xxxx"
    inboundDate: string;
    expiryDate: string;           // ISO String
    initialQty: number;
    currentQty: number;
  }>;
}

export interface Order {
  _id: string;                    // "ord_xxxx"
  customerId: string;             // References Users._id
  storeId: string;                // References Stores._id
  status: "PENDING" | "PICKING" | "READY_FOR_PICKUP" | "IN_TRANSIT" | "DELIVERED" | "CANCELLED" | "PAYMENT_FAILED";
  
  timeline: {
    placedAt: string;
    promisedBy: string;           // Dynamic SLA (10 to 45 mins)
    completedAt?: string;
  };
  
  constraints: {
    containsPerishables: boolean;
    minCartValueMet: boolean;     // If false -> apply maintenance fee
    storeFaultWaiverApplied: boolean; // True if subtotal fell below threshold due to out-of-stock items during picking
  };

  priceLock?: {
    lockedAt: string;             // Timestamp when item was added
    lockedPriceMap: Record<string, number>; // Map of productId to locked price
  };
  
  items: Array<{
    productId: string;
    qty: number;
    pickedQty: number;            // Confirmed physically present during picking
    price: number;                // Locked price from session priceLock
    status: "CONFIRMED" | "SUBSTITUTED" | "OUT_OF_STOCK";
    substitutionDetails?: {
      originalProductId: string;
      substitutedProductId: string;
      customerApproved: boolean;
    };
  }>;
  
  financials: {
    itemsTotal: number;
    deliveryFee: number;          // Dynamic (weather + distance)
    surgeFee: number;             // Peak dynamic surge
    platformFee: number;          // $0 if user is Swish VIP subscriber
    perishableMaintenanceFee: number; // Applied if constraints.minCartValueMet is false AND storeFaultWaiverApplied is false
    taxTotal: number;
    totalAmount: number;          // Final billed amount (hold captured)
    paymentStatus: "HOLD_AUTH" | "CAPTURED" | "REFUNDED" | "PARTIALLY_REFUNDED" | "FAILED";
    paymentMethod: "WALLET" | "CARD" | "COD";
  };
}

export interface Delivery {
  _id: string;                    // "del_xxxx"
  orderId: string;                // References Orders._id
  riderId: string;                // References Users._id
  status: "ASSIGNED" | "PICKED_UP" | "COMPLETED" | "FAILED";
  pickupQrCode: string;           // Matched against WMS bag QR code
  deliveryPin: string;            // Matched against Customer OTP for Proof of Delivery
  
  payout: {
    baseDeliveryFee: number;
    surgeShare: number;
    riderTip: number;
    totalRiderEarnings: number;   // 100% dynamic earnings credited to rider
  };
}

export interface TransitIncident {
  _id: string;
  deliveryId: string;
  riderId: string;
  type: "ACCIDENT" | "VEHICLE_BREAKDOWN" | "WEATHER_BLOCKED";
  gpsCoordinates: { lat: number; lng: number };
  status: "REPORTED" | "RESOLVED";
  timestamp: string;
  emergencyActionsTaken: {
    newDeliveryCreatedId?: string; // Duplicate re-routed order ID
    insuranceLogged: boolean;
    incidentReportFileUrl?: string;
  };
}

export interface RiderShift {
  _id: string;
  riderId: string;
  status: "ACTIVE" | "COMPLETED";
  startTime: string;
  endTime?: string;
  assignedZone: string;           // e.g. "Downtown_H1"
  hoursLogged: number;
  earningsThisShift: number;
  performanceScore: number;       // Average drop SLA rating
}

export interface RiderLedger {
  _id: string;
  riderId: string;
  transactionType: "DELIVERY_CREDIT" | "TIP_CREDIT" | "COD_DEBIT" | "LEASE_DEDUCTION" | "PAYOUT_CLEARING";
  amount: number;                 // Positive or negative
  timestamp: string;
  referenceId: string;            // e.g., deliveryId or bankPayoutId
}

export interface MerchantSettlement {
  _id: string;
  storeId: string;
  periodStart: string;
  periodEnd: string;
  totalRevenue: number;
  swishCommissionDeducted: number;
  payoutAmount: number;
  status: "PENDING" | "TRANSFERRED";
}

export interface PurchaseOrder {
  _id: string;
  storeId: string;
  vendorName: string;
  items: Array<{ productId: string; requestedQty: number; receivedQty: number }>;
  status: "DRAFT" | "SENT" | "PARTIALLY_RECEIVED" | "RECEIVED" | "REJECTED";
  inboundDate?: string;
  grnVerificationFileUrl?: string; // Quality scan receipt proof
}

export interface WastageLog {
  _id: string;
  storeId: string;
  productId: string;
  batchId: string;
  qtyWasted: number;
  reason: "EXPIRED" | "DAMAGED_IN_STORE" | "MELTED_COLD_CHAIN" | "ACCIDENT_LOSS";
  loggedBy: string;               // User ID
  timestamp: string;
}
