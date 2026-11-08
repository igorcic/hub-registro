package br.com.hubregistro.integration.domain;

import java.time.Instant;

/**
 * Resultado de uma verificação de disponibilidade.
 *
 * @param httpStatus nulo quando não houve resposta (timeout, conexão recusada, erro de TLS)
 */
public record HealthCheckResult(HealthStatus status, Integer latencyMs, Integer httpStatus, String errorMessage,
                                Instant checkedAt) {

    /** Acima disto, uma resposta 2xx conta como DEGRADED. */
    public static final int DEGRADED_LATENCY_MS = 2_000;

    public static HealthCheckResult fromResponse(int httpStatus, int latencyMs, Instant checkedAt) {
        HealthStatus status;
        if (httpStatus >= 200 && httpStatus < 300) {
            status = latencyMs > DEGRADED_LATENCY_MS ? HealthStatus.DEGRADED : HealthStatus.UP;
        } else {
            status = HealthStatus.DOWN;
        }
        String error = status == HealthStatus.DOWN ? "Resposta HTTP " + httpStatus : null;
        return new HealthCheckResult(status, latencyMs, httpStatus, error, checkedAt);
    }

    public static HealthCheckResult failure(String error, int latencyMs, Instant checkedAt) {
        String message = error == null ? "Falha de conexão" : error.length() > 1000 ? error.substring(0, 1000) : error;
        return new HealthCheckResult(HealthStatus.DOWN, latencyMs, null, message, checkedAt);
    }
}
