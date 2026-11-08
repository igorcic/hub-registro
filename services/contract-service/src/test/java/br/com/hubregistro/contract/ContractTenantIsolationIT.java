package br.com.hubregistro.contract;

import br.com.hubregistro.security.tenant.TenantContext;
import br.com.hubregistro.security.tenant.TenantContextHolder;
import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Isolamento entre instituições garantido pelo PostgreSQL.
 *
 * <p>O usuário do Testcontainers é superusuário, e superusuários ignoram RLS. Por isso cada consulta roda com
 * {@code SET LOCAL ROLE} para um papel comum, como o usuário de cada serviço no Compose e em produção.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractTenantIsolationIT {

    private static final UUID BANK_A = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID BANK_B = UUID.fromString("bbbbbbbb-0000-7000-8000-000000000002");
    private static final UUID REGISTRY = UUID.fromString("cccccccc-0000-7000-8000-000000000001");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @BeforeAll
    void seed() {
        jdbc.execute("""
                DO $$ BEGIN CREATE ROLE rls_probe NOLOGIN; EXCEPTION WHEN duplicate_object THEN NULL; END $$""");
        jdbc.execute("GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO rls_probe");
        insertContract(BANK_A, REGISTRY, "ISO-A1");
        insertContract(BANK_A, null, "ISO-A2");
        insertContract(BANK_B, REGISTRY, "ISO-B1");
        jdbc.update("""
                INSERT INTO contract_status_history (contract_id, to_status, actor_type)
                SELECT id, 'RECEIVED', 'SYSTEM' FROM contracts WHERE external_id LIKE 'ISO-%'""");
    }

    @Test
    void bankSeesOnlyItsOwnContractsAndTheirHistory() {
        assertThat(visibleContracts("BANK", BANK_A)).containsExactly("ISO-A1", "ISO-A2");
        assertThat(visibleContracts("BANK", BANK_B)).containsExactly("ISO-B1");
        assertThat(countAs("BANK", BANK_B, "SELECT count(*) FROM contract_status_history")).isEqualTo(1);
    }

    @Test
    void registrySeesOnlyContractsRoutedToIt() {
        assertThat(visibleContracts("REGISTRY", REGISTRY)).containsExactly("ISO-A1", "ISO-B1");
        assertThat(visibleContracts("REGISTRY", UUID.randomUUID())).isEmpty();
    }

    @Test
    void hubSeesEverythingAndMissingTenantSeesNothing() {
        assertThat(visibleContracts("HUB", null)).containsExactly("ISO-A1", "ISO-A2", "ISO-B1");
        assertThat(visibleContracts("", null)).isEmpty();
    }

    @Test
    void bankCannotCreateContractForAnotherBank() {
        assertThatThrownBy(() -> asTenant("BANK", BANK_A, () -> insertContract(BANK_B, null, "ISO-X")))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void bankCannotChangeAnotherBanksContract() {
        Integer changed = asTenant("BANK", BANK_A,
                () -> jdbc.update("UPDATE contracts SET status = 'CANCELLED' WHERE external_id = 'ISO-B1'"));

        assertThat(changed).isZero();
    }

    @Test
    void registryCannotHandContractOverToAnotherRegistry() {
        assertThatThrownBy(() -> asTenant("REGISTRY", REGISTRY,
                () -> jdbc.update("UPDATE contracts SET registry_id = ? WHERE external_id = 'ISO-A1'", UUID.randomUUID())))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void applicationAppliesTheCallerTenantToEachConnectionAndClearsItAfterwards() {
        Map<String, Object> inside = TenantContextHolder.runAs(new TenantContext("BANK", BANK_A), () ->
                jdbc.queryForMap("SELECT current_setting('app.tenant_type') AS type, current_setting('app.tenant_id') AS id"));
        Map<String, Object> after = jdbc.queryForMap(
                "SELECT current_setting('app.tenant_type', true) AS type, current_setting('app.tenant_id', true) AS id");

        assertThat(inside).containsEntry("type", "BANK").containsEntry("id", BANK_A.toString());
        assertThat(after.get("type")).isIn("", null);
        assertThat(after.get("id")).isIn("", null);
    }

    private List<String> visibleContracts(String type, UUID tenant) {
        return asTenant(type, tenant,
                () -> jdbc.queryForList("SELECT external_id FROM contracts WHERE external_id LIKE 'ISO-%' ORDER BY 1", String.class));
    }

    private Integer countAs(String type, UUID tenant, String sql) {
        return asTenant(type, tenant, () -> jdbc.queryForObject(sql, Integer.class));
    }

    private <T> T asTenant(String type, UUID tenant, java.util.function.Supplier<T> action) {
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL ROLE rls_probe");
            jdbc.queryForList("SELECT set_config('app.tenant_type', ?, true), set_config('app.tenant_id', ?, true)",
                    type, tenant == null ? "" : tenant.toString());
            return action.get();
        });
    }

    private Void insertContract(UUID bankId, UUID registryId, String externalId) {
        jdbc.update("""
                INSERT INTO contracts (bank_id, registry_id, external_id, contract_number, contract_type, operation_type,
                                       guarantee_type, financed_amount, contracted_at, source)
                VALUES (?, ?, ?, ?, 'RESIDENTIAL', 'HOUSING_FINANCING', 'FIDUCIARY_ALIENATION', 100000, current_date, 'API')""",
                bankId, registryId, externalId, externalId);
        return null;
    }
}
