package br.com.hubregistro.bank.infrastructure.events;

import br.com.hubregistro.bank.application.BankEventPublisher;
import br.com.hubregistro.bank.domain.Bank;
import br.com.hubregistro.bank.domain.StatusChange;
import br.com.hubregistro.messaging.EventActor;
import br.com.hubregistro.messaging.OutboxMessage;
import br.com.hubregistro.messaging.OutboxWriter;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
class OutboxBankEventPublisher implements BankEventPublisher {

    private final OutboxWriter outbox;

    OutboxBankEventPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void created(HubPrincipal actor, Bank bank, long version) {
        publish("bank.created", actor, bank, version, Map.of());
    }

    @Override
    public void updated(HubPrincipal actor, Bank bank, long version) {
        publish("bank.updated", actor, bank, version, Map.of());
    }

    @Override
    public void statusChanged(HubPrincipal actor, Bank bank, StatusChange change, long version) {
        publish("bank.status.changed", actor, bank, version,
                Map.of("previousStatus", change.from().name(), "reason", change.reason()));
    }

    private void publish(String eventType, HubPrincipal actor, Bank bank, long version, Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>(extra);
        data.put("bankId", bank.id().toString());
        data.put("institutionType", "BANK");
        data.put("legalName", bank.legalName());
        data.put("tradeName", bank.tradeName());
        data.put("cnpj", bank.cnpj().value());
        data.put("code", bank.code());
        data.put("status", bank.status().name());
        data.put("requestLimitPerMinute", bank.requestLimitPerMinute());
        data.put("version", version);
        outbox.write(new OutboxMessage("BANK", bank.id().toString(), eventType, OutboxWriter.PLATFORM_EVENTS,
                bank.id().toString(), actorOf(actor), data));
    }

    private static EventActor actorOf(HubPrincipal actor) {
        return actor.apiClient()
                ? EventActor.apiClient(actor.subject(), actor.institutionId())
                : new EventActor("USER", actor.subject(), actor.institutionId());
    }
}
