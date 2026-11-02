package br.com.hubregistro.auth.domain.session;

import java.time.Duration;

/**
 * @param accessTokenTtl  validade do access token (JWT); curta porque não pode ser revogado individualmente
 * @param refreshTokenTtl validade de cada refresh token; renovada a cada rotação
 * @param maxSession      duração máxima de uma sessão desde o login, independentemente das rotações
 */
public record SessionPolicy(Duration accessTokenTtl, Duration refreshTokenTtl, Duration maxSession) {

    public static final SessionPolicy DEFAULT =
            new SessionPolicy(Duration.ofMinutes(15), Duration.ofHours(8), Duration.ofHours(12));

    public SessionPolicy {
        if (refreshTokenTtl.compareTo(maxSession) > 0) {
            throw new IllegalArgumentException("refreshTokenTtl não pode exceder maxSession");
        }
    }
}
