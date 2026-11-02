package br.com.hubregistro.auth.application;

/** Origem da requisição, para auditoria e para a sessão. */
public record ClientInfo(String ip, String userAgent) {

    public static final ClientInfo UNKNOWN = new ClientInfo(null, null);
}
