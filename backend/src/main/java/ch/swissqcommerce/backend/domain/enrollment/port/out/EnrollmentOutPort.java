package ch.swissqcommerce.backend.domain.enrollment.port.out;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.enrollment.core.model.*;
import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import ch.swissqcommerce.backend.model.*;

import java.math.BigDecimal;
import java.util.Optional;

public interface EnrollmentOutPort {
    void saveOnboardingApplication(OnboardingApplication app);
    void saveRider(Rider rider);
    void saveRiderAcademyCertificate(RiderAcademyCertificate cert);
    void saveOrder(Order order);
    void saveCustomer(Customer customer);
    void saveTrustLedger(SecurityTrustLedger ledger);
    Optional<Order> findOrderById(Integer orderId);
    Optional<Rider> findRiderById(String riderId);
    void injectDryIce(Integer orderId);
    OrderTelemetryLogEntity recordTelemetry(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp);
}

