package br.com.hubregistro.integration.domain;

import br.com.hubregistro.commons.error.DomainException;

import java.time.Duration;

/** Retentativas do envio a esta registradora (aplicadas pela mensageria, Etapa 7). */
public record RetryPolicy(int maxAttempts, Duration initialInterval, double multiplier, Duration maxInterval) {

    public static final RetryPolicy DEFAULT = new RetryPolicy(5, Duration.ofSeconds(10), 2.0, Duration.ofMinutes(30));

    public RetryPolicy {
        if (maxAttempts < 0 || maxAttempts > 20) {
            throw new DomainException("invalid_retry_policy", "Tentativas entre 0 e 20");
        }
        if (initialInterval == null || initialInterval.isNegative() || initialInterval.isZero()
                || maxInterval == null || maxInterval.compareTo(initialInterval) < 0) {
            throw new DomainException("invalid_retry_policy", "Intervalo inicial positivo e menor ou igual ao máximo");
        }
        if (multiplier < 1 || multiplier > 10) {
            throw new DomainException("invalid_retry_policy", "Multiplicador entre 1 e 10");
        }
    }

    public Duration delayBefore(int attempt) {
        double millis = initialInterval.toMillis() * Math.pow(multiplier, Math.max(0, attempt - 1));
        return Duration.ofMillis((long) Math.min(millis, maxInterval.toMillis()));
    }
}
