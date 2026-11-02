--liquibase formatted sql

-- Banco hub_operations: modelo de leitura para dashboards e relatórios (projeção por eventos)
-- e registro das ações de reprocessamento de mensagens.

--changeset hub:operations-001-contract-projection
CREATE TABLE contract_projection (
    contract_id      uuid        PRIMARY KEY,
    bank_id          uuid        NOT NULL,
    registry_id      uuid,
    contract_type    varchar(50) NOT NULL,
    status           varchar(30) NOT NULL,
    received_at      timestamptz NOT NULL,
    submitted_at     timestamptz,
    completed_at     timestamptz,
    attempts         integer     NOT NULL DEFAULT 0,
    last_event_at    timestamptz NOT NULL,
    source_version   bigint      NOT NULL DEFAULT 0
);
COMMENT ON TABLE contract_projection IS
    'Uma linha por contrato, atualizada pelos eventos do contract-service. Base de volumes, taxas de sucesso e tempos médios sem consultar o banco transacional';
CREATE INDEX ix_contract_projection_bank ON contract_projection (bank_id, received_at DESC);
CREATE INDEX ix_contract_projection_registry ON contract_projection (registry_id, received_at DESC) WHERE registry_id IS NOT NULL;
CREATE INDEX ix_contract_projection_status ON contract_projection (status, last_event_at);
CREATE INDEX ix_contract_projection_received ON contract_projection (received_at);

--changeset hub:operations-002-message-actions
CREATE TABLE message_actions (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    action         varchar(20)  NOT NULL CHECK (action IN ('REQUEUE', 'DISCARD')),
    source_queue   varchar(150) NOT NULL,
    target_queue   varchar(150),
    message_id     varchar(100) NOT NULL,
    reason         varchar(500) NOT NULL,
    requested_by   uuid         NOT NULL,
    requested_at   timestamptz  NOT NULL DEFAULT now(),
    executed_at    timestamptz,
    outcome        varchar(20)  CHECK (outcome IN ('SUCCEEDED', 'FAILED')),
    error_message  varchar(1000),
    CHECK (action <> 'REQUEUE' OR target_queue IS NOT NULL)
);
COMMENT ON TABLE message_actions IS 'Reprocessamento manual de mensagens da DLQ: quem pediu, por quê e o resultado';
CREATE INDEX ix_message_actions_requested ON message_actions (requested_at DESC);
CREATE INDEX ix_message_actions_message ON message_actions (message_id);
