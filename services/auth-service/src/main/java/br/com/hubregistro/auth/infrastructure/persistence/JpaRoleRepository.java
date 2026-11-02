package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.role.Role;
import br.com.hubregistro.auth.domain.role.RoleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
class JpaRoleRepository implements RoleRepository {

    private final RoleJpaRepository jpa;

    JpaRoleRepository(RoleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Role> findAll() {
        return jpa.findAll().stream().map(RoleEntity::toDomain).toList();
    }

    @Override
    public Set<Role> findByCodes(Set<String> codes) {
        if (codes.isEmpty()) {
            return Set.of();
        }
        return jpa.findByCodeIn(codes).stream().map(RoleEntity::toDomain).collect(Collectors.toUnmodifiableSet());
    }
}
