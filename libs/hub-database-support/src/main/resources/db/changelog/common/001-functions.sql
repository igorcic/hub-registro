--liquibase formatted sql

-- Funções compartilhadas, incluídas primeiro pelo changelog mestre de cada serviço.

--changeset hub:common-001-forbid-mutation splitStatements:false runOnChange:true
CREATE OR REPLACE FUNCTION hub_forbid_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'A tabela % é somente inserção: % não é permitido', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;
COMMENT ON FUNCTION hub_forbid_mutation() IS 'Trigger BEFORE UPDATE OR DELETE para históricos e trilhas de auditoria imutáveis';

--changeset hub:common-002-monthly-partitions splitStatements:false runOnChange:true
CREATE OR REPLACE FUNCTION hub_create_monthly_partitions(p_parent regclass, p_from date, p_months integer)
RETURNS integer
LANGUAGE plpgsql AS $$
DECLARE
    v_schema  text;
    v_table   text;
    v_start   date := date_trunc('month', p_from)::date;
    v_end     date;
    v_name    text;
    v_created integer := 0;
BEGIN
    SELECT n.nspname, c.relname INTO v_schema, v_table
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE c.oid = p_parent;

    FOR i IN 1 .. p_months LOOP
        v_end  := (v_start + interval '1 month')::date;
        v_name := format('%s_%s', v_table, to_char(v_start, 'YYYYMM'));
        IF to_regclass(format('%I.%I', v_schema, v_name)) IS NULL THEN
            EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.%I FOR VALUES FROM (%L) TO (%L)',
                           v_schema, v_name, v_schema, v_table, v_start, v_end);
            v_created := v_created + 1;
        END IF;
        v_start := v_end;
    END LOOP;
    RETURN v_created;
END;
$$;
COMMENT ON FUNCTION hub_create_monthly_partitions(regclass, date, integer)
    IS 'Cria partições mensais ausentes a partir de p_from; idempotente. Chamada pelas migrações e pelo job de manutenção';

--changeset hub:common-003-validators splitStatements:false runOnChange:true
CREATE OR REPLACE FUNCTION hub_is_valid_uf(p_uf text) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT p_uf IN ('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB',
                    'PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO');
$$;

-- Formato do CNPJ: 12 posições alfanuméricas + 2 dígitos verificadores numéricos.
-- Cobre o CNPJ numérico tradicional e o alfanumérico emitido pela Receita desde julho de 2026.
-- O cálculo dos dígitos verificadores é feito na aplicação.
CREATE OR REPLACE FUNCTION hub_is_valid_cnpj_format(p_cnpj text) RETURNS boolean
LANGUAGE sql IMMUTABLE AS $$
    SELECT p_cnpj ~ '^[0-9A-Z]{12}[0-9]{2}$';
$$;
