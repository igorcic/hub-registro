package br.com.hubregistro.auth.domain.user;

import java.time.Duration;

/** Bloqueio temporário após tentativas seguidas de senha errada (proteção contra força bruta por conta). */
public record LoginPolicy(int maxFailedAttempts, Duration lockDuration) {

    public static final LoginPolicy DEFAULT = new LoginPolicy(5, Duration.ofMinutes(15));

    public LoginPolicy {
        if (maxFailedAttempts < 1 || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("Política de login inválida");
        }
    }
}
