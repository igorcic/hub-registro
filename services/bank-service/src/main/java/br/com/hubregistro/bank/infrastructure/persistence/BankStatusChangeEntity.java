package br.com.hubregistro.bank.infrastructure.persistence;

import br.com.hubregistro.bank.domain.BankStatus;
import br.com.hubregistro.bank.domain.StatusChange;
import br.com.hubregistro.commons.id.UuidV7;
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

/** Somente inserção (trigger no banco bloqueia UPDATE e DELETE). */
@Entity
@Immutable
@Table(name = "bank_status_changes")
public class BankStatusChangeEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "bank_id", nullable = false)
    private UUID bankId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private BankStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private BankStatus toStatus;

    @Column(nullable = false)
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected BankStatusChangeEntity() {
    }

    static BankStatusChangeEntity of(StatusChange change) {
        BankStatusChangeEntity entity = new BankStatusChangeEntity();
        entity.id = UuidV7.generate();
        entity.bankId = change.bankId();
        entity.fromStatus = change.from();
        entity.toStatus = change.to();
        entity.reason = change.reason();
        entity.changedBy = change.changedBy();
        entity.changedAt = change.changedAt();
        return entity;
    }

    StatusChange toDomain() {
        return new StatusChange(bankId, fromStatus, toStatus, reason, changedBy, changedAt);
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return true; }
}
