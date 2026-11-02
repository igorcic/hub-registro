package br.com.hubregistro.security;

import br.com.hubregistro.commons.tenant.InstitutionType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Converte o JWT em {@link HubAuthenticationToken}. Authorities: cada permissão como está
 * ({@code contract:submit}) e cada papel com prefixo ({@code ROLE_BANK_ADMIN}).
 */
public class HubJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        HubPrincipal principal = toPrincipal(jwt);
        List<GrantedAuthority> authorities = new ArrayList<>();
        principal.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        principal.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        return new HubAuthenticationToken(jwt, principal, authorities);
    }

    static HubPrincipal toPrincipal(Jwt jwt) {
        String institutionId = jwt.getClaimAsString(HubClaims.INSTITUTION_ID);
        String institutionType = jwt.getClaimAsString(HubClaims.INSTITUTION_TYPE);
        if (institutionId == null || institutionType == null) {
            throw new InvalidBearerTokenException("Token sem instituição");
        }
        try {
            return new HubPrincipal(
                    jwt.getSubject(),
                    HubClaims.TOKEN_USE_CLIENT.equals(jwt.getClaimAsString(HubClaims.TOKEN_USE)),
                    UUID.fromString(institutionId),
                    InstitutionType.valueOf(institutionType),
                    stringSet(jwt, HubClaims.ROLES),
                    stringSet(jwt, HubClaims.PERMISSIONS),
                    jwt.getClaimAsString(HubClaims.EMAIL),
                    jwt.getClaimAsString(HubClaims.NAME));
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Token com instituição inválida");
        }
    }

    private static Set<String> stringSet(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? Set.of() : new HashSet<>(values);
    }
}
