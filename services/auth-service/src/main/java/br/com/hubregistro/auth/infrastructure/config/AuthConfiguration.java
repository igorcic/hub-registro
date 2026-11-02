package br.com.hubregistro.auth.infrastructure.config;

import br.com.hubregistro.auth.domain.session.SessionPolicy;
import br.com.hubregistro.auth.domain.user.LoginPolicy;
import br.com.hubregistro.commons.crypto.AesGcmCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SessionPolicy sessionPolicy(AuthProperties properties) {
        return new SessionPolicy(properties.getAccessTokenTtl(), properties.getRefreshTokenTtl(), properties.getMaxSession());
    }

    @Bean
    LoginPolicy loginPolicy(AuthProperties properties) {
        return new LoginPolicy(properties.getMaxFailedAttempts(), properties.getLockDuration());
    }

    /** Falha a inicialização se a chave não estiver configurada: nunca há chave padrão embutida no código. */
    @Bean
    AesGcmCipher authCipher(AuthProperties properties) {
        if (properties.getEncryptionKey() == null || properties.getEncryptionKey().isBlank()) {
            throw new IllegalStateException(
                    "Configure HUB_AUTH_ENCRYPTION_KEY (32 bytes em Base64, ex.: openssl rand -base64 32)");
        }
        return AesGcmCipher.fromBase64Key(properties.getEncryptionKey());
    }
}
