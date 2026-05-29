package ch.swissqcommerce.backend.domain.transaction.port.out;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import java.util.List;

public interface RiderPort {
    List<Rider> findAll();
}
