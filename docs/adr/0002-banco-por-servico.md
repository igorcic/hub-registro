# ADR 0002 — Um banco PostgreSQL por serviço

**Status:** aceita · **Data:** 2026-11-01

## Decisão
Cada serviço é dono exclusivo do seu banco lógico (`hub_<serviço>`) e do usuário que o acessa. Nenhum serviço lê o banco de outro. Dado de outro contexto chega por evento e é mantido como réplica local (ex.: `institutions` no auth-service e no contract-service, `registry_eligibility` no routing-service), com `source_version` para descartar eventos fora de ordem.

Em desenvolvimento, uma instância PostgreSQL hospeda os dez bancos; em produção podem ser instâncias separadas sem mudança de código.

## Escolhas de modelagem
- **PostgreSQL 18:** `uuidv7()` nativo gera chaves ordenáveis no tempo (índices B-tree mais compactos que UUID v4).
- **Migrações em SQL formatado do Liquibase:** partições, triggers e índices parciais são expressos diretamente, sem tradução de YAML.
- **Históricos e auditoria imutáveis:** trigger `hub_forbid_mutation()` bloqueia UPDATE e DELETE no próprio banco.
- **Particionamento mensal** de `contract_status_history`, `contract_events`, `integration_logs` e `audit_logs`, com partição DEFAULT de segurança; retenção por descarte de partição.
- **Exclusão lógica** (`deleted_at`) só em cadastros, com índices únicos parciais; contratos nunca são apagados, encerram-se por estado.
- **CNPJ** aceita o formato alfanumérico da Receita Federal (12 posições alfanuméricas + 2 dígitos).
- **CPF/CNPJ das partes** só cifrado (`document_enc`) e com índice cego HMAC (`document_hash`).
