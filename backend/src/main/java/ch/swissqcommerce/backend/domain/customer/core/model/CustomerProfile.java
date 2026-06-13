package ch.swissqcommerce.backend.domain.customer.core.model;

import java.util.ArrayList;
import java.util.List;

public class CustomerProfile {
    private String profileId;
    private String userId;
    private Preferences prefs;
    private List<DeliveryAddress> addressBook;

    public CustomerProfile() {}

    public CustomerProfile(
            String profileId, String userId, Preferences prefs, List<DeliveryAddress> addressBook) {
        this.profileId = profileId;
        this.userId = userId;
        this.prefs = prefs;
        this.addressBook = addressBook;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Preferences getPrefs() {
        return prefs;
    }

    public void setPrefs(Preferences prefs) {
        this.prefs = prefs;
    }

    public List<DeliveryAddress> getAddressBook() {
        return addressBook;
    }

    public void setAddressBook(List<DeliveryAddress> addressBook) {
        this.addressBook = addressBook;
    }

    public void addAddress(DeliveryAddress address) {
        if (addressBook == null) addressBook = new ArrayList<>();
        addressBook.add(address);
    }

    public void removeAddress(String addressId) {
        if (addressBook != null) addressBook.removeIf(a -> a.getAddressId().equals(addressId));
    }

    public void updatePreferences(Preferences prefs) {
        this.prefs = prefs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String profileId;
        private String userId;
        private Preferences prefs;
        private List<DeliveryAddress> addressBook;

        public Builder profileId(String profileId) {
            this.profileId = profileId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder prefs(Preferences prefs) {
            this.prefs = prefs;
            return this;
        }

        public Builder addressBook(List<DeliveryAddress> addressBook) {
            this.addressBook = addressBook;
            return this;
        }

        public CustomerProfile build() {
            return new CustomerProfile(profileId, userId, prefs, addressBook);
        }
    }
}
