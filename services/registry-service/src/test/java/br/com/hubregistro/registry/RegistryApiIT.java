package br.com.hubregistro.registry;

import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import br.com.hubregistro.test.TestSecurityConfiguration;
import br.com.hubregistro.test.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "hub.messaging.relay.enabled=false")
@Import({PostgresTestcontainersConfiguration.class, TestSecurityConfiguration.class})
@SuppressWarnings({"rawtypes", "unchecked"})
class RegistryApiIT {

    private static final Map<String, Object> ADDRESS = Map.of("street", "Rua da Quitanda", "number", "50",
            "district", "Centro", "city", "Rio de Janeiro", "ibgeCode", "3304557", "uf", "RJ", "zipCode", "20011-030");

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void lifecycleFromRegistrationToActivation() {
        UUID id = create("REG_LIFE");
        String admin = TestTokens.superAdmin();

        ResponseEntity<Map> early = call(HttpMethod.PATCH, "/api/v1/registries/" + id + "/status",
                Map.of("status", "ACTIVE", "reason", "Liberada"), admin);
        assertThat(early.getBody()).containsEntry("code", "coverage_required");

        ResponseEntity<Map> coverage = call(HttpMethod.PUT, "/api/v1/registries/" + id + "/coverage", Map.of(
                "contractTypes", List.of("residential", "commercial"),
                "serviceAreas", List.of(Map.of("uf", "RJ"), Map.of("uf", "SP", "ibgeCode", "3550308"))), admin);
        assertThat(coverage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List) coverage.getBody().get("contractTypes")).containsExactly("COMMERCIAL", "RESIDENTIAL");

        ResponseEntity<Map> activated = call(HttpMethod.PATCH, "/api/v1/registries/" + id + "/status",
                Map.of("status", "ACTIVE", "reason", "Integração homologada"), admin);
        assertThat(activated.getBody()).containsEntry("status", "ACTIVE");

        List<String> events = jdbc.queryForList(
                "SELECT event_type FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at", String.class, id.toString());
        assertThat(events).containsExactly("registry.created", "registry.updated", "registry.status.changed");
        String areas = jdbc.queryForObject("""
                SELECT payload->'data'->'serviceAreas' FROM outbox_events
                WHERE aggregate_id = ? AND event_type = 'registry.status.changed'""", String.class, id.toString());
        assertThat(areas).contains("3550308").contains("\"RJ\"");
    }

    @Test
    void registryUsersSeeOnlyTheirOwnRegistry() {
        UUID own = create("REG_OWN");
        UUID other = create("REG_OTHER");
        String token = TestTokens.registryViewer(own);

        Map page = call(HttpMethod.GET, "/api/v1/registries", null, token).getBody();
        assertThat((List<Map>) page.get("items")).extracting(r -> r.get("id")).containsExactly(own.toString());
        assertThat(call(HttpMethod.GET, "/api/v1/registries/" + other, null, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/api/v1/registries", null, TestTokens.bankAdmin(UUID.randomUUID())).getStatusCode())
                .as("banco não consulta registradoras").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registryAdminMaintainsTechnicalContactsButNotCoverage() {
        UUID own = create("REG_ADMIN");
        String token = TestTokens.registryAdmin(own);

        ResponseEntity<Map> contacts = call(HttpMethod.PUT, "/api/v1/registries/" + own + "/technical-contacts", Map.of(
                "technicalContacts", List.of(Map.of("name", "Eva", "email", "eva@reg.dev", "primary", true))), token);
        assertThat(contacts.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(call(HttpMethod.PUT, "/api/v1/registries/" + own + "/coverage",
                Map.of("contractTypes", List.of("RESIDENTIAL"), "serviceAreas", List.of(Map.of("uf", "SP"))), token)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void hubCanFilterByContractTypeAndState() {
        UUID id = create("REG_FILTER");
        call(HttpMethod.PUT, "/api/v1/registries/" + id + "/coverage",
                Map.of("contractTypes", List.of("RURAL"), "serviceAreas", List.of(Map.of("uf", "MT"))), TestTokens.superAdmin());

        Map page = call(HttpMethod.GET, "/api/v1/registries?contractType=rural&uf=mt", null, TestTokens.hubOperator()).getBody();

        assertThat((List<Map>) page.get("items")).extracting(r -> r.get("id")).containsExactly(id.toString());
    }

    private UUID create(String code) {
        String cnpj = randomCnpj();
        ResponseEntity<Map> response = call(HttpMethod.POST, "/api/v1/registries", Map.of(
                "legalName", "Registradora " + code + " Ltda.", "tradeName", "Registradora " + code, "cnpj", cnpj,
                "code", code, "address", ADDRESS), TestTokens.superAdmin());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("status", "INACTIVE");
        return UUID.fromString((String) response.getBody().get("id"));
    }

    private static String randomCnpj() {
        String base = UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 8) + "0001";
        int d1 = digit(base, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int d2 = digit(base + d1, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return base + d1 + d2;
    }

    private static int digit(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (value.charAt(i) - '0') * weights[i];
        }
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }
}
