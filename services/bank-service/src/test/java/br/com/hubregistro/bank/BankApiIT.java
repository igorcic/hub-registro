package br.com.hubregistro.bank;

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
class BankApiIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void hubAdminRegistersABankAndTheEventGoesToTheOutbox() {
        ResponseEntity<Map> created = create("Banco Delta S.A.", "Banco Delta", "45.ABC.678/0001-66", "banco_delta_" + suffix());

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("cnpj", "45ABC678000166").containsEntry("status", "ACTIVE");
        assertThat((String) created.getBody().get("code")).startsWith("BANCO_DELTA_");
        String id = (String) created.getBody().get("id");
        Map<String, Object> event = jdbc.queryForMap(
                "SELECT event_type, exchange, routing_key, payload->'data'->>'version' AS version FROM outbox_events WHERE aggregate_id = ?", id);
        assertThat(event).containsEntry("event_type", "bank.created").containsEntry("exchange", "hub.platform.events")
                .containsEntry("version", "0");
    }

    @Test
    void cnpjMustBeValidAndUnique() {
        create("Banco Épsilon S.A.", "Banco Épsilon", "33.444.555/0001-81", "banco_epsilon");

        ResponseEntity<Map> duplicate = create("Outro", "Outro", "33444555000181", "outro_banco");
        ResponseEntity<Map> invalid = create("Inválido", "Inválido", "33444555000182", "invalido");

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(duplicate.getBody()).containsEntry("code", "cnpj_in_use");
        assertThat(invalid.getBody()).containsEntry("code", "invalid_cnpj");
    }

    @Test
    void bankUsersSeeOnlyTheirOwnBankAndCannotRegisterBanks() {
        UUID own = idOf(create("Banco Zeta S.A.", "Banco Zeta", randomCnpj(), "banco_zeta"));
        UUID other = idOf(create("Banco Eta S.A.", "Banco Eta", randomCnpj(), "banco_eta"));
        String token = TestTokens.bankAdmin(own);

        Map page = call(HttpMethod.GET, "/api/v1/banks", null, token).getBody();
        assertThat((List<Map>) page.get("items")).extracting(b -> b.get("id")).containsExactly(own.toString());
        assertThat(call(HttpMethod.GET, "/api/v1/banks/" + other, null, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.POST, "/api/v1/banks", Map.of("legalName", "X", "tradeName", "X",
                "cnpj", "11222333000181", "code", "XX"), token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void suspensionRequiresAReasonAndIsRecordedInTheHistory() {
        UUID id = idOf(create("Banco Teta S.A.", "Banco Teta", randomCnpj(), "banco_teta"));
        String admin = TestTokens.superAdmin();

        assertThat(call(HttpMethod.PATCH, "/api/v1/banks/" + id + "/status", Map.of("status", "SUSPENDED", "reason", ""), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> suspended = call(HttpMethod.PATCH, "/api/v1/banks/" + id + "/status",
                Map.of("status", "SUSPENDED", "reason", "Documentação vencida"), admin);
        assertThat(suspended.getBody()).containsEntry("status", "SUSPENDED").containsEntry("statusReason", "Documentação vencida");

        List history = http.exchange("/api/v1/banks/" + id + "/status-history", HttpMethod.GET,
                new HttpEntity<>(headers(admin)), List.class).getBody();
        assertThat(history).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'bank.status.changed'",
                Integer.class, id.toString())).isEqualTo(1);
    }

    @Test
    void bankAdminMaintainsItsOwnContactsOnly() {
        UUID own = idOf(create("Banco Iota S.A.", "Banco Iota", randomCnpj(), "banco_iota"));
        Map<String, Object> contacts = Map.of("contacts", List.of(
                Map.of("name", "Ana", "email", "ana@iota.dev", "area", "TECHNICAL", "primary", true),
                Map.of("name", "Beto", "email", "beto@iota.dev", "area", "BUSINESS", "primary", true)));

        ResponseEntity<Map> updated = call(HttpMethod.PUT, "/api/v1/banks/" + own + "/contacts", contacts, TestTokens.bankAdmin(own));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List) updated.getBody().get("contacts")).hasSize(2);

        ResponseEntity<Map> twoPrimaries = call(HttpMethod.PUT, "/api/v1/banks/" + own + "/contacts", Map.of("contacts", List.of(
                Map.of("name", "Ana", "email", "ana@iota.dev", "area", "TECHNICAL", "primary", true),
                Map.of("name", "Caio", "email", "caio@iota.dev", "area", "TECHNICAL", "primary", true))), TestTokens.bankAdmin(own));
        assertThat(twoPrimaries.getBody()).containsEntry("code", "duplicate_primary_contact");

        assertThat(call(HttpMethod.PUT, "/api/v1/banks/" + own + "/contacts", contacts, TestTokens.bankAdmin(UUID.randomUUID()))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.PUT, "/api/v1/banks/" + own + "/contacts", contacts, TestTokens.bankViewer(own))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<Map> create(String legalName, String tradeName, String cnpj, String code) {
        return call(HttpMethod.POST, "/api/v1/banks",
                Map.of("legalName", legalName, "tradeName", tradeName, "cnpj", cnpj, "code", code), TestTokens.superAdmin());
    }

    /** CNPJ alfanumérico válido e único para cada teste (o banco de teste é compartilhado). */
    static String randomCnpj() {
        String base = (UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 8)) + "0001";
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

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase().replace("-", "");
    }

    private static UUID idOf(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) response.getBody().get("id"));
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
