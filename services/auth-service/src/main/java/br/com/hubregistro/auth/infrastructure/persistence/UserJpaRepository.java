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

interface UserJpaRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    @Query("select u from UserEntity u where u.email = :email and u.deletedAt is null")
    Optional<UserEntity> findActiveByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from UserEntity u where u.email = :email and u.deletedAt is null")
    boolean existsActiveByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from UserEntity u join u.roles r where r = :role and u.deletedAt is null")
    boolean existsWithRole(@Param("role") String role);
}
