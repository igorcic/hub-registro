package br.com.hubregistro.auth;

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

import static org.assertj.core.api.Assertions.assertThat;

/** Eventos bank.* e registry.* chegam pelo RabbitMQ e mantêm a réplica de instituições usada no login. */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class})
class InstitutionReplicaIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createdBankAppearsAndOlderOrRepeatedEventsAreIgnored() {
        UUID bankId = UUID.randomUUID();
        TestEvents.publish(rabbit, "bank.created", "BANK", bankId, bank("Banco Teste", "ACTIVE", 0));
        Eventually.await(TIMEOUT, () -> status(bankId), "ACTIVE"::equals);

        String suspension = TestEvents.publish(rabbit, "bank.status.changed", "BANK", bankId, bank("Banco Teste", "SUSPENDED", 2));
        Eventually.await(TIMEOUT, () -> status(bankId), "SUSPENDED"::equals);

        // Evento atrasado (versão 1) e reentrega do mesmo evento: nenhum dos dois altera a réplica.
        TestEvents.publish(rabbit, "bank.updated", "BANK", bankId, bank("Nome Antigo", "ACTIVE", 1));
        TestEvents.publish(rabbit, suspension, "bank.status.changed", "BANK", bankId, bank("Banco Teste", "SUSPENDED", 2));
        // Sentinela: a fila tem um consumidor e entrega em ordem; quando ela aparece, as anteriores já foram tratadas.
        UUID sentinel = UUID.randomUUID();
        TestEvents.publish(rabbit, "bank.created", "BANK", sentinel, bank("Sentinela", "ACTIVE", 0));
        Eventually.await(TIMEOUT, () -> status(sentinel), "ACTIVE"::equals);

        Map<String, Object> row = jdbc.queryForMap("SELECT name, status, source_version FROM institutions WHERE id = ?", bankId);
        assertThat(row).containsEntry("name", "Banco Teste").containsEntry("status", "SUSPENDED").containsEntry("source_version", 2L);
    }

    @Test
    void registryEventsAreReplicatedToo() {
        UUID registryId = UUID.randomUUID();
        Map<String, Object> data = Map.of("registryId", registryId.toString(), "institutionType", "REGISTRY",
                "tradeName", "Registradora Teste", "status", "INACTIVE", "version", 0);
        TestEvents.publish(rabbit, "registry.created", "REGISTRY", registryId, data);

        Eventually.await(TIMEOUT, () -> jdbc.queryForList("SELECT type FROM institutions WHERE id = ?", String.class, registryId),
                types -> types.equals(java.util.List.of("REGISTRY")));
    }

    @Test
    void incompleteEventGoesToTheDeadLetterQueue() {
        UUID bankId = UUID.randomUUID();
        TestEvents.publish(rabbit, "bank.created", "BANK", bankId, Map.of("institutionType", "BANK", "version", 0));

        Object dead = Eventually.await(TIMEOUT, () -> rabbit.receive("platform.dead-letter"), m -> m != null);
        assertThat(dead).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM institutions WHERE id = ?", Long.class, bankId)).isZero();
    }

    private String status(UUID id) {
        return jdbc.queryForList("SELECT status FROM institutions WHERE id = ?", String.class, id).stream().findFirst().orElse(null);
    }

    private static Map<String, Object> bank(String tradeName, String status, long version) {
        return Map.of("institutionType", "BANK", "tradeName", tradeName, "legalName", tradeName + " S.A.",
                "status", status, "version", version);
    }
}
