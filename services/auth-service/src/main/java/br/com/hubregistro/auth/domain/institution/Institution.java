package br.com.hubregistro.auth.domain.institution;

import br.com.hubregistro.commons.tenant.InstitutionType;

import java.util.UUID;

/** Réplica local da instituição (a fonte é o bank-service ou o registry-service). */
public record Institution(UUID id, InstitutionType type, String name, InstitutionStatus status) {

    public boolean isActive() {
        return status == InstitutionStatus.ACTIVE;
    }
}
