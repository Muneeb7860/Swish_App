package ch.swissqcommerce.backend.domain.customer.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import ch.swissqcommerce.backend.domain.customer.core.model.Preferences;
import ch.swissqcommerce.backend.domain.customer.port.out.CustomerProfilePort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerProfilePersistenceAdapter implements CustomerProfilePort {
    private final CustomerProfileRepository repository;

    @Override
    public CustomerProfile save(CustomerProfile profile) {
        CustomerProfileEntity entity =
                CustomerProfileEntity.builder()
                        .profileId(profile.getProfileId())
                        .userId(profile.getUserId())
                        .marketingOptIn(
                                profile.getPrefs() != null && profile.getPrefs().isMarketingOptIn())
                        .defaultCurrency(
                                profile.getPrefs() != null
                                        ? profile.getPrefs().getDefaultCurrency()
                                        : "CHF")
                        .build();
        repository.save(entity);
        return profile;
    }

    @Override
    public Optional<CustomerProfile> findById(String profileId) {
        return repository
                .findById(profileId)
                .map(
                        e ->
                                CustomerProfile.builder()
                                        .profileId(e.getProfileId())
                                        .userId(e.getUserId())
                                        .prefs(
                                                Preferences.builder()
                                                        .marketingOptIn(e.isMarketingOptIn())
                                                        .defaultCurrency(e.getDefaultCurrency())
                                                        .build())
                                        .build());
    }
}
