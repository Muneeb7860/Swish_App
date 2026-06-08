package ch.swissqcommerce.backend.domain.enrollment.port.out;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;


import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.enrollment.core.model.*;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
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
    Optional<OnboardingApplication> findOnboardingApplicationById(String applicationId);
    Optional<Rider> findRiderByFullName(String fullName);
    void injectDryIce(Integer orderId);
    OrderTelemetryLog recordTelemetry(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp);
}

