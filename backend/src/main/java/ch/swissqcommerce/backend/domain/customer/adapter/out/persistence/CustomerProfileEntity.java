package ch.swissqcommerce.backend.domain.customer.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_profiles")
public class CustomerProfileEntity {
    @Id
    private String profileId;
    private String userId;
    private boolean marketingOptIn;
    private String defaultCurrency;

    public CustomerProfileEntity() {}

    public CustomerProfileEntity(String profileId, String userId, boolean marketingOptIn, String defaultCurrency) {
        this.profileId = profileId;
        this.userId = userId;
        this.marketingOptIn = marketingOptIn;
        this.defaultCurrency = defaultCurrency;
    }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isMarketingOptIn() { return marketingOptIn; }
    public void setMarketingOptIn(boolean marketingOptIn) { this.marketingOptIn = marketingOptIn; }

    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String profileId;
        private String userId;
        private boolean marketingOptIn;
        private String defaultCurrency;

        public Builder profileId(String profileId) { this.profileId = profileId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder marketingOptIn(boolean marketingOptIn) { this.marketingOptIn = marketingOptIn; return this; }
        public Builder defaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; return this; }

        public CustomerProfileEntity build() {
            return new CustomerProfileEntity(profileId, userId, marketingOptIn, defaultCurrency);
        }
    }
}