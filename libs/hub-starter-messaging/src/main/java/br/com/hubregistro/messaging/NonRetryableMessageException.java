package br.com.hubregistro.messaging;

/** Mensagem que nunca vai dar certo (malformada, regra violada): vai direto para a DLQ, sem retentativas. */
public class NonRetryableMessageException extends RuntimeException {

    public NonRetryableMessageException(String message) {
        super(message);
    }

    public NonRetryableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
