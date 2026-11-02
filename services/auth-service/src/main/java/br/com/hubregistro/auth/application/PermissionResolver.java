package br.com.hubregistro.auth.application;

import br.com.hubregistro.auth.domain.role.Role;
import br.com.hubregistro.auth.domain.role.RoleRepository;
import br.com.hubregistro.auth.domain.user.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** Permissões efetivas de um usuário: a união das permissões dos seus papéis. */
@Component
public class PermissionResolver {

    private final RoleRepository roles;

    public PermissionResolver(RoleRepository roles) {
        this.roles = roles;
    }

    public Set<String> permissionsOf(User user) {
        return roles.findByCodes(user.roleCodes()).stream()
                .flatMap(r -> r.permissions().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Carrega os papéis pedidos; falha se algum não existir. */
    public Set<Role> requireRoles(Set<String> codes) {
        Set<Role> found = roles.findByCodes(codes);
        if (found.size() != codes.size()) {
            Set<String> missing = codes.stream()
                    .filter(c -> found.stream().noneMatch(r -> r.code().equals(c)))
                    .collect(Collectors.toSet());
            throw new br.com.hubregistro.commons.error.DomainException("unknown_role", "Papéis inexistentes: " + missing);
        }
        return found;
    }
}
