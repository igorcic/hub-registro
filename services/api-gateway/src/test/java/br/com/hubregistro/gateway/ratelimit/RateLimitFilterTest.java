package br.com.hubregistro.gateway.ratelimit;

import br.com.hubregistro.gateway.GatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final Map<String, Long> counts = new HashMap<>();
    private final RequestCounter memoryCounter = (key, window) -> Mono.just(counts.merge(key, 1L, Long::sum));
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T12:00:30Z"), ZoneOffset.UTC);
    private final GatewayProperties properties = new GatewayProperties();

    @Test
    void apiClientUsesTheLimitOfItsOwnCredential() {
        RateLimitFilter filter = new RateLimitFilter(memoryCounter, properties, clock);
        AtomicInteger forwarded = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            ServerWebExchange exchange = exchangeWith(clientToken("hub_erp", 2));
            filter.filter(exchange, ex -> { forwarded.incrementAndGet(); return Mono.empty(); }).block();
            if (i == 2) {
                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("30");
            }
        }

        assertThat(forwarded).hasValue(2);
    }

    @Test
    void usersAndClientsAreCountedSeparately() {
        RateLimitFilter filter = new RateLimitFilter(memoryCounter, properties, clock);

        filter.filter(exchangeWith(clientToken("hub_erp", 10)), ex -> Mono.empty()).block();
        filter.filter(exchangeWith(userToken("user-1")), ex -> Mono.empty()).block();

        assertThat(counts.keySet()).anyMatch(k -> k.startsWith("rl:c:hub_erp:")).anyMatch(k -> k.startsWith("rl:u:user-1:"));
    }

    @Test
    void anonymousRequestsAreLimitedPerIp() {
        properties.getRateLimit().setAnonymousPerMinute(1);
        RateLimitFilter filter = new RateLimitFilter(memoryCounter, properties, clock);

        ServerWebExchange first = anonymousFrom("203.0.113.7");
        ServerWebExchange second = anonymousFrom("203.0.113.7");
        ServerWebExchange otherIp = anonymousFrom("198.51.100.1");
        filter.filter(first, ex -> Mono.empty()).block();
        filter.filter(second, ex -> Mono.empty()).block();
        filter.filter(otherIp, ex -> Mono.empty()).block();

        assertThat(first.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(otherIp.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void failsOpenWhenTheCounterIsUnavailable() {
        RequestCounter broken = (key, window) -> Mono.error(new IllegalStateException("redis fora"));
        RateLimitFilter filter = new RateLimitFilter(broken, properties, clock);
        AtomicInteger forwarded = new AtomicInteger();

        filter.filter(exchangeWith(userToken("u")), ex -> { forwarded.incrementAndGet(); return Mono.empty(); }).block();

        assertThat(forwarded).hasValue(1);
    }

    private static ServerWebExchange exchangeWith(Principal principal) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/contracts")).mutate()
                .principal(Mono.just(principal)).build();
    }

    private static ServerWebExchange anonymousFrom(String ip) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress(ip, 40000)));
    }

    private static JwtAuthenticationToken clientToken(String clientId, int limit) {
        return new JwtAuthenticationToken(jwt(clientId, Map.of("token_use", "client", "rl", limit)));
    }

    private static JwtAuthenticationToken userToken(String userId) {
        return new JwtAuthenticationToken(jwt(userId, Map.of("token_use", "user")));
    }

    private static Jwt jwt(String subject, Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
