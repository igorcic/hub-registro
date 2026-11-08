package br.com.hubregistro.integration.domain;

import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.id.UuidV7;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Como o Hub fala com uma registradora: adaptador, URL, autenticação, endpoints, limites e saúde.
 * As credenciais ficam só aqui (cifradas no armazenamento) e nunca saem pela API.
 */
public class RegistryIntegration {

    private static final Pattern ADAPTER = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    private final UUID id;
    private final UUID registryId;
    private String adapterType;
    private String baseUrl;
    private AuthType authType;
    private Map<String, String> credentials;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private RetryPolicy retryPolicy;
    private Integer rateLimitPerMinute;
    private int circuitFailureRatePct;
    private Map<Operation, Endpoint> endpoints;
    private boolean active;
    private HealthStatus healthStatus;
    private Instant lastHealthCheckAt;
    private String webhookSecret;

    private RegistryIntegration(UUID id, UUID registryId) {
        this.id = Objects.requireNonNull(id);
        this.registryId = Objects.requireNonNull(registryId);
    }

    public record Settings(String adapterType, String baseUrl, AuthType authType, Map<String, String> credentials,
                           int connectTimeoutMs, int readTimeoutMs, RetryPolicy retryPolicy, Integer rateLimitPerMinute,
                           int circuitFailureRatePct, Map<Operation, Endpoint> endpoints) {
    }

    public static RegistryIntegration create(UUID registryId, Settings settings, String webhookSecret) {
        RegistryIntegration integration = new RegistryIntegration(UuidV7.generate(), registryId);
        integration.active = false;
        integration.healthStatus = HealthStatus.UNKNOWN;
        integration.webhookSecret = Objects.requireNonNull(webhookSecret);
        integration.apply(settings, Map.of());
        return integration;
    }

    public static RegistryIntegration restore(UUID id, UUID registryId, Settings settings, boolean active,
                                              HealthStatus healthStatus, Instant lastHealthCheckAt, String webhookSecret) {
        RegistryIntegration integration = new RegistryIntegration(id, registryId);
        integration.apply(settings, Map.of());
        integration.active = active;
        integration.healthStatus = healthStatus;
        integration.lastHealthCheckAt = lastHealthCheckAt;
        integration.webhookSecret = webhookSecret;
        return integration;
    }

    /**
     * Reconfigura. Credenciais secretas omitidas mantêm o valor atual (o portal não as conhece para reenviar),
     * desde que o tipo de autenticação não mude.
     */
    public void reconfigure(Settings settings) {
        Map<String, String> keep = settings.authType() == authType ? credentials : Map.of();
        apply(settings, keep);
    }

    private void apply(Settings s, Map<String, String> previousCredentials) {
        if (s.adapterType() == null || !ADAPTER.matcher(s.adapterType()).matches()) {
            throw new DomainException("invalid_adapter", "Tipo de adaptador inválido: " + s.adapterType());
        }
        this.adapterType = s.adapterType();
        this.baseUrl = validateBaseUrl(s.baseUrl());
        this.authType = Objects.requireNonNull(s.authType(), "Tipo de autenticação obrigatório");
        this.credentials = mergeCredentials(s.authType(), s.credentials(), previousCredentials);
        if (s.connectTimeoutMs() < 100 || s.connectTimeoutMs() > 60_000) {
            throw new DomainException("invalid_timeout", "Timeout de conexão entre 100 ms e 60 s");
        }
        if (s.readTimeoutMs() < 100 || s.readTimeoutMs() > 300_000) {
            throw new DomainException("invalid_timeout", "Timeout de resposta entre 100 ms e 5 min");
        }
        this.connectTimeoutMs = s.connectTimeoutMs();
        this.readTimeoutMs = s.readTimeoutMs();
        this.retryPolicy = s.retryPolicy() == null ? RetryPolicy.DEFAULT : s.retryPolicy();
        if (s.rateLimitPerMinute() != null && s.rateLimitPerMinute() < 1) {
            throw new DomainException("invalid_rate_limit", "Limite de requisições deve ser positivo");
        }
        this.rateLimitPerMinute = s.rateLimitPerMinute();
        if (s.circuitFailureRatePct() < 1 || s.circuitFailureRatePct() > 100) {
            throw new DomainException("invalid_circuit_breaker", "Taxa de falha do circuit breaker entre 1 e 100%");
        }
        this.circuitFailureRatePct = s.circuitFailureRatePct();
        this.endpoints = s.endpoints() == null || s.endpoints().isEmpty() ? Map.of() : Map.copyOf(s.endpoints());
    }

