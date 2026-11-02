package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.institution.Institution;
import br.com.hubregistro.auth.domain.institution.InstitutionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaInstitutionRepository implements InstitutionRepository {

    private final InstitutionJpaRepository jpa;

    JpaInstitutionRepository(InstitutionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Institution> findById(UUID id) {
        return jpa.findById(id).map(InstitutionEntity::toDomain);
    }
}
