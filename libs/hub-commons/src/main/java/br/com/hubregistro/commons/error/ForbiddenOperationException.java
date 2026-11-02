package br.com.hubregistro.commons.error;

/** O solicitante está autenticado e vê o recurso, mas não pode executar esta operação sobre ele. */
public class ForbiddenOperationException extends DomainException {

    public ForbiddenOperationException(String message) {
        super("forbidden", message);
    }
}
