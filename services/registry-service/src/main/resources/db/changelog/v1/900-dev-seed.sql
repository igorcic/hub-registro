--liquibase formatted sql

-- SOMENTE DESENVOLVIMENTO (contexto "dev"): a mesma registradora do seed do auth-service (mesmo id), ativa,
-- atendendo SP e RJ inteiros e o município de Belo Horizonte.

--changeset hub:registry-dev-001-seed context:dev
INSERT INTO registries (id, legal_name, trade_name, cnpj, code, status, daily_capacity,
                        street, number, district, city, ibge_code, uf, zip_code) VALUES
    ('01900000-0000-7000-8000-000000000201', 'Registradora Exemplo Ltda.', 'Registradora Exemplo', '12ABC34501DE35',
     'REGISTRADORA_EXEMPLO', 'ACTIVE', 5000, 'Avenida Paulista', '1000', 'Bela Vista', 'São Paulo', '3550308', 'SP', '01310100');

INSERT INTO registry_contract_types (registry_id, contract_type) VALUES
    ('01900000-0000-7000-8000-000000000201', 'RESIDENTIAL'),
    ('01900000-0000-7000-8000-000000000201', 'COMMERCIAL');

INSERT INTO registry_service_areas (registry_id, uf, ibge_code) VALUES
    ('01900000-0000-7000-8000-000000000201', 'SP', NULL),
    ('01900000-0000-7000-8000-000000000201', 'RJ', NULL),
    ('01900000-0000-7000-8000-000000000201', 'MG', '3106200');

INSERT INTO registry_technical_contacts (registry_id, name, email, phone, job_title, is_primary) VALUES
    ('01900000-0000-7000-8000-000000000201', 'Diego Rocha', 'diego.rocha@registradora-exemplo.dev', '+5511988880001',
     'Coordenador de integração', true);
