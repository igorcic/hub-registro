package br.com.hubregistro.auth.domain;

import br.com.hubregistro.commons.error.DomainException;

/** Erros de autenticação. Mensagens deliberadamente genéricas: não revelam se o e-mail existe nem o motivo exato. */
public final class AuthErrors {

    private AuthErrors() {
    }

    public static class InvalidCredentialsException extends DomainException {
        public InvalidCredentialsException() {
            super("invalid_credentials", "E-mail ou senha inválidos");
        }
    }

    public static class InvalidTokenException extends DomainException {
        public InvalidTokenException() {
            super("invalid_token", "Token inválido ou expirado");
        }
    }

    public static class InvalidClientException extends DomainException {
        public InvalidClientException() {
            super("invalid_client", "Credenciais do cliente inválidas");
        }
    }

    public static class WeakPasswordException extends DomainException {
        public WeakPasswordException(String reason) {
            super("weak_password", reason);
        }
    }
}
