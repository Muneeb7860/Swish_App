package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.Picker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PickerRepository extends JpaRepository<Picker, String> {
    List<Picker> findByActiveStoreStoreId(String storeId);

    List<Picker> findByLightningBadge(Boolean lightningBadge);
}
