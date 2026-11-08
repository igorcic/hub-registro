package br.com.hubregistro.integration.infrastructure.config;

import br.com.hubregistro.commons.crypto.AesGcmCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class IntegrationConfiguration {

    /** Cifra credenciais, segredos de webhook e certificados. Sem chave configurada, o serviço não sobe. */
    @Bean
    AesGcmCipher integrationCipher(@Value("${hub.integration.encryption-key:}") String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Configure HUB_INTEGRATION_ENCRYPTION_KEY (32 bytes em Base64, ex.: openssl rand -base64 32)");
        }
        return AesGcmCipher.fromBase64Key(key);
    }
}
