-- Phase 4/13/14: Logistics, Fleet, Geospatial (renumbered from V10).
--
-- IMPORTANT: dispatch.active_shipments is already created in V7 with the full
-- schema (status, total_weight_kg, GPS coords, etc.). The old V10 redefined a
-- minimal `active_shipments` table in the default schema, which conflicted
-- with V7. That redefinition has been REMOVED here on purpose.
--
-- New tables: rider_shifts and delivery_zones, both in the dispatch schema for
-- consistency.

-- Rider shifts ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dispatch.rider_shifts (
    shift_id     VARCHAR(50)  PRIMARY KEY,
    rider_id     VARCHAR(50)  NOT NULL REFERENCES oltp.riders(rider_id) ON DELETE CASCADE,
    start_time   TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time     TIMESTAMP WITH TIME ZONE NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rider_shifts_status_chk
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'ENDED', 'CANCELLED')),
    CONSTRAINT rider_shifts_time_chk CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_rider_shifts_rider ON dispatch.rider_shifts (rider_id, start_time DESC);

-- Delivery zones --------------------------------------------------------------
-- geo_polygon_wkt is stored as text for now; if/when PostGIS is enabled, this
-- column should be replaced with `geometry(Polygon, 4326)` and indexed with
-- a GIST index on ST_GeomFromText(geo_polygon_wkt, 4326).
CREATE TABLE IF NOT EXISTS dispatch.delivery_zones (
    zone_id          VARCHAR(50)  PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    geo_polygon_wkt  TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_zones_status_chk CHECK (status IN ('ACTIVE', 'PAUSED', 'RETIRED'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_zones_status ON dispatch.delivery_zones (status);
