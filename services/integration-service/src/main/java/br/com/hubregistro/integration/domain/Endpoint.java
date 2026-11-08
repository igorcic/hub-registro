package br.com.hubregistro.integration.domain;

import br.com.hubregistro.commons.error.DomainException;

import java.util.Locale;
import java.util.Set;

/** Caminho relativo à URL base para uma operação. */
public record Endpoint(String httpMethod, String path) {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    public Endpoint {
        httpMethod = httpMethod == null ? null : httpMethod.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(httpMethod)) {
            throw new DomainException("invalid_endpoint", "Método HTTP inválido: " + httpMethod);
        }
        if (path == null || !path.startsWith("/") || path.length() > 500 || path.contains("..") || path.contains("://")) {
            throw new DomainException("invalid_endpoint", "Caminho deve começar com / e ser relativo à URL base: " + path);
        }
    }
}
