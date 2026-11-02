package br.com.hubregistro.security;

/** Nomes das claims do access token emitido pelo auth-service. */
public final class HubClaims {

    public static final String ISSUER = "https://auth.hub-registro.local";
    public static final String AUDIENCE = "hub-registro";

    public static final String INSTITUTION_ID = "inst";
    public static final String INSTITUTION_TYPE = "inst_type";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "perms";
    public static final String TOKEN_USE = "token_use";
    public static final String EMAIL = "email";
    public static final String NAME = "name";
    /** Limite de requisições por minuto do cliente de API (lido pelo gateway). */
    public static final String RATE_LIMIT = "rl";

    public static final String TOKEN_USE_USER = "user";
    public static final String TOKEN_USE_CLIENT = "client";

    private HubClaims() {
    }
}
