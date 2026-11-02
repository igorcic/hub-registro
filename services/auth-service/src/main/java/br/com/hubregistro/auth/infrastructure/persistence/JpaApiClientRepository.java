package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.client.ApiClientRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaApiClientRepository implements ApiClientRepository {

    private final ApiClientJpaRepository jpa;

    JpaApiClientRepository(ApiClientJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<ApiClient> findById(UUID id) {
        return jpa.findById(id).map(ApiClientEntity::toDomain);
    }

    @Override
    public Optional<ApiClient> findByClientId(String clientId) {
        return jpa.findByClientId(clientId).map(ApiClientEntity::toDomain);
    }

    @Override
    public List<ApiClient> findByInstitution(UUID institutionId) {
        return jpa.findByInstitutionIdOrderByCreatedAtDesc(institutionId).stream().map(ApiClientEntity::toDomain).toList();
    }

    @Override
    public void save(ApiClient client) {
        jpa.findById(client.id()).ifPresentOrElse(
                existing -> existing.copyFrom(client),
                () -> jpa.save(ApiClientEntity.newFrom(client)));
    }
}
