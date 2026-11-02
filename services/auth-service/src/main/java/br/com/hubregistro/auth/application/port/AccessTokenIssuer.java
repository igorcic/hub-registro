package br.com.hubregistro.auth.application.port;

import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.institution.Institution;
import br.com.hubregistro.auth.domain.user.User;

import java.time.Duration;
import java.util.Set;

/** Emite o access token (JWT assinado) que os demais serviços validam. */
public interface AccessTokenIssuer {

    IssuedToken issueForUser(User user, Institution institution, Set<String> permissions, Duration ttl);

    IssuedToken issueForClient(ApiClient client, Institution institution, Set<String> scopes, Duration ttl);

    record IssuedToken(String value, long expiresInSeconds) {
    }
}
