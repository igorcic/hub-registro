package br.com.hubregistro.registry.infrastructure.persistence;

import br.com.hubregistro.commons.id.UuidV7;
import br.com.hubregistro.registry.domain.TechnicalContact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registry_technical_contacts")
public class TechnicalContactEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "registry_id", nullable = false, updatable = false)
    private UUID registryId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew;

    protected TechnicalContactEntity() {
    }

    static TechnicalContactEntity of(UUID registryId, TechnicalContact c) {
        TechnicalContactEntity e = new TechnicalContactEntity();
        e.id = UuidV7.generate();
        e.registryId = registryId;
        e.name = c.name();
        e.email = c.email();
        e.phone = c.phone();
        e.jobTitle = c.jobTitle();
        e.primary = c.primary();
        e.createdAt = Instant.now();
        e.updatedAt = e.createdAt;
        e.isNew = true;
        return e;
    }

    TechnicalContact toDomain() {
        return new TechnicalContact(name, email, phone, jobTitle, primary);
    }

    void markDeleted(Instant now) {
        deletedAt = now;
        updatedAt = now;
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }
}
