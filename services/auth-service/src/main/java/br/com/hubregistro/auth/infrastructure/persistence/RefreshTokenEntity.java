package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.session.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "session_expires_at", nullable = false, updatable = false)
    private Instant sessionExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "client_ip", updatable = false)
    private String clientIp;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    protected RefreshTokenEntity() {
    }

    static RefreshTokenEntity newFrom(RefreshToken token) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.id = token.id();
        entity.userId = token.userId();
        entity.familyId = token.familyId();
        entity.tokenHash = token.tokenHash();
        entity.issuedAt = token.issuedAt();
        entity.expiresAt = token.expiresAt();
        entity.sessionExpiresAt = token.sessionExpiresAt();
        entity.clientIp = token.clientIp();
        entity.userAgent = token.userAgent();
        entity.copyFrom(token);
        return entity;
    }

    void copyFrom(RefreshToken token) {
        revokedAt = token.revokedAt();
        revokedReason = token.revokedReason();
        replacedBy = token.replacedBy();
    }

    RefreshToken toDomain() {
        return RefreshToken.restore(id, userId, familyId, tokenHash, issuedAt, expiresAt, sessionExpiresAt, clientIp,
                userAgent, revokedAt, revokedReason, replacedBy);
    }

    @Override
    public UUID getId() {
        return id;
    }
}
