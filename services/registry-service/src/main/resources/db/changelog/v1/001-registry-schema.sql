--liquibase formatted sql

-- Banco hub_registry: cadastro das empresas registradoras (quem são, o que atendem e onde).
-- Endpoints, credenciais, certificados, timeout e retentativas ficam no integration-service.

--changeset hub:registry-001-registries
CREATE TABLE registries (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    legal_name     varchar(200) NOT NULL,
    trade_name     varchar(200) NOT NULL,
    cnpj           char(14)     NOT NULL CHECK (hub_is_valid_cnpj_format(cnpj)),
    code           varchar(30)  NOT NULL CHECK (code ~ '^[A-Z0-9_-]{2,30}$'),
    status         varchar(20)  NOT NULL DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    status_reason  varchar(500),
    daily_capacity integer      CHECK (daily_capacity > 0),
    street         varchar(200) NOT NULL,
    number         varchar(20)  NOT NULL,
    complement     varchar(100),
    district       varchar(100) NOT NULL,
    city           varchar(100) NOT NULL,
    ibge_code      char(7)      NOT NULL CHECK (ibge_code ~ '^[0-9]{7}$'),
    uf             char(2)      NOT NULL CHECK (hub_is_valid_uf(uf)),
    zip_code       char(8)      NOT NULL CHECK (zip_code ~ '^[0-9]{8}$'),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    version        bigint       NOT NULL DEFAULT 0,
    deleted_at     timestamptz
);
COMMENT ON COLUMN registries.status IS 'Nasce INACTIVE: só é ativada depois de configurada a integração';
COMMENT ON COLUMN registries.daily_capacity IS 'Capacidade operacional diária usada pelo roteamento; nulo = sem limite';
CREATE UNIQUE INDEX ux_registries_cnpj ON registries (cnpj) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_registries_code ON registries (code) WHERE deleted_at IS NULL;
CREATE INDEX ix_registries_status ON registries (status) WHERE deleted_at IS NULL;

--changeset hub:registry-002-technical-contacts
CREATE TABLE registry_technical_contacts (
    id          uuid         PRIMARY KEY DEFAULT uuidv7(),
    registry_id uuid         NOT NULL REFERENCES registries (id),
    name        varchar(200) NOT NULL,
    email       varchar(254) NOT NULL,
    phone       varchar(20),
    job_title   varchar(100),
    is_primary  boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);
CREATE INDEX ix_registry_technical_contacts_registry ON registry_technical_contacts (registry_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_registry_technical_contacts_primary
    ON registry_technical_contacts (registry_id) WHERE is_primary AND deleted_at IS NULL;

--changeset hub:registry-003-contract-types-and-areas
CREATE TABLE registry_contract_types (
    registry_id   uuid        NOT NULL REFERENCES registries (id),
    contract_type varchar(50) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (registry_id, contract_type)
);
CREATE INDEX ix_registry_contract_types_type ON registry_contract_types (contract_type);

CREATE TABLE registry_service_areas (
    id          uuid        PRIMARY KEY DEFAULT uuidv7(),
    registry_id uuid        NOT NULL REFERENCES registries (id),
    uf          char(2)     NOT NULL CHECK (hub_is_valid_uf(uf)),
    ibge_code   char(7)     CHECK (ibge_code ~ '^[0-9]{7}$'),
    created_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON COLUMN registry_service_areas.ibge_code IS 'Município atendido; nulo = a UF inteira';
CREATE UNIQUE INDEX ux_registry_service_areas ON registry_service_areas (registry_id, uf, COALESCE(ibge_code, ''));
CREATE INDEX ix_registry_service_areas_location ON registry_service_areas (uf, ibge_code);

--changeset hub:registry-004-status-changes
CREATE TABLE registry_status_changes (
    id          uuid         PRIMARY KEY DEFAULT uuidv7(),
    registry_id uuid         NOT NULL REFERENCES registries (id),
    from_status varchar(20)  NOT NULL,
    to_status   varchar(20)  NOT NULL,
    reason      varchar(500) NOT NULL,
    changed_by  uuid         NOT NULL,
    changed_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_registry_status_changes_registry ON registry_status_changes (registry_id, changed_at DESC);
CREATE TRIGGER trg_registry_status_changes_immutable
    BEFORE UPDATE OR DELETE ON registry_status_changes
    FOR EACH ROW EXECUTE FUNCTION hub_forbid_mutation();
