--liquibase formatted sql

-- Banco hub_integration: como falar com cada registradora e com cada banco (callbacks),
-- tentativas, logs de integração e webhooks recebidos. Único serviço com credenciais externas.

--changeset hub:integration-001-registry-integrations
CREATE TABLE registry_integrations (
    id                        uuid          PRIMARY KEY DEFAULT uuidv7(),
    registry_id               uuid          NOT NULL UNIQUE,
    adapter_type              varchar(50)   NOT NULL,
    base_url                  varchar(500)  NOT NULL CHECK (base_url ~ '^https?://'),
    auth_type                 varchar(30)   NOT NULL
                              CHECK (auth_type IN ('NONE', 'API_KEY', 'BASIC', 'OAUTH2_CLIENT_CREDENTIALS', 'MTLS')),
    auth_config_enc           bytea,
    webhook_secret_enc        bytea,
    connect_timeout_ms        integer       NOT NULL DEFAULT 5000 CHECK (connect_timeout_ms BETWEEN 100 AND 60000),
    read_timeout_ms           integer       NOT NULL DEFAULT 30000 CHECK (read_timeout_ms BETWEEN 100 AND 300000),
    retry_max_attempts        integer       NOT NULL DEFAULT 5 CHECK (retry_max_attempts BETWEEN 0 AND 20),
    retry_initial_interval_ms integer       NOT NULL DEFAULT 10000 CHECK (retry_initial_interval_ms > 0),
    retry_multiplier          numeric(4,2)  NOT NULL DEFAULT 2.00 CHECK (retry_multiplier >= 1),
    retry_max_interval_ms     integer       NOT NULL DEFAULT 1800000,
    rate_limit_per_minute     integer       CHECK (rate_limit_per_minute > 0),
    circuit_failure_rate_pct  smallint      NOT NULL DEFAULT 50 CHECK (circuit_failure_rate_pct BETWEEN 1 AND 100),
    status                    varchar(20)   NOT NULL DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    health_status             varchar(20)   NOT NULL DEFAULT 'UNKNOWN' CHECK (health_status IN ('UNKNOWN', 'UP', 'DEGRADED', 'DOWN')),
    last_health_check_at      timestamptz,
    created_at                timestamptz   NOT NULL DEFAULT now(),
    updated_at                timestamptz   NOT NULL DEFAULT now(),
    created_by                uuid,
    updated_by                uuid,
    version                   bigint        NOT NULL DEFAULT 0,
    CHECK (retry_max_interval_ms >= retry_initial_interval_ms)
);
COMMENT ON COLUMN registry_integrations.adapter_type IS 'Seleciona a implementação de RegistryGateway (ex.: GENERIC_REST, SIMULATOR)';
COMMENT ON COLUMN registry_integrations.auth_config_enc IS 'Credenciais (chave, usuário/senha, client secret) cifradas com AES-GCM';

CREATE TABLE registry_integration_endpoints (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    integration_id uuid         NOT NULL REFERENCES registry_integrations (id),
    operation      varchar(30)  NOT NULL
                   CHECK (operation IN ('SUBMIT_CONTRACT', 'SEND_DOCUMENT', 'QUERY_STATUS', 'CANCEL_CONTRACT', 'HEALTH_CHECK')),
    http_method    varchar(10)  NOT NULL CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    path           varchar(500) NOT NULL,
    CONSTRAINT ux_registry_integration_endpoints UNIQUE (integration_id, operation)
);

CREATE TABLE integration_certificates (
    id                 uuid         PRIMARY KEY DEFAULT uuidv7(),
    integration_id     uuid         NOT NULL REFERENCES registry_integrations (id),
    alias              varchar(100) NOT NULL,
    purpose            varchar(30)  NOT NULL CHECK (purpose IN ('CLIENT_AUTH', 'SERVER_TRUST', 'WEBHOOK_SIGNATURE')),
    keystore_type      varchar(10)  NOT NULL CHECK (keystore_type IN ('PKCS12', 'PEM')),
    keystore_enc       bytea        NOT NULL,
    subject_dn         varchar(500) NOT NULL,
    issuer_dn          varchar(500) NOT NULL,
    serial_number      varchar(100) NOT NULL,
    not_before         timestamptz  NOT NULL,
    not_after          timestamptz  NOT NULL,
    sha256_fingerprint char(64)     NOT NULL CHECK (sha256_fingerprint ~ '^[0-9a-f]{64}$'),
    active             boolean      NOT NULL DEFAULT true,
    uploaded_by        uuid         NOT NULL,
    uploaded_at        timestamptz  NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    CHECK (not_after > not_before)
);
CREATE INDEX ix_integration_certificates_integration ON integration_certificates (integration_id) WHERE deleted_at IS NULL;
-- Alerta de vencimento próximo.
CREATE INDEX ix_integration_certificates_expiry ON integration_certificates (not_after) WHERE active AND deleted_at IS NULL;

