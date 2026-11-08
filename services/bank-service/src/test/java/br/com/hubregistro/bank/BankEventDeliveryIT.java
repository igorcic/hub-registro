package br.com.hubregistro.bank;

import br.com.hubregistro.test.Eventually;
import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import br.com.hubregistro.test.RabbitTestcontainersConfiguration;
import br.com.hubregistro.test.TestSecurityConfiguration;
import br.com.hubregistro.test.TestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ponta a ponta da mensageria: cadastro de banco → outbox na mesma transação → relay publica com confirmação →
 * mensagem roteada para as filas de réplica (auth e contract).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SuppressWarnings("rawtypes")
class BankEventDeliveryIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private ObjectMapper json;

    @Test
    void createdBankIsPublishedAndRoutedToTheReplicaQueues() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestTokens.superAdmin());
        headers.setContentType(MediaType.APPLICATION_JSON);
        String code = "BANCO_RELAY_" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        ResponseEntity<Map> created = http.postForEntity("/api/v1/banks", new HttpEntity<>(Map.of(
                "legalName", "Banco Relay S.A.", "tradeName", "Banco Relay", "cnpj", BankApiIT.randomCnpj(),
                "code", code), headers), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bankId = (String) created.getBody().get("id");

        Eventually.await(Duration.ofSeconds(20), () -> jdbc.queryForObject(
                "SELECT published_at IS NOT NULL FROM outbox_events WHERE aggregate_id = ? AND event_type = 'bank.created'",
                Boolean.class, bankId));

        // Nenhum consumidor nesta aplicação: a mensagem fica nas filas de réplica dos outros serviços.
        Message message = Eventually.await(Duration.ofSeconds(10),
                () -> rabbit.receive("auth.institution-replica"), m -> m != null);
        JsonNode envelope = json.readTree(message.getBody());
        assertThat(envelope.path("eventType").asText()).isEqualTo("bank.created");
        assertThat(envelope.path("aggregateId").asText()).isEqualTo(bankId);
        assertThat(envelope.path("data").path("institutionType").asText()).isEqualTo("BANK");
        assertThat(message.getMessageProperties().getMessageId()).isNotBlank();
        assertThat(rabbit.receive("contract.institution-replica", 5_000)).isNotNull();
    }
}
