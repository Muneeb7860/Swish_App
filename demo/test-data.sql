-- ============================================================================
-- Swish OS — Comprehensive UAT / demo test data (idempotent)
-- ============================================================================
-- Apply AFTER migrations, against the persistent demo DB. Safe to re-run.
--   docker exec -i swish-demo-postgres-1 psql -U postgres -d swiss_db < demo/test-data.sql
--
-- Covers: fulfilment stores, a varied multi-category catalog (incl. low/zero
-- stock edge cases), riders in several lifecycle states, multiple wholesaler
-- "vendors" with distinct pricing profiles for the multi-vendor procurement /
-- dynamic-pricing flows, multi-vendor purchase orders, and role promotion +
-- known-password reset for the five demo role accounts.
--
-- Password for every seeded role account below is:  Demo1234!
-- (bcrypt cost-12 hash; Spring BCryptPasswordEncoder verifies the $2y$ prefix.)
-- The role accounts themselves must exist first (register via API — see
-- demo/seed-test-data.sh, which registers them then runs this file).
-- ============================================================================

-- ── 1. Fulfilment dark stores ───────────────────────────────────────────────
-- store_id MUST equal the routing names used by OrderServiceImpl.evaluateCheckoutRouting.
INSERT INTO oltp.dark_stores (store_id, store_name, address, latitude, longitude, storage_capacity_limit) VALUES
  ('Central Store','Central Store','Bahnhofstrasse 1, Zurich',47.376900,8.541700,10000),
  ('East Store','East Store','Stadelhoferstrasse 8, Zurich',47.366200,8.550300,8000),
  ('store-test-1','Zurich HB Hub','Museumstrasse 2, Zurich',47.378200,8.540100,6000),
  ('store-test-2','Oerlikon Hub','Franklinstrasse 20, Zurich',47.410300,8.544000,6000),
  ('store-test-3','Geneva Cornavin Hub','Place de Cornavin 7, Geneva',46.209600,6.142800,5000)
ON CONFLICT (store_id) DO NOTHING;

