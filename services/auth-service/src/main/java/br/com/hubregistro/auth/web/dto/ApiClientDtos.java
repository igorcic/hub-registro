package br.com.hubregistro.auth.web.dto;

import br.com.hubregistro.auth.domain.client.ApiClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ApiClientDtos {

    private ApiClientDtos() {
    }

    public record CreateApiClientRequest(UUID institutionId,
                                         @NotBlank @Size(max = 100) String name,
                                         @NotEmpty Set<@NotBlank String> scopes,
                                         @Min(1) @Max(6000) Integer rateLimitPerMinute,
                                         Instant expiresAt) {
    }

    public record ApiClientResponse(UUID id, UUID institutionId, String name, String clientId, List<String> scopes,
                                    int rateLimitPerMinute, String status, Instant expiresAt, Instant lastUsedAt,
                                    Instant createdAt) {
        public static ApiClientResponse of(ApiClient c) {
            return new ApiClientResponse(c.id(), c.institutionId(), c.name(), c.clientId(),
                    c.scopes().stream().sorted().toList(), c.rateLimitPerMinute(), c.status().name(), c.expiresAt(),
                    c.lastUsedAt(), c.createdAt());
        }
    }

    /** Resposta da criação: o {@code clientSecret} não pode ser consultado depois. */
    public record CreatedApiClientResponse(ApiClientResponse client, String clientSecret) {
    }

    /** Resposta no formato do RFC 6749. */
    public record OAuthTokenResponse(@JsonProperty("access_token") String accessToken,
                                     @JsonProperty("token_type") String tokenType,
                                     @JsonProperty("expires_in") long expiresIn,
                                     String scope) {
    }

    public record OAuthError(String error, @JsonProperty("error_description") String errorDescription) {
    }
}
