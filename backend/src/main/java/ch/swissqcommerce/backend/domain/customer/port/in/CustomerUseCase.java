package ch.swissqcommerce.backend.domain.customer.port.in;

import java.util.Map;

public interface CustomerUseCase {
    Map<String, Object> purgeProfile(String customerId);
}