-- ── 2. Catalog — varied categories, prices, perishable/fragile, stock edges ──
-- Includes deliberate edge cases: LOWSTOCK-001 (stock=3) and OOS-001 (stock=0)
-- for out-of-stock / low-stock UAT scenarios.
INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category, emoji, perishable, version, fragile, reserved_qty) VALUES
  -- Dairy
  ('MILK-001','store-test-1','Swiss Milk 1L',2.40,120,'Dairy','🥛',true,0,false,0),
  ('EGGS-001','store-test-1','Free-range Eggs x10',4.50,60,'Dairy','🥚',true,0,true,0),
  ('CHEESE-001','store-test-2','Gruyère AOP 250g',6.80,55,'Dairy','🧀',true,0,true,0),
  ('YOG-001','store-test-1','Greek Yogurt 500g',3.10,70,'Dairy','🥛',true,0,false,0),
  ('BUTTER-001','store-test-2','Alpine Butter 250g',3.60,48,'Dairy','🧈',true,0,false,0),
  -- Bakery
  ('BREAD-001','store-test-1','Farmer Bread 500g',3.20,80,'Bakery','🍞',true,0,false,0),
  ('CROISS-001','store-test-1','Butter Croissant x4',4.20,40,'Bakery','🥐',true,0,true,0),
  ('BAGEL-001','store-test-2','Sesame Bagels x6',5.00,35,'Bakery','🥯',true,0,false,0),
  -- Produce
  ('BANANA-001','store-test-2','Bananas 1kg',2.10,90,'Produce','🍌',true,0,false,0),
  ('APPLE-001','store-test-1','Gala Apples 1kg',3.30,75,'Produce','🍎',true,0,false,0),
  ('TOMATO-001','store-test-2','Vine Tomatoes 500g',2.90,50,'Produce','🍅',true,0,true,0),
  ('AVO-001','store-test-3','Avocados x3',4.80,30,'Produce','🥑',true,0,true,0),
  -- Drinks
  ('WATER-001','store-test-2','Alpine Still Water 6x1L',5.40,150,'Drinks','💧',false,0,false,0),
  ('COFFEE-001','store-test-2','Roasted Coffee Beans 1kg',18.90,40,'Drinks','☕',false,0,false,0),
  ('OJ-001','store-test-1','Fresh Orange Juice 1L',4.10,45,'Drinks','🧃',true,0,false,0),
  ('WINE-001','store-test-3','Valais Fendant 75cl',14.50,25,'Drinks','🍷',false,0,true,0),
  -- Snacks / Pantry
  ('CHOC-001','store-test-1','Swiss Dark Chocolate',3.90,200,'Snacks','🍫',false,0,false,0),
  ('CHIPS-001','store-test-2','Potato Chips 175g',2.60,110,'Snacks','🥔',false,0,false,0),
  ('PASTA-001','store-test-1','Penne Pasta 500g',1.90,160,'Pantry','🍝',false,0,false,0),
  ('RICE-001','store-test-2','Basmati Rice 1kg',3.40,95,'Pantry','🍚',false,0,false,0),
  ('OLIVE-001','store-test-3','Extra Virgin Olive Oil 500ml',9.90,38,'Pantry','🫒',false,0,true,0),
  -- Frozen / Meat
  ('PIZZA-001','store-test-2','Margherita Frozen Pizza',5.90,42,'Frozen','🍕',true,0,false,0),
  ('ICE-001','store-test-1','Vanilla Ice Cream 1L',6.20,28,'Frozen','🍨',true,0,false,0),
  ('CHKN-001','store-test-2','Chicken Breast 500g',7.50,33,'Meat','🍗',true,0,false,0),
  -- Household / Personal care
  ('SOAP-001','store-test-1','Hand Soap 500ml',3.80,60,'Household','🧼',false,0,false,0),
  ('TP-001','store-test-2','Toilet Paper x12',8.90,52,'Household','🧻',false,0,false,0),
  ('SHAMP-001','store-test-3','Shampoo 400ml',5.50,44,'Personal Care','🧴',false,0,false,0),
  -- Edge cases for UAT
  ('LOWSTOCK-001','store-test-1','Limited Truffle Brie 200g',12.90,3,'Dairy','🧀',true,0,true,0),
  ('OOS-001','store-test-2','Sold-out Seasonal Berries 250g',5.90,0,'Produce','🫐',true,0,true,0),
  ('PREMIUM-001','store-test-3','Swiss Saffron 2g',29.90,10,'Pantry','🌾',false,0,true,0)
ON CONFLICT (item_id) DO NOTHING;

-- ── 3. Riders — several lifecycle states ────────────────────────────────────
INSERT INTO oltp.riders (rider_id, full_name, vehicle_type, onboarding_status, wallet_balance, active_lat, active_lng, trust_score, cash_collected_limit, current_cash_in_hand) VALUES
  ('rider-demo-1','Demo Rider Scooter','scooter','active',0.00,47.376900,8.541700,100,100.00,0.00),
  ('rider-demo-2','Demo Rider Van','van','active',0.00,47.366200,8.550300,100,150.00,0.00),
  ('rider-demo-3','Demo Rider Bike','bike','active',12.50,47.410300,8.544000,88,80.00,15.00),
  ('rider-pending-1','Applicant Rider','bike','onboarding',0.00,0.000000,0.000000,50,0.00,0.00),
  ('rider-susp-1','Suspended Rider','scooter','suspended',0.00,47.376900,8.541700,20,100.00,0.00)
ON CONFLICT (rider_id) DO NOTHING;

-- ── 4. Wholesalers / vendors — distinct pricing profiles ────────────────────
-- The B2B procurement RFQ / negotiation picks among these by base_invoice_amount,
-- trust_score, and academy_discount. Range of profiles = "dynamic pricing from
-- different vendors" (cheapest-but-lower-trust vs premium-but-reliable, etc.).
INSERT INTO oltp.wholesalers (wholesaler_id, name, is_primary, trust_score, is_active, academy_discount_active, base_invoice_amount) VALUES
  ('WHOLESALER-1','Swiss Wholesale Distributors (B2B Core)',true, 100,true, false,25.00),
  ('wholesaler-2','Alpine Backups & Restock Co',       false, 92, true, false,28.00),
  ('vendor-valuemart','ValueMart Bulk (lowest price, mid trust)',false,74,true,false,19.50),
  ('vendor-premiumfresh','PremiumFresh Logistics (highest trust, premium)',false,99,true,true,34.00),
  ('vendor-swissorganic','Swiss Organic Collective (academy discount)',false,85,true,true,26.50),
  ('vendor-inactive','Dormant Supplier AG (inactive — should be skipped)',false,60,false,false,22.00)
