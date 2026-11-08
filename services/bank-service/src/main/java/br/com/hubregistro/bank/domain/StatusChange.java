package br.com.hubregistro.bank.domain;

import java.time.Instant;
import java.util.UUID;

/** Registro imutável de uma mudança de status (gravado em bank_status_changes). */
public record StatusChange(UUID bankId, BankStatus from, BankStatus to, String reason, UUID changedBy, Instant changedAt) {
}
