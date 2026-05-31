-- ====================================================
-- SWISS QUICK COMMERCE NETWORK: PREMIUM DATABASE SEED
-- ====================================================

-- Clear existing OLTP data in reverse dependency order
TRUNCATE TABLE oltp.security_trust_ledger RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.ledger_lines RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.journal_entries RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.order_telemetry_logs RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.b2b_restock_orders RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.order_items RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.orders RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.inventory RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.wholesalers RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.pickers RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.dark_stores RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.rider_academy_certificates RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.riders RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.customer_payment_cards RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.customer_addresses RESTART IDENTITY CASCADE;
TRUNCATE TABLE oltp.customers RESTART IDENTITY CASCADE;

-- 1. Seed Customers
INSERT INTO oltp.customers (customer_id, full_name, email, hashed_email, wallet_balance, loyalty_points, vip_status, trust_score, is_anonymized, is_on_probation, consecutive_orders_completed) VALUES
('swissuser', 'Fritz Bovet', 'swissuser@swish.ch', 'a5840babb164310c98c1d123329446b0a5840babb164310c98c1d123329446b0', 500.00, 150, true, 95, false, false, 12),
('customer-2', 'Marie Dubois', 'marie@swish.ch', 'd81740c23e924fbbcd81740c23e924fbbcd81740c23e924fbbcd81740c23e924', 250.50, 45, false, 100, false, false, 5);

-- 2. Seed Customer Addresses
INSERT INTO oltp.customer_addresses (customer_id, label, address_line, latitude, longitude) VALUES
('swissuser', 'Home', 'Bahnhofstrasse 45, 8001 Zurich', 47.3769, 8.5417),
('swissuser', 'Work', 'Hardbrücke 15, 8005 Zurich', 47.3855, 8.5182),
('customer-2', 'Home', 'Rue du Rhône 10, 1204 Geneva', 46.2044, 6.1432);

-- 3. Seed Customer Payment Cards
INSERT INTO oltp.customer_payment_cards (customer_id, card_type, last_four_digits, token_reference) VALUES
('swissuser', 'Visa', '4321', 'tok_swissuser_visa_premium'),
('customer-2', 'MasterCard', '8888', 'tok_marie_mc_standard');

-- 4. Seed Riders
INSERT INTO oltp.riders (rider_id, full_name, vehicle_type, onboarding_status, wallet_balance, active_lat, active_lng, trust_score) VALUES
('swissrider', 'Rider Express', 'E-Bike', 'active', 75.00, 47.3780, 8.5400, 98),
('rider-2', 'Pierre Martin', 'Scooter', 'active', 0.00, 46.2050, 6.1440, 85),
('rider-3', 'Elena Rossi', 'E-Bike', 'pending', 0.00, NULL, NULL, 100);

-- 5. Seed Rider Academy Certificates
INSERT INTO oltp.rider_academy_certificates (rider_id, course_name) VALUES
('swissrider', 'Safe Swiss E-Bike Navigation & Traffic'),
('swissrider', 'IoT Cold Chain Handover Protocol'),
('rider-2', 'Safe Swiss E-Bike Navigation & Traffic');

-- 6. Seed Dark Stores
INSERT INTO oltp.dark_stores (store_id, store_name, address, latitude, longitude, storage_capacity_limit) VALUES
('store-1', 'Zurich Dark Store Hub', 'Bahnhofstrasse 100, 8001 Zurich', 47.3769, 8.5417, 5000),
('store-2', 'Geneva Micro-Fulfillment Center', 'Rue du Rhône 12, 1204 Geneva', 46.2044, 6.1432, 4000);

-- 7. Seed Pickers
INSERT INTO oltp.pickers (picker_id, full_name, trust_score, lightning_badge, active_store_id) VALUES
('picker-1', 'Fritz Müller', 100, true, 'store-1'),
('picker-2', 'Hans Schmidt', 85, false, 'store-2');

-- 8. Seed Wholesalers (Duplicate uppercase/lowercase to bypass request parameter mapping bugs)
INSERT INTO oltp.wholesalers (wholesaler_id, name, is_primary, trust_score, is_active, academy_discount_active, base_invoice_amount, fallback_invoice_amount) VALUES
('WHOLESALER-1', 'Swiss Wholesale Distributors (B2B Core)', true, 95, true, true, 25.00, 35.00),
('wholesaler-1', 'Swiss Wholesale Distributors (B2B Core - LC)', true, 95, true, true, 25.00, 35.00),
('wholesaler-2', 'Alpine Backups & Restock Co', false, 80, true, false, 28.00, 38.00);

