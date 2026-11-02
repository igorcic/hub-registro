package br.com.hubregistro.auth.domain.session;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    /** Busca com bloqueio da linha: duas renovações simultâneas do mesmo token não geram dois sucessores. */
    Optional<RefreshToken> findByHashForUpdate(byte[] tokenHash);

    void save(RefreshToken token);

    int revokeFamily(UUID familyId, Instant now, String reason);

    int revokeAllForUser(UUID userId, Instant now, String reason);
}
