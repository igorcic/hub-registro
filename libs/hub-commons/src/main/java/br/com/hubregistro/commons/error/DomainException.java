package br.com.hubregistro.commons.error;

/**
 * Violação de regra de negócio. O {@code code} é estável e vai no campo {@code code} do Problem Details,
 * para que frontend e sistemas dos bancos tratem o erro sem depender do texto.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
