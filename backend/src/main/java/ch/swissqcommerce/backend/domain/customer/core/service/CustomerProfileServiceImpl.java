package ch.swissqcommerce.backend.domain.customer.core.service;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import ch.swissqcommerce.backend.domain.customer.port.in.CustomerProfileUseCase;
import ch.swissqcommerce.backend.domain.customer.port.out.CustomerProfilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileUseCase {
    private final CustomerProfilePort port;

    @Override
    public CustomerProfile createProfile(CustomerProfile profile) {
        if (profile.getProfileId() == null) profile.setProfileId(UUID.randomUUID().toString());
        return port.save(profile);
    }

    @Override
    public Optional<CustomerProfile> getProfile(String profileId) {
        return port.findById(profileId);
    }
}
