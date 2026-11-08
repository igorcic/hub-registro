package br.com.hubregistro.bank.domain;

public enum BankStatus {
    /** Opera normalmente. */
    ACTIVE,
    /** Desligado da plataforma (encerramento de contrato comercial, por exemplo). */
    INACTIVE,
    /** Bloqueado temporariamente (pendência, incidente de segurança). */
    SUSPENDED
}
