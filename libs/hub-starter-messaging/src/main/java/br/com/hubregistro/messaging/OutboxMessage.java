package br.com.hubregistro.messaging;

import java.util.Map;
import java.util.Objects;

/**
 * Um evento a publicar.
 *
 * @param aggregateType tipo do agregado afetado (ex.: USER)
 * @param aggregateId   id do agregado; também é a chave de ordenação por agregado
 * @param eventType     nome do evento, igual à routing key (ex.: user.created)
 * @param exchange      exchange de destino
 * @param institutionId instituição dona do recurso afetado (filtra quem pode ver o evento na auditoria)
 * @param data          conteúdo do evento; nunca senhas, tokens ou documentos em claro
 */
public record OutboxMessage(String aggregateType, String aggregateId, String eventType, String exchange,
                            String institutionId, EventActor actor, Map<String, Object> data) {

    public OutboxMessage {
        Objects.requireNonNull(aggregateType);
        Objects.requireNonNull(aggregateId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(exchange);
        actor = actor == null ? EventActor.SYSTEM : actor;
        data = data == null ? Map.of() : data;
    }
}
