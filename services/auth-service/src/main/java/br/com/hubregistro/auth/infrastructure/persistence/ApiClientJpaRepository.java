package br.com.hubregistro.auth.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ApiClientJpaRepository extends JpaRepository<ApiClientEntity, UUID> {

    Optional<ApiClientEntity> findByClientId(String clientId);

    List<ApiClientEntity> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId);
}
