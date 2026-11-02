package br.com.hubregistro.auth.domain.role;

import java.util.List;
import java.util.Set;

public interface RoleRepository {

    List<Role> findAll();

    /** Devolve só os papéis existentes; quem chama confere se todos os pedidos vieram. */
    Set<Role> findByCodes(Set<String> codes);
}
