-- V999__test_data.sql

-- 1. Seed dummy customer
INSERT INTO oltp.customers (customer_id, full_name, email, hashed_email, wallet_balance, loyalty_points, vip_status, trust_score, is_anonymized, is_on_probation, consecutive_orders_completed, version)
VALUES ('cust-test-1', 'Test Customer', 'test@example.com', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 100.00, 0, false, 100, false, false, 0, 0)
ON CONFLICT (customer_id) DO NOTHING;

-- 2. Seed dummy dark store
INSERT INTO oltp.dark_stores (store_id, store_name, address, latitude, longitude, storage_capacity_limit)
VALUES ('store-test-1', 'Test Store', 'Test Address', 47.3769, 8.5417, 5000)
ON CONFLICT (store_id) DO NOTHING;

-- 3. Seed test orders (1 delivered, 1 held)
INSERT INTO oltp.orders (order_id, customer_id, store_id, total_amount, payment_method, status, version, created_at, updated_at)
VALUES (999901, 'cust-test-1', 'store-test-1', 150.00, 'Wallet', 'delivered', 1, NOW() - INTERVAL '40 days', NOW() - INTERVAL '40 days'),
       (999902, 'cust-test-1', 'store-test-1', 250.00, 'Wallet', 'held', 1, NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days')
ON CONFLICT (order_id) DO NOTHING;

-- 4. Seed 2 test chargebacks (1 referencing the delivered order, 1 referencing the held order)
INSERT INTO oltp.chargebacks (chargeback_id, order_id, amount, reason, filed_at, status)
VALUES (999901, 999901, 150.00, 'fraudulent_card', NOW() - INTERVAL '10 days', 'lost'),
       (999902, 999902, 250.00, 'unauthorized_transaction', NOW() - INTERVAL '5 days', 'disputed')
ON CONFLICT (chargeback_id) DO NOTHING;
