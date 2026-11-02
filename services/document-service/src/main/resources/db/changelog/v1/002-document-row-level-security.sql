--liquibase formatted sql

-- Etapa 2: o banco vê os documentos dos próprios contratos; a registradora, os dos contratos encaminhados a ela.
-- O banco é decidido pela própria coluna bank_id (vale mesmo antes de a réplica contract_access chegar);
-- a registradora, pela réplica contract_access.

--changeset hub:document-003-row-level-security
ALTER TABLE contract_access ENABLE ROW LEVEL SECURITY;
ALTER TABLE contract_access FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contract_access
    USING (hub_tenant_can_access(bank_id, registry_id))
    WITH CHECK (hub_tenant_can_access(bank_id, registry_id));

ALTER TABLE contract_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE contract_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contract_documents
    USING (hub_tenant_can_access(bank_id,
           (SELECT a.registry_id FROM contract_access a WHERE a.contract_id = contract_documents.contract_id)))
    WITH CHECK (hub_tenant_can_access(bank_id,
           (SELECT a.registry_id FROM contract_access a WHERE a.contract_id = contract_documents.contract_id)));
