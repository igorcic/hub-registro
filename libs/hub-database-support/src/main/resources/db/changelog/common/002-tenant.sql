--liquibase formatted sql

-- Decisão de acesso usada pelas políticas de Row-Level Security.
-- A aplicação define app.tenant_type e app.tenant_id em cada conexão (TenantAwareDataSource):
--   SYSTEM e HUB          -> todas as linhas
--   BANK     + tenant_id  -> linhas do próprio banco
--   REGISTRY + tenant_id  -> linhas encaminhadas à própria registradora
--   ausente / outro valor -> nenhuma linha (falha fechada)

--changeset hub:common-004-tenant-access splitStatements:false runOnChange:true
CREATE OR REPLACE FUNCTION hub_tenant_can_access(p_bank_id uuid, p_registry_id uuid) RETURNS boolean
LANGUAGE sql STABLE AS $$
    SELECT CASE coalesce(current_setting('app.tenant_type', true), '')
        WHEN 'SYSTEM'   THEN true
        WHEN 'HUB'      THEN true
        WHEN 'BANK'     THEN p_bank_id IS NOT NULL
                             AND p_bank_id = nullif(current_setting('app.tenant_id', true), '')::uuid
        WHEN 'REGISTRY' THEN p_registry_id IS NOT NULL
                             AND p_registry_id = nullif(current_setting('app.tenant_id', true), '')::uuid
        ELSE false
    END;
$$;
COMMENT ON FUNCTION hub_tenant_can_access(uuid, uuid) IS
    'true se o tenant da conexão pode ver/alterar a linha do banco p_bank_id encaminhada à registradora p_registry_id';
