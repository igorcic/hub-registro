package br.com.hubregistro.audit;

import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class AuditDatabaseRulesIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void auditTrailCannotBeChangedOrErased() {
        String eventId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO audit_logs (occurred_at, source_service, source_event_id, actor_type, actor_id,
                                        action, resource_type, resource_id, outcome)
                VALUES (now(), 'bank-service', ?, 'USER', 'u-1', 'BANK_SUSPENDED', 'BANK', 'b-1', 'SUCCESS')""",
                eventId);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE audit_logs SET outcome = 'DENIED' WHERE source_event_id = ?", eventId))
                .hasMessageContaining("somente inserção");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE source_event_id = ?", eventId))
                .hasMessageContaining("somente inserção");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE source_event_id = ?", Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }
}
