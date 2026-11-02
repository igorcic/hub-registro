package br.com.hubregistro.auth;

import br.com.hubregistro.auth.application.port.AccountMailer;
import br.com.hubregistro.auth.domain.reset.TokenPurpose;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.test.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Fluxos de autenticação e isolamento entre instituições, ponta a ponta pela API, com PostgreSQL real e os
 * usuários de desenvolvimento (senha Hub@dev2026!). O envio de e-mail é substituído para capturar os links.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.liquibase.contexts=default,dev",
        "hub.auth.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Import(PostgresTestcontainersConfiguration.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class AuthFlowIT {

    private static final String PASSWORD = "Hub@dev2026!";
    private static final String BANK_EXEMPLO = "01900000-0000-7000-8000-000000000101";
    private static final String BANK_TESTE = "01900000-0000-7000-8000-000000000102";
    private static final String BANK_TESTE_ADMIN_ID = "01900000-0000-7000-8000-000000001201";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AccountMailer mailer;

    // ---------- login e sessão ----------

    @Test
    void loginReturnsTokensAndMeDescribesTheCaller() {
        Map tokens = login("superadmin@hub.dev", PASSWORD);

        assertThat(tokens.get("tokenType")).isEqualTo("Bearer");
        assertThat(((Number) tokens.get("expiresIn")).longValue()).isEqualTo(900);
        Map me = get("/api/v1/auth/me", (String) tokens.get("accessToken")).getBody();
        assertThat(me.get("fullName")).isEqualTo("Super Admin");
        assertThat((Map) me.get("institution")).containsEntry("type", "HUB");
        assertThat((List<String>) me.get("roles")).containsExactly("SUPER_ADMIN");
        assertThat((List<String>) me.get("permissions")).contains("user:manage-any", "contract:reprocess");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() {
        ResponseEntity<Map> wrongPassword = post("/api/v1/auth/login", Map.of("email", "auditor@hub.dev", "password", "errada-123456"), null);
        ResponseEntity<Map> unknownEmail = post("/api/v1/auth/login", Map.of("email", "ninguem@hub.dev", "password", "errada-123456"), null);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody().get("code")).isEqualTo("invalid_credentials");
        assertThat(unknownEmail.getBody().get("detail")).isEqualTo(wrongPassword.getBody().get("detail"));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() {
        String email = "consulta@registradora-exemplo.dev";
        for (int i = 0; i < 5; i++) {
            post("/api/v1/auth/login", Map.of("email", email, "password", "errada-" + i + "-abcdef"), null);
        }

        ResponseEntity<Map> withRightPassword = post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null);

        assertThat(withRightPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT locked_until > now() FROM users WHERE email = ?", Boolean.class, email)).isTrue();
    }

    @Test
    void refreshRotatesTokensAndReusingAnOldOneEndsTheWholeSession() {
        Map first = login("operador@hub.dev", PASSWORD);
        String firstRefresh = (String) first.get("refreshToken");

        ResponseEntity<Map> second = post("/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh), null);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secondRefresh = (String) second.getBody().get("refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        ResponseEntity<Map> replay = post("/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh), null);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> afterReplay = post("/api/v1/auth/refresh", Map.of("refreshToken", secondRefresh), null);
        assertThat(afterReplay.getStatusCode()).as("a família inteira foi revogada").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'user.session.reuse-detected'", Integer.class)).isPositive();
    }

    @Test
    void logoutEndsTheSession() {
        String refresh = (String) login("operador@banco-exemplo.dev", PASSWORD).get("refreshToken");

        assertThat(post("/api/v1/auth/logout", Map.of("refreshToken", refresh), null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointsRequireAToken() {
        ResponseEntity<Map> response = get("/api/v1/users", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void jwksPublishesThePublicSigningKeyOnly() {
        Map jwks = get("/oauth2/jwks", null).getBody();
        Map key = (Map) ((List) jwks.get("keys")).getFirst();

        assertThat(key).containsEntry("kty", "RSA").containsEntry("alg", "RS256").containsKeys("kid", "n", "e");
        assertThat(key).doesNotContainKeys("d", "p", "q");
    }

    @Test
    void securityEventsAreWrittenToTheOutbox() {
        login("auditor@hub.dev", PASSWORD);

        Integer events = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'user.login.succeeded' AND exchange = 'hub.platform.events'",
                Integer.class);
        assertThat(events).isPositive();
    }

    // ---------- isolamento entre instituições ----------

    @Test
    void bankAdminSeesOnlyUsersOfItsOwnBank() {
        String token = accessToken("admin@banco-exemplo.dev");

        Map page = get("/api/v1/users?institutionId=" + BANK_TESTE, token).getBody();
        List<Map> items = (List<Map>) page.get("items");

        assertThat(items).isNotEmpty().allSatisfy(u -> assertThat(u.get("institutionId")).isEqualTo(BANK_EXEMPLO));
        assertThat(get("/api/v1/users/" + BANK_TESTE_ADMIN_ID, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void bankAdminCannotCreateUsersInAnotherBank() {
        ResponseEntity<Map> response = post("/api/v1/users", Map.of(
                "institutionId", BANK_TESTE, "fullName", "Intruso", "email", "intruso@banco-teste.dev",
                "roles", List.of("BANK_VIEWER")), accessToken("admin@banco-exemplo.dev"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void bankAdminCannotGrantHubRoles() {
        ResponseEntity<Map> response = post("/api/v1/users", Map.of(
                "fullName", "Escalada", "email", "escalada@banco-exemplo.dev", "roles", List.of("SUPER_ADMIN")),
                accessToken("admin@banco-exemplo.dev"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code")).isEqualTo("role_not_allowed");
    }

    @Test
    void viewersAndAuditorsCannotManageUsers() {
        Map<String, Object> body = Map.of("fullName", "X", "email", "x@banco-exemplo.dev", "roles", List.of("BANK_VIEWER"));

        assertThat(post("/api/v1/users", body, accessToken("consulta@banco-exemplo.dev")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/api/v1/users", body, accessToken("auditor@hub.dev")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void hubAuditorReadsUsersOfEveryInstitution() {
        Map page = get("/api/v1/users?size=100", accessToken("auditor@hub.dev")).getBody();

        assertThat((List<Map>) page.get("items")).extracting(u -> u.get("institutionId")).contains(BANK_EXEMPLO, BANK_TESTE);
    }

    @Test
    void adminCannotChangeOwnRoles() {
        String token = accessToken("admin@banco-teste.dev");

        ResponseEntity<Map> response = exchange(HttpMethod.PUT, "/api/v1/users/" + BANK_TESTE_ADMIN_ID + "/roles",
                Map.of("roles", List.of("BANK_VIEWER")), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------- ativação e redefinição por e-mail ----------

    @Test
    void newUserActivatesTheAccountThroughTheEmailLink() {
        ResponseEntity<Map> created = post("/api/v1/users", Map.of(
                "fullName", "Nova Operadora", "email", "nova.operadora@banco-exemplo.dev", "roles", List.of("BANK_OPERATOR")),
                accessToken("admin@banco-exemplo.dev"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status")).isEqualTo("PENDING_ACTIVATION");
        assertThat(post("/api/v1/auth/login", Map.of("email", "nova.operadora@banco-exemplo.dev", "password", PASSWORD), null)
                .getStatusCode()).as("sem ativação não entra").isEqualTo(HttpStatus.UNAUTHORIZED);

        String link = capturedLink("nova.operadora@banco-exemplo.dev", TokenPurpose.ACTIVATION);
        assertThat(post("/api/v1/auth/password-reset/confirm", Map.of("token", link, "newPassword", "minha senha nova e longa"), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login("nova.operadora@banco-exemplo.dev", "minha senha nova e longa")).containsKey("accessToken");
        assertThat(post("/api/v1/auth/password-reset/confirm", Map.of("token", link, "newPassword", "outra senha longa demais"), null)
                .getStatusCode()).as("link de uso único").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordResetAnswersTheSameForUnknownEmails() {
        ResponseEntity<Map> response = post("/api/v1/auth/password-reset/request", Map.of("email", "nao-existe@x.dev"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void weakPasswordIsRejectedAndTheLinkStaysUsable() {
        post("/api/v1/auth/password-reset/request", Map.of("email", "operador@registradora-exemplo.dev"), null);
        String link = capturedLink("operador@registradora-exemplo.dev", TokenPurpose.RESET);

        ResponseEntity<Map> weak = post("/api/v1/auth/password-reset/confirm", Map.of("token", link, "newPassword", "curta"), null);
        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(weak.getBody().get("code")).isEqualTo("weak_password");

        assertThat(post("/api/v1/auth/password-reset/confirm", Map.of("token", link, "newPassword", "registradora segura 2026"), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ---------- credenciais de API dos bancos ----------

    @Test
    void bankSystemObtainsAScopedTokenWithClientCredentials() {
        ResponseEntity<Map> created = post("/api/v1/api-clients", Map.of(
                "name", "ERP do banco", "scopes", List.of("contract:read", "contract:submit"), "rateLimitPerMinute", 120),
                accessToken("admin@banco-exemplo.dev"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String clientId = (String) ((Map) created.getBody().get("client")).get("clientId");
        String secret = (String) created.getBody().get("clientSecret");

        ResponseEntity<Map> token = clientToken(clientId, secret, "contract:read");
        assertThat(token.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(token.getBody()).containsEntry("token_type", "Bearer").containsEntry("expires_in", 900);

        Map me = get("/api/v1/auth/me", (String) token.getBody().get("access_token")).getBody();
        assertThat(me.get("apiClient")).isEqualTo(true);
        assertThat((Map) me.get("institution")).containsEntry("id", BANK_EXEMPLO);
        assertThat((List<String>) me.get("permissions")).containsExactly("contract:read");

        assertThat(clientToken(clientId, "secret-errado", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(clientToken(clientId, secret, "user:manage").getBody()).containsEntry("error", "invalid_scope");
    }

    @Test
    void apiClientTokenCannotManageUsers() {
        ResponseEntity<Map> created = post("/api/v1/api-clients", Map.of("name", "Integração", "scopes", List.of("contract:submit")),
                accessToken("admin@banco-exemplo.dev"));
        Map client = (Map) created.getBody().get("client");
        String token = (String) clientToken((String) client.get("clientId"), (String) created.getBody().get("clientSecret"), null)
                .getBody().get("access_token");

        assertThat(get("/api/v1/users", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registriesCannotHaveApiClients() {
        ResponseEntity<Map> response = post("/api/v1/api-clients", Map.of("name", "X", "scopes", List.of("contract:read")),
                accessToken("admin@registradora-exemplo.dev"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------- apoio ----------

    private Map login(String email, String password) {
        ResponseEntity<Map> response = post("/api/v1/auth/login", Map.of("email", email, "password", password), null);
        assertThat(response.getStatusCode()).as("login de " + email).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String accessToken(String email) {
        return (String) login(email, PASSWORD).get("accessToken");
    }

    private String capturedLink(String email, TokenPurpose purpose) {
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(2000).atLeastOnce()).sendPasswordLink(user.capture(), eq(purpose), token.capture());
        for (int i = user.getAllValues().size() - 1; i >= 0; i--) {
            if (user.getAllValues().get(i).email().value().equals(email)) {
                return token.getAllValues().get(i);
            }
        }
        throw new AssertionError("Nenhum link enviado para " + email);
    }

    private ResponseEntity<Map> clientToken(String clientId, String secret, String scope) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, secret);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        if (scope != null) {
            form.add("scope", scope);
        }
        return http.postForEntity("/oauth2/token", new HttpEntity<>(form, headers), Map.class);
    }

    private ResponseEntity<Map> get(String path, String bearer) {
        return exchange(HttpMethod.GET, path, null, bearer);
    }

    private ResponseEntity<Map> post(String path, Object body, String bearer) {
        return exchange(HttpMethod.POST, path, body, bearer);
    }

    private ResponseEntity<Map> exchange(HttpMethod method, String path, Object body, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }
}
