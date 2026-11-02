package br.com.hubregistro.auth.domain.user;

import br.com.hubregistro.auth.domain.AuthErrors.WeakPasswordException;

import java.util.Locale;
import java.util.Set;

/**
 * Política de senha alinhada ao NIST SP 800-63B: comprimento mínimo alto em vez de regras de composição,
 * recusa de senhas comuns e de senhas derivadas do próprio e-mail.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON = Set.of(
            "123456789012", "1234567890123", "senha1234567", "senhasenha12", "password1234", "qwertyuiopas",
            "abc123456789", "admin1234567", "trocar123456", "mudar1234567", "brasil123456", "000000000000",
            "111111111111", "aaaaaaaaaaaa", "iloveyou1234", "senha@123456", "Senha@123456", "Password@123");

    private PasswordPolicy() {
    }

    public static void validate(String password, Email email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new WeakPasswordException("A senha precisa ter pelo menos " + MIN_LENGTH + " caracteres");
        }
        if (password.length() > MAX_LENGTH) {
            throw new WeakPasswordException("A senha pode ter no máximo " + MAX_LENGTH + " caracteres");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(password) || COMMON.contains(lower)) {
            throw new WeakPasswordException("Senha muito comum; escolha outra");
        }
        if (password.chars().distinct().count() < 4) {
            throw new WeakPasswordException("Senha com caracteres repetidos demais");
        }
        if (email != null && email.localPart().length() >= 4 && lower.contains(email.localPart())) {
            throw new WeakPasswordException("A senha não pode conter o seu e-mail");
        }
    }
}
