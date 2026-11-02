package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.user.Email;
import br.com.hubregistro.auth.domain.user.User;
import br.com.hubregistro.auth.domain.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    JpaUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findActiveByEmail(email.value()).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpa.existsActiveByEmail(email.value());
    }

    @Override
    public boolean existsWithRole(String roleCode) {
        return jpa.existsWithRole(roleCode);
    }

    @Override
    public UserPage search(UserSearch criteria, int page, int size) {
        Specification<UserEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (criteria.institutionId() != null) {
                predicates.add(cb.equal(root.get("institutionId"), criteria.institutionId()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.text() != null) {
                String like = "%" + criteria.text().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("fullName")), like, '\\'),
                        cb.like(root.<String>get("email"), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<UserEntity> result = jpa.findAll(spec, PageRequest.of(page, size, Sort.by("fullName").ascending()));
        return new UserPage(result.map(UserEntity::toDomain).getContent(), result.getTotalElements(), page, size);
    }

    @Override
    public void save(User user) {
        jpa.findById(user.id()).ifPresentOrElse(
                existing -> existing.copyFrom(user),
                () -> jpa.save(UserEntity.newFrom(user)));
    }
}
