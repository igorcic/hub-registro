package br.com.hubregistro.auth.application;

import br.com.hubregistro.auth.application.port.AuthEventPublisher;
import br.com.hubregistro.auth.application.port.PasswordHasher;
import br.com.hubregistro.auth.domain.institution.Institution;
import br.com.hubregistro.auth.domain.institution.InstitutionRepository;
import br.com.hubregistro.auth.domain.role.Permissions;
import br.com.hubregistro.auth.domain.role.Role;
import br.com.hubregistro.auth.domain.role.RoleRepository;
import br.com.hubregistro.auth.domain.session.RefreshToken;
import br.com.hubregistro.auth.domain.session.RefreshTokenRepository;
import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserRepository;
import br.com.hubregistro.auth.domain.user.UserRepository.UserPage;
import br.com.hubregistro.auth.domain.user.UserRepository.UserSearch;
import br.com.hubregistro.auth.domain.user.UserStatus;
import br.com.hubregistro.commons.crypto.SecureTokens;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.error.ForbiddenOperationException;
import br.com.hubregistro.commons.error.NotFoundException;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gestão de usuários com isolamento por instituição:
 * <ul>
 *   <li>administrador de banco ou registradora vê e gerencia só os usuários da própria instituição;</li>
 *   <li>usuário do Hub vê todos ({@code user:read}); só quem tem {@code user:manage-any} os altera;</li>
 *   <li>ninguém altera o próprio status nem os próprios papéis (evita escalada e auto-bloqueio).</li>
 * </ul>
 * Usuário de outra instituição responde 404, como se não existisse.
 */
