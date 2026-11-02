package br.com.hubregistro.gateway.ratelimit;

import br.com.hubregistro.gateway.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * Limite de requisições em janela fixa de um minuto, contado no Redis:
 * <ul>
 *   <li>cliente de API: o limite da própria credencial (claim {@code rl});</li>
 *   <li>usuário do portal: {@code hub.gateway.rate-limit.user-per-minute};</li>
 *   <li>sem token (login, redefinição, token OAuth): por IP, {@code anonymous-per-minute}.</li>
 * </ul>
 * Se o Redis estiver fora, a requisição passa (falha aberta) e um aviso é registrado: indisponibilidade do
 * limitador não pode derrubar a recepção de contratos. O bloqueio de conta no auth-service continua valendo.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RequestCounter counter;
    private final GatewayProperties.RateLimit config;
    private final Clock clock;

    @Autowired
    public RateLimitFilter(RequestCounter counter, GatewayProperties properties) {
        this(counter, properties, Clock.systemUTC());
    }

    RateLimitFilter(RequestCounter counter, GatewayProperties properties, Clock clock) {
        this.counter = counter;
        this.config = properties.getRateLimit();
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!config.isEnabled()) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(principal -> limitFor(((JwtAuthenticationToken) principal).getToken()))
                .defaultIfEmpty(anonymousLimit(exchange))
                .flatMap(limit -> apply(exchange, chain, limit));
    }

    private Mono<Void> apply(ServerWebExchange exchange, GatewayFilterChain chain, Limit limit) {
        long minute = clock.millis() / WINDOW.toMillis();
        String key = "rl:" + limit.key() + ":" + minute;
        return counter.increment(key, WINDOW.plusSeconds(10))
                .map(count -> {
                    exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(limit.perMinute()));
                    exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit.perMinute() - count)));
                    return count <= limit.perMinute();
                })
                .onErrorResume(e -> {
                    log.warn("Limitador indisponível; requisição liberada: {}", e.toString());
                    return Mono.just(true);
                })
                .defaultIfEmpty(true)
                .flatMap(allowed -> allowed ? chain.filter(exchange) : tooManyRequests(exchange.getResponse()));
    }

    private Mono<Void> tooManyRequests(ServerHttpResponse response) {
        long secondsToNextWindow = WINDOW.toSeconds() - (clock.millis() / 1000) % WINDOW.toSeconds();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set("Retry-After", String.valueOf(secondsToNextWindow));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Limite de requisições por minuto excedido\",\"code\":\"rate_limited\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    Limit limitFor(Jwt jwt) {
        if ("client".equals(jwt.getClaimAsString("token_use"))) {
            Object claim = jwt.getClaims().get("rl");
            int perMinute = claim instanceof Number n ? n.intValue() : config.getClientDefaultPerMinute();
            return new Limit("c:" + jwt.getSubject(), perMinute);
        }
        return new Limit("u:" + jwt.getSubject(), config.getUserPerMinute());
    }

    private Limit anonymousLimit(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
        return new Limit("ip:" + ip, config.getAnonymousPerMinute());
    }

    record Limit(String key, int perMinute) {
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
