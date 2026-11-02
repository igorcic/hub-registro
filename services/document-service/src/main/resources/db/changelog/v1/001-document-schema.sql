--liquibase formatted sql

-- Banco hub_document: metadados, versões e integridade dos documentos.
-- O conteúdo binário fica no armazenamento de objetos (S3/MinIO), nunca no banco.

--changeset hub:document-001-contract-access
CREATE TABLE contract_access (
    contract_id    uuid        PRIMARY KEY,
    bank_id        uuid        NOT NULL,
    registry_id    uuid,
    source_version bigint      NOT NULL DEFAULT 0,
    updated_at     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE contract_access IS
    'Réplica por eventos de quem pode ver os documentos de cada contrato: o banco dono e a registradora designada';
CREATE INDEX ix_contract_access_bank ON contract_access (bank_id);
CREATE INDEX ix_contract_access_registry ON contract_access (registry_id) WHERE registry_id IS NOT NULL;

--changeset hub:document-002-contract-documents
CREATE TABLE contract_documents (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    contract_id    uuid         NOT NULL,
    bank_id        uuid         NOT NULL,
    document_type  varchar(50)  NOT NULL,
    version        integer      NOT NULL CHECK (version > 0),
    file_name      varchar(255) NOT NULL,
    mime_type      varchar(100) NOT NULL,
    size_bytes     bigint       NOT NULL CHECK (size_bytes > 0),
    sha256         char(64)     NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    storage_bucket varchar(63)  NOT NULL,
    storage_key    varchar(500) NOT NULL UNIQUE,
    status         varchar(20)  NOT NULL DEFAULT 'PENDING_UPLOAD'
                   CHECK (status IN ('PENDING_UPLOAD', 'AVAILABLE', 'REJECTED')),
    status_reason  varchar(500),
    uploaded_by    uuid         NOT NULL,
    uploaded_at    timestamptz,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    deleted_by     uuid,
    CONSTRAINT ux_contract_documents_version UNIQUE (contract_id, document_type, version)
);
COMMENT ON COLUMN contract_documents.version IS 'Cada novo upload do mesmo tipo cria uma versão; versões anteriores são mantidas';
COMMENT ON COLUMN contract_documents.sha256 IS 'Hash do conteúdo, verificado no upload e enviado à registradora para conferência';
CREATE INDEX ix_contract_documents_contract ON contract_documents (contract_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_contract_documents_bank ON contract_documents (bank_id, created_at DESC);