-- 9. Seed Inventory Items
INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category, emoji, perishable, version) VALUES
('item-1', 'store-1', 'Swiss Milk Premium', 2.50, 120, 'Dairy', '🥛', true, 0),
('item-2', 'store-1', 'Aged Gruyère Cheese', 8.50, 80, 'Dairy', '🧀', true, 0),
('item-3', 'store-1', 'Lindt Dark Chocolate Bar', 3.20, 300, 'Sweets', '🍫', false, 0),
('item-4', 'store-1', 'Fresh Organic Bananas', 1.80, 150, 'Produce', '🍌', true, 0),
('item-5', 'store-2', 'Swiss Milk Premium', 2.50, 90, 'Dairy', '🥛', true, 0),
('item-6', 'store-2', 'Aged Gruyère Cheese', 8.50, 40, 'Dairy', '🧀', true, 0),
('item-7', 'store-2', 'Lindt Dark Chocolate Bar', 3.20, 250, 'Sweets', '🍫', false, 0);

-- 10. Seed Orders
INSERT INTO oltp.orders (order_id, customer_id, store_id, rider_id, total_amount, tip_amount, weather_surcharge, payment_method, status, sla_countdown_sec, bags_returned, idempotency_key, created_at) VALUES
(1001, 'swissuser', 'store-1', 'swissrider', 14.60, 2.00, 0.00, 'Wallet', 'pending', 540, 2, 'idemp_order_1001', CURRENT_TIMESTAMP - INTERVAL '15 minutes'),
(1002, 'customer-2', 'store-1', NULL, 25.50, 0.00, 3.00, 'Wallet', 'picking', 320, 0, 'idemp_order_1002', CURRENT_TIMESTAMP - INTERVAL '8 minutes'),
(1003, 'swissuser', 'store-2', 'rider-2', 45.00, 5.00, 0.00, 'Wallet', 'delivered', 0, 4, 'idemp_order_1003', CURRENT_TIMESTAMP - INTERVAL '1 hour');

-- Adjust Serial Sequence for Orders
SELECT setval('oltp.orders_order_id_seq', 1003);

-- 11. Seed Order Items
INSERT INTO oltp.order_items (order_id, item_id, quantity, price) VALUES
(1001, 'item-1', 2, 2.50), -- 5.00
(1001, 'item-3', 3, 3.20), -- 9.60
(1002, 'item-2', 3, 8.50), -- 25.50
(1003, 'item-5', 10, 2.50), -- 25.00
(1003, 'item-7', 5, 3.20); -- 16.00 (plus 4.00 delivery/tip/surcharges = 45.00)

-- 12. Seed B2B Restock Orders
INSERT INTO oltp.b2b_restock_orders (store_id, wholesaler_id, invoice_amount, is_fallback, status, idempotency_key) VALUES
('store-1', 'WHOLESALER-1', 1250.00, false, 'fulfilled', 'idemp_restock_1'),
('store-2', 'wholesaler-2', 850.00, true, 'fulfilled', 'idemp_restock_2');

-- 13. Seed Telemetry Logs for active order 1001
INSERT INTO oltp.order_telemetry_logs (order_id, device_timestamp, latitude, longitude, temperature, dry_ice_injected, alert_triggered) VALUES
(1001, CURRENT_TIMESTAMP - INTERVAL '10 minutes', 47.3769, 8.5417, 4.2, false, false),
(1001, CURRENT_TIMESTAMP - INTERVAL '5 minutes', 47.3775, 8.5408, 4.5, false, false),
(1001, CURRENT_TIMESTAMP, 47.3780, 8.5400, 4.8, false, false);

-- 14. Seed double-entry journal records
INSERT INTO oltp.journal_entries (entry_uuid, timestamp, reference, description) VALUES
('c8f94cb4-ffcd-44b4-a4f6-061ff1e6f54c', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'B2B-DEBIT', 'Settle wholesaler-1 restock invoice store-1'),
('de89ca11-00aa-4bc0-bbdd-123456789abc', CURRENT_TIMESTAMP - INTERVAL '15 minutes', 'ORDER-PAY', 'Payment for order 1001 fritz');

-- 15. Seed Ledger Legs
INSERT INTO oltp.ledger_lines (entry_id, account_type, actor_id, debit, credit) VALUES
(1, 'system', NULL, 1250.00, 0.00),
(1, 'wholesaler', 'WHOLESALER-1', 0.00, 1250.00),
(2, 'customer', 'swissuser', 16.60, 0.00),
(2, 'system', NULL, 0.00, 16.60);

-- 16. Seed Security Trust Logs
INSERT INTO oltp.security_trust_ledger (actor_type, actor_id, event, delta, current_value) VALUES
('customer', 'swissuser', 'LOYALTY_TIER_UPGRADE', 5, 95),
('picker', 'picker-1', 'SPEED_DESK_HANDOVER', 0, 100),
('rider', 'swissrider', 'HIGH_COMPLIANCE_DELIVERY', 2, 98);
