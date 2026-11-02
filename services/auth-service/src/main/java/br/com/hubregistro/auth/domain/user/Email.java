package br.com.hubregistro.auth.domain.user;

import br.com.hubregistro.commons.error.DomainException;

import java.util.Locale;
import java.util.regex.Pattern;

/** E-mail normalizado (sem espaços, minúsculo): é a chave de login. */
public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null) {
            throw new DomainException("invalid_email", "E-mail obrigatório");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !FORMAT.matcher(value).matches()) {
            throw new DomainException("invalid_email", "E-mail inválido");
        }
    }

    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }

    @Override
    public String toString() {
        return value;
    }
}
