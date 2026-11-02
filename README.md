# Hub de Registro de Contratos Imobiliários

Plataforma que intermedeia o registro de contratos imobiliários entre instituições financeiras (bancos) e empresas registradoras: recebe o contrato do banco, valida, escolhe a registradora, envia, acompanha o retorno e notifica o banco.

Arquitetura em **microsserviços Spring Boot 3 (Java 21)**, um banco PostgreSQL por serviço, comunicação assíncrona por **RabbitMQ** com Outbox Pattern, e portais em **Angular**. O documento de arquitetura completo (decisões, diagramas, fluxo, modelo de dados) está em [docs/arquitetura.md](docs/arquitetura.md).

## Estado das etapas

| Etapa | Escopo | Situação |
| --- | --- | --- |
| 1 | Arquitetura, estrutura dos projetos, banco de dados, infraestrutura local | Implementada · aguardando o primeiro `mvn verify` |
| 2 | Autenticação, usuários, papéis, permissões, multi-tenancy | Implementada · aguardando o primeiro `mvn verify` |
| 3 | Bancos e registradoras | Próxima |
| 4 | Contratos e documentos | — |
| 5 | RabbitMQ, processamento assíncrono, Outbox | — |
| 6 | Motor de roteamento | — |
| 7 | Integrações externas e simulador de registradoras | — |
| 8 | Portais Angular | — |
| 9 | Dashboards, relatórios, auditoria, observabilidade | — |
| 10 | Testes, documentação, conteinerização final | — |

> **Atenção:** até aqui o código foi escrito sem acesso ao Maven Central, então ainda não passou por compilação completa nem pelos testes do Maven. As migrações SQL e as regras de domínio (Java puro) foram verificadas à parte, num PostgreSQL real e com `javac`. A primeira tarefa antes da Etapa 3 é rodar `mvn verify` e corrigir o que aparecer.

## Estrutura

```
projeto-java/
├── pom.xml                       POM agregador (versões e módulos)
├── Dockerfile                    imagem de qualquer serviço: --build-arg SERVICE=<nome>
├── docker-compose.yml            infraestrutura + 11 serviços
├── infra/                        PostgreSQL, RabbitMQ, Prometheus, Grafana
├── libs/
│   ├── hub-commons               Java puro: UUID v7, AES-GCM, tokens seguros, erros de domínio
│   ├── hub-starter-observability Actuator, Prometheus, OpenTelemetry, correlation id
│   ├── hub-starter-security      JWT, permissões, Problem Details, tenant por conexão (RLS)
│   ├── hub-starter-messaging     Outbox (gravação); relay e consumidores na Etapa 5
│   ├── hub-database-support      funções SQL comuns: imutabilidade, partições, validações
│   └── hub-test-support          Testcontainers e regras ArchUnit compartilhadas
├── services/
│   ├── api-gateway               :8080  entrada única
│   ├── auth-service              :8081  usuários, papéis, sessões, credenciais de API
│   ├── bank-service              :8082  bancos
│   ├── registry-service          :8083  registradoras
│   ├── contract-service          :8084  contratos e ciclo de vida (orquestrador)
│   ├── document-service          :8085  documentos
│   ├── routing-service           :8086  roteamento
│   ├── integration-service       :8087  registradoras externas e callbacks
│   ├── notification-service      :8088  e-mail e notificações internas
│   ├── audit-service             :8089  trilha de auditoria
│   └── operations-service        :8090  dashboards, relatórios, filas e DLQ
└── docs/                         arquitetura e ADRs
```

Cada serviço segue Clean Architecture (`domain` → `application` → `infrastructure` / `web`), verificada por testes ArchUnit no build.

## Pré-requisitos

- JDK 21
- Maven 3.9+ (ou o Maven embutido do IntelliJ)
- Docker Desktop com pelo menos 8 GB de memória para o ambiente completo

## Como rodar

Ambiente completo (infraestrutura + serviços):

