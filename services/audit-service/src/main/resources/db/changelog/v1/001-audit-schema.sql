--liquibase formatted sql

-- Banco hub_audit: trilha de auditoria de todas as operações, alimentada por eventos de todos os serviços.

--changeset hub:audit-001-audit-logs
CREATE TABLE audit_logs (
    id                   uuid         NOT NULL DEFAULT uuidv7(),
    occurred_at          timestamptz  NOT NULL,
    recorded_at          timestamptz  NOT NULL DEFAULT now(),
    source_service       varchar(60)  NOT NULL,
    source_event_id      varchar(100) NOT NULL,
    actor_type           varchar(20)  NOT NULL CHECK (actor_type IN ('USER', 'API_CLIENT', 'SYSTEM', 'REGISTRY')),
    actor_id             varchar(100),
    actor_institution_id uuid,
    institution_id       uuid,
    action               varchar(100) NOT NULL,
    resource_type        varchar(60)  NOT NULL,
    resource_id          varchar(100),
    outcome              varchar(10)  NOT NULL CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE')),
    before_state         jsonb,
    after_state          jsonb,
    client_ip            varchar(45),
    user_agent           varchar(500),
    correlation_id       varchar(64),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);
COMMENT ON TABLE audit_logs IS 'Somente inserção (trigger bloqueia UPDATE e DELETE), particionada por mês';
COMMENT ON COLUMN audit_logs.institution_id IS 'Instituição dona do recurso afetado: filtra o que cada auditor pode ver';
COMMENT ON COLUMN audit_logs.before_state IS 'Estado anterior com dados pessoais já mascarados pelo serviço de origem';
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;
SELECT hub_create_monthly_partitions('audit_logs', (current_date - interval '1 month')::date, 15);
CREATE INDEX ix_audit_logs_institution ON audit_logs (institution_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_correlation ON audit_logs (correlation_id);
CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION hub_forbid_mutation();
