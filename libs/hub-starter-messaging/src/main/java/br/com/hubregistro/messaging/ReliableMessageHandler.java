package br.com.hubregistro.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Processamento confiável de uma mensagem, usado por todo {@code @RabbitListener} (com ACK manual):
 *
 * <ol>
 *   <li><b>Idempotência:</b> grava {@code (message_id, consumer)} em {@code processed_messages} na mesma transação
 *       do efeito. Se a linha já existe, a mensagem é uma reentrega: recebe ACK sem ser processada de novo.</li>
 *   <li><b>ACK só depois do commit.</b></li>
 *   <li><b>Erro transitório:</b> publica uma cópia em {@code hub.retry} com routing key
 *       {@code <nível>.<fila>} e {@code x-retry-count + 1}, e dá ACK no original. Ao fim da espera, o broker
 *       devolve a cópia à fila de origem.</li>
 *   <li><b>Erro permanente ou retentativas esgotadas:</b> NACK sem requeue: a fila manda a mensagem para a DLQ.</li>
 * </ol>
 */
public class ReliableMessageHandler {

    public static final String RETRY_COUNT_HEADER = "x-retry-count";
    public static final String RETRY_EXCHANGE = "hub.retry";

    private static final Logger log = LoggerFactory.getLogger(ReliableMessageHandler.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final List<String> retryLevels;

    public ReliableMessageHandler(JdbcTemplate jdbc, TransactionTemplate transaction, RabbitTemplate rabbit,
                                  ObjectMapper objectMapper, MessagingProperties properties) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.retryLevels = List.copyOf(properties.getRetryLevels());
    }

    /** Resultado, útil em testes e logs. */
    public enum Outcome { PROCESSED, DUPLICATE, SCHEDULED_FOR_RETRY, DEAD_LETTERED }

    /**
     * @param consumer nome estável do consumidor (chave de idempotência junto com o message_id)
     * @param queue    fila de onde a mensagem veio (destino da volta após a espera)
     * @param handler  efeito; roda dentro da transação, depois do registro de idempotência
     */
    public Outcome handle(Message message, Channel channel, String consumer, String queue, Consumer<EventEnvelope> handler)
            throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), EventEnvelope.class);
            if (messageId == null) {
                messageId = envelope.eventId();
            }
            if (messageId == null) {
                throw new NonRetryableMessageException("Mensagem sem message_id");
            }
        } catch (Exception e) {
            log.error("Mensagem ilegível em {} enviada para a DLQ: {}", queue, e.getMessage());
            channel.basicReject(tag, false);
            return Outcome.DEAD_LETTERED;
        }

        String id = messageId;
        if (envelope.correlationId() != null) {
            MDC.put("correlationId", envelope.correlationId());
        }
        try {
            Boolean processed = transaction.execute(status -> {
                int inserted = jdbc.update("""
                        INSERT INTO processed_messages (message_id, consumer) VALUES (?, ?)
                        ON CONFLICT (message_id, consumer) DO NOTHING""", id, consumer);
                if (inserted == 0) {
                    return false;
                }
                handler.accept(envelope);
                return true;
            });
            channel.basicAck(tag, false);
            return Boolean.TRUE.equals(processed) ? Outcome.PROCESSED : Outcome.DUPLICATE;
        } catch (NonRetryableMessageException e) {
            log.error("Mensagem {} ({}) recusada em {}, enviada para a DLQ: {}", id, envelope.eventType(), queue, e.getMessage());
            channel.basicReject(tag, false);
            return Outcome.DEAD_LETTERED;
        } catch (RuntimeException e) {
            return retryOrDeadLetter(message, channel, queue, id, envelope, e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private Outcome retryOrDeadLetter(Message message, Channel channel, String queue, String id, EventEnvelope envelope,
                                      RuntimeException error) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        int attempt = retryCount(message.getMessageProperties());
        if (attempt >= retryLevels.size()) {
            log.error("Mensagem {} ({}) falhou {} vezes em {}, enviada para a DLQ", id, envelope.eventType(), attempt + 1, queue, error);
            channel.basicReject(tag, false);
            return Outcome.DEAD_LETTERED;
        }
        String level = retryLevels.get(attempt);
        try {
            MessageProperties properties = message.getMessageProperties();
            properties.setHeader(RETRY_COUNT_HEADER, attempt + 1);
            properties.setHeader("x-last-error", truncate(error.getClass().getSimpleName() + ": " + error.getMessage()));
            rabbit.send(RETRY_EXCHANGE, level + "." + queue, new Message(message.getBody(), properties));
            channel.basicAck(tag, false);
            log.warn("Mensagem {} ({}) falhou em {}; nova tentativa em {} ({}ª de {})",
                    id, envelope.eventType(), queue, level, attempt + 1, retryLevels.size(), error);
            return Outcome.SCHEDULED_FOR_RETRY;
        } catch (RuntimeException publishError) {
            // Sem como agendar a retentativa: devolve à fila para nova entrega (x-delivery-limit protege de laço).
            log.error("Não foi possível agendar a retentativa da mensagem {}; devolvida à fila", id, publishError);
            channel.basicNack(tag, false, true);
            return Outcome.SCHEDULED_FOR_RETRY;
        }
    }

    static int retryCount(MessageProperties properties) {
        Object value = properties.getHeaders().get(RETRY_COUNT_HEADER);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
