package ch.swissqcommerce.backend.domain.governance.core.model;

public enum AssigneeRole {
    PRICING_MANAGER("pricing"),
    OPS_MANAGER("inventory"),
    LOGISTICS_MANAGER("routing"),
    RISK_ANALYST("risk"),
    SUPPORT_LEAD("support");

    private final String domain;

    AssigneeRole(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }

    public static AssigneeRole fromString(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        for (AssigneeRole role : values()) {
            if (role.name().equalsIgnoreCase(value) || role.getDomain().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown assignee role: " + value);
    }
}
