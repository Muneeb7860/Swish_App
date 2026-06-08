import os

base_paths = [
    "backend/src/main/java/ch/swissqcommerce/backend/domain/fleet",
    "backend/src/main/java/ch/swissqcommerce/backend/domain/geospatial",
    "backend/src/main/java/ch/swissqcommerce/backend/domain/support"
]

for base_path in base_paths:
    dirs = [
        f"{base_path}/core/model",
        f"{base_path}/core/service",
        f"{base_path}/port/in",
        f"{base_path}/port/out",
        f"{base_path}/adapter/in/event",
        f"{base_path}/adapter/in/web",
        f"{base_path}/adapter/out/persistence",
    ]
    for d in dirs:
        os.makedirs(d, exist_ok=True)

files = {
    # Phase 13: Fleet Operations
    f"{base_paths[0]}/core/model/RiderShift.java": """package ch.swissqcommerce.backend.domain.fleet.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderShift {
    private String shiftId;
    private String riderId;
    private Instant startTime;
    private Instant endTime;
    private String status;

    public void checkIn() { this.status = "ACTIVE"; }
    public void complete() { this.status = "COMPLETED"; }
    public void markNoShow() { this.status = "NO_SHOW"; }
}
""",
    f"{base_paths[0]}/core/model/PayoutLedger.java": """package ch.swissqcommerce.backend.domain.fleet.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutLedger {
    private String ledgerId;
    private String riderId;
    private BigDecimal balance;
    private String status;

    public void addEarnings(BigDecimal amount) {
        if(balance == null) balance = BigDecimal.ZERO;
        this.balance = this.balance.add(amount);
    }
    public void processPayout() {
        this.balance = BigDecimal.ZERO;
        this.status = "PAID";
    }
}
""",
    f"{base_paths[0]}/port/in/FleetUseCase.java": """package ch.swissqcommerce.backend.domain.fleet.port.in;

import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;

public interface FleetUseCase {
    RiderShift scheduleShift(RiderShift shift);
    PayoutLedger processRiderPayout(String riderId);
}
""",
    f"{base_paths[0]}/port/out/FleetPort.java": """package ch.swissqcommerce.backend.domain.fleet.port.out;

import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;

public interface FleetPort {
    RiderShift saveShift(RiderShift shift);
    PayoutLedger saveLedger(PayoutLedger ledger);
}
""",
    f"{base_paths[0]}/core/service/FleetServiceImpl.java": """package ch.swissqcommerce.backend.domain.fleet.core.service;

import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;
import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.port.in.FleetUseCase;
import ch.swissqcommerce.backend.domain.fleet.port.out.FleetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FleetServiceImpl implements FleetUseCase {
    private final FleetPort port;

    @Override
    public RiderShift scheduleShift(RiderShift shift) {
        shift.setStatus("SCHEDULED");
        return port.saveShift(shift);
    }

    @Override
    public PayoutLedger processRiderPayout(String riderId) {
        // Find, process, save
        return null;
    }
}
""",
    f"{base_paths[0]}/adapter/out/persistence/RiderShiftEntity.java": """package ch.swissqcommerce.backend.domain.fleet.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "rider_shifts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderShiftEntity {
    @Id
    private String shiftId;
    private String riderId;
    private Instant startTime;
    private Instant endTime;
    private String status;
}
""",
    f"{base_paths[0]}/adapter/out/persistence/RiderShiftRepository.java": """package ch.swissqcommerce.backend.domain.fleet.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderShiftRepository extends JpaRepository<RiderShiftEntity, String> {
}
""",
    f"{base_paths[0]}/adapter/out/persistence/FleetPersistenceAdapter.java": """package ch.swissqcommerce.backend.domain.fleet.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;
import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.port.out.FleetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FleetPersistenceAdapter implements FleetPort {
    private final RiderShiftRepository shiftRepository;

    @Override
    public RiderShift saveShift(RiderShift shift) {
        RiderShiftEntity entity = RiderShiftEntity.builder()
                .shiftId(shift.getShiftId())
                .riderId(shift.getRiderId())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .status(shift.getStatus())
                .build();
        shiftRepository.save(entity);
        return shift;
    }

    @Override
    public PayoutLedger saveLedger(PayoutLedger ledger) {
        return ledger; // mock
    }
}
""",

    # Phase 14: Geospatial
    f"{base_paths[1]}/core/model/DeliveryZone.java": """package ch.swissqcommerce.backend.domain.geospatial.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryZone {
    private String zoneId;
    private String name;
    private String geoPolygonWkt;
    private String status;

    public void suspend() { this.status = "SUSPENDED"; }
    public void activate() { this.status = "ACTIVE"; }
}
""",
    f"{base_paths[1]}/port/in/GeospatialUseCase.java": """package ch.swissqcommerce.backend.domain.geospatial.port.in;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import java.util.Optional;

public interface GeospatialUseCase {
    DeliveryZone createZone(DeliveryZone zone);
    Optional<DeliveryZone> getZone(String zoneId);
}
""",
    f"{base_paths[1]}/port/out/GeospatialPort.java": """package ch.swissqcommerce.backend.domain.geospatial.port.out;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import java.util.Optional;

public interface GeospatialPort {
    DeliveryZone save(DeliveryZone zone);
    Optional<DeliveryZone> findById(String zoneId);
}
""",
    f"{base_paths[1]}/core/service/GeospatialServiceImpl.java": """package ch.swissqcommerce.backend.domain.geospatial.core.service;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import ch.swissqcommerce.backend.domain.geospatial.port.in.GeospatialUseCase;
import ch.swissqcommerce.backend.domain.geospatial.port.out.GeospatialPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GeospatialServiceImpl implements GeospatialUseCase {
    private final GeospatialPort port;

    @Override
    public DeliveryZone createZone(DeliveryZone zone) {
        zone.activate();
        return port.save(zone);
    }

    @Override
    public Optional<DeliveryZone> getZone(String zoneId) {
        return port.findById(zoneId);
    }
}
""",
    f"{base_paths[1]}/adapter/out/persistence/DeliveryZoneEntity.java": """package ch.swissqcommerce.backend.domain.geospatial.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "delivery_zones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryZoneEntity {
    @Id
    private String zoneId;
    private String name;
    private String geoPolygonWkt;
    private String status;
}
""",
    f"{base_paths[1]}/adapter/out/persistence/DeliveryZoneRepository.java": """package ch.swissqcommerce.backend.domain.geospatial.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZoneEntity, String> {
}
""",
    f"{base_paths[1]}/adapter/out/persistence/GeospatialPersistenceAdapter.java": """package ch.swissqcommerce.backend.domain.geospatial.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import ch.swissqcommerce.backend.domain.geospatial.port.out.GeospatialPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GeospatialPersistenceAdapter implements GeospatialPort {
    private final DeliveryZoneRepository repository;

    @Override
    public DeliveryZone save(DeliveryZone zone) {
        DeliveryZoneEntity entity = DeliveryZoneEntity.builder()
                .zoneId(zone.getZoneId())
                .name(zone.getName())
                .geoPolygonWkt(zone.getGeoPolygonWkt())
                .status(zone.getStatus())
                .build();
        repository.save(entity);
        return zone;
    }

    @Override
    public Optional<DeliveryZone> findById(String zoneId) {
        return repository.findById(zoneId).map(e -> DeliveryZone.builder()
                .zoneId(e.getZoneId())
                .name(e.getName())
                .geoPolygonWkt(e.getGeoPolygonWkt())
                .status(e.getStatus())
                .build());
    }
}
""",

    # Phase 15: Customer Support
    f"{base_paths[2]}/core/model/SupportTicket.java": """package ch.swissqcommerce.backend.domain.support.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {
    private String ticketId;
    private String customerId;
    private String orderId;
    private String priority;
    private String status;

    public void escalate() { this.priority = "HIGH"; }
    public void resolve() { this.status = "RESOLVED"; }
}
""",
    f"{base_paths[2]}/port/in/SupportUseCase.java": """package ch.swissqcommerce.backend.domain.support.port.in;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import java.util.Optional;

public interface SupportUseCase {
    SupportTicket createTicket(SupportTicket ticket);
    Optional<SupportTicket> getTicket(String ticketId);
}
""",
    f"{base_paths[2]}/port/out/SupportPort.java": """package ch.swissqcommerce.backend.domain.support.port.out;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import java.util.Optional;

public interface SupportPort {
    SupportTicket save(SupportTicket ticket);
    Optional<SupportTicket> findById(String ticketId);
}
""",
    f"{base_paths[2]}/core/service/SupportServiceImpl.java": """package ch.swissqcommerce.backend.domain.support.core.service;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import ch.swissqcommerce.backend.domain.support.port.in.SupportUseCase;
import ch.swissqcommerce.backend.domain.support.port.out.SupportPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SupportServiceImpl implements SupportUseCase {
    private final SupportPort port;

    @Override
    public SupportTicket createTicket(SupportTicket ticket) {
        ticket.setStatus("OPEN");
        return port.save(ticket);
    }

    @Override
    public Optional<SupportTicket> getTicket(String ticketId) {
        return port.findById(ticketId);
    }
}
""",
    f"{base_paths[2]}/adapter/out/persistence/SupportTicketEntity.java": """package ch.swissqcommerce.backend.domain.support.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "support_tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketEntity {
    @Id
    private String ticketId;
    private String customerId;
    private String orderId;
    private String priority;
    private String status;
}
""",
    f"{base_paths[2]}/adapter/out/persistence/SupportTicketRepository.java": """package ch.swissqcommerce.backend.domain.support.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, String> {
}
""",
    f"{base_paths[2]}/adapter/out/persistence/SupportPersistenceAdapter.java": """package ch.swissqcommerce.backend.domain.support.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import ch.swissqcommerce.backend.domain.support.port.out.SupportPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SupportPersistenceAdapter implements SupportPort {
    private final SupportTicketRepository repository;

    @Override
    public SupportTicket save(SupportTicket ticket) {
        SupportTicketEntity entity = SupportTicketEntity.builder()
                .ticketId(ticket.getTicketId())
                .customerId(ticket.getCustomerId())
                .orderId(ticket.getOrderId())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .build();
        repository.save(entity);
        return ticket;
    }

    @Override
    public Optional<SupportTicket> findById(String ticketId) {
        return repository.findById(ticketId).map(e -> SupportTicket.builder()
                .ticketId(e.getTicketId())
                .customerId(e.getCustomerId())
                .orderId(e.getOrderId())
                .priority(e.getPriority())
                .status(e.getStatus())
                .build());
    }
}
"""
}

for path, content in files.items():
    with open(path, "w") as f:
        f.write(content)

print("Phases 13, 14, and 15 scaffolded successfully!")
