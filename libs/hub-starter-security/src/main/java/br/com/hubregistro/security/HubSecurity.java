package br.com.hubregistro.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Acesso ao chamador autenticado da requisição atual. */
public final class HubSecurity {

    private HubSecurity() {
    }

    public static Optional<HubPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HubPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static HubPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Requisição sem usuário autenticado"));
    }
}
