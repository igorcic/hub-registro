--liquibase formatted sql

-- SOMENTE DESENVOLVIMENTO (contexto "dev"): instituições e um usuário por perfil.
-- Senha de todos: Hub@dev2026!
-- Os mesmos ids de instituição serão usados pelo bank-service e pelo registry-service na Etapa 3.

--changeset hub:auth-dev-001-seed context:dev
INSERT INTO institutions (id, type, name, status) VALUES
    ('01900000-0000-7000-8000-000000000101', 'BANK',     'Banco Exemplo S.A.',      'ACTIVE'),
    ('01900000-0000-7000-8000-000000000102', 'BANK',     'Banco Teste S.A.',        'ACTIVE'),
    ('01900000-0000-7000-8000-000000000201', 'REGISTRY', 'Registradora Exemplo',    'ACTIVE');

INSERT INTO users (id, institution_id, full_name, email, password_hash, status, password_changed_at) VALUES
    ('01900000-0000-7000-8000-000000001001', '01900000-0000-7000-8000-000000000001', 'Super Admin',          'superadmin@hub.dev',             '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000001002', '01900000-0000-7000-8000-000000000001', 'Operador do Hub',      'operador@hub.dev',               '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000001003', '01900000-0000-7000-8000-000000000001', 'Auditor do Hub',       'auditor@hub.dev',                '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000001101', '01900000-0000-7000-8000-000000000101', 'Admin Banco Exemplo',  'admin@banco-exemplo.dev',        '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000001102', '01900000-0000-7000-8000-000000000101', 'Operador Banco Exemplo','operador@banco-exemplo.dev',    '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000001103', '01900000-0000-7000-8000-000000000101', 'Consulta Banco Exemplo','consulta@banco-exemplo.dev',    '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000001201', '01900000-0000-7000-8000-000000000102', 'Admin Banco Teste',    'admin@banco-teste.dev',          '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000002101', '01900000-0000-7000-8000-000000000201', 'Admin Registradora',   'admin@registradora-exemplo.dev', '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000002102', '01900000-0000-7000-8000-000000000201', 'Operador Registradora','operador@registradora-exemplo.dev', '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now()),
    ('01900000-0000-7000-8000-000000002103', '01900000-0000-7000-8000-000000000201', 'Consulta Registradora','consulta@registradora-exemplo.dev', '$argon2id$v=19$m=16384,t=2,p=1$UHJyb0aJF5N7I+30yuAcmQ$qrJUAAOMo2KJJR+tHbGPzmCVnz/Y5a4klUmRcyqE7AI', 'ACTIVE', now());

INSERT INTO user_roles (user_id, role_code) VALUES
    ('01900000-0000-7000-8000-000000001001', 'SUPER_ADMIN'),
    ('01900000-0000-7000-8000-000000001002', 'HUB_OPERATOR'),
    ('01900000-0000-7000-8000-000000001003', 'HUB_AUDITOR'),
    ('01900000-0000-7000-8000-000000001101', 'BANK_ADMIN'),
    ('01900000-0000-7000-8000-000000001102', 'BANK_OPERATOR'),
    ('01900000-0000-7000-8000-000000001103', 'BANK_VIEWER'),
    ('01900000-0000-7000-8000-000000001201', 'BANK_ADMIN'),
    ('01900000-0000-7000-8000-000000002101', 'REGISTRY_ADMIN'),
    ('01900000-0000-7000-8000-000000002102', 'REGISTRY_OPERATOR'),
    ('01900000-0000-7000-8000-000000002103', 'REGISTRY_VIEWER');
