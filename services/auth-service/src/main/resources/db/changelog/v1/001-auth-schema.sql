--liquibase formatted sql

-- Banco hub_auth: identidade, papéis, permissões, sessões e credenciais de API dos bancos.

--changeset hub:auth-001-institutions
CREATE TABLE institutions (
    id             uuid         PRIMARY KEY,
    type           varchar(20)  NOT NULL CHECK (type IN ('HUB', 'BANK', 'REGISTRY')),
    name           varchar(200) NOT NULL,
    status         varchar(20)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    source_version bigint       NOT NULL DEFAULT 0,
    updated_at     timestamptz  NOT NULL DEFAULT now()
);
COMMENT ON TABLE institutions IS
    'Réplica local das instituições, mantida por eventos do bank-service e do registry-service. Bloqueia login de instituição inativa ou suspensa sem chamada síncrona.';
COMMENT ON COLUMN institutions.source_version IS 'Versão do agregado de origem; evento com versão menor ou igual é descartado (fora de ordem)';

-- A própria operadora do Hub é a única instituição criada aqui; bancos e registradoras chegam por evento.
INSERT INTO institutions (id, type, name, status)
VALUES ('01900000-0000-7000-8000-000000000001', 'HUB', 'Hub de Registro', 'ACTIVE');

--changeset hub:auth-002-users
CREATE TABLE users (
    id                    uuid         PRIMARY KEY DEFAULT uuidv7(),
    institution_id        uuid         NOT NULL REFERENCES institutions (id),
    full_name             varchar(200) NOT NULL,
    email                 varchar(254) NOT NULL,
    password_hash         varchar(255) NOT NULL,
    status                varchar(20)  NOT NULL DEFAULT 'PENDING_ACTIVATION'
                          CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'LOCKED', 'DISABLED')),
    failed_login_attempts integer      NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until          timestamptz,
    password_changed_at   timestamptz,
    last_login_at         timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    version               bigint       NOT NULL DEFAULT 0,
    deleted_at            timestamptz
);
COMMENT ON COLUMN users.password_hash IS 'Argon2id (formato PHC)';
-- Login por e-mail sem escolher instituição: e-mail único na plataforma entre usuários não excluídos.
CREATE UNIQUE INDEX ux_users_email ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_institution ON users (institution_id) WHERE deleted_at IS NULL;

--changeset hub:auth-003-roles-permissions
CREATE TABLE roles (
    code             varchar(50)  PRIMARY KEY,
    name             varchar(100) NOT NULL,
    description      varchar(500) NOT NULL,
    institution_type varchar(20)  NOT NULL CHECK (institution_type IN ('HUB', 'BANK', 'REGISTRY'))
);
COMMENT ON COLUMN roles.institution_type IS 'Um papel só pode ser concedido a usuário de instituição deste tipo';

CREATE TABLE permissions (
    code        varchar(100) PRIMARY KEY CHECK (code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$'),
    description varchar(500) NOT NULL
);
COMMENT ON COLUMN permissions.code IS 'Formato recurso:acao, ex.: contract:submit';

CREATE TABLE role_permissions (
    role_code       varchar(50)  NOT NULL REFERENCES roles (code),
    permission_code varchar(100) NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE user_roles (
    user_id    uuid        NOT NULL REFERENCES users (id),
    role_code  varchar(50) NOT NULL REFERENCES roles (code),
    granted_at timestamptz NOT NULL DEFAULT now(),
    granted_by uuid,
    PRIMARY KEY (user_id, role_code)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_code);

INSERT INTO roles (code, name, description, institution_type) VALUES
    ('SUPER_ADMIN',       'Super administrador',        'Acesso administrativo total à plataforma',               'HUB'),
    ('HUB_OPERATOR',      'Operador do Hub',            'Operação e acompanhamento de contratos e integrações',   'HUB'),
    ('HUB_AUDITOR',       'Auditor do Hub',             'Consulta e auditoria, sem alterações',                   'HUB'),
    ('BANK_ADMIN',        'Administrador do banco',     'Administra usuários e integrações do próprio banco',     'BANK'),
    ('BANK_OPERATOR',     'Operador do banco',          'Envia e acompanha contratos do próprio banco',           'BANK'),
    ('BANK_VIEWER',       'Consulta do banco',          'Somente consulta dos dados do próprio banco',            'BANK'),
    ('REGISTRY_ADMIN',    'Administrador da registradora', 'Administra usuários e configurações da registradora', 'REGISTRY'),
    ('REGISTRY_OPERATOR', 'Operador da registradora',   'Opera os contratos recebidos pela registradora',         'REGISTRY'),
    ('REGISTRY_VIEWER',   'Consulta da registradora',   'Somente consulta dos contratos recebidos',               'REGISTRY');

--changeset hub:auth-004-refresh-tokens
CREATE TABLE refresh_tokens (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    user_id        uuid         NOT NULL REFERENCES users (id),
    family_id      uuid         NOT NULL,
    token_hash     bytea        NOT NULL UNIQUE,
    issued_at      timestamptz  NOT NULL DEFAULT now(),
    expires_at     timestamptz  NOT NULL,
    revoked_at     timestamptz,
    revoked_reason varchar(50),
    replaced_by    uuid         REFERENCES refresh_tokens (id),
    client_ip      varchar(45),
    user_agent     varchar(500),
    CHECK (expires_at > issued_at)
);
COMMENT ON TABLE refresh_tokens IS
    'Rotação de refresh token: cada uso emite um novo da mesma família; reuso de um token já trocado revoga a família inteira';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 do token; o valor em claro nunca é armazenado';
CREATE INDEX ix_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

--changeset hub:auth-005-password-reset-tokens
CREATE TABLE password_reset_tokens (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id      uuid        NOT NULL REFERENCES users (id),
    token_hash   bytea       NOT NULL UNIQUE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    requested_ip varchar(45),
    CHECK (expires_at > created_at)
);
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id) WHERE used_at IS NULL;

--changeset hub:auth-006-api-clients
CREATE TABLE api_clients (
    id                    uuid         PRIMARY KEY DEFAULT uuidv7(),
    institution_id        uuid         NOT NULL REFERENCES institutions (id),
    name                  varchar(100) NOT NULL,
    client_id             varchar(64)  NOT NULL UNIQUE,
    client_secret_hash    varchar(255) NOT NULL,
    scopes                text[]       NOT NULL DEFAULT '{}',
    rate_limit_per_minute integer      NOT NULL DEFAULT 600 CHECK (rate_limit_per_minute > 0),
    status                varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    expires_at            timestamptz,
    last_used_at          timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    version               bigint       NOT NULL DEFAULT 0
);
COMMENT ON TABLE api_clients IS 'Credenciais OAuth2 client credentials para os sistemas dos bancos integrarem via API';
CREATE INDEX ix_api_clients_institution ON api_clients (institution_id);
