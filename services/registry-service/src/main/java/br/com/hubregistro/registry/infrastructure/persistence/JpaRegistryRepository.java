package br.com.hubregistro.registry.infrastructure.persistence;

import br.com.hubregistro.commons.document.Cnpj;
import br.com.hubregistro.registry.domain.Registry;
import br.com.hubregistro.registry.domain.RegistryRepository;
import br.com.hubregistro.registry.domain.StatusChange;
import br.com.hubregistro.registry.domain.TechnicalContact;
import jakarta.persistence.criteria.Join;
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
class JpaRegistryRepository implements RegistryRepository {

    private final RegistryJpaRepository registries;
    private final TechnicalContactJpaRepository contacts;
    private final RegistryStatusChangeJpaRepository statusChanges;

    JpaRegistryRepository(RegistryJpaRepository registries, TechnicalContactJpaRepository contacts,
                          RegistryStatusChangeJpaRepository statusChanges) {
        this.registries = registries;
        this.contacts = contacts;
        this.statusChanges = statusChanges;
    }

    @Override
    public Optional<Registry> findById(UUID id) {
        return registries.findById(id).filter(e -> e.getDeletedAt() == null).map(this::toDomain);
    }

    @Override
    public boolean existsByCnpj(Cnpj cnpj) {
        return registries.existsActiveByCnpj(cnpj.value());
    }

    @Override
    public boolean existsByCode(String code) {
        return registries.existsActiveByCode(code);
    }

    @Override
    public RegistryPage search(RegistrySearch criteria, int page, int size) {
        Specification<RegistryEntity> spec = (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.contractType() != null) {
                Join<RegistryEntity, String> types = root.join("contractTypes");
                predicates.add(cb.equal(types, criteria.contractType()));
            }
            if (criteria.uf() != null) {
                Join<RegistryEntity, ServiceAreaEmbeddable> areas = root.join("serviceAreas");
                predicates.add(cb.equal(areas.get("uf"), criteria.uf()));
            }
            if (criteria.text() != null) {
                String text = "%" + criteria.text().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("legalName")), text, '\\'),
                        cb.like(cb.lower(root.<String>get("tradeName")), text, '\\'),
                        cb.like(cb.lower(root.<String>get("code")), text, '\\'),
                        cb.like(root.<String>get("cnpj"), Cnpj.normalize(criteria.text()) + "%")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<RegistryEntity> result = registries.findAll(spec, PageRequest.of(page, size, Sort.by("tradeName")));
        return new RegistryPage(result.getContent().stream().map(this::toDomain).toList(), result.getTotalElements(), page, size);
    }

    @Override
    public long save(Registry registry) {
        RegistryEntity entity = registries.findById(registry.id()).orElse(null);
        if (entity == null) {
            entity = registries.save(RegistryEntity.newFrom(registry));
        } else {
            entity.copyFrom(registry);
        }
        syncContacts(registry);
        registries.flush();
        return entity.getVersion();
    }

    private void syncContacts(Registry registry) {
        List<TechnicalContactEntity> current = contacts.findActive(registry.id());
        List<TechnicalContact> currentDomain = current.stream().map(TechnicalContactEntity::toDomain).toList();
        if (currentDomain.size() == registry.technicalContacts().size()
                && currentDomain.containsAll(registry.technicalContacts())) {
            return;
        }
        Instant now = Instant.now();
        current.forEach(c -> c.markDeleted(now));
        contacts.saveAllAndFlush(current);
        contacts.saveAll(registry.technicalContacts().stream().map(c -> TechnicalContactEntity.of(registry.id(), c)).toList());
    }

    @Override
    public void appendStatusChange(StatusChange change) {
        statusChanges.save(RegistryStatusChangeEntity.of(change));
    }

    @Override
    public List<StatusChange> statusHistory(UUID registryId) {
        return statusChanges.findByRegistryIdOrderByChangedAtDesc(registryId).stream()
                .map(RegistryStatusChangeEntity::toDomain).toList();
    }

    private Registry toDomain(RegistryEntity e) {
        List<TechnicalContact> list = contacts.findActive(e.getId()).stream().map(TechnicalContactEntity::toDomain).toList();
        return Registry.restore(e.getId(), new Cnpj(e.getCnpj()), e.getCode(), e.getLegalName(), e.getTradeName(),
                e.address(), e.getDailyCapacity(), e.getStatus(), e.getStatusReason(), e.getContractTypes(),
                e.serviceAreas(), list, e.getVersion() == null ? 0 : e.getVersion());
    }
}
