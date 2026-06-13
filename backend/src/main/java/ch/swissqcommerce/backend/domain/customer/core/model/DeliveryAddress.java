package ch.swissqcommerce.backend.domain.customer.core.model;

public class DeliveryAddress {
    private String addressId;
    private String label;
    private String street;
    private String city;
    private String geoHash;

    public DeliveryAddress() {}

    public DeliveryAddress(
            String addressId, String label, String street, String city, String geoHash) {
        this.addressId = addressId;
        this.label = label;
        this.street = street;
        this.city = city;
        this.geoHash = geoHash;
    }

    public String getAddressId() {
        return addressId;
    }

    public void setAddressId(String addressId) {
        this.addressId = addressId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getGeoHash() {
        return geoHash;
    }

    public void setGeoHash(String geoHash) {
        this.geoHash = geoHash;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String addressId;
        private String label;
        private String street;
        private String city;
        private String geoHash;

        public Builder addressId(String addressId) {
            this.addressId = addressId;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder street(String street) {
            this.street = street;
            return this;
        }

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder geoHash(String geoHash) {
            this.geoHash = geoHash;
            return this;
        }

        public DeliveryAddress build() {
            return new DeliveryAddress(addressId, label, street, city, geoHash);
        }
    }
}
