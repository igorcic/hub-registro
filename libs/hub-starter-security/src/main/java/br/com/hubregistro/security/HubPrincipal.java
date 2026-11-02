package br.com.hubregistro.security;

import br.com.hubregistro.commons.tenant.InstitutionType;

import java.util.Set;
import java.util.UUID;

/**
 * Quem está chamando: um usuário (portal) ou um cliente de API (sistema do banco).
 * A instituição vem sempre do token assinado, nunca do corpo da requisição.
 */
public record HubPrincipal(
        String subject,
        boolean apiClient,
        UUID institutionId,
        InstitutionType institutionType,
        Set<String> roles,
        Set<String> permissions,
        String email,
        String name) {

    public HubPrincipal {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean isHub() {
        return institutionType == InstitutionType.HUB;
    }

    /** Usuário do Hub vê qualquer instituição; os demais, só a própria. */
    public boolean canAccessInstitution(UUID otherInstitutionId) {
        return isHub() || institutionId.equals(otherInstitutionId);
    }
}
