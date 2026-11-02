package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private UUID institutionId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_code")
    private Set<String> roles = new HashSet<>();

    protected UserEntity() {
    }

    static UserEntity newFrom(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.id();
        entity.institutionId = user.institutionId();
        entity.email = user.email().value();
        entity.copyFrom(user);
        return entity;
    }

    void copyFrom(User user) {
        fullName = user.fullName();
        passwordHash = user.passwordHash();
        status = user.status();
        failedLoginAttempts = user.failedLoginAttempts();
        lockedUntil = user.lockedUntil();
        passwordChangedAt = user.passwordChangedAt();
        lastLoginAt = user.lastLoginAt();
        roles.clear();
        roles.addAll(user.roleCodes());
    }

    User toDomain() {
        return User.restore(id, institutionId, new Email(email), fullName, passwordHash, status, failedLoginAttempts,
                lockedUntil, passwordChangedAt, lastLoginAt, Set.copyOf(roles));
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }
}
