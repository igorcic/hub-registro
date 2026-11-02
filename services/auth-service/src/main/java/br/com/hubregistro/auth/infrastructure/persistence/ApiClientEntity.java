package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.client.ApiClientStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "api_clients")
public class ApiClientEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private UUID institutionId;

    @Column(nullable = false)
    private String name;

    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(name = "client_secret_hash", nullable = false, updatable = false)
    private String clientSecretHash;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] scopes;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiClientStatus status;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ApiClientEntity() {
    }

    static ApiClientEntity newFrom(ApiClient client) {
        ApiClientEntity entity = new ApiClientEntity();
        entity.id = client.id();
        entity.institutionId = client.institutionId();
        entity.clientId = client.clientId();
        entity.clientSecretHash = client.secretHash();
        entity.expiresAt = client.expiresAt();
        entity.createdAt = client.createdAt();
        entity.updatedAt = client.createdAt();
        entity.copyFrom(client);
        return entity;
    }

    void copyFrom(ApiClient client) {
        name = client.name();
        scopes = client.scopes().toArray(String[]::new);
        rateLimitPerMinute = client.rateLimitPerMinute();
        status = client.status();
        lastUsedAt = client.lastUsedAt();
    }

    ApiClient toDomain() {
        return ApiClient.restore(id, institutionId, name, clientId, clientSecretHash, Set.of(scopes), rateLimitPerMinute,
                status, expiresAt, lastUsedAt, createdAt);
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }
}
