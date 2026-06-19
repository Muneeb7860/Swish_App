package ch.swissqcommerce.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.logistics")
public class LogisticsConfig {
    private double costSavingsThreshold = 2.00;
    private double longHaulMiles = 500.0;

    public double getCostSavingsThreshold() {
        return costSavingsThreshold;
    }

    public void setCostSavingsThreshold(double costSavingsThreshold) {
        this.costSavingsThreshold = costSavingsThreshold;
    }

    public double getLongHaulMiles() {
        return longHaulMiles;
    }

    public void setLongHaulMiles(double longHaulMiles) {
        this.longHaulMiles = longHaulMiles;
    }
}
