package br.com.hubregistro.registry.infrastructure.events;

import br.com.hubregistro.messaging.EventActor;
import br.com.hubregistro.messaging.OutboxMessage;
import br.com.hubregistro.messaging.OutboxWriter;
import br.com.hubregistro.registry.application.RegistryEventPublisher;
import br.com.hubregistro.registry.domain.Registry;
import br.com.hubregistro.registry.domain.StatusChange;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class OutboxRegistryEventPublisher implements RegistryEventPublisher {

    private final OutboxWriter outbox;

    OutboxRegistryEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void created(HubPrincipal actor, Registry registry, long version) {
        publish("registry.created", actor, registry, version, Map.of());
    }

    @Override
    public void updated(HubPrincipal actor, Registry registry, long version) {
        publish("registry.updated", actor, registry, version, Map.of());
    }

    @Override
    public void statusChanged(HubPrincipal actor, Registry registry, StatusChange change, long version) {
        publish("registry.status.changed", actor, registry, version,
                Map.of("previousStatus", change.from().name(), "reason", change.reason()));
    }

    private void publish(String eventType, HubPrincipal actor, Registry r, long version, Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>(extra);
        data.put("registryId", r.id().toString());
        data.put("institutionType", "REGISTRY");
        data.put("legalName", r.legalName());
        data.put("tradeName", r.tradeName());
        data.put("cnpj", r.cnpj().value());
        data.put("code", r.code());
        data.put("status", r.status().name());
        data.put("dailyCapacity", r.dailyCapacity());
        data.put("contractTypes", r.contractTypes().stream().sorted().toList());
        data.put("serviceAreas", r.serviceAreas().stream()
                .map(a -> { Map<String, Object> m = new HashMap<>(); m.put("uf", a.uf()); m.put("ibgeCode", a.ibgeCode()); return m; })
                .toList());
        data.put("version", version);
        EventActor eventActor = actor.apiClient()
                ? EventActor.apiClient(actor.subject(), actor.institutionId())
                : new EventActor("USER", actor.subject(), actor.institutionId());
        outbox.write(new OutboxMessage("REGISTRY", r.id().toString(), eventType, OutboxWriter.PLATFORM_EVENTS,
                r.id().toString(), eventActor, data));
    }
}
