package ch.swissqcommerce.backend.domain.fleet.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rider_shifts", schema = "dispatch")
public class RiderShiftEntity {
    @Id
    private String shiftId;
    private String riderId;
    private Instant startTime;
    private Instant endTime;
    private String status;

    public RiderShiftEntity() {}

    public RiderShiftEntity(String shiftId, String riderId, Instant startTime, Instant endTime, String status) {
        this.shiftId = shiftId;
        this.riderId = riderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }

    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String shiftId;
        private String riderId;
        private Instant startTime;
        private Instant endTime;
        private String status;

        public Builder shiftId(String shiftId) { this.shiftId = shiftId; return this; }
        public Builder riderId(String riderId) { this.riderId = riderId; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder status(String status) { this.status = status; return this; }

        public RiderShiftEntity build() {
            return new RiderShiftEntity(shiftId, riderId, startTime, endTime, status);
        }
    }
}