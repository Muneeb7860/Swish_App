package ch.swissqcommerce.backend.domain.transaction.port.out;

import ch.swissqcommerce.backend.model.DarkStore;
import java.util.Optional;

public interface DarkStorePort {
    Optional<DarkStore> findDarkStoreById(String id);
}
