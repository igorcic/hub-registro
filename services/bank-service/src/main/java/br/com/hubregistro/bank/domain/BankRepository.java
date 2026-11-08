package br.com.hubregistro.bank.domain;

import br.com.hubregistro.commons.document.Cnpj;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankRepository {

    Optional<Bank> findById(UUID id);

    boolean existsByCnpj(Cnpj cnpj);

    boolean existsByCode(String code);

    BankPage search(BankSearch criteria, int page, int size);

    /** Grava e devolve a nova versão do agregado (usada como ordem dos eventos). */
    long save(Bank bank);

    void appendStatusChange(StatusChange change);

    List<StatusChange> statusHistory(UUID bankId);

    record BankSearch(BankStatus status, String text) {
    }

    record BankPage(List<Bank> items, long total, int page, int size) {
    }
}
