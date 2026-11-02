# Arquitetura

Documento completo e atualizado (diagramas, fluxo, máquina de estados, modelo de dados, comunicação):
https://claude.ai/artifact/VXuVy1cfkg2aK6BZ7fHp8m

Este arquivo resume as decisões; o detalhe de cada uma está nos ADRs.

## Visão geral

```
Portais Angular ─┐                           ┌─ PostgreSQL 18 (1 banco por serviço)
                 ├─ api-gateway ─ serviços ──┤
Sistemas bancos ─┘        (REST /api/v1)     └─ RabbitMQ 4.1 (eventos e comandos)
                                                    │
                          integration-service ── Registradoras externas (REST, webhook)
```

- **Síncrono só na borda, assíncrono no núcleo.** O banco recebe `202 Accepted` assim que o contrato é validado e gravado; o resto anda por mensagens.
- **contract-service orquestra** o ciclo de vida e é o único que muda o status de um contrato.
- **Só o integration-service** fala com registradoras e guarda credenciais externas.
- **Nenhum serviço lê o banco de outro**: dados de outro contexto chegam por evento e viram réplica local.

## Decisões registradas

| ADR | Decisão |
| --- | --- |
| [0001](adr/0001-microsservicos-em-monorepo.md) | Microsserviços por contexto delimitado, em monorepo Maven |
| [0002](adr/0002-banco-por-servico.md) | Um banco PostgreSQL por serviço; modelagem, imutabilidade e particionamento |
| [0003](adr/0003-mensageria-confiavel.md) | RabbitMQ com Outbox, idempotência, retry com backoff e DLQ |

## Ciclo de vida do contrato

```
RECEIVED → VALIDATING → PENDING_PROCESSING → PROCESSING → SENT_TO_REGISTRY → REGISTRY_PROCESSING → APPROVED → COMPLETED
               │                                  │                                  │
               └→ VALIDATION_FAILED               └→ ERROR (retentativas esgotadas)  ├→ REJECTED
                                                                                     └→ PENDING_DOCUMENTS ⇄ REGISTRY_PROCESSING
CANCELLED: a pedido do banco ou do Hub; a partir de PROCESSING, só se a registradora aceitar o cancelamento.
```

Cada transição grava uma linha imutável em `contract_status_history`.
