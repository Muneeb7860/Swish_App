-- Demo fixtures required for a WORKING customer journey on a fresh demo DB.
-- Idempotent (ON CONFLICT DO NOTHING). Apply after migrations, e.g.:
--   docker exec -i -e PGPASSWORD=$DB_PW swish-demo-postgres-1 \
--     psql -U postgres -d swiss_db < demo/seed-demo-fixtures.sql
--
-- WHY: checkout routing (OrderServiceImpl.evaluateCheckoutRouting) resolves the
-- fulfilment store by the names 'Central Store' / 'East Store' and looks them up
-- via findDarkStoreById(<that name>). If those stores do not exist, EVERY checkout
-- fails ("Dark Store not found: Central Store"). Likewise, with zero ACTIVE riders,
-- findOptimalRider() returns null and a tipped order produces an unbalanced ledger
-- ("Unbalanced transaction"), failing checkout. See UAT findings F15a / F15c.

-- ── Fulfilment dark stores (store_id MUST equal the routing names) ───────────
INSERT INTO oltp.dark_stores (store_id, store_name, address, latitude, longitude, storage_capacity_limit)
VALUES ('Central Store','Central Store','Bahnhofstrasse 1, Zurich',47.376900,8.541700,10000)
ON CONFLICT (store_id) DO NOTHING;
INSERT INTO oltp.dark_stores (store_id, store_name, address, latitude, longitude, storage_capacity_limit)
VALUES ('East Store','East Store','Stadelhoferstrasse 8, Zurich',47.366200,8.550300,8000)
ON CONFLICT (store_id) DO NOTHING;

-- ── Active riders (required so orders can be assigned + tips credited) ───────
INSERT INTO oltp.riders (rider_id, full_name, vehicle_type, onboarding_status, wallet_balance, active_lat, active_lng, trust_score, cash_collected_limit, current_cash_in_hand)
VALUES ('rider-demo-1','Demo Rider','scooter','active',0.00,47.376900,8.541700,100,100.00,0.00)
ON CONFLICT (rider_id) DO NOTHING;
INSERT INTO oltp.riders (rider_id, full_name, vehicle_type, onboarding_status, wallet_balance, active_lat, active_lng, trust_score, cash_collected_limit, current_cash_in_hand)
VALUES ('rider-demo-2','Demo Rider Van','van','active',0.00,47.366200,8.550300,100,100.00,0.00)
ON CONFLICT (rider_id) DO NOTHING;

-- NOTE (F4): login accounts are NOT seeded here because passwords must be hashed
-- by the app. Create the 5 demo role accounts via the register API after boot:
--   for r in customer rider inventory business admin; do
--     curl -s -XPOST localhost:8083/api/v1/auth/register \
--       -H 'Content-Type: application/json' \
--       -d "{\"email\":\"$r@swish.local\",\"password\":\"Demo1234!\"}"; done
-- (register currently assigns role CUSTOMER to all — proper per-role seeding is a
-- separate follow-up.)
