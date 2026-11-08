package br.com.hubregistro.integration.infrastructure.persistence;

import br.com.hubregistro.commons.crypto.AesGcmCipher;
import br.com.hubregistro.integration.domain.AuthType;
import br.com.hubregistro.integration.domain.BankIntegration;
import br.com.hubregistro.integration.domain.Certificate;
import br.com.hubregistro.integration.domain.Endpoint;
import br.com.hubregistro.integration.domain.HealthCheckResult;
import br.com.hubregistro.integration.domain.HealthStatus;
import br.com.hubregistro.integration.domain.IntegrationRepository;
import br.com.hubregistro.integration.domain.Operation;
import br.com.hubregistro.integration.domain.RegistryIntegration;
import br.com.hubregistro.integration.domain.RetryPolicy;
import br.com.hubregistro.security.HubSecurity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do integration-service. Credenciais, segredos e certificados são cifrados com AES-GCM tendo o
 * id do registro como contexto: um valor copiado para outra linha não decifra.
 */
@Repository
class JdbcIntegrationRepository implements IntegrationRepository {

    private static final UUID HUB = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private final JdbcTemplate jdbc;
    private final AesGcmCipher cipher;
    private final ObjectMapper json;

    JdbcIntegrationRepository(JdbcTemplate jdbc, AesGcmCipher integrationCipher, ObjectMapper json) {
        this.jdbc = jdbc;
        this.cipher = integrationCipher;
        this.json = json;
    }

    // ---------- integração das registradoras ----------

    @Override
    public Optional<RegistryIntegration> findByRegistry(UUID registryId) {
        return jdbc.query("SELECT * FROM registry_integrations WHERE registry_id = ?", this::toIntegration, registryId)
                .stream().findFirst();
    }

    @Override
    public Optional<RegistryIntegration> findById(UUID id) {
        return jdbc.query("SELECT * FROM registry_integrations WHERE id = ?", this::toIntegration, id).stream().findFirst();
    }

    @Override
    public void save(RegistryIntegration i) {
        RetryPolicy retry = i.retryPolicy();
        jdbc.update("""
                INSERT INTO registry_integrations (id, registry_id, adapter_type, base_url, auth_type, auth_config_enc,
                    webhook_secret_enc, connect_timeout_ms, read_timeout_ms, retry_max_attempts, retry_initial_interval_ms,
                    retry_multiplier, retry_max_interval_ms, rate_limit_per_minute, circuit_failure_rate_pct, status,
                    health_status, last_health_check_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    adapter_type = EXCLUDED.adapter_type, base_url = EXCLUDED.base_url, auth_type = EXCLUDED.auth_type,
                    auth_config_enc = EXCLUDED.auth_config_enc, webhook_secret_enc = EXCLUDED.webhook_secret_enc,
                    connect_timeout_ms = EXCLUDED.connect_timeout_ms, read_timeout_ms = EXCLUDED.read_timeout_ms,
                    retry_max_attempts = EXCLUDED.retry_max_attempts, retry_initial_interval_ms = EXCLUDED.retry_initial_interval_ms,
                    retry_multiplier = EXCLUDED.retry_multiplier, retry_max_interval_ms = EXCLUDED.retry_max_interval_ms,
                    rate_limit_per_minute = EXCLUDED.rate_limit_per_minute,
                    circuit_failure_rate_pct = EXCLUDED.circuit_failure_rate_pct, status = EXCLUDED.status,
                    health_status = EXCLUDED.health_status, last_health_check_at = EXCLUDED.last_health_check_at,
                    updated_by = EXCLUDED.updated_by, updated_at = now(),
                    version = registry_integrations.version + 1""",
                i.id(), i.registryId(), i.adapterType(), i.baseUrl(), i.authType().name(),
                encrypt(toJson(i.credentials()), i.id().toString()), encrypt(i.webhookSecret(), i.id() + ":webhook"),
                i.connectTimeoutMs(), i.readTimeoutMs(), retry.maxAttempts(), (int) retry.initialInterval().toMillis(),
                retry.multiplier(), (int) retry.maxInterval().toMillis(), i.rateLimitPerMinute(), i.circuitFailureRatePct(),
                i.active() ? "ACTIVE" : "INACTIVE", i.healthStatus().name(), ts(i.lastHealthCheckAt()), actor(), actor());
        jdbc.update("DELETE FROM registry_integration_endpoints WHERE integration_id = ?", i.id());
        i.endpoints().forEach((operation, endpoint) -> jdbc.update("""
                INSERT INTO registry_integration_endpoints (integration_id, operation, http_method, path) VALUES (?, ?, ?, ?)""",
                i.id(), operation.name(), endpoint.httpMethod(), endpoint.path()));
    }

