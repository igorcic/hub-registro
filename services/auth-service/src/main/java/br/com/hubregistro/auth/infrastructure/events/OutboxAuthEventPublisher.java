package br.com.hubregistro.auth.infrastructure.events;

import br.com.hubregistro.auth.application.port.AuthEventPublisher;
import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.messaging.EventActor;
import br.com.hubregistro.messaging.OutboxMessage;
import br.com.hubregistro.messaging.OutboxWriter;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Eventos de segurança em {@code hub.platform.events} (consumidos pela auditoria). */
@Component
class OutboxAuthEventPublisher implements AuthEventPublisher {

    private final OutboxWriter outbox;

    OutboxAuthEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void loginSucceeded(User user, String clientIp) {
        publishUser("user.login.succeeded", user, EventActor.user(user.id(), user.institutionId()),
                Map.of("clientIp", nullable(clientIp)));
    }

    @Override
    public void loginFailed(User user, Email attemptedEmail, String clientIp, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", attemptedEmail.value());
        data.put("reason", reason);
        data.put("clientIp", clientIp);
        if (user == null) {
            outbox.write(new OutboxMessage("USER", "unknown", "user.login.failed", OutboxWriter.PLATFORM_EVENTS,
                    null, EventActor.SYSTEM, data));
        } else {
            publishUser("user.login.failed", user, EventActor.user(user.id(), user.institutionId()), data);
        }
    }

    @Override
    public void userLocked(User user, String clientIp) {
        publishUser("user.locked", user, EventActor.SYSTEM,
                Map.of("lockedUntil", String.valueOf(user.lockedUntil()), "clientIp", nullable(clientIp)));
    }

    @Override
    public void refreshReuseDetected(UUID userId, UUID familyId, String clientIp) {
        Map<String, Object> data = new HashMap<>();
        data.put("familyId", familyId.toString());
        data.put("clientIp", clientIp);
        outbox.write(new OutboxMessage("USER", userId.toString(), "user.session.reuse-detected",
                OutboxWriter.PLATFORM_EVENTS, null, EventActor.SYSTEM, data));
    }

    @Override
    public void passwordLinkRequested(User user, String purpose, String clientIp) {
        Map<String, Object> data = new HashMap<>();
        data.put("purpose", purpose);
        data.put("clientIp", clientIp);
        publishUser("user.password.link-requested", user, EventActor.SYSTEM, data);
    }

    @Override
    public void passwordChanged(User user, String how) {
        publishUser("user.password.changed", user, EventActor.user(user.id(), user.institutionId()), Map.of("how", how));
    }

    @Override
    public void userCreated(HubPrincipal actor, User user) {
        publishUser("user.created", user, actorOf(actor), snapshot(user));
    }

    @Override
    public void userUpdated(HubPrincipal actor, User user) {
        publishUser("user.updated", user, actorOf(actor), snapshot(user));
    }

    @Override
    public void userStatusChanged(HubPrincipal actor, User user, String previousStatus, String reason) {
        publishUser("user.status.changed", user, actorOf(actor),
                Map.of("from", previousStatus, "to", user.status().name(), "reason", reason));
    }

    @Override
    public void userRolesChanged(HubPrincipal actor, User user, Set<String> previousRoles) {
        publishUser("user.roles.changed", user, actorOf(actor),
                Map.of("from", List.copyOf(previousRoles), "to", List.copyOf(user.roleCodes())));
    }

    @Override
    public void apiClientCreated(HubPrincipal actor, ApiClient client) {
        publishClient("api-client.created", client, actor);
    }

    @Override
    public void apiClientRevoked(HubPrincipal actor, ApiClient client) {
        publishClient("api-client.revoked", client, actor);
    }

    private void publishUser(String eventType, User user, EventActor actor, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>(data);
        payload.put("userId", user.id().toString());
        outbox.write(new OutboxMessage("USER", user.id().toString(), eventType, OutboxWriter.PLATFORM_EVENTS,
                user.institutionId().toString(), actor, payload));
    }

    private void publishClient(String eventType, ApiClient client, HubPrincipal actor) {
        Map<String, Object> data = new HashMap<>();
        data.put("clientId", client.clientId());
        data.put("name", client.name());
        data.put("scopes", List.copyOf(client.scopes()));
        data.put("rateLimitPerMinute", client.rateLimitPerMinute());
        data.put("status", client.status().name());
        outbox.write(new OutboxMessage("API_CLIENT", client.id().toString(), eventType, OutboxWriter.PLATFORM_EVENTS,
                client.institutionId().toString(), actorOf(actor), data));
    }

    private static Map<String, Object> snapshot(User user) {
        return Map.of("fullName", user.fullName(), "email", user.email().value(), "status", user.status().name(),
                "roles", List.copyOf(user.roleCodes()));
    }

    private static EventActor actorOf(HubPrincipal actor) {
        return actor.apiClient()
                ? EventActor.apiClient(actor.subject(), actor.institutionId())
                : new EventActor("USER", actor.subject(), actor.institutionId());
    }

    private static Object nullable(String value) {
        return value == null ? "" : value;
    }
}