    /** Ativa só com o envio de contratos configurado: sem ele a integração não tem o que fazer. */
    public void activate() {
        if (!endpoints.containsKey(Operation.SUBMIT_CONTRACT)) {
            throw new DomainException("endpoint_required", "Configure o endpoint SUBMIT_CONTRACT antes de ativar");
        }
        active = true;
    }

    public void deactivate() {
        active = false;
        healthStatus = HealthStatus.UNKNOWN;
    }

    /** Registra o resultado de uma verificação; devolve o status anterior se mudou, ou null. */
    public HealthStatus recordHealth(HealthStatus status, Instant checkedAt) {
        HealthStatus previous = healthStatus;
        healthStatus = status;
        lastHealthCheckAt = checkedAt;
        return previous == status ? null : previous;
    }

    public void rotateWebhookSecret(String newSecret) {
        webhookSecret = Objects.requireNonNull(newSecret);
    }

    /** Credenciais sem os valores secretos, para exibição. */
    public Map<String, String> publicCredentials() {
        Map<String, String> visible = new HashMap<>(credentials);
        authType.secretKeys().forEach(visible::remove);
        return Map.copyOf(visible);
    }

    public Set<String> configuredSecretKeys() {
        Set<String> configured = new HashSet<>(authType.secretKeys());
        configured.retainAll(credentials.keySet());
        return Set.copyOf(configured);
    }

    public URI resolve(Endpoint endpoint) {
        return URI.create(baseUrl.replaceAll("/+$", "") + endpoint.path());
    }

    static String validateBaseUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw new DomainException("invalid_base_url", "URL base inválida");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || !("https".equals(scheme) || "http".equals(scheme))) {
            throw new DomainException("invalid_base_url", "URL base precisa ser http(s) com host");
        }
        // HTTP sem TLS só para nomes internos (simulador, serviços do Compose); registradoras reais usam HTTPS.
        if ("http".equals(scheme) && host.contains(".") && !host.equals("127.0.0.1")) {
            throw new DomainException("insecure_base_url", "Registradoras externas exigem HTTPS");
        }
        if (uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new DomainException("invalid_base_url", "URL base não pode ter credenciais, query ou fragmento");
        }
        return uri.toString().replaceAll("/+$", "");
    }

    private static Map<String, String> mergeCredentials(AuthType type, Map<String, String> provided,
                                                        Map<String, String> previous) {
        Map<String, String> merged = new HashMap<>();
        if (provided != null) {
            provided.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    merged.put(k, v.trim());
                }
            });
        }
        for (String secret : type.secretKeys()) {
            if (!merged.containsKey(secret) && previous.containsKey(secret)) {
                merged.put(secret, previous.get(secret));
            }
        }
        merged.keySet().retainAll(type.requiredKeys());
        for (String key : type.requiredKeys()) {
            if (!merged.containsKey(key)) {
                throw new DomainException("missing_credentials", "Credencial obrigatória para " + type + ": " + key);
            }
        }
        return Map.copyOf(merged);
    }

    public UUID id() { return id; }
    public UUID registryId() { return registryId; }
    public String adapterType() { return adapterType; }
    public String baseUrl() { return baseUrl; }
    public AuthType authType() { return authType; }
    public Map<String, String> credentials() { return credentials; }
    public int connectTimeoutMs() { return connectTimeoutMs; }
    public int readTimeoutMs() { return readTimeoutMs; }
    public RetryPolicy retryPolicy() { return retryPolicy; }
    public Integer rateLimitPerMinute() { return rateLimitPerMinute; }
    public int circuitFailureRatePct() { return circuitFailureRatePct; }
    public Map<Operation, Endpoint> endpoints() { return endpoints; }
    public boolean active() { return active; }
    public HealthStatus healthStatus() { return healthStatus; }
    public Instant lastHealthCheckAt() { return lastHealthCheckAt; }
    public String webhookSecret() { return webhookSecret; }
}
