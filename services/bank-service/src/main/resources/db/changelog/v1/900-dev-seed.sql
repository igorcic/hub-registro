--liquibase formatted sql

-- SOMENTE DESENVOLVIMENTO (contexto "dev"): os mesmos bancos do seed do auth-service (mesmos ids).

--changeset hub:bank-dev-001-seed context:dev
INSERT INTO banks (id, legal_name, trade_name, cnpj, code, status, request_limit_per_minute) VALUES
    ('01900000-0000-7000-8000-000000000101', 'Banco Exemplo S.A.', 'Banco Exemplo', '33444555000181', 'BANCO_EXEMPLO', 'ACTIVE', 600),
    ('01900000-0000-7000-8000-000000000102', 'Banco Teste S.A.',   'Banco Teste',   '77888999000181', 'BANCO_TESTE',   'ACTIVE', 600);

INSERT INTO bank_contacts (bank_id, name, email, phone, area, is_primary) VALUES
    ('01900000-0000-7000-8000-000000000101', 'Ana Souza',    'ana.souza@banco-exemplo.dev',   '+5511999990001', 'BUSINESS',  true),
    ('01900000-0000-7000-8000-000000000101', 'Bruno Lima',   'bruno.lima@banco-exemplo.dev',  '+5511999990002', 'TECHNICAL', true),
    ('01900000-0000-7000-8000-000000000102', 'Carla Mendes', 'carla.mendes@banco-teste.dev',  '+5521999990003', 'TECHNICAL', true);
