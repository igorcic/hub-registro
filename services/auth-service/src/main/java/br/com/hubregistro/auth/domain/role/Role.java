package br.com.hubregistro.auth.domain.role;

import br.com.hubregistro.commons.tenant.InstitutionType;

import java.util.Set;

/** Papel (RBAC): conjunto de permissões concedível a usuários de um tipo de instituição. */
public record Role(String code, String name, String description, InstitutionType institutionType, Set<String> permissions) {

    public Role {
        permissions = Set.copyOf(permissions);
    }
}
