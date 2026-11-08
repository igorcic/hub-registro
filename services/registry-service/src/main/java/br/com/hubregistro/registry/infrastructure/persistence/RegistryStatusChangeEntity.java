package br.com.hubregistro.registry.infrastructure.persistence;

import br.com.hubregistro.commons.id.UuidV7;
import br.com.hubregistro.registry.domain.RegistryStatus;
import br.com.hubregistro.registry.domain.StatusChange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "registry_status_changes")
public class RegistryStatusChangeEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "registry_id", nullable = false)
    private UUID registryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private RegistryStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private RegistryStatus toStatus;

    @Column(nullable = false)
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected RegistryStatusChangeEntity() {
    }

    static RegistryStatusChangeEntity of(StatusChange c) {
        RegistryStatusChangeEntity e = new RegistryStatusChangeEntity();
        e.id = UuidV7.generate();
        e.registryId = c.registryId();
        e.fromStatus = c.from();
        e.toStatus = c.to();
        e.reason = c.reason();
        e.changedBy = c.changedBy();
        e.changedAt = c.changedAt();
        return e;
    }

    StatusChange toDomain() {
        return new StatusChange(registryId, fromStatus, toStatus, reason, changedBy, changedAt);
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return true; }
}
