# ADR 0004 — Autenticação, autorização e isolamento entre instituições

**Status:** aceita · **Data:** 2026-11-02

## Autenticação
- **auth-service emite os tokens.** Access token JWT RS256 de 15 min; refresh token opaco de 8 h, rotacionado a cada uso, com teto de 12 h por sessão.
- **Reuso de refresh token revoga a sessão inteira.** Reapresentar um token já trocado significa que existe uma cópia (do usuário ou de um atacante); como não dá para saber qual, a família toda é revogada e o evento vai para a auditoria.
- **Chaves de assinatura no banco, cifradas.** RSA 2048 em `signing_keys`, privada cifrada com AES-256-GCM (`HUB_AUTH_ENCRYPTION_KEY`, contexto = `kid`). Os serviços validam pelo JWKS (`/oauth2/jwks`), sem chamar o auth-service a cada requisição. Chaves aposentadas ficam no JWKS por 24 h.
- **Senhas com Argon2id** (parâmetros do Spring Security 5.8+). Política NIST 800-63B: mínimo de 12 caracteres, sem regras de composição, recusa de senhas comuns e derivadas do e-mail.
- **Respostas que não revelam contas.** E-mail inexistente, senha errada, conta bloqueada ou desativada e instituição inativa respondem igual (401 `invalid_credentials`); o tempo de resposta é igualado com uma verificação simulada. O motivo real vai para a auditoria.
- **Bloqueio temporário:** 5 senhas erradas seguidas bloqueiam a conta por 15 min.
- **Ativação por e-mail:** usuário criado por administrador nasce `PENDING_ACTIVATION` e define a senha por link de uso único (72 h). Redefinição de senha: link de 30 min. Trocar ou redefinir a senha encerra todas as sessões.
- **Sistemas dos bancos:** OAuth2 client credentials (`/oauth2/token`). Escopos restritos a contratos e documentos; secret mostrado uma única vez; limite de requisições por credencial levado no token (claim `rl`).

## Autorização (RBAC)
- 9 papéis fixos, cada um amarrado a um tipo de instituição (HUB, BANK, REGISTRY); 24 permissões `recurso:ação`.
- O token carrega papéis e permissões; cada serviço decide com `@PreAuthorize("hasAuthority('...')")` e com regras de escopo nos casos de uso.
- Ninguém altera o próprio status nem os próprios papéis. Mudar papéis encerra as sessões do usuário.

## Isolamento entre instituições (multi-tenant)
Duas camadas independentes:
1. **Aplicação:** a instituição vem sempre do token assinado, nunca do corpo da requisição. Recurso de outra instituição responde 404, como se não existisse.
2. **Banco de dados:** Row-Level Security com `FORCE` nas tabelas de contratos e documentos. O `TenantAwareDataSource` define `app.tenant_type` e `app.tenant_id` em cada conexão retirada do pool e limpa ao devolver. Sem tenant, nenhuma linha é visível (falha fechada). Processos internos (consumidores, jobs) usam `TenantContextHolder.runAsSystem`.

O usuário de cada serviço é dono do próprio banco e **não** é superusuário (superusuários ignoram RLS). Os testes de isolamento assumem um papel comum com `SET LOCAL ROLE` pelo mesmo motivo.

## Borda (api-gateway)
- Valida o JWT antes de encaminhar (os serviços validam de novo).
- Limite de requisições por minuto no Redis: credencial de API (claim `rl`), usuário (300) e IP em caminhos públicos (30, cobre tentativas de login). Se o Redis cair, o limitador libera (falha aberta) e registra aviso; o bloqueio de conta continua no auth-service.
- CORS só para as origens do portal; `/actuator/gateway` não é exposto.

## Decisões pequenas
- `ddl-auto: none`: o schema pertence ao Liquibase; os testes de integração exercitam as entidades contra o banco real.
- Mapeamento entidade ↔ domínio escrito à mão (sem MapStruct): poucos campos e nenhum processador de anotação no build.
- E-mails de acesso saem por SMTP direto do auth-service só depois do commit. Na Etapa 5 passam ao notification-service por eventos.