ON CONFLICT (wholesaler_id) DO NOTHING;

-- ── 5. Multi-vendor purchase orders (restock sourcing across vendors) ────────
INSERT INTO wholesaler.purchase_orders (po_id, store_id, vendor_name, status, created_at) VALUES
  ('PO-DEMO-1','store-test-1','ValueMart Bulk (lowest price, mid trust)','SENT',CURRENT_TIMESTAMP),
  ('PO-DEMO-2','store-test-2','PremiumFresh Logistics (highest trust, premium)','RECEIVED',CURRENT_TIMESTAMP),
  ('PO-DEMO-3','store-test-1','Swiss Organic Collective (academy discount)','PARTIALLY_RECEIVED',CURRENT_TIMESTAMP),
  ('PO-DEMO-4','store-test-3','Swiss Wholesale Distributors (B2B Core)','DRAFT',CURRENT_TIMESTAMP)
ON CONFLICT (po_id) DO NOTHING;

INSERT INTO wholesaler.purchase_order_items (item_id, po_id, product_id, requested_qty, received_qty) VALUES
  ('POI-1','PO-DEMO-1','MILK-001',200,0),
  ('POI-2','PO-DEMO-1','BREAD-001',150,0),
  ('POI-3','PO-DEMO-2','CHKN-001',100,100),
  ('POI-4','PO-DEMO-2','CHEESE-001',80,80),
  ('POI-5','PO-DEMO-3','APPLE-001',120,60),
  ('POI-6','PO-DEMO-4','COFFEE-001',60,0)
ON CONFLICT (item_id) DO NOTHING;

-- ── 6. Role promotion + known-password reset for the 5 demo accounts ────────
-- Accounts are created by demo/seed-test-data.sh (register API) which assigns
-- CUSTOMER to all; this promotes them to their intended roles and resets the
-- password to the known bcrypt hash so testers have deterministic credentials.
-- Roles allowed by V20 CHECK: CUSTOMER, ADMIN, RIDER, WHOLESALER.
UPDATE oltp.user_accounts SET role='ADMIN',      password_hash='$2y$12$TRqx6qsNA7Skko2knO8AwuywuolQwwiuqS.uJHpcfx7NhkOiKv1Wy', status='ACTIVE' WHERE email='admin@swish.local';
UPDATE oltp.user_accounts SET role='RIDER',      password_hash='$2y$12$TRqx6qsNA7Skko2knO8AwuywuolQwwiuqS.uJHpcfx7NhkOiKv1Wy', status='ACTIVE' WHERE email='rider@swish.local';
UPDATE oltp.user_accounts SET role='WHOLESALER', password_hash='$2y$12$TRqx6qsNA7Skko2knO8AwuywuolQwwiuqS.uJHpcfx7NhkOiKv1Wy', status='ACTIVE' WHERE email='business@swish.local';
UPDATE oltp.user_accounts SET role='WHOLESALER', password_hash='$2y$12$TRqx6qsNA7Skko2knO8AwuywuolQwwiuqS.uJHpcfx7NhkOiKv1Wy', status='ACTIVE' WHERE email='inventory@swish.local';
UPDATE oltp.user_accounts SET role='CUSTOMER',   password_hash='$2y$12$TRqx6qsNA7Skko2knO8AwuywuolQwwiuqS.uJHpcfx7NhkOiKv1Wy', status='ACTIVE' WHERE email='customer@swish.local';
-- Reset the pre-existing admin too (unknown password) so it is usable.
UPDATE oltp.user_accounts SET role='ADMIN',      password_hash='$2y$12$TRqx6qsNA7Skko2knO8AwuywuolQwwiuqS.uJHpcfx7NhkOiKv1Wy', status='ACTIVE' WHERE email='myadmin@swish.local';
