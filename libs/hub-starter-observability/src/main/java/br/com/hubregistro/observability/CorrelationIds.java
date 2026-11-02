package br.com.hubregistro.observability;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Regras do correlation id, compartilhadas pelo filtro servlet dos serviços e pelo filtro reativo do gateway.
 *
 * <p>Um id recebido de fora só é aceito se tiver até 64 caracteres seguros; qualquer outro valor é
 * descartado e substituído, para que um cabeçalho malicioso não chegue aos logs.
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private CorrelationIds() {
    }

    public static boolean isValid(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches();
    }

    /** Devolve o id recebido quando válido; caso contrário, gera um novo. */
    public static String resolve(String candidate) {
        return isValid(candidate) ? candidate : generate();
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
