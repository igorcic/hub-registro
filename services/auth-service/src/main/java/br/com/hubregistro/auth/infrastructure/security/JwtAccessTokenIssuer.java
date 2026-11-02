package br.com.hubregistro.auth.infrastructure.security;

import br.com.hubregistro.auth.application.port.AccessTokenIssuer;
import br.com.hubregistro.auth.domain.client.ApiClient;
import br.com.hubregistro.auth.domain.institution.Institution;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.security.HubClaims;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Access tokens RS256 com a instituição, os papéis e as permissões do chamador. */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final SigningKeyStore keys;
    private final NimbusJwtEncoder encoder;
    private final Clock clock;

    JwtAccessTokenIssuer(SigningKeyStore keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
        JWKSource<SecurityContext> source = (selector, context) -> selector.select(new JWKSet(keys.activeSigningKey()));
        this.encoder = new NimbusJwtEncoder(source);
    }

    @Override
    public IssuedToken issueForUser(User user, Institution institution, Set<String> permissions, Duration ttl) {
        JwtClaimsSet.Builder claims = baseClaims(user.id().toString(), institution, ttl)
                .claim(HubClaims.TOKEN_USE, HubClaims.TOKEN_USE_USER)
                .claim(HubClaims.ROLES, List.copyOf(user.roleCodes()))
                .claim(HubClaims.PERMISSIONS, List.copyOf(permissions))
                .claim(HubClaims.EMAIL, user.email().value())
                .claim(HubClaims.NAME, user.fullName());
        return encode(claims, ttl);
    }

    @Override
    public IssuedToken issueForClient(ApiClient client, Institution institution, Set<String> scopes, Duration ttl) {
        JwtClaimsSet.Builder claims = baseClaims(client.clientId(), institution, ttl)
                .claim(HubClaims.TOKEN_USE, HubClaims.TOKEN_USE_CLIENT)
                .claim(HubClaims.ROLES, List.of())
                .claim(HubClaims.PERMISSIONS, List.copyOf(scopes))
                .claim(HubClaims.NAME, client.name())
                .claim(HubClaims.RATE_LIMIT, client.rateLimitPerMinute())
                .claim("scope", String.join(" ", scopes));
        return encode(claims, ttl);
    }

    private JwtClaimsSet.Builder baseClaims(String subject, Institution institution, Duration ttl) {
        Instant now = clock.instant();
        return JwtClaimsSet.builder()
                .issuer(HubClaims.ISSUER)
                .audience(List.of(HubClaims.AUDIENCE))
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(ttl))
                .claim(HubClaims.INSTITUTION_ID, institution.id().toString())
                .claim(HubClaims.INSTITUTION_TYPE, institution.type().name());
    }

    private IssuedToken encode(JwtClaimsSet.Builder claims, Duration ttl) {
        RSAKey key = keys.activeSigningKey();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(value, ttl.toSeconds());
    }
}
