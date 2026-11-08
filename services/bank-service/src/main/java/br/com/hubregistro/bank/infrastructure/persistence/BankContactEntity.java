package br.com.hubregistro.bank.infrastructure.persistence;

import br.com.hubregistro.bank.domain.BankContact;
import br.com.hubregistro.bank.domain.ContactArea;
import br.com.hubregistro.commons.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_contacts")
public class BankContactEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "bank_id", nullable = false, updatable = false)
    private UUID bankId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactArea area;

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

    protected BankContactEntity() {
    }

    static BankContactEntity of(UUID bankId, BankContact contact) {
        BankContactEntity entity = new BankContactEntity();
        entity.id = UuidV7.generate();
        entity.bankId = bankId;
        entity.name = contact.name();
        entity.email = contact.email();
        entity.phone = contact.phone();
        entity.area = contact.area();
        entity.primary = contact.primary();
        entity.createdAt = Instant.now();
        entity.updatedAt = entity.createdAt;
        entity.isNew = true;
        return entity;
    }

    BankContact toDomain() {
        return new BankContact(name, email, phone, area, primary);
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
