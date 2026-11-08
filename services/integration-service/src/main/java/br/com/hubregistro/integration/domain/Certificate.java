package br.com.hubregistro.integration.domain;

import br.com.hubregistro.commons.error.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Metadados de um certificado digital cadastrado (o conteúdo fica cifrado no armazenamento). */
public record Certificate(UUID id, UUID integrationId, String alias, Purpose purpose, String keystoreType,
                          String subjectDn, String issuerDn, String serialNumber, Instant notBefore, Instant notAfter,
                          String sha256Fingerprint, boolean active, Instant uploadedAt) {

    public enum Purpose {
        /** Certificado de cliente para mTLS (precisa da chave privada). */
        CLIENT_AUTH,
        /** Certificado (ou cadeia) do servidor da registradora em que o Hub deve confiar. */
        SERVER_TRUST,
        /** Verificação da assinatura de webhooks. */
        WEBHOOK_SIGNATURE
    }

    public Certificate {
        if (alias == null || !alias.matches("^[A-Za-z0-9._-]{1,100}$")) {
            throw new DomainException("invalid_alias", "Alias: até 100 caracteres entre letras, números, . _ e -");
        }
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(notAfter);
    }

    public boolean expiresWithin(Duration window, Instant now) {
        return !isExpired(now) && now.plus(window).isAfter(notAfter);
    }
}
