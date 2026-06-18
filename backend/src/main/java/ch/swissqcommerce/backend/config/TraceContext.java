package ch.swissqcommerce.backend.config;

import java.util.UUID;

public class TraceContext {

    private static final ThreadLocal<UUID> TRACE_ID = new ThreadLocal<>();

    public static UUID getTraceId() {
        UUID id = TRACE_ID.get();
        if (id == null) {
            id = UUID.randomUUID();
            TRACE_ID.set(id);
        }
        return id;
    }

    public static void setTraceId(UUID id) {
        TRACE_ID.set(id);
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
