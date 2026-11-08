package br.com.hubregistro.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** O serviço em teste passa a confiar nos tokens de {@link TestTokens} (em vez do JWKS do auth-service). */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

    @Bean
    JwtDecoder testJwtDecoder() {
        return TestTokens.decoder();
    }
}
