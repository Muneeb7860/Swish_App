package ch.swissqcommerce.backend.domain.enrollment.port.in;

import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface RiderUseCase {
    Map<String, Object> submitOnboarding(String name, String vehicleType, String details);
    Map<String, Object> injectCoolant(Integer orderId);
    OrderTelemetryLog recordPing(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp);
    Map<String, Object> confirmDelivery(Integer orderId, String pin, String photoUrl);
    Map<String, Object> rejectDelivery(Integer orderId, String reason, String rejectionPhotoUrl);
    List<Map<String, String>> getAcademyCourses();
    Map<String, Object> completeAcademyCourse(String riderId, String courseId);
    Map<String, Object> approveOnboarding(String applicationId, String gateName);
}
