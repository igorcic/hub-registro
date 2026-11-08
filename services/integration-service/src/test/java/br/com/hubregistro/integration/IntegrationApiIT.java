package br.com.hubregistro.integration;

import br.com.hubregistro.integration.application.HealthMonitor;
import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import br.com.hubregistro.test.TestSecurityConfiguration;
import br.com.hubregistro.test.TestTokens;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração com uma "registradora" local (HttpServer do JDK) que responde conforme o caminho, e certificado real
 * gerado com o keytool do JDK.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "hub.integration.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "hub.messaging.relay.enabled=false",
        "hub.integration.health-check.initial-delay=1h"
})
@Import({PostgresTestcontainersConfiguration.class, TestSecurityConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings({"rawtypes", "unchecked"})
class IntegrationApiIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HealthMonitor monitor;

    private HttpServer registry;
    private final AtomicInteger healthStatus = new AtomicInteger(200);
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    @BeforeAll
    void startFakeRegistry() throws IOException {
        registry = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        registry.createContext("/health", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(healthStatus.get(), -1);
            exchange.close();
        });
        registry.start();
    }

    @AfterAll
    void stopFakeRegistry() {
        registry.stop(0);
    }

    private String baseUrl() {
        return "http://localhost:" + registry.getAddress().getPort();
    }

    @Test
    void configureWithBasicAuthNeverReturnsTheSecretAndKeepsItOnReconfigure() {
        UUID registryId = UUID.randomUUID();
        ResponseEntity<Map> created = configure(registryId, Map.of("username", "hub", "password", "s3nh4"), TestTokens.superAdmin());

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map) created.getBody().get("credentials")).containsOnlyKeys("username");
        assertThat((List) created.getBody().get("configuredSecrets")).containsExactly("password");
        assertThat((String) created.getBody().get("webhookSecret")).as("mostrado só na criação").isNotBlank();
        byte[] stored = jdbc.queryForObject("SELECT auth_config_enc FROM registry_integrations WHERE registry_id = ?", byte[].class, registryId);
        assertThat(new String(stored)).doesNotContain("s3nh4");

        ResponseEntity<Map> again = configure(registryId, Map.of("username", "hub"), TestTokens.superAdmin());
        assertThat(again.getBody().get("webhookSecret")).isNull();
        assertThat(call(HttpMethod.POST, "/api/v1/integrations/registries/" + registryId + "/connectivity-test", null,
                TestTokens.superAdmin()).getBody()).containsEntry("status", "UP");
        assertThat(lastAuthorization.get()).isEqualTo("Basic aHViOnMzbmg0");
    }

    @Test
    void healthMonitorDetectsOutagesAndPublishesTheChange() {
        UUID registryId = UUID.randomUUID();
        configure(registryId, Map.of("username", "hub", "password", "x"), TestTokens.superAdmin());
        assertThat(call(HttpMethod.PATCH, "/api/v1/integrations/registries/" + registryId + "/status", Map.of("active", true),
                TestTokens.superAdmin()).getBody()).containsEntry("active", true);

        healthStatus.set(200);
        monitor.runDueChecks();
        healthStatus.set(503);
        jdbc.update("UPDATE registry_integrations SET last_health_check_at = now() - interval '1 hour' WHERE registry_id = ?", registryId);
        monitor.runDueChecks();

        Map integration = call(HttpMethod.GET, "/api/v1/integrations/registries/" + registryId, null, TestTokens.hubOperator()).getBody();
        assertThat(integration).containsEntry("healthStatus", "DOWN");
        List<String> changes = jdbc.queryForList("""
                SELECT payload->'data'->>'status' FROM outbox_events
                WHERE event_type = 'integration.health.changed' AND payload->'data'->>'registryId' = ? ORDER BY created_at""",
                String.class, registryId.toString());
        assertThat(changes).containsExactly("UP", "DOWN");
        List history = http.exchange("/api/v1/integrations/registries/" + registryId + "/health-checks", HttpMethod.GET,
                new HttpEntity<>(headers(TestTokens.superAdmin())), List.class).getBody();
        assertThat(history).hasSize(2);
        healthStatus.set(200);
    }

    @Test
    void registryManagesOnlyItsOwnIntegration() {
        UUID own = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        configure(other, Map.of("username", "a", "password", "b"), TestTokens.superAdmin());

        assertThat(configure(own, Map.of("username", "a", "password", "b"), TestTokens.registryAdmin(own)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/api/v1/integrations/registries/" + other, null, TestTokens.registryAdmin(own))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(configure(own, Map.of("username", "a", "password", "b"), TestTokens.registryViewer(own)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clientCertificateIsParsedAndStoredEncrypted() throws Exception {
        UUID registryId = UUID.randomUUID();
        configure(registryId, Map.of("username", "a", "password", "b"), TestTokens.superAdmin());
        byte[] p12 = selfSignedPkcs12("changeit");

        ResponseEntity<Map> uploaded = upload(registryId, p12, "cliente-hub", "CLIENT_AUTH", "changeit");
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) uploaded.getBody().get("subject")).contains("CN=hub-registro-teste");
        assertThat((String) uploaded.getBody().get("sha256Fingerprint")).hasSize(64);

        assertThat(upload(registryId, p12, "outro", "CLIENT_AUTH", "senha-errada").getBody())
                .containsEntry("code", "invalid_certificate");
        List list = http.exchange("/api/v1/integrations/registries/" + registryId + "/certificates", HttpMethod.GET,
                new HttpEntity<>(headers(TestTokens.superAdmin())), List.class).getBody();
        assertThat(list).hasSize(1);
    }

    @Test
    void bankConfiguresItsCallbackAndAllowedIps() {
        UUID bank = UUID.randomUUID();
        ResponseEntity<Map> response = call(HttpMethod.PUT, "/api/v1/integrations/banks/" + bank, Map.of(
                "callbackUrl", "https://erp.banco.com.br/hub/callback", "callbackAuth", "HMAC", "callbackSecret", "segredo",
                "allowedIps", List.of("200.10.20.0/24")), TestTokens.bankAdmin(bank));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("callbackSecretConfigured", true).containsEntry("active", true)
                .doesNotContainKey("callbackSecret");
        assertThat(call(HttpMethod.GET, "/api/v1/integrations/banks/" + bank, null, TestTokens.bankAdmin(UUID.randomUUID()))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Map> configure(UUID registryId, Map<String, String> credentials, String token) {
        return call(HttpMethod.PUT, "/api/v1/integrations/registries/" + registryId, Map.of(
                "adapterType", "generic_rest", "baseUrl", baseUrl(), "authType", "BASIC", "credentials", credentials,
                "connectTimeoutMs", 1000, "readTimeoutMs", 2000,
                "endpoints", Map.of("SUBMIT_CONTRACT", Map.of("method", "POST", "path", "/contracts"),
                        "HEALTH_CHECK", Map.of("method", "GET", "path", "/health"))), token);
    }

    private ResponseEntity<Map> upload(UUID registryId, byte[] content, String alias, String purpose, String password) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "cert.p12";
            }
        });
        form.add("alias", alias);
        form.add("purpose", purpose);
        form.add("password", password);
        HttpHeaders headers = headers(TestTokens.superAdmin());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/v1/integrations/registries/" + registryId + "/certificates", HttpMethod.POST,
                new HttpEntity<>(form, headers), Map.class);
    }

    /** Gera um PKCS#12 autoassinado com o keytool do próprio JDK que roda os testes. */
    private static byte[] selfSignedPkcs12(String password) throws Exception {
        Path dir = Files.createTempDirectory("cert");
        Path file = dir.resolve("cert.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "hub", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "365", "-dname", "CN=hub-registro-teste, O=Hub, C=BR", "-storetype", "PKCS12",
                "-keystore", file.toString(), "-storepass", password, "-keypass", password)
                .redirectErrorStream(true).start();
        assertThat(process.waitFor()).as(new String(process.getInputStream().readAllBytes())).isZero();
        return Files.readAllBytes(file);
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, Object body, String token) {
        return http.exchange(path, method, new HttpEntity<>(body, headers(token)), Map.class);
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
