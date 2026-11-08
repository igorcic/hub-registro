package br.com.hubregistro.integration.web;

import br.com.hubregistro.integration.domain.AuthType;
import br.com.hubregistro.integration.domain.BankIntegration;
import br.com.hubregistro.integration.domain.Certificate;
import br.com.hubregistro.integration.domain.Endpoint;
import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.HealthStatus;
import br.com.hubregistro.integration.domain.Operation;
import br.com.hubregistro.integration.domain.RegistryIntegration;
import br.com.hubregistro.integration.domain.RetryPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class IntegrationDtos {

    private IntegrationDtos() {
    }

    public record EndpointDto(@NotBlank String method, @NotBlank String path) {
    }

    public record RetryPolicyDto(@Min(0) @Max(20) int maxAttempts, @Min(1) long initialIntervalMs,
                                 @Min(1) @Max(10) double multiplier, @Min(1) long maxIntervalMs) {
        RetryPolicy toDomain() {
            return new RetryPolicy(maxAttempts, Duration.ofMillis(initialIntervalMs), multiplier, Duration.ofMillis(maxIntervalMs));
        }

        static RetryPolicyDto of(RetryPolicy p) {
            return new RetryPolicyDto(p.maxAttempts(), p.initialInterval().toMillis(), p.multiplier(), p.maxInterval().toMillis());
        }
    }

    /**
     * Credenciais: para manter um segredo já cadastrado, basta omiti-lo (ex.: trocar a URL sem reenviar a senha).
     */
    public record ConfigureRegistryIntegration(@NotBlank @Size(max = 50) String adapterType,
                                               @NotBlank @Size(max = 500) String baseUrl,
                                               @NotNull AuthType authType,
                                               Map<String, String> credentials,
                                               @Min(100) @Max(60000) Integer connectTimeoutMs,
                                               @Min(100) @Max(300000) Integer readTimeoutMs,
                                               @Valid RetryPolicyDto retryPolicy,
                                               @Min(1) Integer rateLimitPerMinute,
                                               @Min(1) @Max(100) Integer circuitFailureRatePct,
                                               Map<Operation, @Valid EndpointDto> endpoints) {
        RegistryIntegration.Settings toSettings() {
            Map<Operation, Endpoint> eps = new EnumMap<>(Operation.class);
            if (endpoints != null) {
                endpoints.forEach((op, e) -> eps.put(op, new Endpoint(e.method(), e.path())));
            }
            return new RegistryIntegration.Settings(adapterType == null ? null : adapterType.trim().toUpperCase(),
                    baseUrl, authType, credentials,
                    connectTimeoutMs == null ? 5_000 : connectTimeoutMs, readTimeoutMs == null ? 30_000 : readTimeoutMs,
                    retryPolicy == null ? null : retryPolicy.toDomain(), rateLimitPerMinute,
                    circuitFailureRatePct == null ? 50 : circuitFailureRatePct, eps);
        }
    }

    public record RegistryIntegrationResponse(UUID id, UUID registryId, String adapterType, String baseUrl, AuthType authType,
                                              Map<String, String> credentials, Set<String> configuredSecrets,
                                              int connectTimeoutMs, int readTimeoutMs, RetryPolicyDto retryPolicy,
                                              Integer rateLimitPerMinute, int circuitFailureRatePct,
                                              Map<Operation, EndpointDto> endpoints, boolean active,
                                              HealthStatus healthStatus, Instant lastHealthCheckAt,
                                              String webhookSecret) {
        static RegistryIntegrationResponse of(RegistryIntegration i, String newWebhookSecret) {
            Map<Operation, EndpointDto> eps = new TreeMap<>();
            i.endpoints().forEach((op, e) -> eps.put(op, new EndpointDto(e.httpMethod(), e.path())));
            return new RegistryIntegrationResponse(i.id(), i.registryId(), i.adapterType(), i.baseUrl(), i.authType(),
                    i.publicCredentials(), i.configuredSecretKeys(), i.connectTimeoutMs(), i.readTimeoutMs(),
                    RetryPolicyDto.of(i.retryPolicy()), i.rateLimitPerMinute(), i.circuitFailureRatePct(), eps, i.active(),
                    i.healthStatus(), i.lastHealthCheckAt(), newWebhookSecret);
        }
    }

    public record StatusRequest(@NotNull Boolean active) {
    }

    public record WebhookSecretResponse(String webhookSecret) {
    }

    public record HealthCheckResponse(HealthStatus status, Integer latencyMs, Integer httpStatus, String error, Instant checkedAt) {
        static HealthCheckResponse of(HealthCheckResult r) {
            return new HealthCheckResponse(r.status(), r.latencyMs(), r.httpStatus(), r.errorMessage(), r.checkedAt());
        }
    }

    public record CertificateResponse(UUID id, String alias, Certificate.Purpose purpose, String format, String subject,
                                      String issuer, String serialNumber, Instant notBefore, Instant notAfter,
                                      String sha256Fingerprint, boolean expiringSoon) {
        static CertificateResponse of(Certificate c) {
            return new CertificateResponse(c.id(), c.alias(), c.purpose(), c.keystoreType(), c.subjectDn(), c.issuerDn(),
                    c.serialNumber(), c.notBefore(), c.notAfter(), c.sha256Fingerprint(),
                    c.expiresWithin(Duration.ofDays(30), Instant.now()));
        }
    }

    public record ConfigureBankIntegration(@Size(max = 500) String callbackUrl, @NotNull BankIntegration.CallbackAuth callbackAuth,
                                           @Size(max = 500) String callbackSecret,
                                           @Size(max = 50) List<@NotBlank String> allowedIps) {
    }

    public record BankIntegrationResponse(UUID id, UUID bankId, String callbackUrl, BankIntegration.CallbackAuth callbackAuth,
                                          boolean callbackSecretConfigured, List<String> allowedIps, boolean active) {
        static BankIntegrationResponse of(BankIntegration b) {
            return new BankIntegrationResponse(b.id(), b.bankId(), b.callbackUrl(), b.callbackAuth(), b.hasCallbackSecret(),
                    b.allowedIps(), b.active());
        }
    }
}
