package br.com.hubregistro.auth;

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
class AuthSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate http;

    @Test
    void migrationsCreateTheExpectedTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains(
                    "institutions",
                    "users",
                    "roles",
                    "permissions",
                    "role_permissions",
                    "user_roles",
                    "refresh_tokens",
                    "password_reset_tokens",
                    "api_clients",
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
