package br.com.hubregistro.gateway;

import br.com.hubregistro.gateway.ratelimit.RequestCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Os serviços de destino não estão no ar: requisição autorizada chega ao roteamento e falha lá (5xx), nunca 401. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    @Autowired
    private WebTestClient web;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @MockitoBean
    private RequestCounter counter;

    @BeforeEach
    void setUp() {
        when(counter.increment(anyString(), any(Duration.class))).thenReturn(Mono.just(1L));
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("inválido")));
        when(jwtDecoder.decode("valido")).thenReturn(Mono.just(Jwt.withTokenValue("valido").header("alg", "RS256")
                .subject("user-1").claim("token_use", "user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build()));
    }

    @Test
    void apiWithoutTokenIsRejectedAtTheEdge() {
        web.get().uri("/api/v1/contracts").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.code").isEqualTo("unauthorized");
    }

    @Test
    void invalidTokenIsRejectedAtTheEdge() {
        web.get().uri("/api/v1/contracts").header("Authorization", "Bearer forjado").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validTokenIsForwarded() {
        HttpStatusCode status = web.get().uri("/api/v1/contracts").header("Authorization", "Bearer valido")
                .exchange().returnResult(String.class).getStatus();

        assertThat(status.is5xxServerError()).as("passou pela segurança e tentou o serviço").isTrue();
    }

    @Test
    void loginIsPublic() {
        HttpStatusCode status = web.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.c\",\"password\":\"x\"}").exchange().returnResult(String.class).getStatus();

        assertThat(status.value()).isNotEqualTo(401);
    }

    @Test
    void gatewayManagementEndpointsAreNotExposed() {
        web.get().uri("/actuator/gateway/routes").header("Authorization", "Bearer valido").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void healthIsPublic() {
        web.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void exceededLimitReturns429() {
        when(counter.increment(anyString(), any(Duration.class))).thenReturn(Mono.just(10_000L));

        web.get().uri("/api/v1/contracts").header("Authorization", "Bearer valido").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists("Retry-After");
    }

    @Test
    void corsPreflightFromThePortalIsAllowed() {
        web.options().uri("/api/v1/contracts")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:4200");
    }

    @Test
    void corsFromUnknownOriginIsRefused() {
        web.options().uri("/api/v1/contracts")
                .header("Origin", "https://malicioso.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden();
    }
}
