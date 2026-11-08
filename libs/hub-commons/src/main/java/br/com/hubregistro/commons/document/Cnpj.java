package br.com.hubregistro.commons.document;

import br.com.hubregistro.commons.error.DomainException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CNPJ validado pelos dígitos verificadores. Aceita o formato numérico tradicional e o alfanumérico
 * (12 posições com letras e números + 2 dígitos), em uso desde julho de 2026.
 *
 * <p>Cálculo (módulo 11): cada caractere vale o seu código ASCII menos 48 ("0" = 0 ... "9" = 9, "A" = 17 ...
 * "Z" = 42); pesos 5,4,3,2,9,8,7,6,5,4,3,2 para o primeiro dígito e 6,5,4,3,2,9,8,7,6,5,4,3,2 para o segundo;
 * resto menor que 2 dá dígito 0, senão 11 menos o resto.
 */
public record Cnpj(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[0-9A-Z]{12}[0-9]{2}$");
    private static final int[] WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cnpj {
        value = normalize(value);
        if (!isValid(value)) {
            throw new DomainException("invalid_cnpj", "CNPJ inválido");
        }
    }

    /** Remove pontuação e espaços e passa para maiúsculas: "12.ABC.345/01DE-35" vira "12ABC34501DE35". */
    public static String normalize(String raw) {
        return raw == null ? null : raw.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        if (normalized == null || !FORMAT.matcher(normalized).matches()) {
            return false;
        }
        if (normalized.chars().allMatch(c -> c == normalized.charAt(0))) {
            return false;
        }
        int first = checkDigit(normalized, WEIGHTS_1);
        int second = checkDigit(normalized, WEIGHTS_2);
        return normalized.charAt(12) - '0' == first && normalized.charAt(13) - '0' == second;
    }

    private static int checkDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cnpj.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    /** 12.ABC.345/01DE-35 */
    public String formatted() {
        return value.substring(0, 2) + "." + value.substring(2, 5) + "." + value.substring(5, 8) + "/"
                + value.substring(8, 12) + "-" + value.substring(12);
    }

    @Override
    public String toString() {
        return value;
    }
}
