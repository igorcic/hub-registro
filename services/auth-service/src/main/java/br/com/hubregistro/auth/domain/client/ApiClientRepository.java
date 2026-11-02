package br.com.hubregistro.auth.domain.client;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiClientRepository {

    Optional<ApiClient> findById(UUID id);

    Optional<ApiClient> findByClientId(String clientId);

    List<ApiClient> findByInstitution(UUID institutionId);

    void save(ApiClient client);
}
