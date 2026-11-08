package br.com.hubregistro.bank.infrastructure.persistence;

import br.com.hubregistro.bank.domain.Bank;
import br.com.hubregistro.bank.domain.BankContact;
import br.com.hubregistro.bank.domain.BankRepository;
import br.com.hubregistro.bank.domain.StatusChange;
import br.com.hubregistro.commons.document.Cnpj;
import br.com.hubregistro.security.HubSecurity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaBankRepository implements BankRepository {

    private final BankJpaRepository banks;
    private final BankContactJpaRepository contacts;
    private final BankStatusChangeJpaRepository statusChanges;

    JpaBankRepository(BankJpaRepository banks, BankContactJpaRepository contacts, BankStatusChangeJpaRepository statusChanges) {
        this.banks = banks;
        this.contacts = contacts;
        this.statusChanges = statusChanges;
    }

    @Override
    public Optional<Bank> findById(UUID id) {
        return banks.findById(id).filter(e -> e.getDeletedAt() == null).map(this::toDomain);
    }

    @Override
    public boolean existsByCnpj(Cnpj cnpj) {
        return banks.existsActiveByCnpj(cnpj.value());
    }

    @Override
    public boolean existsByCode(String code) {
        return banks.existsActiveByCode(code);
    }

    @Override
    public BankPage search(BankSearch criteria, int page, int size) {
        Specification<BankEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.text() != null) {
                String text = criteria.text().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_");
                String digits = Cnpj.normalize(criteria.text());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("legalName")), "%" + text + "%", '\\'),
                        cb.like(cb.lower(root.<String>get("tradeName")), "%" + text + "%", '\\'),
                        cb.like(cb.lower(root.<String>get("code")), "%" + text + "%", '\\'),
                        cb.like(root.<String>get("cnpj"), digits + "%")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<BankEntity> result = banks.findAll(spec, PageRequest.of(page, size, Sort.by("tradeName")));
        return new BankPage(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements(), page, size);
    }

    @Override
    public long save(Bank bank) {
        UUID actor = currentActor();
        BankEntity entity = banks.findById(bank.id()).orElse(null);
        if (entity == null) {
            entity = banks.save(BankEntity.newFrom(bank, actor));
        } else {
            entity.copyFrom(bank, actor);
        }
        syncContacts(bank);
        banks.flush();
        return entity.getVersion();
    }

    /** Contatos mudaram: exclui logicamente os atuais e grava os novos (exclusões antes, por causa do índice único). */
    private void syncContacts(Bank bank) {
        List<BankContactEntity> current = contacts.findActive(bank.id());
        List<BankContact> currentDomain = current.stream().map(BankContactEntity::toDomain).toList();
        if (currentDomain.size() == bank.contacts().size() && currentDomain.containsAll(bank.contacts())) {
            return;
        }
        Instant now = Instant.now();
        current.forEach(c -> c.markDeleted(now));
        contacts.saveAllAndFlush(current);
        contacts.saveAll(bank.contacts().stream().map(c -> BankContactEntity.of(bank.id(), c)).toList());
    }

    @Override
    public void appendStatusChange(StatusChange change) {
        statusChanges.save(BankStatusChangeEntity.of(change));
    }

    @Override
    public List<StatusChange> statusHistory(UUID bankId) {
        return statusChanges.findByBankIdOrderByChangedAtDesc(bankId).stream().map(BankStatusChangeEntity::toDomain).toList();
    }

    private Bank toDomain(BankEntity e) {
        List<BankContact> list = contacts.findActive(e.getId()).stream().map(BankContactEntity::toDomain).toList();
        return Bank.restore(e.getId(), new Cnpj(e.getCnpj()), e.getCode(), e.getLegalName(), e.getTradeName(), e.getStatus(),
                e.getStatusReason(), e.getRequestLimitPerMinute(), e.getDailyContractLimit(), e.getProcessingSettings(),
                list, e.getVersion() == null ? 0 : e.getVersion());
    }

    private static UUID currentActor() {
        return HubSecurity.currentPrincipal()
                .filter(p -> !p.apiClient())
                .map(p -> { try { return UUID.fromString(p.subject()); } catch (IllegalArgumentException ex) { return null; } })
                .orElse(null);
    }
}
