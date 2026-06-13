package ch.swissqcommerce.backend.domain.customer.port.out;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import java.util.Optional;

public interface CustomerProfilePort {
    CustomerProfile save(CustomerProfile profile);

    Optional<CustomerProfile> findById(String profileId);
}
