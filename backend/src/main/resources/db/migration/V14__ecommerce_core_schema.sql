-- Phase 9-10: E-Commerce Core (renumbered from V9).
-- Catalog (product listings), customer profiles, the customer-order saga
-- coordination table, and the promotions table.
--
-- Aligned with the @Entity classes under
--   ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence.*
--   ch.swissqcommerce.backend.domain.customer.adapter.out.persistence.*
--   ch.swissqcommerce.backend.domain.pricing.adapter.out.persistence.*

-- Catalog ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oltp.product_listings (
    product_id    VARCHAR(50)   PRIMARY KEY,
    title         VARCHAR(255)  NOT NULL,
    description   TEXT,
    base_price    NUMERIC(10,2) NOT NULL,
    status        VARCHAR(30)   NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT product_listings_status_chk
        CHECK (status IN ('DRAFT', 'ACTIVE', 'OUT_OF_STOCK', 'ARCHIVED')),
    CONSTRAINT product_listings_price_chk CHECK (base_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_product_listings_status ON oltp.product_listings (status);

-- Customer profiles -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS oltp.customer_profiles (
    profile_id          VARCHAR(50)  PRIMARY KEY,
    user_id             VARCHAR(50)  NOT NULL UNIQUE,
    marketing_opt_in    BOOLEAN      NOT NULL DEFAULT FALSE,
    default_currency    VARCHAR(10)  NOT NULL DEFAULT 'CHF',
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customer_profiles_user ON oltp.customer_profiles (user_id);

-- Saga coordinator for customer order workflow --------------------------------
CREATE TABLE IF NOT EXISTS oltp.saga_customer_orders (
    order_id      VARCHAR(50)  PRIMARY KEY,
    customer_id   VARCHAR(50)  NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    saga_state    VARCHAR(30)  NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT saga_customer_orders_status_chk
        CHECK (status IN ('CREATED', 'PAID', 'FULFILLED', 'CANCELLED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_saga_customer_orders_customer ON oltp.saga_customer_orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_saga_customer_orders_status   ON oltp.saga_customer_orders (status);

-- Promotions ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oltp.promotions (
    code         VARCHAR(50)   PRIMARY KEY,
    type         VARCHAR(30)   NOT NULL,
    "value"      NUMERIC(10,2) NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT promotions_type_chk CHECK (type IN ('PERCENT', 'FLAT', 'FREE_SHIPPING')),
    CONSTRAINT promotions_value_chk CHECK ("value" >= 0)
);

CREATE INDEX IF NOT EXISTS idx_promotions_expires ON oltp.promotions (expires_at);
