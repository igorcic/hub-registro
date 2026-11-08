package br.com.hubregistro.contract.infrastructure.messaging;

import br.com.hubregistro.messaging.EventEnvelope;
import br.com.hubregistro.messaging.NonRetryableMessageException;
import br.com.hubregistro.messaging.ReliableMessageHandler;
import br.com.hubregistro.security.tenant.TenantContextHolder;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Réplica {@code institutions} do contract-service (status de bancos e registradoras), usada na validação de
 * negócio dos contratos. Roda como processo interno (tenant SYSTEM).
 */
@Component
class InstitutionReplicaListener {

    static final String QUEUE = "contract.institution-replica";

    private final ReliableMessageHandler handler;
    private final JdbcTemplate jdbc;

    InstitutionReplicaListener(ReliableMessageHandler handler, JdbcTemplate jdbc) {
        this.handler = handler;
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        TenantContextHolder.runAsSystem(() -> {
            try {
                handler.handle(message, channel, QUEUE, QUEUE, this::apply);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    void apply(EventEnvelope event) {
        String type = event.text("institutionType");
        String status = event.text("status");
        if (!Set.of("BANK", "REGISTRY").contains(type) || !Set.of("ACTIVE", "INACTIVE", "SUSPENDED").contains(status)) {
            throw new NonRetryableMessageException("Evento de instituição incompleto: " + event.eventType());
        }
        jdbc.update("""
                INSERT INTO institutions (id, type, status, source_version, updated_at) VALUES (?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, source_version = EXCLUDED.source_version, updated_at = now()
                WHERE institutions.source_version < EXCLUDED.source_version""",
                UUID.fromString(event.aggregateId()), type, status, event.longValue("version", 0));
    }
}
