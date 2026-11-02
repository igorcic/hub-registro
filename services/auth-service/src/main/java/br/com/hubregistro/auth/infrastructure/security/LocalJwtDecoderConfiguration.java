package br.com.hubregistro.auth.infrastructure.security;

import br.com.hubregistro.security.HubClaims;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

/**
 * O auth-service valida os próprios tokens com as chaves locais (sem chamar o próprio JWKS por HTTP).
 * Os demais serviços usam o JWKS via {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}.
 */
@Configuration(proxyBeanMethods = false)
class LocalJwtDecoderConfiguration {

    @Bean
    JwtDecoder jwtDecoder(SigningKeyStore keys) {
        JWKSource<SecurityContext> source = (selector, context) -> selector.select(keys.publicKeys());
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source));
        processor.setJWTClaimsSetVerifier((claims, context) -> {
            // Validação de claims feita pelo Spring Security abaixo.
        });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(HubClaims.ISSUER),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(HubClaims.AUDIENCE))));
        return decoder;
    }
}
