package br.com.hubregistro.routing;

import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o serviço contra um PostgreSQL real: as migrações Liquibase precisam aplicar sem erro,
 * o Hibernate precisa validar o schema e o health check precisa responder UP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfiguration.class)
class RoutingSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate http;

    @Test
    void migrationsCreateTheExpectedTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains(
                    "registry_eligibility",
                    "registry_service_areas",
                    "routing_rules",
                    "routing_rule_targets",
                    "registry_daily_load",
                    "routing_decisions",
                    "outbox_events",
                    "processed_messages");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void healthIsUp() {
        ResponseEntity<Map> response = http.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }
}
