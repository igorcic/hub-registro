--liquibase formatted sql

-- Incluído pelo changelog mestre de todo serviço que publica ou consome mensagens.
-- O evento é gravado nesta tabela na MESMA transação da mudança de estado; o relay publica no
-- RabbitMQ depois (com publisher confirms) e só então marca published_at. Entrega: pelo menos uma vez.

--changeset hub:messaging-001-outbox-events
CREATE TABLE outbox_events (
    id              uuid         PRIMARY KEY DEFAULT uuidv7(),
    aggregate_type  varchar(100) NOT NULL,
    aggregate_id    varchar(100) NOT NULL,
    event_type      varchar(150) NOT NULL,
    exchange        varchar(150) NOT NULL,
    routing_key     varchar(255) NOT NULL,
    payload         jsonb        NOT NULL,
    headers         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    correlation_id  varchar(64),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    attempts        integer      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error      text,
    published_at    timestamptz
);
COMMENT ON TABLE outbox_events IS 'Transactional Outbox: eventos aguardando publicação no RabbitMQ';

-- O relay lê só pendentes, em ordem, com FOR UPDATE SKIP LOCKED.
CREATE INDEX ix_outbox_events_pending ON outbox_events (next_attempt_at, created_at) WHERE published_at IS NULL;
-- Limpeza periódica dos já publicados.
CREATE INDEX ix_outbox_events_published_at ON outbox_events (published_at) WHERE published_at IS NOT NULL;

--changeset hub:messaging-002-processed-messages
CREATE TABLE processed_messages (
    message_id   varchar(100) NOT NULL,
    consumer     varchar(150) NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, consumer)
);
COMMENT ON TABLE processed_messages IS 'Idempotência: inserido na mesma transação do efeito; duplicata viola a PK e é descartada';
CREATE INDEX ix_processed_messages_processed_at ON processed_messages (processed_at);
