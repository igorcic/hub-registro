package br.com.hubregistro.test;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tokens de teste com as mesmas claims, issuer e audiência do auth-service, assinados por uma chave gerada
 * para os testes. Use junto com {@link TestSecurityConfiguration}, que faz o serviço confiar nessa chave.
 */
public final class TestTokens {

    public static final String ISSUER = "https://auth.hub-registro.local";
    public static final String AUDIENCE = "hub-registro";
    public static final UUID HUB = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private static final RSAKey KEY = generateKey();
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));

    private TestTokens() {
    }

    public static JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(AUDIENCE))));
            return decoder;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String user(UUID userId, UUID institutionId, String institutionType, Set<String> roles, Set<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER).audience(List.of(AUDIENCE)).subject(userId.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(600)).id(UUID.randomUUID().toString())
                .claim("inst", institutionId.toString()).claim("inst_type", institutionType)
                .claim("token_use", "user").claim("roles", List.copyOf(roles)).claim("perms", List.copyOf(permissions))
                .claim("email", "teste@hub.dev").claim("name", "Usuário de teste")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // ---------- perfis usados nos testes (permissões iguais às da migração auth-009) ----------

    public static String superAdmin() {
        return user(UUID.randomUUID(), HUB, "HUB", Set.of("SUPER_ADMIN"), Set.of(
                "bank:read", "bank:manage", "registry:read", "registry:manage", "integration:read", "integration:manage",
                "user:read", "user:manage-any", "routing-rule:read", "routing-rule:manage", "contract:read", "operations:read"));
    }

    public static String hubOperator() {
        return user(UUID.randomUUID(), HUB, "HUB", Set.of("HUB_OPERATOR"),
                Set.of("bank:read", "registry:read", "integration:read", "contract:read", "operations:read"));
    }

    public static String bankAdmin(UUID bankId) {
        return user(UUID.randomUUID(), bankId, "BANK", Set.of("BANK_ADMIN"),
                Set.of("bank:read", "user:read", "user:manage", "integration:read", "integration:manage", "contract:read"));
    }

    public static String bankViewer(UUID bankId) {
        return user(UUID.randomUUID(), bankId, "BANK", Set.of("BANK_VIEWER"), Set.of("bank:read", "contract:read"));
    }

    public static String registryAdmin(UUID registryId) {
        return user(UUID.randomUUID(), registryId, "REGISTRY", Set.of("REGISTRY_ADMIN"),
                Set.of("registry:read", "integration:read", "integration:manage", "contract:read"));
    }

    public static String registryViewer(UUID registryId) {
        return user(UUID.randomUUID(), registryId, "REGISTRY", Set.of("REGISTRY_VIEWER"), Set.of("registry:read", "contract:read"));
    }

    private static RSAKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID("test-key").build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
