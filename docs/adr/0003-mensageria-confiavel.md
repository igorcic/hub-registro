# ADR 0003 — Mensageria confiável com RabbitMQ e Outbox

**Status:** aceita · **Data:** 2026-11-01

## Decisão
- **Outbox:** todo evento é gravado em `outbox_events` na mesma transação da mudança de estado; um relay publica com publisher confirms e marca `published_at`. Entrega pelo menos uma vez.
- **Idempotência:** cada consumidor grava `(message_id, consumer)` em `processed_messages` na transação do efeito.
- **Topologia como código:** `infra/rabbitmq/definitions.json` é a fonte única de exchanges, filas e bindings, carregada pelo broker na inicialização. O usuário da aplicação não tem permissão de `configure`, então nenhum serviço cria ou altera filas.
- **Filas quorum** para todas as filas (replicadas, duráveis; padrão no RabbitMQ 4).
- **Retry com backoff exponencial** por filas de espera com TTL (10 s, 1 min, 5 min, 30 min): a mensagem é publicada em `hub.retry` com routing key `<nível>.<fila-original>` e, ao expirar, `hub.retry.return` a devolve à fila original.
- **DLQ:** esgotadas as tentativas ou erro permanente, NACK sem requeue leva a mensagem a `contract.dead-letter` (fluxo do contrato) ou `platform.dead-letter` (réplicas e auditoria). O cabeçalho `x-death` identifica a fila de origem para reprocessamento.

## Exchanges
| Exchange | Tipo | Uso |
| --- | --- | --- |
| `hub.contract.events` | topic | fatos do ciclo de vida do contrato |
| `hub.contract.commands` | direct | trabalho sobre um contrato |
| `hub.platform.events` | topic | cadastro de bancos e registradoras, saúde das integrações |
| `hub.retry` / `hub.retry.return` | topic | espera e retorno das retentativas |
| `hub.dlx` | direct | dead letter |

## Antecipação para a Etapa 3 (2026-11-08)
A base da mensageria foi trazida da Etapa 5 para a 3, para que bancos e registradoras cadastrados cheguem às réplicas já nesta etapa:
- `OutboxRelay` (hub-starter-messaging): lê lotes com `FOR UPDATE SKIP LOCKED`, publica com publisher confirms e `mandatory`, só marca `published_at` com ACK e rota; falha reagenda com espera de 2^n s (máx. 5 min).
- `ReliableMessageHandler`: ACK manual depois do commit; idempotência por `processed_messages`; `NonRetryableMessageException` vai direto para a DLQ; demais erros seguem os níveis de retry.
- Réplicas aplicam o evento só se `version` for maior que a aplicada (`source_version`); a saúde da integração usa `checkedAt` como ordem própria.
A Etapa 5 fica com o fluxo assíncrono do contrato sobre esta base.
