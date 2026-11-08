package br.com.hubregistro.bank.application;

import br.com.hubregistro.bank.domain.Bank;
import br.com.hubregistro.bank.domain.StatusChange;
import br.com.hubregistro.security.HubPrincipal;

/** Eventos bank.* em hub.platform.events: alimentam as réplicas (auth, contract) e a auditoria. */
public interface BankEventPublisher {

    void created(HubPrincipal actor, Bank bank, long version);

    void updated(HubPrincipal actor, Bank bank, long version);

    void statusChanged(HubPrincipal actor, Bank bank, StatusChange change, long version);
}
