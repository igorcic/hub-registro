package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.reset.PasswordToken;
import br.com.hubregistro.auth.domain.reset.PasswordTokenRepository;
import br.com.hubregistro.auth.domain.session.RefreshToken;
import br.com.hubregistro.auth.domain.session.RefreshTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;

    JpaRefreshTokenRepository(RefreshTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<RefreshToken> findByHashForUpdate(byte[] tokenHash) {
        return jpa.findByHashForUpdate(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    @Override
    public void save(RefreshToken token) {
        jpa.findById(token.id()).ifPresentOrElse(
                existing -> existing.copyFrom(token),
                () -> jpa.save(RefreshTokenEntity.newFrom(token)));
    }

    @Override
    public int revokeFamily(UUID familyId, Instant now, String reason) {
        return jpa.revokeFamily(familyId, now, reason);
    }

    @Override
    public int revokeAllForUser(UUID userId, Instant now, String reason) {
        return jpa.revokeAllForUser(userId, now, reason);
    }
}

@Repository
class JpaPasswordTokenRepository implements PasswordTokenRepository {

    private final PasswordTokenJpaRepository jpa;

    JpaPasswordTokenRepository(PasswordTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<PasswordToken> findByHashForUpdate(byte[] tokenHash) {
        return jpa.findByHashForUpdate(tokenHash).map(PasswordTokenEntity::toDomain);
    }

    @Override
    public void save(PasswordToken token) {
        jpa.findById(token.id()).ifPresentOrElse(
                existing -> existing.copyFrom(token),
                () -> jpa.save(PasswordTokenEntity.newFrom(token)));
    }

    @Override
    public int invalidatePendingForUser(UUID userId, Instant now) {
        return jpa.invalidatePendingForUser(userId, now);
    }
}
