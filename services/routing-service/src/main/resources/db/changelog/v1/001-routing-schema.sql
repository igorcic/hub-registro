--liquibase formatted sql

-- Banco hub_routing: regras configuráveis e decisão de qual registradora recebe cada contrato.

--changeset hub:routing-001-registry-eligibility
CREATE TABLE registry_eligibility (
    registry_id        uuid        PRIMARY KEY,
    status             varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    integration_health varchar(20) NOT NULL DEFAULT 'UNKNOWN' CHECK (integration_health IN ('UNKNOWN', 'UP', 'DEGRADED', 'DOWN')),
    contract_types     text[]      NOT NULL DEFAULT '{}',
    daily_capacity     integer     CHECK (daily_capacity > 0),
    source_version     bigint      NOT NULL DEFAULT 0,
    updated_at         timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE registry_eligibility IS
    'Réplica por eventos (registry-service e integration-service): status, saúde da integração, tipos atendidos e capacidade';

CREATE TABLE registry_service_areas (
    registry_id uuid    NOT NULL REFERENCES registry_eligibility (registry_id),
    uf          char(2) NOT NULL CHECK (hub_is_valid_uf(uf)),
    ibge_code   char(7) CHECK (ibge_code ~ '^[0-9]{7}$')
);
COMMENT ON COLUMN registry_service_areas.ibge_code IS 'Nulo = a UF inteira';
CREATE UNIQUE INDEX ux_routing_service_areas ON registry_service_areas (registry_id, uf, COALESCE(ibge_code, ''));
CREATE INDEX ix_routing_service_areas_location ON registry_service_areas (uf, ibge_code);

--changeset hub:routing-002-rules
CREATE TABLE routing_rules (
    id               uuid          PRIMARY KEY DEFAULT uuidv7(),
    name             varchar(100)  NOT NULL,
    description      varchar(500),
    priority         integer       NOT NULL CHECK (priority >= 0),
    contract_type    varchar(50),
    operation_type   varchar(50),
    guarantee_type   varchar(50),
    uf               char(2)       CHECK (uf IS NULL OR hub_is_valid_uf(uf)),
    ibge_code        char(7)       CHECK (ibge_code ~ '^[0-9]{7}$'),
    min_amount       numeric(18,2) CHECK (min_amount >= 0),
    max_amount       numeric(18,2),
    extra_conditions jsonb         NOT NULL DEFAULT '{}'::jsonb,
    exclusive        boolean       NOT NULL DEFAULT false,
    strategy         varchar(20)   NOT NULL DEFAULT 'PRIORITY'
                     CHECK (strategy IN ('PRIORITY', 'WEIGHTED', 'LEAST_LOADED', 'ROUND_ROBIN')),
    active           boolean       NOT NULL DEFAULT true,
    valid_from       timestamptz,
    valid_to         timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_by       uuid,
    version          bigint        NOT NULL DEFAULT 0,
    deleted_at       timestamptz,
    CHECK (ibge_code IS NULL OR uf IS NOT NULL),
    CHECK (max_amount IS NULL OR min_amount IS NULL OR max_amount >= min_amount),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from)
);
COMMENT ON TABLE routing_rules IS
    'Critério nulo = vale para qualquer valor. Avaliadas por prioridade crescente; a primeira com candidata elegível decide';
COMMENT ON COLUMN routing_rules.exclusive IS
    'Regra exclusiva (ex.: competência territorial da circunscrição): se nenhuma candidata estiver elegível, o contrato NÃO cai para regras seguintes';
COMMENT ON COLUMN routing_rules.extra_conditions IS 'Regras comerciais adicionais, validadas pela aplicação';
CREATE INDEX ix_routing_rules_active_priority ON routing_rules (priority) WHERE active AND deleted_at IS NULL;
CREATE INDEX ix_routing_rules_location ON routing_rules (uf, ibge_code) WHERE deleted_at IS NULL;

CREATE TABLE routing_rule_targets (
    id          uuid    PRIMARY KEY DEFAULT uuidv7(),
    rule_id     uuid    NOT NULL REFERENCES routing_rules (id),
    registry_id uuid    NOT NULL REFERENCES registry_eligibility (registry_id),
    priority    integer NOT NULL DEFAULT 0 CHECK (priority >= 0),
    weight      integer NOT NULL DEFAULT 100 CHECK (weight > 0),
    CONSTRAINT ux_routing_rule_targets UNIQUE (rule_id, registry_id)
);
CREATE INDEX ix_routing_rule_targets_registry ON routing_rule_targets (registry_id);

--changeset hub:routing-003-load-and-decisions
CREATE TABLE registry_daily_load (
    registry_id    uuid    NOT NULL REFERENCES registry_eligibility (registry_id),
    day            date    NOT NULL,
    assigned_count integer NOT NULL DEFAULT 0 CHECK (assigned_count >= 0),
    PRIMARY KEY (registry_id, day)
);
COMMENT ON TABLE registry_daily_load IS 'Contratos atribuídos por dia: base da capacidade operacional e da estratégia LEAST_LOADED';

CREATE TABLE routing_decisions (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    contract_id    uuid         NOT NULL,
    outcome        varchar(30)  NOT NULL CHECK (outcome IN ('ROUTED', 'NO_ELIGIBLE_REGISTRY')),
    rule_id        uuid         REFERENCES routing_rules (id),
    registry_id    uuid,
    strategy       varchar(20),
    candidates     jsonb        NOT NULL DEFAULT '[]'::jsonb,
    reason         varchar(500) NOT NULL,
    correlation_id varchar(64),
    decided_at     timestamptz  NOT NULL DEFAULT now(),
    CHECK (outcome <> 'ROUTED' OR registry_id IS NOT NULL)
);
COMMENT ON COLUMN routing_decisions.candidates IS 'Cada registradora avaliada e o motivo de ter sido aceita ou descartada';
CREATE INDEX ix_routing_decisions_contract ON routing_decisions (contract_id, decided_at DESC);
CREATE INDEX ix_routing_decisions_registry ON routing_decisions (registry_id, decided_at DESC) WHERE registry_id IS NOT NULL;
