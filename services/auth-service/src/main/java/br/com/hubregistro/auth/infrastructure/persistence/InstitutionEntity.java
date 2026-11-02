package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.institution.Institution;
import br.com.hubregistro.auth.domain.institution.InstitutionStatus;
import br.com.hubregistro.commons.tenant.InstitutionType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** Réplica mantida por eventos (Etapa 5); por ora, só leitura. */
@Entity
@Immutable
@Table(name = "institutions")
public class InstitutionEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private InstitutionType type;

    private String name;

    @Enumerated(EnumType.STRING)
    private InstitutionStatus status;

    protected InstitutionEntity() {
    }

    Institution toDomain() {
        return new Institution(id, type, name, status);
    }
}
