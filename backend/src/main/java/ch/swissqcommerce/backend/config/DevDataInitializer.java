package ch.swissqcommerce.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevDataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DevDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println(
                "====== [DevDataInitializer] Seeding local H2 database for Logistics/Routing"
                        + " validation ======");

        // Clear existing to ensure clean seed
        jdbcTemplate.execute("DELETE FROM oltp.order_items");
        jdbcTemplate.execute("DELETE FROM oltp.orders");
        jdbcTemplate.execute("DELETE FROM oltp.inventory");
        jdbcTemplate.execute("DELETE FROM oltp.warehouse_baseline");
        jdbcTemplate.execute("DELETE FROM oltp.region_pref");
        jdbcTemplate.execute("DELETE FROM oltp.dark_stores");
        jdbcTemplate.execute("DELETE FROM oltp.carrier_sla");
        jdbcTemplate.execute("DELETE FROM oltp.customer_addresses");
        jdbcTemplate.execute("DELETE FROM oltp.customers");

        // 1. Seed dummy customer
        jdbcTemplate.execute(
                "INSERT INTO oltp.customers (customer_id, full_name, email, hashed_email,"
                    + " wallet_balance, loyalty_points, vip_status, trust_score, is_anonymized,"
                    + " is_on_probation, consecutive_orders_completed, version) VALUES"
                    + " ('cust-test-1', 'Test Customer', 'test@example.com',"
                    + " 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 100.00,"
                    + " 0, false, 100, false, false, 0, 0)");

        // 1b. Seed customer address (zip prefix 800)
        jdbcTemplate.execute(
                "INSERT INTO oltp.customer_addresses (address_id, customer_id, label, address_line,"
                    + " latitude, longitude) VALUES (1, 'cust-test-1', 'Home', 'Bahnhofstrasse 1,"
                    + " 8000 Zurich', 47.3769, 8.5417)");

        // 2. Seed dark stores ( Zurich and Geneva )
        jdbcTemplate.execute(
                "INSERT INTO oltp.dark_stores (store_id, store_name, address, latitude, longitude,"
                        + " storage_capacity_limit, active, daily_order_capacity) VALUES"
                        + " ('store-test-1', 'Zurich Store Hub', 'Bahnhofstrasse 100, 8001 Zurich',"
                        + " 47.3769, 8.5417, 5000, true, 500)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.dark_stores (store_id, store_name, address, latitude, longitude,"
                        + " storage_capacity_limit, active, daily_order_capacity) VALUES"
                        + " ('store-test-2', 'Geneva Store Hub', 'Second Test Address', 47.4500,"
                        + " 8.6000, 5000, true, 500)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.dark_stores (store_id, store_name, address, latitude, longitude,"
                        + " storage_capacity_limit, active, daily_order_capacity) VALUES"
                        + " ('store-test-inactive', 'Inactive Store Hub', 'Third Address', 47.5000,"
                        + " 8.7000, 5000, false, 500)");

        // 2b. Seed inventory items
        // Standard item (Zurich: item-test-1, Geneva: item-test-2)
        jdbcTemplate.execute(
                "INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category,"
                    + " emoji, perishable, fragile, reserved_qty, version) VALUES ('item-test-1',"
                    + " 'store-test-1', 'Standard Item', 2.50, 100, 'grocery', '🍏', false, false,"
                    + " 0, 1)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category,"
                    + " emoji, perishable, fragile, reserved_qty, version) VALUES ('item-test-2',"
                    + " 'store-test-2', 'Standard Item 2', 2.50, 100, 'grocery', '🍏', false,"
                    + " false, 0, 1)");

        // Heavy item (Zurich: item-test-heavy, Geneva: item-test-heavy-2)
        jdbcTemplate.execute(
                "INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category,"
                    + " emoji, perishable, fragile, reserved_qty, version) VALUES"
                    + " ('item-test-heavy', 'store-test-1', 'Heavy Item', 8.50, 100, 'furniture',"
                    + " '📦', false, false, 0, 1)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category,"
                        + " emoji, perishable, fragile, reserved_qty, version) VALUES"
                        + " ('item-test-heavy-2', 'store-test-2', 'Heavy Item 2', 8.50, 100,"
                        + " 'furniture', '📦', false, false, 0, 1)");

        // Fragile item (Zurich: item-test-fragile, Geneva: item-test-fragile-2)
        jdbcTemplate.execute(
                "INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category,"
                        + " emoji, perishable, fragile, reserved_qty, version) VALUES"
                        + " ('item-test-fragile', 'store-test-1', 'Fragile Item', 12.00, 100,"
                        + " 'glassware', '🍷', false, true, 0, 1)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.inventory (item_id, store_id, name, price, stock, category,"
                        + " emoji, perishable, fragile, reserved_qty, version) VALUES"
                        + " ('item-test-fragile-2', 'store-test-2', 'Fragile Item 2', 12.00, 100,"
                        + " 'glassware', '🍷', false, true, 0, 1)");

        // 3. Seed warehouse baseline rates
        jdbcTemplate.execute(
                "INSERT INTO oltp.warehouse_baseline (zip_prefix, warehouse_id, avg_shipping_cost,"
                        + " sample_size) VALUES ('800', 'store-test-1', 5.50, 10)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.warehouse_baseline (zip_prefix, warehouse_id, avg_shipping_cost,"
                        + " sample_size) VALUES ('800', 'store-test-2', 6.20, 10)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.warehouse_baseline (zip_prefix, warehouse_id, avg_shipping_cost,"
                        + " sample_size) VALUES ('800', 'store-test-inactive', 4.10, 10)");

        // 4. Seed region preference fallbacks
        jdbcTemplate.execute(
                "INSERT INTO oltp.region_pref (zip_prefix, primary_warehouse_id,"
                    + " secondary_warehouse_id) VALUES ('800', 'store-test-1', 'store-test-2')");

        // 5. Seed carrier SLA rules
        jdbcTemplate.execute(
                "INSERT INTO oltp.carrier_sla (carrier, max_weight_kg, standard_days, express_days,"
                        + " fragile_ok, active) VALUES ('USPS', 31.75, 5, 2, false, true)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.carrier_sla (carrier, max_weight_kg, standard_days, express_days,"
                        + " fragile_ok, active) VALUES ('UPS', 68.04, 5, 1, true, true)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.carrier_sla (carrier, max_weight_kg, standard_days, express_days,"
                        + " fragile_ok, active) VALUES ('FedEx', 68.04, 5, 1, true, true)");
        jdbcTemplate.execute(
                "INSERT INTO oltp.carrier_sla (carrier, max_weight_kg, standard_days, express_days,"
                        + " fragile_ok, active) VALUES ('DHL', 70.00, 7, 2, true, true)");

        // 6. Seed test orders (999901, 999902, 999903, 999904)
        // 999901: Standard order, standard SLA
        jdbcTemplate.execute(
                "INSERT INTO oltp.orders (order_id, customer_id, store_id, total_amount,"
                    + " payment_method, status, version, promised_by, weather_surcharge,"
                    + " tip_amount, sla_countdown_sec, bags_returned, contains_perishables,"
                    + " min_cart_value_met, store_fault_waiver_applied, perishable_maintenance_fee)"
                    + " VALUES (999901, 'cust-test-1', 'store-test-1', 150.00, 'Wallet', 'pending',"
                    + " 1, DATEADD('DAY', 5, CURRENT_TIMESTAMP), 0.0, 0.0, 540, 0, false, true,"
                    + " false, 0.0)");

        // 999902: Heavy order (exceeds single package limit 30kg)
        jdbcTemplate.execute(
                "INSERT INTO oltp.orders (order_id, customer_id, store_id, total_amount,"
                    + " payment_method, status, version, promised_by, weather_surcharge,"
                    + " tip_amount, sla_countdown_sec, bags_returned, contains_perishables,"
                    + " min_cart_value_met, store_fault_waiver_applied, perishable_maintenance_fee)"
                    + " VALUES (999902, 'cust-test-1', 'store-test-1', 250.00, 'Wallet', 'pending',"
                    + " 1, DATEADD('DAY', 5, CURRENT_TIMESTAMP), 0.0, 0.0, 540, 0, false, true,"
                    + " false, 0.0)");

        // 999903: Fragile order
        jdbcTemplate.execute(
                "INSERT INTO oltp.orders (order_id, customer_id, store_id, total_amount,"
                    + " payment_method, status, version, promised_by, weather_surcharge,"
                    + " tip_amount, sla_countdown_sec, bags_returned, contains_perishables,"
                    + " min_cart_value_met, store_fault_waiver_applied, perishable_maintenance_fee)"
                    + " VALUES (999903, 'cust-test-1', 'store-test-1', 120.00, 'Wallet', 'pending',"
                    + " 1, DATEADD('DAY', 5, CURRENT_TIMESTAMP), 0.0, 0.0, 540, 0, false, true,"
                    + " false, 0.0)");

        // 999904: Tight SLA order (1 day delivery)
        jdbcTemplate.execute(
                "INSERT INTO oltp.orders (order_id, customer_id, store_id, total_amount,"
                    + " payment_method, status, version, promised_by, weather_surcharge,"
                    + " tip_amount, sla_countdown_sec, bags_returned, contains_perishables,"
                    + " min_cart_value_met, store_fault_waiver_applied, perishable_maintenance_fee)"
                    + " VALUES (999904, 'cust-test-1', 'store-test-1', 100.00, 'Wallet', 'pending',"
                    + " 1, DATEADD('DAY', 1, CURRENT_TIMESTAMP), 0.0, 0.0, 540, 0, false, true,"
                    + " false, 0.0)");

        // 7. Seed order items
        // 999901 items: 2x standard items
        jdbcTemplate.execute(
                "INSERT INTO oltp.order_items (order_id, item_id, quantity, price) "
                        + "VALUES (999901, 'item-test-1', 2, 2.50)");

        // 999902 items: 1x heavy item (35kg)
        jdbcTemplate.execute(
                "INSERT INTO oltp.order_items (order_id, item_id, quantity, price) "
                        + "VALUES (999902, 'item-test-heavy', 1, 8.50)");

        // 999903 items: 1x fragile item
        jdbcTemplate.execute(
                "INSERT INTO oltp.order_items (order_id, item_id, quantity, price) "
                        + "VALUES (999903, 'item-test-fragile', 1, 12.00)");

        // 999904 items: 1x standard item
        jdbcTemplate.execute(
                "INSERT INTO oltp.order_items (order_id, item_id, quantity, price) "
                        + "VALUES (999904, 'item-test-1', 1, 2.50)");

        System.out.println("====== [DevDataInitializer] Seed Completed successfully ======");
    }
}
