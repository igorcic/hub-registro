package br.com.hubregistro.auth.domain.client;

import br.com.hubregistro.auth.domain.role.Permissions;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.id.UuidV7;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Credencial OAuth2 (client credentials) com que o sistema de um banco chama a API. */
public class ApiClient {

    public static final int DEFAULT_RATE_LIMIT = 600;
    public static final int MAX_RATE_LIMIT = 6000;

    private final UUID id;
    private final UUID institutionId;
    private final String clientId;
    private final String secretHash;
    private final Instant expiresAt;
    private final Instant createdAt;
    private String name;
    private Set<String> scopes;
    private int rateLimitPerMinute;
    private ApiClientStatus status;
    private Instant lastUsedAt;

    private ApiClient(UUID id, UUID institutionId, String name, String clientId, String secretHash, Set<String> scopes,
                      int rateLimitPerMinute, ApiClientStatus status, Instant expiresAt, Instant lastUsedAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.institutionId = Objects.requireNonNull(institutionId);
        this.clientId = Objects.requireNonNull(clientId);
        this.secretHash = Objects.requireNonNull(secretHash);
        this.name = requireName(name);
        this.scopes = validScopes(scopes);
        this.rateLimitPerMinute = validRateLimit(rateLimitPerMinute);
        this.status = Objects.requireNonNull(status);
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt;
    }

    public static ApiClient create(UUID institutionId, String name, String clientId, String secretHash,
                                   Set<String> scopes, Integer rateLimitPerMinute, Instant expiresAt, Instant now) {
        return new ApiClient(UuidV7.generate(), institutionId, name, clientId, secretHash, scopes,
                rateLimitPerMinute == null ? DEFAULT_RATE_LIMIT : rateLimitPerMinute, ApiClientStatus.ACTIVE,
                expiresAt, null, now);
    }

    public static ApiClient restore(UUID id, UUID institutionId, String name, String clientId, String secretHash,
                                    Set<String> scopes, int rateLimitPerMinute, ApiClientStatus status,
                                    Instant expiresAt, Instant lastUsedAt, Instant createdAt) {
        return new ApiClient(id, institutionId, name, clientId, secretHash, scopes, rateLimitPerMinute, status,
                expiresAt, lastUsedAt, createdAt);
    }

    public boolean isUsable(Instant now) {
        return status == ApiClientStatus.ACTIVE && (expiresAt == null || now.isBefore(expiresAt));
    }

    /** Escopos do token: os pedidos (se houver), que precisam estar todos entre os concedidos. */
    public Set<String> grantScopes(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return scopes;
        }
        if (!scopes.containsAll(requested)) {
            throw new DomainException("invalid_scope", "Escopo não concedido a este cliente");
        }
        return Set.copyOf(requested);
    }

    public void recordUse(Instant now) {
        lastUsedAt = now;
    }

    public void revoke() {
        status = ApiClientStatus.REVOKED;
    }

    private static Set<String> validScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new DomainException("scopes_required", "Informe ao menos um escopo");
        }
        Set<String> invalid = new HashSet<>(scopes);
        invalid.removeAll(Permissions.API_CLIENT_SCOPES);
        if (!invalid.isEmpty()) {
            throw new DomainException("invalid_scope", "Escopos não permitidos para cliente de API: " + invalid);
        }
        return Set.copyOf(scopes);
    }

    private static int validRateLimit(int value) {
        if (value < 1 || value > MAX_RATE_LIMIT) {
            throw new DomainException("invalid_rate_limit", "Limite deve estar entre 1 e " + MAX_RATE_LIMIT + " requisições por minuto");
        }
        return value;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new DomainException("invalid_name", "Nome obrigatório, com até 100 caracteres");
        }
        return name.trim();
    }

    public UUID id() { return id; }
    public UUID institutionId() { return institutionId; }
    public String name() { return name; }
    public String clientId() { return clientId; }
    public String secretHash() { return secretHash; }
    public Set<String> scopes() { return scopes; }
    public int rateLimitPerMinute() { return rateLimitPerMinute; }
    public ApiClientStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public Instant createdAt() { return createdAt; }
}
