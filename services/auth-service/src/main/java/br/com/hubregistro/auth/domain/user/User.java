package br.com.hubregistro.auth.domain.user;

import br.com.hubregistro.auth.domain.role.Role;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.id.UuidV7;
import br.com.hubregistro.commons.tenant.InstitutionType;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Usuário de uma instituição. A instituição nunca muda depois de criado. */
public class User {

    private final UUID id;
    private final UUID institutionId;
    private final Email email;
    private String fullName;
    private String passwordHash;
    private UserStatus status;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant passwordChangedAt;
    private Instant lastLoginAt;
    private Set<String> roleCodes;

    private User(UUID id, UUID institutionId, Email email, String fullName, String passwordHash, UserStatus status,
                 int failedLoginAttempts, Instant lockedUntil, Instant passwordChangedAt, Instant lastLoginAt,
                 Set<String> roleCodes) {
        this.id = Objects.requireNonNull(id);
        this.institutionId = Objects.requireNonNull(institutionId);
        this.email = Objects.requireNonNull(email);
        this.fullName = requireName(fullName);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.status = Objects.requireNonNull(status);
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.passwordChangedAt = passwordChangedAt;
        this.lastLoginAt = lastLoginAt;
        this.roleCodes = Set.copyOf(roleCodes);
    }

    /**
     * Novo usuário criado por administrador: aguarda ativação. {@code unusablePasswordHash} é o hash de um
     * valor aleatório descartado, para que a conta não aceite login até a senha ser definida pelo link.
     */
    public static User create(UUID institutionId, InstitutionType institutionType, String fullName, Email email,
                              Set<Role> roles, String unusablePasswordHash) {
        User user = new User(UuidV7.generate(), institutionId, email, fullName, unusablePasswordHash,
                UserStatus.PENDING_ACTIVATION, 0, null, null, null, Set.of());
        user.replaceRoles(roles, institutionType);
        return user;
    }

    /** Reconstrói a partir do armazenamento. */
    public static User restore(UUID id, UUID institutionId, Email email, String fullName, String passwordHash,
                               UserStatus status, int failedLoginAttempts, Instant lockedUntil, Instant passwordChangedAt,
                               Instant lastLoginAt, Set<String> roleCodes) {
        return new User(id, institutionId, email, fullName, passwordHash, status, failedLoginAttempts, lockedUntil,
                passwordChangedAt, lastLoginAt, roleCodes);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public boolean canAuthenticate(Instant now) {
        return status == UserStatus.ACTIVE && !isLocked(now);
    }

    public void loginSucceeded(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Registra senha errada. Devolve {@code true} se esta tentativa bloqueou a conta. */
    public boolean loginFailed(Instant now, LoginPolicy policy) {
        if (isLocked(now)) {
            return false;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= policy.maxFailedAttempts()) {
            lockedUntil = now.plus(policy.lockDuration());
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    /** Define nova senha (já validada e com hash). Na primeira vez, ativa a conta. Também desbloqueia. */
    public void setPassword(String newPasswordHash, Instant now) {
        if (status == UserStatus.DISABLED) {
            throw new DomainException("user_disabled", "Usuário desativado não pode definir senha");
        }
        passwordHash = Objects.requireNonNull(newPasswordHash);
        passwordChangedAt = now;
        failedLoginAttempts = 0;
        lockedUntil = null;
        if (status == UserStatus.PENDING_ACTIVATION) {
            status = UserStatus.ACTIVE;
        }
    }

    public void rename(String newFullName) {
        fullName = requireName(newFullName);
    }

    /** Administrador ativa ou desativa. A ativação inicial só acontece definindo a senha pelo link. */
    public void changeStatus(UserStatus target) {
        if (target == UserStatus.PENDING_ACTIVATION) {
            throw new DomainException("invalid_status", "Status inválido para alteração manual");
        }
        if (target == UserStatus.ACTIVE && status == UserStatus.PENDING_ACTIVATION) {
            throw new DomainException("activation_required", "O usuário precisa ativar a conta pelo link enviado por e-mail");
        }
        status = target;
    }

    /** Substitui os papéis; todos precisam ser do mesmo tipo de instituição do usuário. */
    public void replaceRoles(Set<Role> roles, InstitutionType institutionType) {
        if (roles == null || roles.isEmpty()) {
            throw new DomainException("roles_required", "Informe ao menos um papel");
        }
        for (Role role : roles) {
            if (role.institutionType() != institutionType) {
                throw new DomainException("role_not_allowed",
                        "O papel " + role.code() + " não pode ser concedido a usuário de " + institutionType);
            }
        }
        roleCodes = roles.stream().map(Role::code).collect(Collectors.toUnmodifiableSet());
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new DomainException("invalid_name", "Nome obrigatório, com até 200 caracteres");
        }
        return name.trim();
    }

    public UUID id() { return id; }
    public UUID institutionId() { return institutionId; }
    public Email email() { return email; }
    public String fullName() { return fullName; }
    public String passwordHash() { return passwordHash; }
    public UserStatus status() { return status; }
    public int failedLoginAttempts() { return failedLoginAttempts; }
    public Instant lockedUntil() { return lockedUntil; }
    public Instant passwordChangedAt() { return passwordChangedAt; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public Set<String> roleCodes() { return roleCodes; }
}
