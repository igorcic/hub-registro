package br.com.hubregistro.messaging;

import br.com.hubregistro.commons.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grava o evento em {@code outbox_events} dentro da transação de negócio em andamento.
 * {@link Propagation#MANDATORY}: chamar fora de transação é erro de programação, porque o evento
 * poderia ser gravado sem a mudança de estado que o originou (ou vice-versa).
 */
public class OutboxWriter {

    /** Exchanges da topologia (infra/rabbitmq/definitions.json). */
    public static final String CONTRACT_EVENTS = "hub.contract.events";
    public static final String CONTRACT_COMMANDS = "hub.contract.commands";
    public static final String PLATFORM_EVENTS = "hub.platform.events";

    private static final String INSERT = """
            INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, exchange, routing_key,
                                       payload, headers, correlation_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final String source;
    private final Clock clock;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper objectMapper, String source, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.source = source;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID write(OutboxMessage message) {
        UUID eventId = UuidV7.generate(clock);
        Instant now = clock.instant();
        String correlationId = MDC.get("correlationId");

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", message.actor().type());
        actor.put("id", message.actor().id());
        actor.put("institutionId", message.actor().institutionId() == null ? null : message.actor().institutionId().toString());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", message.eventType());
        envelope.put("occurredAt", now.toString());
        envelope.put("source", source);
        envelope.put("correlationId", correlationId);
        envelope.put("aggregateType", message.aggregateType());
        envelope.put("aggregateId", message.aggregateId());
        envelope.put("institutionId", message.institutionId());
        envelope.put("actor", actor);
        envelope.put("data", message.data());

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("message_id", eventId.toString());
        headers.put("event_type", message.eventType());
        headers.put("source", source);
        if (correlationId != null) {
            headers.put("x-correlation-id", correlationId);
        }

        jdbc.update(INSERT, eventId, message.aggregateType(), message.aggregateId(), message.eventType(),
                message.exchange(), message.eventType(), json(envelope), json(headers), correlationId,
                java.sql.Timestamp.from(now));
        return eventId;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Evento não serializável", e);
        }
    }
}
