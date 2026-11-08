package br.com.hubregistro.contract;

import br.com.hubregistro.test.Eventually;
import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import br.com.hubregistro.test.RabbitTestcontainersConfiguration;
import br.com.hubregistro.test.TestEvents;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** O contract-service conhece o status de bancos e registradoras pela réplica mantida por eventos. */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class})
class InstitutionReplicaIT {

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bankStatusIsReplicated() {
        UUID bankId = UUID.randomUUID();
        TestEvents.publish(rabbit, "bank.created", "BANK", bankId,
                Map.of("institutionType", "BANK", "tradeName", "Banco", "status", "ACTIVE", "version", 0));
        TestEvents.publish(rabbit, "bank.status.changed", "BANK", bankId,
                Map.of("institutionType", "BANK", "tradeName", "Banco", "status", "INACTIVE", "version", 1));

        Eventually.await(Duration.ofSeconds(20),
                () -> jdbc.queryForList("SELECT status FROM institutions WHERE id = ?", String.class, bankId),
                statuses -> statuses.contains("INACTIVE"));
    }
}
