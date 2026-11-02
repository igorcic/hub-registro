package br.com.hubregistro.auth.application.port;

import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.security.HubPrincipal;

import java.util.Set;
import java.util.UUID;

/** Eventos de segurança, gravados no Outbox na mesma transação e consumidos pela auditoria. */
public interface AuthEventPublisher {

    void loginSucceeded(User user, String clientIp);

    /** {@code user} nulo quando o e-mail não existe. */
    void loginFailed(User user, Email attemptedEmail, String clientIp, String reason);

    void userLocked(User user, String clientIp);

    void refreshReuseDetected(UUID userId, UUID familyId, String clientIp);

    void passwordLinkRequested(User user, String purpose, String clientIp);

    void passwordChanged(User user, String how);

    void userCreated(HubPrincipal actor, User user);

    void userUpdated(HubPrincipal actor, User user);

    void userStatusChanged(HubPrincipal actor, User user, String previousStatus, String reason);

    void userRolesChanged(HubPrincipal actor, User user, Set<String> previousRoles);

    void apiClientCreated(HubPrincipal actor, ApiClient client);

    void apiClientRevoked(HubPrincipal actor, ApiClient client);
}
