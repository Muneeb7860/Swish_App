package ch.swissqcommerce.backend.domain.fleet.core.model;

import java.time.Instant;

public class RiderShift {
    private String shiftId;
    private String riderId;
    private Instant startTime;
    private Instant endTime;
    private String status;

    public RiderShift() {}

    public RiderShift(
            String shiftId, String riderId, Instant startTime, Instant endTime, String status) {
        this.shiftId = shiftId;
        this.riderId = riderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public String getRiderId() {
        return riderId;
    }

    public void setRiderId(String riderId) {
        this.riderId = riderId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void checkIn() {
        this.status = "ACTIVE";
    }

    public void complete() {
        this.status = "COMPLETED";
    }

    public void markNoShow() {
        this.status = "NO_SHOW";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String shiftId;
        private String riderId;
        private Instant startTime;
        private Instant endTime;
        private String status;

        public Builder shiftId(String shiftId) {
            this.shiftId = shiftId;
            return this;
        }

        public Builder riderId(String riderId) {
            this.riderId = riderId;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public RiderShift build() {
            return new RiderShift(shiftId, riderId, startTime, endTime, status);
        }
    }
}
