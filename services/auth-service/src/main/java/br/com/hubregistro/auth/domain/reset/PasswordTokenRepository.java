package br.com.hubregistro.auth.domain.reset;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordTokenRepository {

    Optional<PasswordToken> findByHashForUpdate(byte[] tokenHash);

    void save(PasswordToken token);

    /** Invalida links anteriores ainda não usados: só o mais recente vale. */
    int invalidatePendingForUser(UUID userId, Instant now);
}
