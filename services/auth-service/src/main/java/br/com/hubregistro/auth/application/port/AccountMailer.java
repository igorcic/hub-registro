package br.com.hubregistro.auth.application.port;

import br.com.hubregistro.auth.domain.reset.TokenPurpose;
import br.com.hubregistro.auth.domain.user.User;

/** Envia o link de ativação ou de redefinição de senha. Só envia depois que a transação confirmar. */
public interface AccountMailer {

    void sendPasswordLink(User user, TokenPurpose purpose, String rawToken);
}
