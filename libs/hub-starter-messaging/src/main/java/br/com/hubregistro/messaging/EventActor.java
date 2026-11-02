package br.com.hubregistro.messaging;

import java.util.UUID;

/**
 * Quem causou o evento (vai para a trilha de auditoria).
 *
 * @param type          USER, API_CLIENT, SYSTEM ou REGISTRY
 * @param id            id do usuário, client_id ou nulo para SYSTEM
 * @param institutionId instituição do autor, quando houver
 */
public record EventActor(String type, String id, UUID institutionId) {

    public static final EventActor SYSTEM = new EventActor("SYSTEM", null, null);

    public static EventActor user(UUID userId, UUID institutionId) {
        return new EventActor("USER", userId.toString(), institutionId);
    }

    public static EventActor apiClient(String clientId, UUID institutionId) {
        return new EventActor("API_CLIENT", clientId, institutionId);
    }
}
