package com.platform.notification.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Fix #12: Standardized notification envelope for all WebSocket messages.
 * Every notification pushed to the frontend must conform to this contract.
 */
public record NotificationEnvelope(
    String id,
    String type,       // ORDER_EVALUATED, PAYMENT_CONFIRMED, PAYMENT_FAILED, RIDER_ASSIGNED, HEARTBEAT
    int version,
    String timestamp,   // ISO-8601
    String recipientId,
    JsonNode payload,
    String priority     // HIGH, MEDIUM, LOW
) {}
