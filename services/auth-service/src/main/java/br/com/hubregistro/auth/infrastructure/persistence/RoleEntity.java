package br.com.hubregistro.auth.infrastructure.persistence;

import br.com.hubregistro.auth.domain.role.Role;
import br.com.hubregistro.commons.tenant.InstitutionType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.HashSet;
import java.util.Set;

/** Papéis e permissões vêm das migrações; a aplicação só lê. */
@Entity
@Immutable
@Table(name = "roles")
public class RoleEntity {

    @Id
    private String code;

    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type")
    private InstitutionType institutionType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_code"))
    @Column(name = "permission_code")
    private Set<String> permissions = new HashSet<>();

    protected RoleEntity() {
    }

    Role toDomain() {
        return new Role(code, name, description, institutionType, permissions);
    }
}
