# ADR 0001 — Microsserviços por contexto delimitado, em monorepo

**Status:** aceita · **Data:** 2026-11-01

## Contexto
O escopo pede fronteiras rígidas entre domínios (autenticação, bancos, registradoras, contratos, documentos, roteamento, integração, notificação) e escala independente onde o volume se concentra (recepção de contratos e despacho às registradoras).

## Decisão
Um serviço Spring Boot por contexto delimitado, cada um com seu próprio banco, imagem e deploy. Todos ficam num único repositório Maven multimódulo, com bibliotecas técnicas compartilhadas em `libs/` (sem regra de negócio).

## Consequências
- Uma mudança de contrato de evento que toca dois serviços é um único commit e um único review.
- Bibliotecas compartilhadas só contêm infraestrutura (observabilidade, mensageria, testes), para não virar um monólito distribuído.
- Sem Eureka nem Config Server: DNS do Compose/Kubernetes e variáveis de ambiente bastam e reduzem peças móveis.
