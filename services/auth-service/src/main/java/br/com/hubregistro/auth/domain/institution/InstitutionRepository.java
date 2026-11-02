package br.com.hubregistro.auth.domain.institution;

import java.util.Optional;
import java.util.UUID;

public interface InstitutionRepository {

    UUID HUB_INSTITUTION_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    Optional<Institution> findById(UUID id);
}
