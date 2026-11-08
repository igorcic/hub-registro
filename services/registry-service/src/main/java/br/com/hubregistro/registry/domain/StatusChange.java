package br.com.hubregistro.registry.domain;

import java.time.Instant;
import java.util.UUID;

public record StatusChange(UUID registryId, RegistryStatus from, RegistryStatus to, String reason, UUID changedBy,
                           Instant changedAt) {
}
