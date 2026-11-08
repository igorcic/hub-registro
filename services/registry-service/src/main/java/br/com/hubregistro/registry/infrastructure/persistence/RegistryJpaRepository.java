package br.com.hubregistro.registry.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface RegistryJpaRepository extends JpaRepository<RegistryEntity, UUID>, JpaSpecificationExecutor<RegistryEntity> {

    @Query("select count(r) > 0 from RegistryEntity r where r.cnpj = :cnpj and r.deletedAt is null")
    boolean existsActiveByCnpj(@Param("cnpj") String cnpj);

    @Query("select count(r) > 0 from RegistryEntity r where r.code = :code and r.deletedAt is null")
    boolean existsActiveByCode(@Param("code") String code);
}
