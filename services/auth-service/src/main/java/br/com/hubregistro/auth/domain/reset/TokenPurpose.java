package br.com.hubregistro.auth.domain.reset;

import java.time.Duration;

/** Para que serve um link enviado por e-mail, e por quanto tempo vale. */
public enum TokenPurpose {
    ACTIVATION(Duration.ofHours(72)),
    RESET(Duration.ofMinutes(30));

    private final Duration validity;

    TokenPurpose(Duration validity) {
        this.validity = validity;
    }

    public Duration validity() {
        return validity;
    }
}
