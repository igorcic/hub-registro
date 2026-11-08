package br.com.hubregistro.registry.domain;

import br.com.hubregistro.commons.document.Cnpj;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistryRepository {

    Optional<Registry> findById(UUID id);

    boolean existsByCnpj(Cnpj cnpj);

    boolean existsByCode(String code);

    RegistryPage search(RegistrySearch criteria, int page, int size);

    long save(Registry registry);

    void appendStatusChange(StatusChange change);

    List<StatusChange> statusHistory(UUID registryId);

    record RegistrySearch(RegistryStatus status, String contractType, String uf, String text) {
    }

    record RegistryPage(List<Registry> items, long total, int page, int size) {
    }
}
