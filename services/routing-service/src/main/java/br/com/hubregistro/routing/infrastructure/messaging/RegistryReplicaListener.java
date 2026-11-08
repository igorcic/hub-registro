package br.com.hubregistro.routing.infrastructure.messaging;

import br.com.hubregistro.messaging.EventEnvelope;
import br.com.hubregistro.messaging.NonRetryableMessageException;
import br.com.hubregistro.messaging.ReliableMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Elegibilidade das registradoras para o roteamento, mantida por eventos:
 * registry.* (status, tipos de contrato, regiões, capacidade) e integration.health.changed (saúde da integração).
 * As duas fontes têm ordem independente: versão do cadastro e horário da verificação.
 */
@Component
class RegistryReplicaListener {

    static final String QUEUE = "routing.registry-replica";

    private final ReliableMessageHandler handler;
    private final JdbcTemplate jdbc;

    RegistryReplicaListener(ReliableMessageHandler handler, JdbcTemplate jdbc) {
        this.handler = handler;
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        handler.handle(message, channel, QUEUE, QUEUE, this::apply);
    }

    void apply(EventEnvelope event) {
        if (event.eventType() == null) {
            throw new NonRetryableMessageException("Evento sem tipo");
        }
        if (event.eventType().startsWith("registry.")) {
            applyRegistry(event);
        } else if (event.eventType().equals("integration.health.changed")) {
            applyHealth(event);
        }
        // Outros eventos de integração (configured, status.changed) não afetam a elegibilidade.
    }

    private void applyRegistry(EventEnvelope event) {
        UUID registryId = UUID.fromString(event.aggregateId());
        long version = event.longValue("version", 0);
        List<String> types = new ArrayList<>();
        event.data().path("contractTypes").forEach(t -> types.add(t.asText()));
        Integer capacity = event.data().path("dailyCapacity").isNumber() ? event.data().get("dailyCapacity").asInt() : null;

        int applied = jdbc.update("""
                INSERT INTO registry_eligibility (registry_id, status, contract_types, daily_capacity, source_version, updated_at)
                VALUES (?, ?, ?::text[], ?, ?, now())
                ON CONFLICT (registry_id) DO UPDATE SET status = EXCLUDED.status, contract_types = EXCLUDED.contract_types,
                    daily_capacity = EXCLUDED.daily_capacity, source_version = EXCLUDED.source_version, updated_at = now()
                WHERE registry_eligibility.source_version < EXCLUDED.source_version""",
                registryId, event.text("status"), "{" + String.join(",", types) + "}", capacity, version);
        if (applied == 0) {
            return;
        }
        jdbc.update("DELETE FROM registry_service_areas WHERE registry_id = ?", registryId);
        for (JsonNode area : event.data().path("serviceAreas")) {
            jdbc.update("INSERT INTO registry_service_areas (registry_id, uf, ibge_code) VALUES (?, ?, ?)",
                    registryId, area.path("uf").asText(), area.path("ibgeCode").isNull() ? null : area.path("ibgeCode").asText(null));
        }
    }

    private void applyHealth(EventEnvelope event) {
        UUID registryId = UUID.fromString(event.text("registryId"));
        Instant checkedAt = Instant.parse(event.text("checkedAt"));
        // A saúde pode chegar antes do cadastro: cria a linha como INACTIVE (versão -1), que o cadastro sobrescreve.
        jdbc.update("""
                INSERT INTO registry_eligibility (registry_id, status, integration_health, source_version, health_checked_at, updated_at)
                VALUES (?, 'INACTIVE', ?, -1, ?, now())
                ON CONFLICT (registry_id) DO UPDATE SET integration_health = EXCLUDED.integration_health,
                    health_checked_at = EXCLUDED.health_checked_at, updated_at = now()
                WHERE registry_eligibility.health_checked_at IS NULL
                   OR registry_eligibility.health_checked_at < EXCLUDED.health_checked_at""",
                registryId, event.text("status"), Timestamp.from(checkedAt));
    }
}