@Service
public class UserManagementService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final InstitutionRepository institutions;
    private final RefreshTokenRepository refreshTokens;
    private final PermissionResolver permissionResolver;
    private final PasswordHasher hasher;
    private final PasswordService passwordService;
    private final AuthEventPublisher events;
    private final Clock clock;

    public UserManagementService(UserRepository users, RoleRepository roles, InstitutionRepository institutions,
                                 RefreshTokenRepository refreshTokens, PermissionResolver permissionResolver,
                                 PasswordHasher hasher, PasswordService passwordService, AuthEventPublisher events,
                                 Clock clock) {
        this.users = users;
        this.roles = roles;
        this.institutions = institutions;
        this.refreshTokens = refreshTokens;
        this.permissionResolver = permissionResolver;
        this.hasher = hasher;
        this.passwordService = passwordService;
        this.events = events;
        this.clock = clock;
    }

    public record CreateUser(UUID institutionId, String fullName, String email, Set<String> roles) {
    }

    @Transactional(readOnly = true)
    public UserPage search(HubPrincipal actor, UUID institutionId, UserStatus status, String text, int page, int size) {
        UUID scope = actor.isHub() ? institutionId : actor.institutionId();
        String normalizedText = text == null || text.isBlank() ? null : text.trim();
        return users.search(new UserSearch(scope, status, normalizedText), Math.max(page, 0), Math.clamp(size, 1, 100));
    }

    @Transactional(readOnly = true)
    public User get(HubPrincipal actor, UUID userId) {
        return visibleUser(actor, userId);
    }

    @Transactional
    public User create(HubPrincipal actor, CreateUser command, ClientInfo client) {
        UUID institutionId = actor.isHub() && command.institutionId() != null ? command.institutionId() : actor.institutionId();
        if (!actor.isHub() && command.institutionId() != null && !command.institutionId().equals(actor.institutionId())) {
            throw new ForbiddenOperationException("Só é possível criar usuários na própria instituição");
        }
        requireCanManage(actor, institutionId);

        Institution institution = institutions.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("Instituição"));
        if (!institution.isActive()) {
            throw new DomainException("institution_inactive", "Instituição inativa não pode receber usuários");
        }
        Email email = new Email(command.email());
        if (users.existsByEmail(email)) {
            throw new DomainException("email_in_use", "Já existe um usuário com este e-mail");
        }
        Set<Role> granted = permissionResolver.requireRoles(command.roles() == null ? Set.of() : command.roles());

        String unusableHash = hasher.hash(SecureTokens.generate(32));
        User user = User.create(institutionId, institution.type(), command.fullName(), email, granted, unusableHash);
        users.save(user);
        events.userCreated(actor, user);
        passwordService.sendActivation(user, client.ip());
        return user;
    }

    @Transactional
    public User rename(HubPrincipal actor, UUID userId, String fullName) {
        User user = visibleUser(actor, userId);
        requireCanManage(actor, user.institutionId());
        user.rename(fullName);
        users.save(user);
        events.userUpdated(actor, user);
        return user;
    }

    @Transactional
    public User changeStatus(HubPrincipal actor, UUID userId, UserStatus target, String reason) {
        User user = visibleUser(actor, userId);
        requireCanManage(actor, user.institutionId());
        requireNotSelf(actor, user, "o próprio status");
        if (reason == null || reason.isBlank()) {
            throw new DomainException("reason_required", "Informe o motivo da alteração de status");
        }
        UserStatus previous = user.status();
        user.changeStatus(target);
        users.save(user);
        if (target == UserStatus.DISABLED) {
            refreshTokens.revokeAllForUser(user.id(), clock.instant(), RefreshToken.REASON_USER_DISABLED);
        }
        events.userStatusChanged(actor, user, previous.name(), reason.trim());
        return user;
    }

    @Transactional
    public User replaceRoles(HubPrincipal actor, UUID userId, Set<String> roleCodes) {
        User user = visibleUser(actor, userId);
        requireCanManage(actor, user.institutionId());
        requireNotSelf(actor, user, "os próprios papéis");
        Institution institution = institutions.findById(user.institutionId())
                .orElseThrow(() -> new NotFoundException("Instituição"));

        Set<String> previous = user.roleCodes();
        user.replaceRoles(permissionResolver.requireRoles(roleCodes == null ? Set.of() : roleCodes), institution.type());
        users.save(user);
        // Papéis valem a partir do próximo access token; sessões abertas são encerradas para não manter privilégios antigos.
        refreshTokens.revokeAllForUser(user.id(), clock.instant(), RefreshToken.REASON_ACCESS_REVOKED);
        events.userRolesChanged(actor, user, previous);
        return user;
    }

    @Transactional
    public void resendActivation(HubPrincipal actor, UUID userId, ClientInfo client) {
        User user = visibleUser(actor, userId);
        requireCanManage(actor, user.institutionId());
        if (user.status() != UserStatus.PENDING_ACTIVATION) {
            throw new DomainException("already_active", "Usuário já ativou a conta");
        }
        passwordService.sendActivation(user, client.ip());
    }

    /** Papéis que o chamador pode consultar: os do tipo da própria instituição, ou todos para o Hub. */
    @Transactional(readOnly = true)
    public List<Role> visibleRoles(HubPrincipal actor) {
        return roles.findAll().stream()
                .filter(r -> actor.isHub() || r.institutionType() == actor.institutionType())
                .toList();
    }

    private User visibleUser(HubPrincipal actor, UUID userId) {
        return users.findById(userId)
                .filter(u -> actor.canAccessInstitution(u.institutionId()))
                .orElseThrow(() -> new NotFoundException("Usuário"));
    }

    private static void requireCanManage(HubPrincipal actor, UUID institutionId) {
        boolean allowed = actor.isHub()
                ? actor.hasPermission(Permissions.USER_MANAGE_ANY)
                : actor.hasPermission(Permissions.USER_MANAGE) && actor.institutionId().equals(institutionId);
        if (!allowed) {
            throw new ForbiddenOperationException("Sem permissão para gerenciar usuários desta instituição");
        }
    }

    private static void requireNotSelf(HubPrincipal actor, User user, String what) {
        if (!actor.apiClient() && user.id().toString().equals(actor.subject())) {
            throw new ForbiddenOperationException("Não é permitido alterar " + what);
        }
    }
}
