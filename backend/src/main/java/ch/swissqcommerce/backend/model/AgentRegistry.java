package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "agent_registry", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRegistry {

    @Id
    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "domain", length = 50, nullable = false)
    private String domain;

    @Column(name = "version", length = 30, nullable = false)
    private String version;

    @Column(name = "status", length = 20, nullable = false)
    private String status; // active, inactive, shadow

    @Column(name = "owner_team", length = 50, nullable = false)
    private String ownerTeam;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
