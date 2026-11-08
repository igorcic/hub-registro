package br.com.hubregistro.routing;

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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A elegibilidade das registradoras é mantida pelos eventos do cadastro e da saúde da integração. */
@SpringBootTest
@Import({PostgresTestcontainersConfiguration.class, RabbitTestcontainersConfiguration.class})
class RegistryReplicaIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registryCoverageAndHealthAreReplicated() {
        UUID registryId = UUID.randomUUID();
        Instant checkedAt = Instant.parse("2026-11-01T10:00:00Z");

        // A saúde chega antes do cadastro: cria a linha, que o cadastro completa depois.
        TestEvents.publish(rabbit, "integration.health.changed", "REGISTRY_INTEGRATION", UUID.randomUUID(),
                health(registryId, "UP", checkedAt));
        TestEvents.publish(rabbit, "registry.created", "REGISTRY", registryId, registry("ACTIVE", 1,
                List.of(Map.of("uf", "SP"), Map.of("uf", "MG", "ibgeCode", "3106200"))));

        Map<String, Object> row = Eventually.await(TIMEOUT, () -> eligibility(registryId),
                r -> r != null && "ACTIVE".equals(r.get("status")));
        assertThat(row).containsEntry("integration_health", "UP").containsEntry("daily_capacity", 500)
                .containsEntry("source_version", 1L);
        assertThat(jdbc.queryForObject("SELECT array_to_string(contract_types, ',') FROM registry_eligibility WHERE registry_id = ?",
                String.class, registryId)).isEqualTo("FINANCIAMENTO_IMOBILIARIO,ALIENACAO_FIDUCIARIA");
        assertThat(jdbc.queryForList("SELECT uf || coalesce('/' || ibge_code, '') FROM registry_service_areas WHERE registry_id = ? ORDER BY uf",
                String.class, registryId)).containsExactly("MG/3106200", "SP");

        // Cobertura nova (versão 2) substitui as regiões; saúde mais antiga que a aplicada é ignorada.
        TestEvents.publish(rabbit, "registry.coverage.changed", "REGISTRY", registryId, registry("ACTIVE", 2, List.of(Map.of("uf", "RJ"))));
        TestEvents.publish(rabbit, "integration.health.changed", "REGISTRY_INTEGRATION", UUID.randomUUID(),
                health(registryId, "DOWN", checkedAt.minusSeconds(60)));
        TestEvents.publish(rabbit, "registry.updated", "REGISTRY", registryId, registry("SUSPENDED", 1, List.of()));
        TestEvents.publish(rabbit, "integration.health.changed", "REGISTRY_INTEGRATION", UUID.randomUUID(),
                health(registryId, "DEGRADED", checkedAt.plusSeconds(60)));

        Eventually.await(TIMEOUT, () -> eligibility(registryId), r -> "DEGRADED".equals(r.get("integration_health")));
        assertThat(eligibility(registryId)).containsEntry("status", "ACTIVE").containsEntry("source_version", 2L);
        assertThat(jdbc.queryForList("SELECT uf FROM registry_service_areas WHERE registry_id = ?", String.class, registryId))
                .containsExactly("RJ");
    }

    private Map<String, Object> eligibility(UUID registryId) {
        return jdbc.queryForList("""
                SELECT status, integration_health, daily_capacity, source_version
                  FROM registry_eligibility WHERE registry_id = ?""", registryId).stream().findFirst().orElse(null);
    }

    private static Map<String, Object> registry(String status, long version, List<Map<String, String>> areas) {
        Map<String, Object> data = new HashMap<>();
        data.put("institutionType", "REGISTRY");
        data.put("tradeName", "Registradora Teste");
        data.put("status", status);
        data.put("version", version);
        data.put("dailyCapacity", 500);
        data.put("contractTypes", List.of("FINANCIAMENTO_IMOBILIARIO", "ALIENACAO_FIDUCIARIA"));
        data.put("serviceAreas", areas);
        return data;
    }

    private static Map<String, Object> health(UUID registryId, String status, Instant checkedAt) {
        return Map.of("registryId", registryId.toString(), "status", status, "previousStatus", "UNKNOWN",
                "checkedAt", checkedAt.toString(), "version", checkedAt.toEpochMilli());
    }
}
