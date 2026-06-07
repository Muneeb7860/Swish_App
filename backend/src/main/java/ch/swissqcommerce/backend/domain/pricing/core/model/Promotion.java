package ch.swissqcommerce.backend.domain.pricing.core.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Promotion {
    private String code;
    private String type; // PERCENTAGE, FIXED_AMOUNT
    private BigDecimal value;
    private OffsetDateTime expiresAt;

    public Promotion() {}

    public Promotion(String code, String type, BigDecimal value, OffsetDateTime expiresAt) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.expiresAt = expiresAt;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String code;
        private String type;
        private BigDecimal value;
        private OffsetDateTime expiresAt;

        public Builder code(String code) { this.code = code; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder value(BigDecimal value) { this.value = value; return this; }
        public Builder expiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; return this; }

        public Promotion build() {
            return new Promotion(code, type, value, expiresAt);
        }
    }
}
