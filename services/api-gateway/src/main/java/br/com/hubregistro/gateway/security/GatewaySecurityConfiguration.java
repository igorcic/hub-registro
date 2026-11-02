package br.com.hubregistro.gateway.security;

import br.com.hubregistro.gateway.GatewayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Primeira barreira: rejeita token ausente, inválido ou expirado antes de qualquer serviço ser chamado.
 * Cada serviço valida o token de novo e decide a autorização fina (defesa em profundidade).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewayProperties.class)
class GatewaySecurityConfiguration {

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http, GatewayProperties properties) {
        String[] publicPaths = properties.getPublicPaths().toArray(String[]::new);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsSource(properties)))
                .authorizeExchange(auth -> {
                    auth.pathMatchers(HttpMethod.OPTIONS).permitAll();
                    auth.pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll();
                    if (publicPaths.length > 0) {
                        auth.pathMatchers(publicPaths).permitAll();
                    }
                    auth.pathMatchers("/api/**").authenticated();
                    // Tudo o mais (inclusive /actuator/gateway) não é exposto.
                    auth.anyExchange().denyAll();
                })
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> { })
                        .authenticationEntryPoint(unauthorized())
                        .accessDeniedHandler(forbidden()))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized()).accessDeniedHandler(forbidden()))
                .build();
    }

    private static CorsConfigurationSource corsSource(GatewayProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getCorsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("X-Correlation-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        source.registerCorsConfiguration("/oauth2/token", cors);
        return source;
    }

    private static ServerAuthenticationEntryPoint unauthorized() {
        return (exchange, e) -> {
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
            return problem(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "unauthorized", "Token ausente, inválido ou expirado");
        };
    }

    private static ServerAccessDeniedHandler forbidden() {
        return (exchange, e) -> problem(exchange.getResponse(), HttpStatus.FORBIDDEN, "forbidden", "Acesso não permitido");
    }

    static Mono<Void> problem(ServerHttpResponse response, HttpStatus status, String code, String detail) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"code\":\"%s\"}"
                .formatted(status.getReasonPhrase(), status.value(), detail, code);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
