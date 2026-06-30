-- V38__carrier_sla_rules.sql
-- Carrier SLA rules for RoutingAgent v1.0
-- Defines per-carrier delivery time windows, weight limits, and fragile-item support.

CREATE TABLE oltp.carrier_sla (
  carrier       VARCHAR(50)    PRIMARY KEY,
  max_weight_kg NUMERIC(6,2)  NOT NULL DEFAULT 30.0,
  standard_days INT            NOT NULL DEFAULT 5,
  express_days  INT            NOT NULL DEFAULT 2,
  fragile_ok    BOOLEAN        NOT NULL DEFAULT FALSE,
  active        BOOLEAN        NOT NULL DEFAULT TRUE
);

INSERT INTO oltp.carrier_sla (carrier, max_weight_kg, standard_days, express_days, fragile_ok, active)
VALUES
  ('USPS',   31.75, 5, 2, FALSE, TRUE),
  ('UPS',    68.04, 5, 1, TRUE,  TRUE),
  ('FedEx',  68.04, 5, 1, TRUE,  TRUE),
  ('DHL',    70.00, 7, 2, TRUE,  TRUE);
