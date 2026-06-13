package ch.swissqcommerce.backend.domain.dispatch.adapter.in.web;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import ch.swissqcommerce.backend.domain.dispatch.port.in.DispatchUseCase;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    @Autowired private DispatchUseCase dispatchUseCase;

    @PostMapping("/gear-scan")
    public ResponseEntity<GearScan> submitGearScan(@RequestBody GearScanRequest request) {
        GearScan scan =
                dispatchUseCase.submitGearScan(
                        request.getRiderId(),
                        request.getGearType(),
                        request.getVerificationStatus(),
                        request.getImageUrl());
        return ResponseEntity.ok(scan);
    }

    @PostMapping("/gps-ping")
    public ResponseEntity<ActiveShipment> updateRiderGps(@RequestBody GpsPingRequest request) {
        ActiveShipment shipment =
                dispatchUseCase.updateRiderGps(
                        request.getRiderId(), request.getLatitude(), request.getLongitude());
        return ResponseEntity.ok(shipment);
    }

    @PostMapping("/reallocate")
    public ResponseEntity<List<Integer>> runReallocationAudit() {
        List<Integer> reallocated = dispatchUseCase.runReallocationAudit();
        return ResponseEntity.ok(reallocated);
    }

    @PostMapping("/assign")
    public ResponseEntity<ActiveShipment> assignOrder(@RequestBody AssignmentRequest request) {
        ActiveShipment shipment =
                dispatchUseCase.assignOrder(
                        request.getOrderId(), request.getRiderId(), request.getWeightKg());
        return ResponseEntity.ok(shipment);
    }

    @PostMapping("/status")
    public ResponseEntity<ActiveShipment> updateStatus(@RequestBody StatusUpdateRequest request) {
        ActiveShipment shipment =
                dispatchUseCase.updateShipmentStatus(request.getOrderId(), request.getStatus());
        return ResponseEntity.ok(shipment);
    }

    public static class GearScanRequest {
        private String riderId;
        private String gearType;
        private String verificationStatus;
        private String imageUrl;

        public String getRiderId() {
            return riderId;
        }

        public void setRiderId(String riderId) {
            this.riderId = riderId;
        }

        public String getGearType() {
            return gearType;
        }

        public void setGearType(String gearType) {
            this.gearType = gearType;
        }

        public String getVerificationStatus() {
            return verificationStatus;
        }

        public void setVerificationStatus(String verificationStatus) {
            this.verificationStatus = verificationStatus;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }
    }

    public static class GpsPingRequest {
        private String riderId;
        private BigDecimal latitude;
        private BigDecimal longitude;

        public String getRiderId() {
            return riderId;
        }

        public void setRiderId(String riderId) {
            this.riderId = riderId;
        }

        public BigDecimal getLatitude() {
            return latitude;
        }

        public void setLatitude(BigDecimal latitude) {
            this.latitude = latitude;
        }

        public BigDecimal getLongitude() {
            return longitude;
        }

        public void setLongitude(BigDecimal longitude) {
            this.longitude = longitude;
        }
    }

    public static class AssignmentRequest {
        private Integer orderId;
        private String riderId;
        private BigDecimal weightKg;

        public Integer getOrderId() {
            return orderId;
        }

        public void setOrderId(Integer orderId) {
            this.orderId = orderId;
        }

        public String getRiderId() {
            return riderId;
        }

        public void setRiderId(String riderId) {
            this.riderId = riderId;
        }

        public BigDecimal getWeightKg() {
            return weightKg;
        }

        public void setWeightKg(BigDecimal weightKg) {
            this.weightKg = weightKg;
        }
    }

    public static class StatusUpdateRequest {
        private Integer orderId;
        private String status;

        public Integer getOrderId() {
            return orderId;
        }

        public void setOrderId(Integer orderId) {
            this.orderId = orderId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
