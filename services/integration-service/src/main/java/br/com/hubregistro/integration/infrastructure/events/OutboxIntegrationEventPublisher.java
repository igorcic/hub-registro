package br.com.hubregistro.integration.infrastructure.events;

import br.com.hubregistro.integration.application.IntegrationEventPublisher;
import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.HealthStatus;
import br.com.hubregistro.integration.domain.RegistryIntegration;
import br.com.hubregistro.messaging.EventActor;
import br.com.hubregistro.messaging.OutboxMessage;
import br.com.hubregistro.messaging.OutboxWriter;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
class OutboxIntegrationEventPublisher implements IntegrationEventPublisher {

    private final OutboxWriter outbox;

    OutboxIntegrationEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void healthChanged(RegistryIntegration integration, HealthStatus previous, HealthCheckResult result) {
        Map<String, Object> data = base(integration);
        data.put("status", result.status().name());
        data.put("previousStatus", previous.name());
        data.put("latencyMs", result.latencyMs());
        data.put("httpStatus", result.httpStatus());
        data.put("error", result.errorMessage());
        data.put("checkedAt", result.checkedAt().toString());
        data.put("version", result.checkedAt().toEpochMilli());
        outbox.write(new OutboxMessage("REGISTRY_INTEGRATION", integration.id().toString(), "integration.health.changed",
                OutboxWriter.PLATFORM_EVENTS, integration.registryId().toString(), EventActor.SYSTEM, data));
    }

    @Override
    public void configured(HubPrincipal actor, RegistryIntegration integration) {
        Map<String, Object> data = base(integration);
        data.put("adapterType", integration.adapterType());
        data.put("baseUrl", integration.baseUrl());
        data.put("authType", integration.authType().name());
        data.put("endpoints", integration.endpoints().keySet().stream().map(Enum::name).sorted().toList());
        outbox.write(new OutboxMessage("REGISTRY_INTEGRATION", integration.id().toString(), "integration.configured",
                OutboxWriter.PLATFORM_EVENTS, integration.registryId().toString(), actorOf(actor), data));
    }

    @Override
    public void statusChanged(HubPrincipal actor, RegistryIntegration integration) {
        Map<String, Object> data = base(integration);
        data.put("active", integration.active());
        outbox.write(new OutboxMessage("REGISTRY_INTEGRATION", integration.id().toString(), "integration.status.changed",
                OutboxWriter.PLATFORM_EVENTS, integration.registryId().toString(), actorOf(actor), data));
    }

    private static Map<String, Object> base(RegistryIntegration integration) {
        Map<String, Object> data = new HashMap<>();
        data.put("integrationId", integration.id().toString());
        data.put("registryId", integration.registryId().toString());
        return data;
    }

    private static EventActor actorOf(HubPrincipal actor) {
        return new EventActor("USER", actor.subject(), actor.institutionId());
    }
}
