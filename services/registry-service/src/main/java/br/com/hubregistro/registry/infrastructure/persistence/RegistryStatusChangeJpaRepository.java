package br.com.hubregistro.registry.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface RegistryStatusChangeJpaRepository extends JpaRepository<RegistryStatusChangeEntity, UUID> {

    List<RegistryStatusChangeEntity> findByRegistryIdOrderByChangedAtDesc(UUID registryId);
}