```bash
cp .env.example .env
docker compose up -d --build
```

Só a infraestrutura, para rodar os serviços pela IDE:

```bash
docker compose up -d postgres rabbitmq jaeger prometheus grafana
```

Cada serviço aplica as próprias migrações Liquibase ao subir.

### Acesso em desenvolvimento

Com `LIQUIBASE_CONTEXTS=default,dev` (padrão do `.env.example`) o auth-service cria três instituições e um usuário por perfil. Senha de todos: `Hub@dev2026!`

| Perfil | E-mail |
| --- | --- |
| SUPER_ADMIN | superadmin@hub.dev |
| HUB_OPERATOR | operador@hub.dev |
| HUB_AUDITOR | auditor@hub.dev |
| BANK_ADMIN / OPERATOR / VIEWER (Banco Exemplo) | admin@ / operador@ / consulta@banco-exemplo.dev |
| BANK_ADMIN (Banco Teste) | admin@banco-teste.dev |
| REGISTRY_ADMIN / OPERATOR / VIEWER | admin@ / operador@ / consulta@registradora-exemplo.dev |

```bash
# Login
curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"admin@banco-exemplo.dev","password":"Hub@dev2026!"}'

# Quem sou eu (use o accessToken da resposta)
curl -s localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
```

Os e-mails de ativação e de redefinição de senha aparecem no Mailpit (http://localhost:8025). A documentação interativa da API de autenticação fica em http://localhost:8081/swagger-ui.html.

Fora do desenvolvimento, use `LIQUIBASE_CONTEXTS=default`, gere uma chave própria (`openssl rand -base64 32`) para `HUB_AUTH_ENCRYPTION_KEY` e crie o primeiro administrador com `HUB_AUTH_BOOTSTRAP_ADMIN_EMAIL` e `HUB_AUTH_BOOTSTRAP_ADMIN_PASSWORD`.

| O quê | Endereço | Acesso (dev) |
| --- | --- | --- |
| API Gateway | http://localhost:8080 | — |
| Health de um serviço | http://localhost:8082/actuator/health | — |
| RabbitMQ | http://localhost:15672 | hub_admin / hub_admin_dev |
| Grafana | http://localhost:3000 | admin / admin_dev |
| Jaeger | http://localhost:16686 | — |
| Prometheus | http://localhost:9090 | — |
| Mailpit (e-mails) | http://localhost:8025 | — |
| PostgreSQL | localhost:5432 | hub_&lt;serviço&gt; / hub_&lt;serviço&gt; |

## Testes

```bash
mvn test      # unitários e ArchUnit (sem Docker)
mvn verify    # + testes de integração com PostgreSQL real via Testcontainers (requer Docker)
```

Os testes de integração sobem cada serviço contra um PostgreSQL 18 real e cobrem:

- migrações e regras que o banco garante sozinho: histórico imutável, unicidade do `external_id` por banco, estados válidos, CNPJ alfanumérico, particionamento mensal;
- autenticação ponta a ponta: login, bloqueio após 5 tentativas, rotação do refresh token e revogação da sessão no reuso, ativação e redefinição por e-mail, client credentials dos bancos;
- isolamento: um banco não vê nem altera usuários, contratos ou documentos de outro; a registradora só vê o que foi encaminhado a ela; sem tenant, nada é visível;
- gateway: token obrigatório na borda, limite de requisições (429), CORS.

## Convenções

- Pacote base: `br.com.hubregistro.<serviço>`
- Migrações em SQL formatado do Liquibase, uma pasta `db/changelog/v<N>` por versão; changesets nunca são editados depois de aplicados, só acrescentados
- Chaves primárias UUID v7 geradas pelo PostgreSQL (`uuidv7()`), ordenáveis no tempo
- Topologia do RabbitMQ declarada em `infra/rabbitmq/definitions.json`; os serviços não criam filas
- Commits e mensagens de log em português; nomes de código em inglês
