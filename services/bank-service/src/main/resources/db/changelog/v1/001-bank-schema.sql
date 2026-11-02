--liquibase formatted sql

-- Banco hub_bank: cadastro das instituições financeiras.
-- Usuários e credenciais de API ficam no auth-service; configuração técnica de callback, no integration-service.

--changeset hub:bank-001-banks
CREATE TABLE banks (
    id                       uuid         PRIMARY KEY DEFAULT uuidv7(),
    legal_name               varchar(200) NOT NULL,
    trade_name               varchar(200) NOT NULL,
    cnpj                     char(14)     NOT NULL CHECK (hub_is_valid_cnpj_format(cnpj)),
    code                     varchar(30)  NOT NULL CHECK (code ~ '^[A-Z0-9_-]{2,30}$'),
    status                   varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    status_reason            varchar(500),
    request_limit_per_minute integer      NOT NULL DEFAULT 600 CHECK (request_limit_per_minute > 0),
    daily_contract_limit     integer      CHECK (daily_contract_limit > 0),
    processing_settings      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    created_by               uuid,
    updated_by               uuid,
    version                  bigint       NOT NULL DEFAULT 0,
    deleted_at               timestamptz
);
COMMENT ON COLUMN banks.processing_settings IS 'Configurações específicas de processamento do banco (validadas pela aplicação)';
COMMENT ON COLUMN banks.daily_contract_limit IS 'Limite de contratos por dia; nulo = sem limite';
CREATE UNIQUE INDEX ux_banks_cnpj ON banks (cnpj) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_banks_code ON banks (code) WHERE deleted_at IS NULL;
CREATE INDEX ix_banks_status ON banks (status) WHERE deleted_at IS NULL;

--changeset hub:bank-002-bank-contacts
CREATE TABLE bank_contacts (
    id         uuid         PRIMARY KEY DEFAULT uuidv7(),
    bank_id    uuid         NOT NULL REFERENCES banks (id),
    name       varchar(200) NOT NULL,
    email      varchar(254) NOT NULL,
    phone      varchar(20),
    area       varchar(30)  NOT NULL CHECK (area IN ('BUSINESS', 'TECHNICAL', 'OPERATIONS', 'LEGAL')),
    is_primary boolean      NOT NULL DEFAULT false,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX ix_bank_contacts_bank ON bank_contacts (bank_id) WHERE deleted_at IS NULL;
-- No máximo um contato principal por área em cada banco.
CREATE UNIQUE INDEX ux_bank_contacts_primary ON bank_contacts (bank_id, area) WHERE is_primary AND deleted_at IS NULL;

--changeset hub:bank-003-bank-status-changes
CREATE TABLE bank_status_changes (
    id          uuid         PRIMARY KEY DEFAULT uuidv7(),
    bank_id     uuid         NOT NULL REFERENCES banks (id),
    from_status varchar(20)  NOT NULL,
    to_status   varchar(20)  NOT NULL,
    reason      varchar(500) NOT NULL,
    changed_by  uuid         NOT NULL,
    changed_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_bank_status_changes_bank ON bank_status_changes (bank_id, changed_at DESC);
CREATE TRIGGER trg_bank_status_changes_immutable
    BEFORE UPDATE OR DELETE ON bank_status_changes
    FOR EACH ROW EXECUTE FUNCTION hub_forbid_mutation();
