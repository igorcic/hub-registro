package br.com.hubregistro.auth.domain.session;

import br.com.hubregistro.commons.id.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresh token opaco (só o hash é guardado). Cada uso gera um novo token da mesma família e invalida o
 * anterior; reapresentar um token já trocado indica roubo e revoga a família inteira.
 */
public class RefreshToken {

    public static final String REASON_ROTATED = "ROTATED";
    public static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";
    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REASON_USER_DISABLED = "USER_DISABLED";
    public static final String REASON_ACCESS_REVOKED = "ACCESS_REVOKED";

    private final UUID id;
    private final UUID userId;
    private final UUID familyId;
    private final byte[] tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant sessionExpiresAt;
    private final String clientIp;
    private final String userAgent;
    private Instant revokedAt;
    private String revokedReason;
    private UUID replacedBy;

    private RefreshToken(UUID id, UUID userId, UUID familyId, byte[] tokenHash, Instant issuedAt, Instant expiresAt,
                         Instant sessionExpiresAt, String clientIp, String userAgent, Instant revokedAt,
                         String revokedReason, UUID replacedBy) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.familyId = Objects.requireNonNull(familyId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.sessionExpiresAt = Objects.requireNonNull(sessionExpiresAt);
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.revokedAt = revokedAt;
        this.revokedReason = revokedReason;
        this.replacedBy = replacedBy;
    }

    /** Primeiro token de uma sessão (login). */
    public static RefreshToken startSession(UUID userId, byte[] tokenHash, Instant now, SessionPolicy policy,
                                            String clientIp, String userAgent) {
        Instant sessionExpiresAt = now.plus(policy.maxSession());
        return new RefreshToken(UuidV7.generate(), userId, UuidV7.generate(), tokenHash, now,
                earliest(now.plus(policy.refreshTokenTtl()), sessionExpiresAt), sessionExpiresAt,
                truncate(clientIp, 45), truncate(userAgent, 500), null, null, null);
    }

    public static RefreshToken restore(UUID id, UUID userId, UUID familyId, byte[] tokenHash, Instant issuedAt,
                                       Instant expiresAt, Instant sessionExpiresAt, String clientIp, String userAgent,
                                       Instant revokedAt, String revokedReason, UUID replacedBy) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, sessionExpiresAt, clientIp,
                userAgent, revokedAt, revokedReason, replacedBy);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean wasRotated() {
        return REASON_ROTATED.equals(revokedReason);
    }

    /** Troca este token por um novo da mesma família, sem estender a duração máxima da sessão. */
    public RefreshToken rotate(byte[] newTokenHash, Instant now, SessionPolicy policy, String clientIp, String userAgent) {
        if (!isActive(now)) {
            throw new IllegalStateException("Só um token ativo pode ser rotacionado");
        }
        RefreshToken next = new RefreshToken(UuidV7.generate(), userId, familyId, newTokenHash, now,
                earliest(now.plus(policy.refreshTokenTtl()), sessionExpiresAt), sessionExpiresAt,
                truncate(clientIp, 45), truncate(userAgent, 500), null, null, null);
        revokedAt = now;
        revokedReason = REASON_ROTATED;
        replacedBy = next.id;
        return next;
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public UUID familyId() { return familyId; }
    public byte[] tokenHash() { return tokenHash.clone(); }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant sessionExpiresAt() { return sessionExpiresAt; }
    public String clientIp() { return clientIp; }
    public String userAgent() { return userAgent; }
    public Instant revokedAt() { return revokedAt; }
    public String revokedReason() { return revokedReason; }
    public UUID replacedBy() { return replacedBy; }
}
