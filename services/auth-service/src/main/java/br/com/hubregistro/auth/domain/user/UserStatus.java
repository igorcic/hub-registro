package br.com.hubregistro.auth.domain.user;

public enum UserStatus {
    /** Criado por um administrador; ainda não definiu a senha pelo link de ativação. */
    PENDING_ACTIVATION,
    ACTIVE,
    /** Desativado por um administrador: não autentica e perde as sessões abertas. */
    DISABLED
}
