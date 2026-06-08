package ch.swissqcommerce.backend.domain.customer.core.model;

public class Preferences {
    private boolean marketingOptIn;
    private String defaultCurrency;

    public Preferences() {}

    public Preferences(boolean marketingOptIn, String defaultCurrency) {
        this.marketingOptIn = marketingOptIn;
        this.defaultCurrency = defaultCurrency;
    }

    public boolean isMarketingOptIn() { return marketingOptIn; }
    public void setMarketingOptIn(boolean marketingOptIn) { this.marketingOptIn = marketingOptIn; }

    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean marketingOptIn;
        private String defaultCurrency;

        public Builder marketingOptIn(boolean marketingOptIn) { this.marketingOptIn = marketingOptIn; return this; }
        public Builder defaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; return this; }

        public Preferences build() {
            return new Preferences(marketingOptIn, defaultCurrency);
        }
    }
}
