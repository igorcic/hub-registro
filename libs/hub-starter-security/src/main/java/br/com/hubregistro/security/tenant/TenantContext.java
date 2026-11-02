package br.com.hubregistro.security.tenant;

import java.util.UUID;

/**
 * Escopo de dados aplicado pelo PostgreSQL (Row-Level Security) em cada conexão.
 *
 * @param type   HUB, BANK, REGISTRY ou SYSTEM (processos internos); vazio = nenhum acesso
 * @param tenant instituição do chamador; nulo para HUB e SYSTEM
 */
public record TenantContext(String type, UUID tenant) {

    public static final TenantContext NONE = new TenantContext("", null);
    public static final TenantContext SYSTEM = new TenantContext("SYSTEM", null);

    public String tenantAsText() {
        return tenant == null ? "" : tenant.toString();
    }
}
