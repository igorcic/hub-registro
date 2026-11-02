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

interface PasswordTokenJpaRepository extends JpaRepository<PasswordTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordTokenEntity t where t.tokenHash = :hash")
    Optional<PasswordTokenEntity> findByHashForUpdate(@Param("hash") byte[] hash);

    @Modifying(flushAutomatically = true)
    @Query("update PasswordTokenEntity t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidatePendingForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
