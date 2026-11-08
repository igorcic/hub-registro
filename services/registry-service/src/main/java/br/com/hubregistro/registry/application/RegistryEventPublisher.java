package br.com.hubregistro.registry.application;

import br.com.hubregistro.registry.domain.Registry;
import br.com.hubregistro.registry.domain.StatusChange;
import br.com.hubregistro.security.HubPrincipal;

/**
 * Eventos registry.* em hub.platform.events. Carregam o estado completo relevante para o roteamento (status,
 * tipos de contrato, regiões, capacidade) para que a réplica do routing-service se mantenha só com eles.
 */
public interface RegistryEventPublisher {

    void created(HubPrincipal actor, Registry registry, long version);

    void updated(HubPrincipal actor, Registry registry, long version);

    void statusChanged(HubPrincipal actor, Registry registry, StatusChange change, long version);
}
