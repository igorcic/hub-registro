package br.com.hubregistro.auth.domain.role;

import java.util.Set;

/** Permissões usadas nas regras do auth-service (o catálogo completo está na migração auth-009). */
public final class Permissions {

    public static final String USER_READ = "user:read";
    public static final String USER_MANAGE = "user:manage";
    public static final String USER_MANAGE_ANY = "user:manage-any";
    public static final String API_CLIENT_MANAGE = "api-client:manage";

    /** Permissões que um cliente de API (sistema do banco) pode receber como escopo. */
    public static final Set<String> API_CLIENT_SCOPES = Set.of(
            "contract:submit", "contract:read", "contract:cancel", "document:upload", "document:read");

    private Permissions() {
    }
}
