package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.Picker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PickerRepository extends JpaRepository<Picker, String> {
    List<Picker> findByActiveStoreStoreId(String storeId);
    List<Picker> findByLightningBadge(Boolean lightningBadge);
}
