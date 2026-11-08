package br.com.hubregistro.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Publica eventos no formato do Outbox, para testar consumidores sem subir o serviço de origem. */
public final class TestEvents {

    public static final String PLATFORM_EVENTS = "hub.platform.events";
    private static final ObjectMapper JSON = new ObjectMapper();

    private TestEvents() {
    }

    /** Publica e devolve o eventId (= message_id). */
    public static String publish(RabbitTemplate rabbit, String eventType, String aggregateType, UUID aggregateId,
                                 Map<String, Object> data) {
        return publish(rabbit, UUID.randomUUID().toString(), eventType, aggregateType, aggregateId, data);
    }

    public static String publish(RabbitTemplate rabbit, String eventId, String eventType, String aggregateType,
                                 UUID aggregateId, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("source", "test");
        envelope.put("correlationId", UUID.randomUUID().toString());
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("institutionId", aggregateId.toString());
        envelope.put("actor", Map.of("type", "SYSTEM"));
        envelope.put("data", data);
        try {
            MessageProperties properties = new MessageProperties();
            properties.setMessageId(eventId);
            properties.setType(eventType);
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            rabbit.send(PLATFORM_EVENTS, eventType, new Message(JSON.writeValueAsBytes(envelope), properties));
            return eventId;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
