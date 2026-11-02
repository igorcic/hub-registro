package br.com.hubregistro.contract;

import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras que o próprio PostgreSQL garante, independentemente de bugs na aplicação. */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class ContractDatabaseRulesIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sameExternalIdFromTheSameBankIsRejected() {
        UUID bankId = UUID.randomUUID();
        insertContract(bankId, "EXT-001", "RECEIVED");

        assertThatThrownBy(() -> insertContract(bankId, "EXT-001", "RECEIVED"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void sameExternalIdFromDifferentBanksIsAllowed() {
        insertContract(UUID.randomUUID(), "EXT-002", "RECEIVED");
        insertContract(UUID.randomUUID(), "EXT-002", "RECEIVED");
    }

    @Test
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> insertContract(UUID.randomUUID(), "EXT-003", "SENT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusHistoryIsInsertOnly() {
        UUID contractId = insertContract(UUID.randomUUID(), "EXT-004", "RECEIVED");
        jdbc.update("""
                INSERT INTO contract_status_history (contract_id, from_status, to_status, actor_type)
                VALUES (?, NULL, 'RECEIVED', 'API_CLIENT')""", contractId);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE contract_status_history SET to_status = 'COMPLETED' WHERE contract_id = ?", contractId))
                .hasMessageContaining("somente inserção");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM contract_status_history WHERE contract_id = ?", contractId))
                .hasMessageContaining("somente inserção");
    }

    @Test
    void eventsAreInsertOnly() {
        UUID contractId = insertContract(UUID.randomUUID(), "EXT-005", "RECEIVED");
        jdbc.update("""
                INSERT INTO contract_events (contract_id, event_type, description, actor_type)
                VALUES (?, 'DOCUMENT_ATTACHED', 'Contrato anexado', 'USER')""", contractId);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM contract_events WHERE contract_id = ?", contractId))
                .hasMessageContaining("somente inserção");
    }

    @Test
    void historyRowsLandInTheCurrentMonthPartition() {
        UUID contractId = insertContract(UUID.randomUUID(), "EXT-006", "RECEIVED");
        jdbc.update("""
                INSERT INTO contract_status_history (contract_id, to_status, actor_type)
                VALUES (?, 'RECEIVED', 'SYSTEM')""", contractId);

        String partition = jdbc.queryForObject(
                "SELECT tableoid::regclass::text FROM contract_status_history WHERE contract_id = ?",
                String.class, contractId);
        String expected = jdbc.queryForObject(
                "SELECT 'contract_status_history_' || to_char(now(), 'YYYYMM')", String.class);

        assertThat(partition).isEqualTo(expected);
    }

    @Test
    void monthlyPartitionsAreCreatedAheadOfTime() {
        List<String> partitions = jdbc.queryForList("""
                SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
                WHERE i.inhparent = 'contract_events'::regclass AND c.relname <> 'contract_events_default'""",
                String.class);

        assertThat(partitions).hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    void partyDocumentsAreStoredOnlyEncrypted() {
        List<String> columns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'contract_parties'""",
                String.class);

        assertThat(columns).contains("document_enc", "document_hash").doesNotContain("document", "cpf", "cnpj");
    }

    private UUID insertContract(UUID bankId, String externalId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO contracts (bank_id, external_id, contract_number, contract_type, operation_type,
                                       guarantee_type, status, financed_amount, contracted_at, source)
                VALUES (?, ?, 'CT-' || ?, 'RESIDENTIAL', 'HOUSING_FINANCING', 'FIDUCIARY_ALIENATION', ?,
                        350000.00, current_date, 'API')
                RETURNING id""", UUID.class, bankId, externalId, externalId, status);
    }
}
