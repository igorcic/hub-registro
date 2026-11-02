package br.com.hubregistro.auth.domain.reset;

import br.com.hubregistro.auth.domain.AuthErrors.InvalidTokenException;
import br.com.hubregistro.commons.id.UuidV7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Token de uso único enviado por e-mail para ativar a conta ou redefinir a senha. */
public class PasswordToken {

    private final UUID id;
    private final UUID userId;
    private final byte[] tokenHash;
    private final TokenPurpose purpose;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String requestedIp;
    private Instant usedAt;

    private PasswordToken(UUID id, UUID userId, byte[] tokenHash, TokenPurpose purpose, Instant createdAt,
                          Instant expiresAt, String requestedIp, Instant usedAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.purpose = Objects.requireNonNull(purpose);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.requestedIp = requestedIp;
        this.usedAt = usedAt;
    }

    public static PasswordToken issue(UUID userId, byte[] tokenHash, TokenPurpose purpose, Instant now, String requestedIp) {
        return new PasswordToken(UuidV7.generate(), userId, tokenHash, purpose, now, now.plus(purpose.validity()),
                requestedIp, null);
    }

    public static PasswordToken restore(UUID id, UUID userId, byte[] tokenHash, TokenPurpose purpose, Instant createdAt,
                                        Instant expiresAt, String requestedIp, Instant usedAt) {
        return new PasswordToken(id, userId, tokenHash, purpose, createdAt, expiresAt, requestedIp, usedAt);
    }

    /** Marca como usado; falha se já usado ou expirado. */
    public void consume(Instant now) {
        if (usedAt != null || !now.isBefore(expiresAt)) {
            throw new InvalidTokenException();
        }
        usedAt = now;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public byte[] tokenHash() { return tokenHash.clone(); }
    public TokenPurpose purpose() { return purpose; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public String requestedIp() { return requestedIp; }
    public Instant usedAt() { return usedAt; }
}
