package br.com.hubregistro.document;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ver ContractTenantIsolationIT: as consultas assumem um papel comum porque superusuário ignora RLS. */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentTenantIsolationIT {

    private static final UUID BANK = UUID.fromString("aaaaaaaa-0000-7000-8000-000000000001");
    private static final UUID REGISTRY = UUID.fromString("cccccccc-0000-7000-8000-000000000001");
    private static final UUID ROUTED_CONTRACT = UUID.fromString("11111111-0000-7000-8000-000000000001");
    private static final UUID CONTRACT_WITHOUT_REPLICA = UUID.fromString("22222222-0000-7000-8000-000000000002");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @BeforeAll
    void seed() {
        jdbc.execute("""
                DO $$ BEGIN CREATE ROLE rls_probe NOLOGIN; EXCEPTION WHEN duplicate_object THEN NULL; END $$""");
        jdbc.execute("GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA public TO rls_probe");
        jdbc.update("INSERT INTO contract_access (contract_id, bank_id, registry_id) VALUES (?, ?, ?)",
                ROUTED_CONTRACT, BANK, REGISTRY);
        insertDocument(ROUTED_CONTRACT, "k-routed");
        insertDocument(CONTRACT_WITHOUT_REPLICA, "k-pending");
    }

    @Test
    void owningBankSeesItsDocumentsEvenBeforeTheAccessReplicaArrives() {
        assertThat(visibleKeys("BANK", BANK)).containsExactly("k-pending", "k-routed");
    }

    @Test
    void otherBankSeesNothing() {
        assertThat(visibleKeys("BANK", UUID.randomUUID())).isEmpty();
    }

    @Test
    void registrySeesOnlyDocumentsOfContractsRoutedToIt() {
        assertThat(visibleKeys("REGISTRY", REGISTRY)).containsExactly("k-routed");
        assertThat(visibleKeys("REGISTRY", UUID.randomUUID())).isEmpty();
    }

    private List<String> visibleKeys(String type, UUID tenant) {
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL ROLE rls_probe");
            jdbc.queryForList("SELECT set_config('app.tenant_type', ?, true), set_config('app.tenant_id', ?, true)",
                    type, tenant.toString());
            return jdbc.queryForList("SELECT storage_key FROM contract_documents ORDER BY 1", String.class);
        });
    }

    private void insertDocument(UUID contractId, String key) {
        jdbc.update("""
                INSERT INTO contract_documents (contract_id, bank_id, document_type, version, file_name, mime_type,
                                                size_bytes, sha256, storage_bucket, storage_key, uploaded_by)
                VALUES (?, ?, 'CONTRATO', 1, 'contrato.pdf', 'application/pdf', 1024, repeat('a', 64), 'docs', ?, ?)""",
                contractId, BANK, key, BANK);
    }
}
