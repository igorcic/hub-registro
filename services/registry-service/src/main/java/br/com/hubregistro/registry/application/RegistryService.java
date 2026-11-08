package br.com.hubregistro.registry.application;

import br.com.hubregistro.commons.document.Cnpj;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.error.ForbiddenOperationException;
import br.com.hubregistro.commons.error.NotFoundException;
import br.com.hubregistro.commons.tenant.InstitutionType;
import br.com.hubregistro.registry.domain.Address;
import br.com.hubregistro.registry.domain.Registry;
import br.com.hubregistro.registry.domain.RegistryRepository;
import br.com.hubregistro.registry.domain.RegistryRepository.RegistryPage;
import br.com.hubregistro.registry.domain.RegistryRepository.RegistrySearch;
import br.com.hubregistro.registry.domain.RegistryStatus;
import br.com.hubregistro.registry.domain.ServiceArea;
import br.com.hubregistro.registry.domain.StatusChange;
import br.com.hubregistro.registry.domain.TechnicalContact;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro de registradoras. Cadastro, cobertura (tipos e regiões, que alimentam o roteamento) e status são do
 * Hub ({@code registry:manage}). A registradora vê só o próprio cadastro e seu administrador mantém os
 * responsáveis técnicos.
 */
@Service
public class RegistryService {

    static final String MANAGE = "registry:manage";

    private final RegistryRepository registries;
    private final RegistryEventPublisher events;
    private final Clock clock;

    public RegistryService(RegistryRepository registries, RegistryEventPublisher events, Clock clock) {
        this.registries = registries;
        this.events = events;
        this.clock = clock;
    }

    public record RegistryData(String legalName, String tradeName, String cnpj, String code, Address address,
                               Integer dailyCapacity, Set<String> contractTypes, Set<ServiceArea> serviceAreas,
                               List<TechnicalContact> technicalContacts) {
    }

    @Transactional(readOnly = true)
    public RegistryPage search(HubPrincipal actor, RegistryStatus status, String contractType, String uf, String text,
                               int page, int size) {
        if (!actor.isHub()) {
            return registries.findById(actor.institutionId())
                    .map(r -> new RegistryPage(List.of(r), 1, 0, 1))
                    .orElse(new RegistryPage(List.of(), 0, 0, 1));
        }
        return registries.search(new RegistrySearch(status, blankToNull(contractType, true), blankToNull(uf, true),
                blankToNull(text, false)), Math.max(page, 0), Math.clamp(size, 1, 100));
    }

    @Transactional(readOnly = true)
    public Registry get(HubPrincipal actor, UUID id) {
        return visible(actor, id);
    }

    @Transactional
    public Registry create(HubPrincipal actor, RegistryData data) {
        requireHubManager(actor);
        Cnpj cnpj = new Cnpj(data.cnpj());
        Registry registry = Registry.create(data.legalName(), data.tradeName(), cnpj, data.code(), data.address(),
                data.dailyCapacity(), data.contractTypes(), data.serviceAreas(), data.technicalContacts());
        if (registries.existsByCnpj(cnpj)) {
            throw new DomainException("cnpj_in_use", "Já existe uma registradora com este CNPJ");
        }
        if (registries.existsByCode(registry.code())) {
            throw new DomainException("code_in_use", "Já existe uma registradora com este código");
        }
        long version = registries.save(registry);
        events.created(actor, registry, version);
        return registry;
    }

    @Transactional
    public Registry update(HubPrincipal actor, UUID id, String legalName, String tradeName, Address address, Integer dailyCapacity) {
        requireHubManager(actor);
        Registry registry = visible(actor, id);
        registry.update(legalName, tradeName, address, dailyCapacity);
        long version = registries.save(registry);
        events.updated(actor, registry, version);
        return registry;
    }

    @Transactional
    public Registry defineCoverage(HubPrincipal actor, UUID id, Set<String> contractTypes, Set<ServiceArea> serviceAreas) {
        requireHubManager(actor);
        Registry registry = visible(actor, id);
        registry.defineCoverage(contractTypes, serviceAreas);
        long version = registries.save(registry);
        events.updated(actor, registry, version);
        return registry;
    }

    @Transactional
    public Registry replaceTechnicalContacts(HubPrincipal actor, UUID id, List<TechnicalContact> contacts) {
        Registry registry = visible(actor, id);
        boolean ownAdmin = actor.institutionType() == InstitutionType.REGISTRY && actor.roles().contains("REGISTRY_ADMIN");
        if (!actor.hasPermission(MANAGE) && !ownAdmin) {
            throw new ForbiddenOperationException("Sem permissão para alterar os responsáveis técnicos");
        }
        registry.replaceTechnicalContacts(contacts);
        long version = registries.save(registry);
        events.updated(actor, registry, version);
        return registry;
    }

    @Transactional
    public Registry changeStatus(HubPrincipal actor, UUID id, RegistryStatus target, String reason) {
        requireHubManager(actor);
        Registry registry = visible(actor, id);
        StatusChange change = registry.changeStatus(target, reason, UUID.fromString(actor.subject()), clock.instant());
        long version = registries.save(registry);
        registries.appendStatusChange(change);
        events.statusChanged(actor, registry, change, version);
        return registry;
    }

    @Transactional(readOnly = true)
    public List<StatusChange> statusHistory(HubPrincipal actor, UUID id) {
        visible(actor, id);
        return registries.statusHistory(id);
    }

    private Registry visible(HubPrincipal actor, UUID id) {
        if (!actor.isHub() && !(actor.institutionType() == InstitutionType.REGISTRY && actor.institutionId().equals(id))) {
            throw new NotFoundException("Registradora");
        }
        return registries.findById(id).orElseThrow(() -> new NotFoundException("Registradora"));
    }

    private static void requireHubManager(HubPrincipal actor) {
        if (!actor.isHub() || !actor.hasPermission(MANAGE) || actor.apiClient()) {
            throw new ForbiddenOperationException("Somente a administração do Hub cadastra e altera registradoras");
        }
    }

    private static String blankToNull(String value, boolean upper) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return upper ? value.trim().toUpperCase(Locale.ROOT) : value.trim();
    }
}