    @Override
    public List<UUID> claimDueForHealthCheck(Duration interval, int limit) {
        return jdbc.queryForList("""
                UPDATE registry_integrations SET last_health_check_at = now()
                 WHERE id IN (SELECT id FROM registry_integrations
                               WHERE status = 'ACTIVE'
                                 AND (last_health_check_at IS NULL OR last_health_check_at < now() - make_interval(secs => ?))
                               ORDER BY last_health_check_at NULLS FIRST
                               LIMIT ?
                                 FOR UPDATE SKIP LOCKED)
                RETURNING id""", UUID.class, (double) interval.toSeconds(), limit);
    }

    @Override
    public void appendHealthCheck(UUID integrationId, HealthCheckResult r) {
        jdbc.update("""
                INSERT INTO integration_health_checks (integration_id, status, latency_ms, http_status, error_message, checked_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                integrationId, r.status().name(), r.latencyMs(), r.httpStatus(), r.errorMessage(), ts(r.checkedAt()));
    }

    @Override
    public List<HealthCheckResult> recentHealthChecks(UUID integrationId, int limit) {
        return jdbc.query("""
                SELECT status, latency_ms, http_status, error_message, checked_at FROM integration_health_checks
                WHERE integration_id = ? ORDER BY checked_at DESC LIMIT ?""",
                (rs, n) -> new HealthCheckResult(HealthStatus.valueOf(rs.getString("status")),
                        (Integer) rs.getObject("latency_ms"), (Integer) rs.getObject("http_status"),
                        rs.getString("error_message"), rs.getTimestamp("checked_at").toInstant()),
                integrationId, limit);
    }

    private RegistryIntegration toIntegration(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        Map<Operation, Endpoint> endpoints = new EnumMap<>(Operation.class);
        jdbc.query("SELECT operation, http_method, path FROM registry_integration_endpoints WHERE integration_id = ?",
                (ResultSet e) -> {
                    endpoints.put(Operation.valueOf(e.getString("operation")),
                            new Endpoint(e.getString("http_method"), e.getString("path")));
                }, id);
        Map<String, String> credentials = rs.getBytes("auth_config_enc") == null
                ? Map.of() : fromJson(decrypt(rs.getBytes("auth_config_enc"), id.toString()));
        RetryPolicy retry = new RetryPolicy(rs.getInt("retry_max_attempts"),
                Duration.ofMillis(rs.getInt("retry_initial_interval_ms")), rs.getDouble("retry_multiplier"),
                Duration.ofMillis(rs.getInt("retry_max_interval_ms")));
        RegistryIntegration.Settings settings = new RegistryIntegration.Settings(rs.getString("adapter_type"),
                rs.getString("base_url"), AuthType.valueOf(rs.getString("auth_type")), credentials,
                rs.getInt("connect_timeout_ms"), rs.getInt("read_timeout_ms"), retry,
                (Integer) rs.getObject("rate_limit_per_minute"), rs.getInt("circuit_failure_rate_pct"), endpoints);
        Timestamp lastCheck = rs.getTimestamp("last_health_check_at");
        return RegistryIntegration.restore(id, rs.getObject("registry_id", UUID.class), settings,
                "ACTIVE".equals(rs.getString("status")), HealthStatus.valueOf(rs.getString("health_status")),
                lastCheck == null ? null : lastCheck.toInstant(), decrypt(rs.getBytes("webhook_secret_enc"), id + ":webhook"));
    }

    // ---------- certificados ----------

    @Override
    public void addCertificate(Certificate c, byte[] keystoreBytes, String keystorePassword) {
        jdbc.update("""
                INSERT INTO integration_certificates (id, integration_id, alias, purpose, keystore_type, keystore_enc,
                    subject_dn, issuer_dn, serial_number, not_before, not_after, sha256_fingerprint, active, uploaded_by, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?)""",
                c.id(), c.integrationId(), c.alias(), c.purpose().name(), c.keystoreType(),
                cipher.encrypt(pack(keystorePassword, keystoreBytes), c.id().toString().getBytes(StandardCharsets.UTF_8)),
                c.subjectDn(), c.issuerDn(), c.serialNumber(), ts(c.notBefore()), ts(c.notAfter()), c.sha256Fingerprint(),
                actorOrHub(), ts(c.uploadedAt()));
    }

    @Override
    public List<Certificate> certificates(UUID integrationId) {
        return jdbc.query("""
                SELECT * FROM integration_certificates WHERE integration_id = ? AND deleted_at IS NULL ORDER BY uploaded_at DESC""",
                (rs, n) -> toCertificate(rs), integrationId);
    }

    @Override
    public Optional<StoredKeystore> activeCertificate(UUID integrationId, String alias, Certificate.Purpose purpose) {
        return jdbc.query("""
                SELECT * FROM integration_certificates
                WHERE integration_id = ? AND purpose = ? AND (CAST(? AS varchar) IS NULL OR alias = ?)
                  AND active AND deleted_at IS NULL AND not_after > now()
                ORDER BY uploaded_at DESC LIMIT 1""",
                (rs, n) -> {
                    Certificate certificate = toCertificate(rs);
                    byte[] packed = cipher.decrypt(rs.getBytes("keystore_enc"), certificate.id().toString().getBytes(StandardCharsets.UTF_8));
                    ByteBuffer buffer = ByteBuffer.wrap(packed);
                    byte[] password = new byte[buffer.getShort()];
                    buffer.get(password);
                    byte[] keystore = new byte[buffer.remaining()];
                    buffer.get(keystore);
                    return new StoredKeystore(certificate, keystore, new String(password, StandardCharsets.UTF_8));
                }, integrationId, purpose.name(), alias, alias).stream().findFirst();
    }

    @Override
    public boolean removeCertificate(UUID integrationId, UUID certificateId) {
        return jdbc.update("""
                UPDATE integration_certificates SET active = false, deleted_at = now()
                WHERE id = ? AND integration_id = ? AND deleted_at IS NULL""", certificateId, integrationId) > 0;
    }

    private static Certificate toCertificate(ResultSet rs) throws SQLException {
        return new Certificate(rs.getObject("id", UUID.class), rs.getObject("integration_id", UUID.class),
                rs.getString("alias"), Certificate.Purpose.valueOf(rs.getString("purpose")), rs.getString("keystore_type"),
                rs.getString("subject_dn"), rs.getString("issuer_dn"), rs.getString("serial_number"),
                rs.getTimestamp("not_before").toInstant(), rs.getTimestamp("not_after").toInstant(),
                rs.getString("sha256_fingerprint"), rs.getBoolean("active"), rs.getTimestamp("uploaded_at").toInstant());
    }

    /** Senha do keystore + conteúdo, cifrados juntos: {@code tamanho(2) | senha | keystore}. */
    private static byte[] pack(String password, byte[] keystore) {
        byte[] pwd = password.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(2 + pwd.length + keystore.length).putShort((short) pwd.length).put(pwd).put(keystore).array();
    }

    // ---------- integração dos bancos ----------

    @Override
    public Optional<BankIntegration> findBankIntegration(UUID bankId) {
        return jdbc.query("SELECT * FROM bank_integrations WHERE bank_id = ?", (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            byte[] secret = rs.getBytes("callback_auth_config_enc");
            Array ips = rs.getArray("allowed_ips");
            return BankIntegration.restore(id, bankId, rs.getString("callback_url"),
                    BankIntegration.CallbackAuth.valueOf(rs.getString("callback_auth_type")),
                    secret == null ? null : decrypt(secret, id + ":callback"),
                    ips == null ? List.of() : Arrays.asList((String[]) ips.getArray()),
                    "ACTIVE".equals(rs.getString("status")));
        }, bankId).stream().findFirst();
    }

    @Override
    public void save(BankIntegration b) {
        jdbc.update("""
                INSERT INTO bank_integrations (id, bank_id, callback_url, callback_auth_type, callback_auth_config_enc,
                    allowed_ips, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?::text[], ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET callback_url = EXCLUDED.callback_url,
                    callback_auth_type = EXCLUDED.callback_auth_type, callback_auth_config_enc = EXCLUDED.callback_auth_config_enc,
                    allowed_ips = EXCLUDED.allowed_ips, status = EXCLUDED.status, updated_by = EXCLUDED.updated_by,
                    updated_at = now(), version = bank_integrations.version + 1""",
                b.id(), b.bankId(), b.callbackUrl(), b.callbackAuth().name(),
                b.callbackSecret() == null ? null : encrypt(b.callbackSecret(), b.id() + ":callback"),
                "{" + String.join(",", b.allowedIps()) + "}", b.active() ? "ACTIVE" : "INACTIVE", actor(), actor());
    }

    // ---------- apoio ----------

    private byte[] encrypt(String value, String context) {
        return value == null ? null : cipher.encrypt(value.getBytes(StandardCharsets.UTF_8), context.getBytes(StandardCharsets.UTF_8));
    }

    private String decrypt(byte[] value, String context) {
        return value == null ? null : new String(cipher.decrypt(value, context.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private String toJson(Map<String, String> map) {
        try {
            return json.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> fromJson(String value) {
        try {
            return json.readValue(value, new TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Credenciais ilegíveis", e);
        }
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static UUID actor() {
        return HubSecurity.currentPrincipal().filter(p -> !p.apiClient()).map(p -> {
            try {
                return UUID.fromString(p.subject());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).orElse(null);
    }

    private static UUID actorOrHub() {
        UUID actor = actor();
        return actor == null ? HUB : actor;
    }
}
