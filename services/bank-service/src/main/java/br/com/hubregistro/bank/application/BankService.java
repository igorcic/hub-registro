package br.com.hubregistro.bank.application;

import br.com.hubregistro.bank.domain.Bank;
import br.com.hubregistro.bank.domain.BankContact;
import br.com.hubregistro.bank.domain.BankRepository;
import br.com.hubregistro.bank.domain.BankRepository.BankPage;
import br.com.hubregistro.bank.domain.BankRepository.BankSearch;
import br.com.hubregistro.bank.domain.BankStatus;
import br.com.hubregistro.bank.domain.StatusChange;
import br.com.hubregistro.commons.document.Cnpj;
import br.com.hubregistro.commons.error.DomainException;
import br.com.hubregistro.commons.error.ForbiddenOperationException;
import br.com.hubregistro.commons.error.NotFoundException;
import br.com.hubregistro.commons.tenant.InstitutionType;
import br.com.hubregistro.security.HubPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cadastro de bancos. Cadastrar, alterar e mudar status é do Hub ({@code bank:manage}).
 * O banco vê só o próprio cadastro e o administrador do banco mantém os próprios contatos.
 */
@Service
public class BankService {

    static final String MANAGE = "bank:manage";

    private final BankRepository banks;
    private final BankEventPublisher events;
    private final Clock clock;

    public BankService(BankRepository banks, BankEventPublisher events, Clock clock) {
        this.banks = banks;
        this.events = events;
        this.clock = clock;
    }

    public record BankData(String legalName, String tradeName, String cnpj, String code, Integer requestLimitPerMinute,
                           Integer dailyContractLimit, Map<String, Object> processingSettings, List<BankContact> contacts) {
    }

    @Transactional(readOnly = true)
    public BankPage search(HubPrincipal actor, BankStatus status, String text, int page, int size) {
        if (!actor.isHub()) {
            return banks.findById(actor.institutionId())
                    .map(b -> new BankPage(List.of(b), 1, 0, 1))
                    .orElse(new BankPage(List.of(), 0, 0, 1));
        }
        String normalized = text == null || text.isBlank() ? null : text.trim();
        return banks.search(new BankSearch(status, normalized), Math.max(page, 0), Math.clamp(size, 1, 100));
    }

    @Transactional(readOnly = true)
    public Bank get(HubPrincipal actor, UUID id) {
        return visible(actor, id);
    }

    @Transactional
    public Bank create(HubPrincipal actor, BankData data) {
        requireHubManager(actor);
        Cnpj cnpj = new Cnpj(data.cnpj());
        Bank bank = Bank.create(data.legalName(), data.tradeName(), cnpj, data.code(), data.requestLimitPerMinute(),
                data.dailyContractLimit(), data.processingSettings(), data.contacts());
        if (banks.existsByCnpj(cnpj)) {
            throw new DomainException("cnpj_in_use", "Já existe um banco com este CNPJ");
        }
        if (banks.existsByCode(bank.code())) {
            throw new DomainException("code_in_use", "Já existe um banco com este código");
        }
        long version = banks.save(bank);
        events.created(actor, bank, version);
        return bank;
    }

    @Transactional
    public Bank update(HubPrincipal actor, UUID id, BankData data) {
        requireHubManager(actor);
        Bank bank = visible(actor, id);
        bank.update(data.legalName(), data.tradeName(),
                data.requestLimitPerMinute() == null ? bank.requestLimitPerMinute() : data.requestLimitPerMinute(),
                data.dailyContractLimit(), data.processingSettings());
        long version = banks.save(bank);
        events.updated(actor, bank, version);
        return bank;
    }

    @Transactional
    public Bank changeStatus(HubPrincipal actor, UUID id, BankStatus target, String reason) {
        requireHubManager(actor);
        Bank bank = visible(actor, id);
        StatusChange change = bank.changeStatus(target, reason, actorId(actor), clock.instant());
        long version = banks.save(bank);
        banks.appendStatusChange(change);
        events.statusChanged(actor, bank, change, version);
        return bank;
    }

    @Transactional
    public Bank replaceContacts(HubPrincipal actor, UUID id, List<BankContact> contacts) {
        Bank bank = visible(actor, id);
        boolean ownAdmin = actor.institutionType() == InstitutionType.BANK && actor.roles().contains("BANK_ADMIN");
        if (!actor.hasPermission(MANAGE) && !ownAdmin) {
            throw new ForbiddenOperationException("Sem permissão para alterar os contatos deste banco");
        }
        bank.replaceContacts(contacts);
        long version = banks.save(bank);
        events.updated(actor, bank, version);
        return bank;
    }

    @Transactional(readOnly = true)
    public List<StatusChange> statusHistory(HubPrincipal actor, UUID id) {
        visible(actor, id);
        return banks.statusHistory(id);
    }

    private Bank visible(HubPrincipal actor, UUID id) {
        if (!actor.isHub() && !(actor.institutionType() == InstitutionType.BANK && actor.institutionId().equals(id))) {
            throw new NotFoundException("Banco");
        }
        return banks.findById(id).orElseThrow(() -> new NotFoundException("Banco"));
    }

    private static void requireHubManager(HubPrincipal actor) {
        if (!actor.isHub() || !actor.hasPermission(MANAGE)) {
            throw new ForbiddenOperationException("Somente a administração do Hub cadastra e altera bancos");
        }
    }

    private static UUID actorId(HubPrincipal actor) {
        try {
            return UUID.fromString(actor.subject());
        } catch (IllegalArgumentException e) {
            throw new ForbiddenOperationException("Operação exclusiva de usuários");
        }
    }
}
