package br.com.hubregistro.integration.domain;

import java.util.Set;

/** Como o Hub se autentica na registradora, e quais credenciais cada forma exige. */
public enum AuthType {
    NONE(Set.of(), Set.of()),
    API_KEY(Set.of("headerName", "apiKey"), Set.of("apiKey")),
    BASIC(Set.of("username", "password"), Set.of("password")),
    OAUTH2_CLIENT_CREDENTIALS(Set.of("tokenUrl", "clientId", "clientSecret"), Set.of("clientSecret")),
    /** Certificado de cliente: a credencial é o alias de um certificado CLIENT_AUTH cadastrado. */
    MTLS(Set.of("certificateAlias"), Set.of());

    private final Set<String> requiredKeys;
    private final Set<String> secretKeys;

    AuthType(Set<String> requiredKeys, Set<String> secretKeys) {
        this.requiredKeys = requiredKeys;
        this.secretKeys = secretKeys;
    }

    public Set<String> requiredKeys() { return requiredKeys; }

    /** Chaves nunca devolvidas pela API (só informamos que estão configuradas). */
    public Set<String> secretKeys() { return secretKeys; }
}
