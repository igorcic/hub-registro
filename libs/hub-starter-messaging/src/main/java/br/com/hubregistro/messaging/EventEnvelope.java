package br.com.hubregistro.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** Formato de todo evento publicado pelo {@link OutboxWriter}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String correlationId,
        String aggregateType,
        String aggregateId,
        String institutionId,
        JsonNode actor,
        JsonNode data) {

    public String text(String field) {
        JsonNode node = data == null ? null : data.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    public long longValue(String field, long defaultValue) {
        JsonNode node = data == null ? null : data.get(field);
        return node == null || node.isNull() ? defaultValue : node.asLong(defaultValue);
    }
}
