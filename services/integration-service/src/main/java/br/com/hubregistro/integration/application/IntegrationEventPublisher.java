package br.com.hubregistro.integration.application;

import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.HealthStatus;
import br.com.hubregistro.integration.domain.RegistryIntegration;
import br.com.hubregistro.security.HubPrincipal;

public interface IntegrationEventPublisher {

    /** integration.health.changed: alimenta a elegibilidade no roteamento e os alertas operacionais. */
    void healthChanged(RegistryIntegration integration, HealthStatus previous, HealthCheckResult result);

    void configured(HubPrincipal actor, RegistryIntegration integration);

    void statusChanged(HubPrincipal actor, RegistryIntegration integration);
}
