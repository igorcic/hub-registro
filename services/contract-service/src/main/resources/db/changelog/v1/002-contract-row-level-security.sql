--liquibase formatted sql

-- Etapa 2: isolamento entre bancos e entre registradoras garantido pelo próprio PostgreSQL.
-- FORCE vale também para o dono das tabelas (o usuário da aplicação): um bug de consulta na aplicação
-- não consegue devolver contrato de outra instituição.
-- As tabelas filhas herdam a decisão do contrato: a subconsulta em contracts já passa pela política dele.

--changeset hub:contract-006-row-level-security
ALTER TABLE contracts ENABLE ROW LEVEL SECURITY;
ALTER TABLE contracts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contracts
    USING (hub_tenant_can_access(bank_id, registry_id))
    WITH CHECK (hub_tenant_can_access(bank_id, registry_id));

ALTER TABLE contract_properties ENABLE ROW LEVEL SECURITY;
ALTER TABLE contract_properties FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contract_properties
    USING (EXISTS (SELECT 1 FROM contracts c WHERE c.id = contract_id))
    WITH CHECK (EXISTS (SELECT 1 FROM contracts c WHERE c.id = contract_id));

ALTER TABLE contract_parties ENABLE ROW LEVEL SECURITY;
ALTER TABLE contract_parties FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contract_parties
    USING (EXISTS (SELECT 1 FROM contracts c WHERE c.id = contract_id))
    WITH CHECK (EXISTS (SELECT 1 FROM contracts c WHERE c.id = contract_id));

ALTER TABLE contract_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE contract_status_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contract_status_history
    USING (EXISTS (SELECT 1 FROM contracts c WHERE c.id = contract_id))
    WITH CHECK (EXISTS (SELECT 1 FROM contracts c WHERE c.id = contract_id));

ALTER TABLE contract_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE contract_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON contract_events
    USING (EXISTS (SELECT 1 FROM contracts c WHERE c.id = contract_id))
    WITH CHECK (EXISTS (SELECT 1 FROM contracts c WHERE c.id = contract_id));
