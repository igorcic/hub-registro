package br.com.hubregistro.auth.infrastructure.messaging;

import br.com.hubregistro.messaging.EventEnvelope;
import br.com.hubregistro.messaging.NonRetryableMessageException;
import br.com.hubregistro.messaging.ReliableMessageHandler;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Mantém a réplica {@code institutions} a partir dos eventos bank.* e registry.*: um banco ou registradora
 * criado (ou suspenso) no cadastro passa a valer para login e para a gestão de usuários.
 * Evento com versão menor ou igual à já aplicada é ignorado (fora de ordem ou repetido).
 */
@Component
class InstitutionReplicaListener {

    static final String QUEUE = "auth.institution-replica";
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "SUSPENDED");

    private final ReliableMessageHandler handler;
    private final JdbcTemplate jdbc;

    InstitutionReplicaListener(ReliableMessageHandler handler, JdbcTemplate jdbc) {
        this.handler = handler;
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        handler.handle(message, channel, QUEUE, QUEUE, this::apply);
    }

    void apply(EventEnvelope event) {
        String type = event.text("institutionType");
        String status = event.text("status");
        String name = event.text("tradeName");
        if (!Set.of("BANK", "REGISTRY").contains(type) || !STATUSES.contains(status) || name == null) {
            throw new NonRetryableMessageException("Evento de instituição incompleto: " + event.eventType());
        }
        UUID id;
        try {
            id = UUID.fromString(event.aggregateId());
        } catch (RuntimeException e) {
            throw new NonRetryableMessageException("aggregateId inválido: " + event.aggregateId());
        }
        jdbc.update("""
                INSERT INTO institutions (id, type, name, status, source_version, updated_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, status = EXCLUDED.status,
                    source_version = EXCLUDED.source_version, updated_at = now()
                WHERE institutions.source_version < EXCLUDED.source_version""",
                id, type, name, status, event.longValue("version", 0));
    }
}
