package br.com.hubregistro.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.util.Collection;
import java.util.Map;

/** Autenticação de um JWT válido do Hub, com o {@link HubPrincipal} já montado. */
public class HubAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

    private final HubPrincipal principal;

    public HubAuthenticationToken(Jwt jwt, HubPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(jwt, principal, jwt, authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public HubPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.subject();
    }

    @Override
    public Map<String, Object> getTokenAttributes() {
        return getToken().getClaims();
    }
}