CREATE TABLE integration_health_checks (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    integration_id uuid         NOT NULL REFERENCES registry_integrations (id),
    status         varchar(20)  NOT NULL CHECK (status IN ('UP', 'DEGRADED', 'DOWN')),
    latency_ms     integer      CHECK (latency_ms >= 0),
    http_status    smallint,
    error_message  varchar(1000),
    checked_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_integration_health_checks_integration ON integration_health_checks (integration_id, checked_at DESC);

--changeset hub:integration-002-bank-integrations
CREATE TABLE bank_integrations (
    id                      uuid         PRIMARY KEY DEFAULT uuidv7(),
    bank_id                 uuid         NOT NULL UNIQUE,
    callback_url            varchar(500) CHECK (callback_url ~ '^https?://'),
    callback_auth_type      varchar(30)  NOT NULL DEFAULT 'HMAC'
                            CHECK (callback_auth_type IN ('NONE', 'HMAC', 'API_KEY', 'OAUTH2_CLIENT_CREDENTIALS')),
    callback_auth_config_enc bytea,
    allowed_ips             text[]       NOT NULL DEFAULT '{}',
    status                  varchar(20)  NOT NULL DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint       NOT NULL DEFAULT 0
);
COMMENT ON COLUMN bank_integrations.callback_url IS 'Endpoint do banco que recebe as mudanças de status dos seus contratos';
COMMENT ON COLUMN bank_integrations.allowed_ips IS 'Lista de IPs/CIDRs autorizados a chamar a API com as credenciais deste banco; vazia = sem restrição';

CREATE TABLE callback_deliveries (
    id               uuid         PRIMARY KEY DEFAULT uuidv7(),
    bank_id          uuid         NOT NULL,
    contract_id      uuid         NOT NULL,
    event_id         varchar(100) NOT NULL UNIQUE,
    event_type       varchar(80)  NOT NULL,
    status           varchar(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts         integer      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_http_status smallint,
    last_error       varchar(1000),
    next_attempt_at  timestamptz  NOT NULL DEFAULT now(),
    delivered_at     timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_callback_deliveries_pending ON callback_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_callback_deliveries_contract ON callback_deliveries (contract_id);

--changeset hub:integration-003-processing-attempts
CREATE TABLE processing_attempts (
    id              uuid          PRIMARY KEY DEFAULT uuidv7(),
    contract_id     uuid          NOT NULL,
    registry_id     uuid          NOT NULL,
    attempt_number  integer       NOT NULL CHECK (attempt_number > 0),
    operation       varchar(30)   NOT NULL
                    CHECK (operation IN ('SUBMIT_CONTRACT', 'SEND_DOCUMENT', 'QUERY_STATUS', 'CANCEL_CONTRACT')),
    idempotency_key varchar(100)  NOT NULL UNIQUE,
    status          varchar(20)   NOT NULL DEFAULT 'IN_PROGRESS'
                    CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_PERMANENT')),
    http_status     smallint,
    error_code      varchar(100),
    error_message   varchar(1000),
    protocol_number varchar(100),
    correlation_id  varchar(64),
    started_at      timestamptz   NOT NULL DEFAULT now(),
    finished_at     timestamptz,
    duration_ms     integer       CHECK (duration_ms >= 0)
);
COMMENT ON COLUMN processing_attempts.idempotency_key IS 'Enviada à registradora; um reenvio da mesma tentativa nunca cria registro duplicado';
CREATE INDEX ix_processing_attempts_contract ON processing_attempts (contract_id, started_at);
-- Métricas por registradora: taxa de falha e tempo de resposta.
CREATE INDEX ix_processing_attempts_registry ON processing_attempts (registry_id, started_at DESC);

--changeset hub:integration-004-logs-and-webhooks
CREATE TABLE integration_logs (
    id             uuid         NOT NULL DEFAULT uuidv7(),
    attempt_id     uuid,
    contract_id    uuid,
    registry_id    uuid,
    bank_id        uuid,
    direction      varchar(20)  NOT NULL
                   CHECK (direction IN ('OUTBOUND_REQUEST', 'INBOUND_RESPONSE', 'INBOUND_WEBHOOK', 'OUTBOUND_CALLBACK')),
    http_method    varchar(10),
    url            varchar(1000),
    http_status    smallint,
    headers        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    body           text,
    duration_ms    integer,
    correlation_id varchar(64),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
COMMENT ON TABLE integration_logs IS
    'Requisições e respostas trocadas com registradoras e bancos. Cabeçalhos e corpo gravados já mascarados (credenciais, CPF/CNPJ). Retenção por descarte de partição';
CREATE TABLE integration_logs_default PARTITION OF integration_logs DEFAULT;
SELECT hub_create_monthly_partitions('integration_logs', (current_date - interval '1 month')::date, 15);
CREATE INDEX ix_integration_logs_contract ON integration_logs (contract_id, created_at);
CREATE INDEX ix_integration_logs_registry ON integration_logs (registry_id, created_at DESC);
CREATE INDEX ix_integration_logs_correlation ON integration_logs (correlation_id);

CREATE TABLE webhook_receipts (
    id                uuid         PRIMARY KEY DEFAULT uuidv7(),
    registry_id       uuid         NOT NULL,
    external_event_id varchar(200) NOT NULL,
    signature_valid   boolean      NOT NULL,
    payload           jsonb        NOT NULL,
    received_at       timestamptz  NOT NULL DEFAULT now(),
    processed_at      timestamptz,
    CONSTRAINT ux_webhook_receipts_event UNIQUE (registry_id, external_event_id)
);
COMMENT ON TABLE webhook_receipts IS 'Webhooks recebidos; a chave única descarta reentregas da mesma notificação';
CREATE INDEX ix_webhook_receipts_unprocessed ON webhook_receipts (received_at) WHERE processed_at IS NULL;
