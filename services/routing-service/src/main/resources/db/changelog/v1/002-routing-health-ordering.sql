--liquibase formatted sql

-- Etapa 3: a saúde da integração chega por evento próprio, com ordem independente da versão do cadastro.

--changeset hub:routing-004-health-ordering
ALTER TABLE registry_eligibility ADD COLUMN health_checked_at timestamptz;
COMMENT ON COLUMN registry_eligibility.health_checked_at IS 'Horário da verificação aplicada; evento de saúde mais antigo é ignorado';
COMMENT ON COLUMN registry_eligibility.source_version IS 'Versão do cadastro aplicada; -1 = linha criada por evento de saúde antes do cadastro';
