--liquibase formatted sql

-- Etapa 2: ajustes de sessão e links de e-mail, chaves de assinatura e catálogo de permissões.

--changeset hub:auth-007-session-and-links
ALTER TABLE refresh_tokens ADD COLUMN session_expires_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE refresh_tokens ALTER COLUMN session_expires_at DROP DEFAULT;
COMMENT ON COLUMN refresh_tokens.session_expires_at IS 'Fim absoluto da sessão desde o login; rotações não estendem';

ALTER TABLE password_reset_tokens ADD COLUMN purpose varchar(20) NOT NULL DEFAULT 'RESET'
    CHECK (purpose IN ('ACTIVATION', 'RESET'));
ALTER TABLE password_reset_tokens ALTER COLUMN purpose DROP DEFAULT;
COMMENT ON TABLE password_reset_tokens IS 'Links de uso único enviados por e-mail: ativação de conta (72 h) e redefinição de senha (30 min)';

-- O bloqueio por tentativas é temporário (locked_until); o status não muda.
ALTER TABLE users DROP CONSTRAINT users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DISABLED'));

--changeset hub:auth-008-signing-keys
CREATE TABLE signing_keys (
    kid             varchar(64) PRIMARY KEY,
    algorithm       varchar(10) NOT NULL CHECK (algorithm = 'RS256'),
    public_key_der  bytea       NOT NULL,
    private_key_enc bytea       NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    retired_at      timestamptz
);
COMMENT ON TABLE signing_keys IS
    'Chaves RSA dos access tokens. A privada é cifrada com AES-GCM (HUB_AUTH_ENCRYPTION_KEY, contexto = kid). Uma ativa por vez; aposentadas continuam no JWKS por 24 h';
CREATE UNIQUE INDEX ux_signing_keys_single_active ON signing_keys ((true)) WHERE retired_at IS NULL;

--changeset hub:auth-009-permissions
INSERT INTO permissions (code, description) VALUES
    ('user:read',              'Consultar usuários'),
    ('user:manage',            'Gerenciar usuários da própria instituição'),
    ('user:manage-any',        'Gerenciar usuários de qualquer instituição'),
    ('api-client:manage',      'Gerenciar credenciais de API'),
    ('bank:read',              'Consultar bancos'),
    ('bank:manage',            'Cadastrar e alterar bancos'),
    ('registry:read',          'Consultar registradoras'),
    ('registry:manage',        'Cadastrar e alterar registradoras'),
    ('contract:read',          'Consultar contratos'),
    ('contract:submit',        'Enviar contratos para registro'),
    ('contract:cancel',        'Cancelar solicitações de registro'),
    ('contract:reprocess',     'Reprocessar contratos com falha'),
    ('contract:update-status', 'Atualizar status de contratos recebidos (registradora)'),
    ('document:read',          'Consultar e baixar documentos'),
    ('document:upload',        'Anexar documentos'),
    ('routing-rule:read',      'Consultar regras de roteamento'),
    ('routing-rule:manage',    'Gerenciar regras de roteamento'),
    ('integration:read',       'Consultar configurações de integração'),
    ('integration:manage',     'Gerenciar configurações de integração'),
    ('report:read',            'Consultar dashboards e relatórios'),
    ('audit:read',             'Consultar a trilha de auditoria'),
    ('operations:read',        'Monitorar filas e processamento'),
    ('operations:manage',      'Reprocessar mensagens e operar filas'),
    ('notification:read',      'Ver notificações');

-- SUPER_ADMIN recebe todas as permissões.
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', code FROM permissions;

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('HUB_OPERATOR', 'user:read'), ('HUB_OPERATOR', 'bank:read'), ('HUB_OPERATOR', 'registry:read'),
    ('HUB_OPERATOR', 'contract:read'), ('HUB_OPERATOR', 'contract:reprocess'), ('HUB_OPERATOR', 'document:read'),
    ('HUB_OPERATOR', 'routing-rule:read'), ('HUB_OPERATOR', 'integration:read'), ('HUB_OPERATOR', 'report:read'),
    ('HUB_OPERATOR', 'operations:read'), ('HUB_OPERATOR', 'operations:manage'), ('HUB_OPERATOR', 'notification:read'),

    ('HUB_AUDITOR', 'user:read'), ('HUB_AUDITOR', 'bank:read'), ('HUB_AUDITOR', 'registry:read'),
    ('HUB_AUDITOR', 'contract:read'), ('HUB_AUDITOR', 'document:read'), ('HUB_AUDITOR', 'routing-rule:read'),
    ('HUB_AUDITOR', 'integration:read'), ('HUB_AUDITOR', 'report:read'), ('HUB_AUDITOR', 'audit:read'),
    ('HUB_AUDITOR', 'operations:read'),

    ('BANK_ADMIN', 'user:read'), ('BANK_ADMIN', 'user:manage'), ('BANK_ADMIN', 'api-client:manage'),
    ('BANK_ADMIN', 'bank:read'), ('BANK_ADMIN', 'contract:read'), ('BANK_ADMIN', 'contract:submit'),
    ('BANK_ADMIN', 'contract:cancel'), ('BANK_ADMIN', 'document:read'), ('BANK_ADMIN', 'document:upload'),
    ('BANK_ADMIN', 'integration:read'), ('BANK_ADMIN', 'integration:manage'), ('BANK_ADMIN', 'report:read'),
    ('BANK_ADMIN', 'notification:read'),

    ('BANK_OPERATOR', 'bank:read'), ('BANK_OPERATOR', 'contract:read'), ('BANK_OPERATOR', 'contract:submit'),
    ('BANK_OPERATOR', 'contract:cancel'), ('BANK_OPERATOR', 'document:read'), ('BANK_OPERATOR', 'document:upload'),
    ('BANK_OPERATOR', 'report:read'), ('BANK_OPERATOR', 'notification:read'),

    ('BANK_VIEWER', 'bank:read'), ('BANK_VIEWER', 'contract:read'), ('BANK_VIEWER', 'document:read'),
    ('BANK_VIEWER', 'report:read'), ('BANK_VIEWER', 'notification:read'),

    ('REGISTRY_ADMIN', 'user:read'), ('REGISTRY_ADMIN', 'user:manage'), ('REGISTRY_ADMIN', 'registry:read'),
    ('REGISTRY_ADMIN', 'contract:read'), ('REGISTRY_ADMIN', 'contract:update-status'), ('REGISTRY_ADMIN', 'document:read'),
    ('REGISTRY_ADMIN', 'integration:read'), ('REGISTRY_ADMIN', 'integration:manage'), ('REGISTRY_ADMIN', 'report:read'),
    ('REGISTRY_ADMIN', 'notification:read'),

    ('REGISTRY_OPERATOR', 'registry:read'), ('REGISTRY_OPERATOR', 'contract:read'),
    ('REGISTRY_OPERATOR', 'contract:update-status'), ('REGISTRY_OPERATOR', 'document:read'),
    ('REGISTRY_OPERATOR', 'report:read'), ('REGISTRY_OPERATOR', 'notification:read'),

    ('REGISTRY_VIEWER', 'registry:read'), ('REGISTRY_VIEWER', 'contract:read'), ('REGISTRY_VIEWER', 'document:read'),
    ('REGISTRY_VIEWER', 'report:read'), ('REGISTRY_VIEWER', 'notification:read');
