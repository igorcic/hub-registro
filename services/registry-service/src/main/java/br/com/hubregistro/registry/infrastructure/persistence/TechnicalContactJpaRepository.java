package br.com.hubregistro.registry.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface TechnicalContactJpaRepository extends JpaRepository<TechnicalContactEntity, UUID> {

    @Query("select c from TechnicalContactEntity c where c.registryId = :registryId and c.deletedAt is null order by c.primary desc, c.name")
    List<TechnicalContactEntity> findActive(@Param("registryId") UUID registryId);
}
