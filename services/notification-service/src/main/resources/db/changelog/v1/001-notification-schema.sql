--liquibase formatted sql

-- Banco hub_notification: notificações internas (sino do portal) e e-mails enviados.

--changeset hub:notification-001-notifications
CREATE TABLE notifications (
    id                uuid         PRIMARY KEY DEFAULT uuidv7(),
    institution_id    uuid,
    recipient_user_id uuid,
    recipient_email   varchar(254),
    channel           varchar(10)  NOT NULL CHECK (channel IN ('EMAIL', 'IN_APP')),
    type              varchar(60)  NOT NULL,
    severity          varchar(10)  NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    title             varchar(200) NOT NULL,
    body              text         NOT NULL,
    payload           jsonb        NOT NULL DEFAULT '{}'::jsonb,
    status            varchar(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'READ')),
    attempts          integer      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error        varchar(1000),
    source_event_id   varchar(100),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    sent_at           timestamptz,
    read_at           timestamptz,
    CHECK (channel <> 'EMAIL' OR recipient_email IS NOT NULL),
    CHECK (channel <> 'IN_APP' OR recipient_user_id IS NOT NULL OR institution_id IS NOT NULL)
);
COMMENT ON COLUMN notifications.institution_id IS 'Instituição destinatária; nulo = equipe do Hub (alertas operacionais)';
COMMENT ON COLUMN notifications.recipient_user_id IS 'Nulo em notificação IN_APP = todos os usuários da instituição com permissão';
CREATE INDEX ix_notifications_user_inbox ON notifications (recipient_user_id, created_at DESC) WHERE channel = 'IN_APP';
CREATE INDEX ix_notifications_institution ON notifications (institution_id, created_at DESC);
CREATE INDEX ix_notifications_pending ON notifications (created_at) WHERE status = 'PENDING';
