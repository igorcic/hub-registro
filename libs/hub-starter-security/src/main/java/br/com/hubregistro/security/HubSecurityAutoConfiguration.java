package br.com.hubregistro.security;

import br.com.hubregistro.security.tenant.RlsDataSourcePostProcessor;
import br.com.hubregistro.security.web.HubExceptionHandler;
import br.com.hubregistro.security.web.ProblemDetailsSecurityHandlers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração padrão de segurança de todo serviço servlet:
 * stateless, só Bearer JWT, tudo exige autenticação exceto health/info/prometheus e os caminhos públicos
 * declarados em {@code hub.security.public-paths}; autorização fina por {@code @PreAuthorize} nos casos de uso.
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(HubSecurityProperties.class)
@EnableMethodSecurity
@Import(HubExceptionHandler.class)
public class HubSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain hubSecurityFilterChain(HttpSecurity http, HubSecurityProperties properties,
                                               ObjectMapper objectMapper) throws Exception {
        ProblemDetailsSecurityHandlers handlers = new ProblemDetailsSecurityHandlers(objectMapper);
        String[] publicPaths = properties.getPublicPaths().toArray(String[]::new);

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error").permitAll();
                    if (publicPaths.length > 0) {
                        auth.requestMatchers(publicPaths).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new HubJwtAuthenticationConverter()))
                        .authenticationEntryPoint(handlers.authenticationEntryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers.authenticationEntryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "hub.security.rls", name = "enabled", havingValue = "true")
    static RlsDataSourcePostProcessor rlsDataSourcePostProcessor() {
        return new RlsDataSourcePostProcessor();
    }
}
