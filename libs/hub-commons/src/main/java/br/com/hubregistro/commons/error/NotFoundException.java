package br.com.hubregistro.commons.error;

/** Recurso inexistente ou fora do escopo do solicitante (os dois casos respondem igual, para não vazar existência). */
public class NotFoundException extends DomainException {

    public NotFoundException(String resource) {
        super("not_found", resource + " não encontrado");
    }
}
