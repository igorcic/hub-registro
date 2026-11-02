package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.reset.PasswordToken;
import br.com.hubregistro.auth.domain.reset.TokenPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordTokenEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TokenPurpose purpose;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "requested_ip", updatable = false)
    private String requestedIp;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordTokenEntity() {
    }

    static PasswordTokenEntity newFrom(PasswordToken token) {
        PasswordTokenEntity entity = new PasswordTokenEntity();
        entity.id = token.id();
        entity.userId = token.userId();
        entity.tokenHash = token.tokenHash();
        entity.purpose = token.purpose();
        entity.createdAt = token.createdAt();
        entity.expiresAt = token.expiresAt();
        entity.requestedIp = token.requestedIp();
        entity.usedAt = token.usedAt();
        return entity;
    }

    void copyFrom(PasswordToken token) {
        usedAt = token.usedAt();
    }

    PasswordToken toDomain() {
        return PasswordToken.restore(id, userId, tokenHash, purpose, createdAt, expiresAt, requestedIp, usedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }
}
