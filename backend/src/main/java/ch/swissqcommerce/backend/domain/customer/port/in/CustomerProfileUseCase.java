package ch.swissqcommerce.backend.domain.customer.port.in;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import java.util.Optional;

public interface CustomerProfileUseCase {
    CustomerProfile createProfile(CustomerProfile profile);

    Optional<CustomerProfile> getProfile(String profileId);
}
