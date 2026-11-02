package br.com.hubregistro.auth.application;

import br.com.hubregistro.auth.application.port.AuthEventPublisher;
import br.com.hubregistro.auth.application.port.PasswordHasher;
import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.client.ApiClientRepository;
import br.com.hubregistro.auth.domain.institution.Institution;
import br.com.hubregistro.auth.domain.institution.InstitutionRepository;
import br.com.hubregistro.auth.domain.role.Permissions;
import br.com.hubregistro.commons.crypto.SecureTokens;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.error.ForbiddenOperationException;
import br.com.hubregistro.commons.error.NotFoundException;
import br.com.hubregistro.commons.tenant.InstitutionType;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Credenciais de API dos bancos. Só bancos recebem credenciais; o administrador do banco gerencia as do
 * próprio banco e o Hub ({@code api-client:manage}) as de qualquer banco. O secret é mostrado uma única vez.
 */
@Service
public class ApiClientService {

    private final ApiClientRepository clients;
    private final InstitutionRepository institutions;
    private final PasswordHasher hasher;
    private final AuthEventPublisher events;
    private final Clock clock;

    public ApiClientService(ApiClientRepository clients, InstitutionRepository institutions, PasswordHasher hasher,
                            AuthEventPublisher events, Clock clock) {
        this.clients = clients;
        this.institutions = institutions;
        this.hasher = hasher;
        this.events = events;
        this.clock = clock;
    }

    public record CreateApiClient(UUID institutionId, String name, Set<String> scopes, Integer rateLimitPerMinute,
                                  Instant expiresAt) {
    }

    /** O secret em claro só existe neste retorno. */
    public record CreatedApiClient(ApiClient client, String clientSecret) {
    }

    @Transactional
    public CreatedApiClient create(HubPrincipal actor, CreateApiClient command) {
        UUID institutionId = actor.isHub() ? command.institutionId() : actor.institutionId();
        if (institutionId == null) {
            throw new DomainException("institution_required", "Informe o banco");
        }
        requireCanManage(actor, institutionId);
        Institution institution = institutions.findById(institutionId).orElseThrow(() -> new NotFoundException("Instituição"));
        if (institution.type() != InstitutionType.BANK) {
            throw new DomainException("not_a_bank", "Credenciais de API são emitidas apenas para bancos");
        }
        Instant now = clock.instant();
        if (command.expiresAt() != null && !command.expiresAt().isAfter(now)) {
            throw new DomainException("invalid_expiration", "A expiração precisa ser no futuro");
        }

        String clientId = "hub_" + SecureTokens.generateHex(12);
        String secret = SecureTokens.generate(32);
        ApiClient client = ApiClient.create(institutionId, command.name(), clientId, hasher.hash(secret),
                command.scopes(), command.rateLimitPerMinute(), command.expiresAt(), now);
        clients.save(client);
        events.apiClientCreated(actor, client);
        return new CreatedApiClient(client, secret);
    }

    @Transactional(readOnly = true)
    public List<ApiClient> list(HubPrincipal actor, UUID institutionId) {
        UUID scope = actor.isHub() ? institutionId : actor.institutionId();
        if (scope == null) {
            throw new DomainException("institution_required", "Informe o banco");
        }
        return clients.findByInstitution(scope);
    }

    @Transactional
    public void revoke(HubPrincipal actor, UUID id) {
        ApiClient client = clients.findById(id)
                .filter(c -> actor.canAccessInstitution(c.institutionId()))
                .orElseThrow(() -> new NotFoundException("Credencial de API"));
        requireCanManage(actor, client.institutionId());
        client.revoke();
        clients.save(client);
        events.apiClientRevoked(actor, client);
    }

    private static void requireCanManage(HubPrincipal actor, UUID institutionId) {
        boolean allowed = actor.hasPermission(Permissions.API_CLIENT_MANAGE)
                && (actor.isHub() || actor.institutionId().equals(institutionId));
        if (!allowed) {
            throw new ForbiddenOperationException("Sem permissão para gerenciar credenciais deste banco");
        }
    }
}
