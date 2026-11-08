package br.com.hubregistro.integration.application;

import br.com.hubregistro.commons.error.ForbiddenOperationException;
import br.com.hubregistro.commons.error.NotFoundException;
import br.com.hubregistro.commons.tenant.InstitutionType;
import br.com.hubregistro.security.HubPrincipal;

import java.util.UUID;

/**
 * Escopo: o Hub vê e configura qualquer integração; a registradora (ou o banco) só a própria.
 * Fora do escopo responde 404; dentro do escopo sem permissão de alterar, 403.
 */
final class IntegrationAccess {

    private IntegrationAccess() {
    }

    static void requireVisible(HubPrincipal actor, InstitutionType ownerType, UUID ownerId) {
        if (!actor.isHub() && !(actor.institutionType() == ownerType && actor.institutionId().equals(ownerId))) {
            throw new NotFoundException("Integração");
        }
    }

    static void requireManage(HubPrincipal actor, InstitutionType ownerType, UUID ownerId) {
        requireVisible(actor, ownerType, ownerId);
        if (!actor.hasPermission("integration:manage") || actor.apiClient()) {
            throw new ForbiddenOperationException("Sem permissão para alterar esta integração");
        }
    }
}
