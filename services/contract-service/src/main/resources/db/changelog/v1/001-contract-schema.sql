--liquibase formatted sql

-- Banco hub_contract: agregado Contract, máquina de estados e histórico imutável.
-- Contratos nunca são apagados: encerram-se por transição de estado (CANCELLED, COMPLETED...).

--changeset hub:contract-001-institutions-replica
CREATE TABLE institutions (
    id             uuid        PRIMARY KEY,
    type           varchar(20) NOT NULL CHECK (type IN ('BANK', 'REGISTRY')),
    status         varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    source_version bigint      NOT NULL DEFAULT 0,
    updated_at     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE institutions IS 'Réplica por eventos; a validação de negócio recusa contrato de banco inativo sem chamada síncrona';

--changeset hub:contract-002-contracts
CREATE TABLE contracts (
    id                   uuid          PRIMARY KEY DEFAULT uuidv7(),
    bank_id              uuid          NOT NULL,
    registry_id          uuid,
    external_id          varchar(100)  NOT NULL,
    contract_number      varchar(60)   NOT NULL,
    contract_type        varchar(50)   NOT NULL,
    operation_type       varchar(50)   NOT NULL,
    guarantee_type       varchar(50)   NOT NULL,
    status               varchar(30)   NOT NULL DEFAULT 'RECEIVED' CHECK (status IN (
                             'RECEIVED', 'VALIDATING', 'VALIDATION_FAILED', 'PENDING_PROCESSING', 'PROCESSING',
                             'SENT_TO_REGISTRY', 'REGISTRY_PROCESSING', 'PENDING_DOCUMENTS', 'APPROVED',
                             'REJECTED', 'ERROR', 'CANCELLED', 'COMPLETED')),
    status_reason        varchar(500),
    financed_amount      numeric(18,2) NOT NULL CHECK (financed_amount > 0),
    property_value       numeric(18,2) CHECK (property_value > 0),
    down_payment         numeric(18,2) CHECK (down_payment >= 0),
    installment_count    integer       CHECK (installment_count > 0),
    annual_interest_rate numeric(7,4)  CHECK (annual_interest_rate >= 0),
    currency             char(3)       NOT NULL DEFAULT 'BRL',
    contracted_at        date          NOT NULL,
    source               varchar(10)   NOT NULL CHECK (source IN ('API', 'MANUAL')),
    received_at          timestamptz   NOT NULL DEFAULT now(),
    submitted_at         timestamptz,
    completed_at         timestamptz,
    protocol_number      varchar(100),
    current_attempt      integer       NOT NULL DEFAULT 0 CHECK (current_attempt >= 0),
    correlation_id       varchar(64),
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_by           uuid,
    version              bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ux_contracts_bank_external UNIQUE (bank_id, external_id)
);
COMMENT ON COLUMN contracts.external_id IS 'Identificador do contrato no sistema do banco; garante idempotência do envio por API';
COMMENT ON COLUMN contracts.submitted_at IS 'Primeiro envio à registradora';
COMMENT ON COLUMN contracts.version IS 'Lock otimista: impede duas transições simultâneas do mesmo contrato';

-- Portal do banco: listagem por período e filtros por status.
CREATE INDEX ix_contracts_bank_received ON contracts (bank_id, received_at DESC);
CREATE INDEX ix_contracts_bank_status ON contracts (bank_id, status);
-- Portal da registradora.
CREATE INDEX ix_contracts_registry_status ON contracts (registry_id, status) WHERE registry_id IS NOT NULL;
-- Operação: contratos parados num estado intermediário.
CREATE INDEX ix_contracts_status_updated ON contracts (status, updated_at);
CREATE INDEX ix_contracts_contract_number ON contracts (contract_number);
CREATE INDEX ix_contracts_protocol_number ON contracts (protocol_number) WHERE protocol_number IS NOT NULL;

--changeset hub:contract-003-properties-parties
CREATE TABLE contract_properties (
    id                   uuid          PRIMARY KEY DEFAULT uuidv7(),
    contract_id          uuid          NOT NULL REFERENCES contracts (id),
    registration_number  varchar(30)   NOT NULL,
    registry_office_code varchar(20),
    property_type        varchar(30)   NOT NULL,
    street               varchar(200)  NOT NULL,
    number               varchar(20)   NOT NULL,
    complement           varchar(100),
    district             varchar(100)  NOT NULL,
    city                 varchar(100)  NOT NULL,
    ibge_code            char(7)       NOT NULL CHECK (ibge_code ~ '^[0-9]{7}$'),
    uf                   char(2)       NOT NULL CHECK (hub_is_valid_uf(uf)),
    zip_code             char(8)       NOT NULL CHECK (zip_code ~ '^[0-9]{8}$'),
    area_m2              numeric(12,2) CHECK (area_m2 > 0),
    created_at           timestamptz   NOT NULL DEFAULT now()
);
COMMENT ON COLUMN contract_properties.registration_number IS 'Número da matrícula do imóvel';
COMMENT ON COLUMN contract_properties.registry_office_code IS 'Código (CNS) do cartório de registro de imóveis da matrícula';
CREATE INDEX ix_contract_properties_contract ON contract_properties (contract_id);
CREATE INDEX ix_contract_properties_location ON contract_properties (uf, ibge_code);

CREATE TABLE contract_parties (
    id            uuid         PRIMARY KEY DEFAULT uuidv7(),
    contract_id   uuid         NOT NULL REFERENCES contracts (id),
    role          varchar(20)  NOT NULL CHECK (role IN ('DEBTOR', 'CO_DEBTOR', 'CREDITOR', 'GUARANTOR')),
    person_type   varchar(10)  NOT NULL CHECK (person_type IN ('NATURAL', 'LEGAL')),
    name          varchar(200) NOT NULL,
    document_enc  bytea        NOT NULL,
    document_hash bytea        NOT NULL,
    email         varchar(254),
    created_at    timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON COLUMN contract_parties.document_enc IS 'CPF/CNPJ cifrado com AES-GCM pela aplicação';
COMMENT ON COLUMN contract_parties.document_hash IS 'HMAC-SHA256 do documento normalizado (índice cego para busca exata)';
CREATE INDEX ix_contract_parties_contract ON contract_parties (contract_id);
CREATE INDEX ix_contract_parties_document_hash ON contract_parties (document_hash);

--changeset hub:contract-004-status-history
CREATE TABLE contract_status_history (
    id             uuid         NOT NULL DEFAULT uuidv7(),
    contract_id    uuid         NOT NULL REFERENCES contracts (id),
    from_status    varchar(30),
    to_status      varchar(30)  NOT NULL,
    reason         varchar(500),
    actor_type     varchar(20)  NOT NULL CHECK (actor_type IN ('USER', 'API_CLIENT', 'SYSTEM', 'REGISTRY')),
    actor_id       varchar(100),
    correlation_id varchar(64),
    occurred_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);
COMMENT ON TABLE contract_status_history IS 'Histórico imutável de transições de status, particionado por mês';
CREATE TABLE contract_status_history_default PARTITION OF contract_status_history DEFAULT;
SELECT hub_create_monthly_partitions('contract_status_history', (current_date - interval '1 month')::date, 15);
CREATE INDEX ix_contract_status_history_contract ON contract_status_history (contract_id, occurred_at);
CREATE TRIGGER trg_contract_status_history_immutable
    BEFORE UPDATE OR DELETE ON contract_status_history
    FOR EACH ROW EXECUTE FUNCTION hub_forbid_mutation();

--changeset hub:contract-005-events
CREATE TABLE contract_events (
    id             uuid         NOT NULL DEFAULT uuidv7(),
    contract_id    uuid         NOT NULL REFERENCES contracts (id),
    event_type     varchar(80)  NOT NULL,
    description    varchar(500) NOT NULL,
    payload        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    actor_type     varchar(20)  NOT NULL CHECK (actor_type IN ('USER', 'API_CLIENT', 'SYSTEM', 'REGISTRY')),
    actor_id       varchar(100),
    correlation_id varchar(64),
    occurred_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);
COMMENT ON TABLE contract_events IS
    'Linha do tempo detalhada (documento anexado, decisão de roteamento, protocolo, tentativas); imutável, particionada por mês';
CREATE TABLE contract_events_default PARTITION OF contract_events DEFAULT;
SELECT hub_create_monthly_partitions('contract_events', (current_date - interval '1 month')::date, 15);
CREATE INDEX ix_contract_events_contract ON contract_events (contract_id, occurred_at);
CREATE TRIGGER trg_contract_events_immutable
    BEFORE UPDATE OR DELETE ON contract_events
    FOR EACH ROW EXECUTE FUNCTION hub_forbid_mutation();
