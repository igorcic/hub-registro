package br.com.hubregistro.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publica no RabbitMQ os eventos gravados em {@code outbox_events}.
 *
 * <ul>
 *   <li>{@code FOR UPDATE SKIP LOCKED}: várias instâncias do serviço rodam o relay sem publicar o mesmo evento
 *       duas vezes ao mesmo tempo;</li>
 *   <li>publisher confirms + mandatory: só marca {@code published_at} quando o broker confirmou e roteou a
 *       mensagem para ao menos uma fila;</li>
 *   <li>falha (broker fora, sem rota): incrementa {@code attempts} e reagenda com espera exponencial (até 5 min).</li>
 * </ul>
 * A entrega é "pelo menos uma vez": um evento confirmado pode ser publicado de novo se a instância cair antes do
 * commit. Os consumidores são idempotentes por {@code message_id}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private static final String SELECT_PENDING = """
            SELECT id, exchange, routing_key, event_type, payload::text AS payload, headers::text AS headers,
                   correlation_id, attempts
              FROM outbox_events
             WHERE published_at IS NULL AND next_attempt_at <= ?
             ORDER BY created_at
             LIMIT ?
               FOR UPDATE SKIP LOCKED""";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final MessagingProperties.Relay config;
    private final Clock clock;
    private volatile boolean failing;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transaction, RabbitTemplate rabbit,
                       ObjectMapper objectMapper, MessagingProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.config = properties.getRelay();
        this.clock = clock;
    }

    private record Pending(UUID id, String exchange, String routingKey, String eventType, String payload,
                           String headers, String correlationId, int attempts) {
    }

    @Scheduled(fixedDelayString = "${hub.messaging.relay.interval:500ms}", initialDelayString = "${hub.messaging.relay.initial-delay:5s}")
    public void publishPending() {
        if (!config.isEnabled()) {
            return;
        }
        int published;
        do {
            Integer count = transaction.execute(status -> publishBatch());
            published = count == null ? 0 : count;
        } while (published == config.getBatchSize());
    }

    /** Publica um lote; devolve quantos foram confirmados. */
    int publishBatch() {
        Instant now = clock.instant();
        List<Pending> batch = jdbc.query(SELECT_PENDING, (rs, i) -> new Pending(
                rs.getObject("id", UUID.class), rs.getString("exchange"), rs.getString("routing_key"),
                rs.getString("event_type"), rs.getString("payload"), rs.getString("headers"),
                rs.getString("correlation_id"), rs.getInt("attempts")), Timestamp.from(now), config.getBatchSize());

        int confirmed = 0;
        for (Pending event : batch) {
            String error = publish(event);
            if (error == null) {
                jdbc.update("UPDATE outbox_events SET published_at = ?, last_error = NULL WHERE id = ?",
                        Timestamp.from(clock.instant()), event.id());
                confirmed++;
            } else {
                int attempts = event.attempts() + 1;
                jdbc.update("UPDATE outbox_events SET attempts = ?, last_error = ?, next_attempt_at = ? WHERE id = ?",
                        attempts, truncate(error), Timestamp.from(clock.instant().plus(backoff(attempts))), event.id());
            }
        }
        if (!batch.isEmpty() && confirmed == batch.size() && failing) {
            failing = false;
            log.info("Relay do Outbox voltou a publicar normalmente");
        }
        return confirmed;
    }

    /** Devolve null em caso de sucesso, ou a descrição do erro. */
    private String publish(Pending event) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setMessageId(event.id().toString());
            properties.setType(event.eventType());
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setCorrelationId(event.correlationId());
            Map<String, Object> headers = objectMapper.readValue(event.headers(), new TypeReference<>() { });
            headers.forEach(properties::setHeader);
            Message message = new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties);

            CorrelationData correlation = new CorrelationData(event.id().toString());
            rabbit.send(event.exchange(), event.routingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(config.getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                return warn(event, "Sem fila para a routing key (verifique definitions.json)");
            }
            if (!confirm.isAck()) {
                return warn(event, "Broker recusou: " + confirm.getReason());
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return warn(event, "Interrompido");
        } catch (Exception e) {
            return warn(event, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String warn(Pending event, String error) {
        if (!failing) {
            failing = true;
            log.warn("Falha ao publicar evento {} ({}): {}. Novas tentativas com espera crescente.",
                    event.id(), event.eventType(), error);
        }
        return error;
    }

    static Duration backoff(int attempts) {
        long seconds = (long) Math.min(MAX_BACKOFF.toSeconds(), Math.pow(2, Math.min(attempts, 20)));
        return Duration.ofSeconds(seconds);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
