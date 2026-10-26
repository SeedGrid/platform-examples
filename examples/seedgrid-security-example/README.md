# seedgrid-quarkus-ext-security — Guia Completo (Tutorial para Iniciantes)

Este documento explica **cada propriedade, cada fluxo e cada conceito** do módulo de segurança SeedGrid. Se você nunca configurou autenticação JWT, 2FA ou SMTP antes, este guia foi escrito para você.

---

## Índice

0. [Quickstart — Do zero ao primeiro login](#0-quickstart--do-zero-ao-primeiro-login)
1. [Conceitos Fundamentais](#1-conceitos-fundamentais)
2. [Variáveis de Ambiente e o padrão `${VAR:default}`](#2-variáveis-de-ambiente-e-o-padrão-vardefault)
3. [Configurações Quarkus Base](#3-configurações-quarkus-base)
4. [Banco de Dados — Datasource e Hibernate](#4-banco-de-dados--datasource-e-hibernate)
5. [CORS — Cross-Origin Resource Sharing](#5-cors--cross-origin-resource-sharing)
6. [JWT — Autenticação por Token](#6-jwt--autenticação-por-token)
7. [Refresh Token](#7-refresh-token)
8. [Política de Senha](#8-política-de-senha)
9. [Dois Fatores (2FA)](#9-dois-fatores-2fa)
10. [RBAC — Controle de Acesso](#10-rbac--controle-de-acesso)
11. [URLs do Frontend](#11-urls-do-frontend)
12. [Criptografia em Repouso](#12-criptografia-em-repouso)
13. [Mailer — Envio de E-mails](#13-mailer--envio-de-e-mails)
14. [Avatar Storage](#14-avatar-storage)
15. [Self-Enter — Auto-Cadastro](#15-self-enter--auto-cadastro)
16. [Auditoria](#16-auditoria)
17. [Primeiro Usuário Admin](#17-primeiro-usuário-admin)
18. [Flyway — Migrações de Banco](#18-flyway--migrações-de-banco)
19. [Rate Limiting de Login](#19-rate-limiting-de-login)
20. [Logging e Diagnóstico](#20-logging-e-diagnóstico)
21. [Fluxos de Negócio](#21-fluxos-de-negócio)
22. [SPIs — Pontos de Extensão](#22-spis--pontos-de-extensão)
23. [Templates de E-mail](#23-templates-de-e-mail)
24. [Configuração Completa de Referência](#24-configuração-completa-de-referência)
25. [Verificação End-to-End](#25-verificação-end-to-end)

---

## 0. Quickstart — Do zero ao primeiro login

Esta seção mostra o caminho mais rápido para ter a aplicação rodando pela primeira vez.

### Passo 1 — Adicione a dependência no `pom.xml`

```xml
<dependency>
    <groupId>br.com.seedgrid</groupId>
    <artifactId>seedgrid-quarkus-ext-security</artifactId>
    <version><!-- versão atual --></version>
</dependency>
```

### Passo 2 — Gere o par de chaves RSA

O módulo usa RSA-256 para assinar os tokens JWT. Você precisa de dois arquivos: `private.pem` e `public.pem`.

```bash
# Execute na raiz do projeto:

# 1. Gerar chave privada
openssl genrsa -out private_temp.pem 2048

# 2. Converter para PKCS#8 (formato exigido pelo módulo)
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
  -in private_temp.pem -out src/main/resources/keys/private.pem

# 3. Extrair chave pública
openssl rsa -in src/main/resources/keys/private.pem \
  -pubout -out src/main/resources/keys/public.pem

# 4. Remover arquivo temporário
rm private_temp.pem
```

Crie o diretório antes: `mkdir -p src/main/resources/keys`

> **Em produção:** nunca coloque as chaves dentro do JAR. Gere em local seguro e injete via variável de ambiente (`SECURITY_PRIVATE_KEY_LOCATION=/etc/secrets/private.pem`).

### Passo 3 — Gere a chave de criptografia AES

```bash
openssl rand -base64 32
# Exemplo de saída: K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

Guarde essa saída — vai para `seedgrid.security.encryption.key`.

### Passo 4 — Crie o `application.properties` mínimo

```properties
# Banco de dados
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/meudb
quarkus.datasource.username=postgres
quarkus.datasource.password=postgres
quarkus.hibernate-orm.packages=br.com.seedgrid.security.entities

# Flyway (cria as tabelas automaticamente)
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration/security,classpath:db/migration/audit

# JWT
seedgrid.security.jwt.issuer=https://app.minha-empresa.com.br
seedgrid.security.jwt.private-key-location=src/main/resources/keys/private.pem
seedgrid.security.jwt.public-key-location=src/main/resources/keys/public.pem

# Chave de criptografia (gerada no passo 3)
seedgrid.security.encryption.key=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=

# Mailer SMTP
seedgrid.security.mailer.from=noreply@minha-empresa.com.br
seedgrid.security.mailer.host=smtp.gmail.com
seedgrid.security.mailer.port=587
seedgrid.security.mailer.start-tls=REQUIRED
seedgrid.security.mailer.username=meu-email@gmail.com
seedgrid.security.mailer.password=minha-senha-de-app

# Frontend (onde ficam suas páginas de login, reset de senha, etc.)
seedgrid.security.frontend.base-url=http://localhost:3000
seedgrid.security.frontend.login-path=/login
seedgrid.security.frontend.password-reset-path=/auth/forgot-password/confirm
seedgrid.security.frontend.email-confirmation-path=/auth/confirm-email
seedgrid.security.frontend.welcome-path=/auth/welcome

# Primeiro admin (será criado automaticamente se o banco estiver vazio)
seedgrid.security.first-admin-user.email=admin@minha-empresa.com.br
seedgrid.security.first-admin-user.name=Administrador
# seedgrid.security.first-admin-user.whatsapp=5511999999999  ← opcional, necessário para 2FA via WhatsApp

# Auditoria
seedgrid.audit.persistence.strategy=JPA
```

### Passo 5 — Suba a aplicação

```bash
./mvnw quarkus:dev
```

### O que acontece ao subir pela primeira vez

1. Flyway cria todas as tabelas no banco
2. O módulo detecta que não há usuários cadastrados
3. Cria o primeiro admin com o e-mail configurado
4. Envia um e-mail de boas-vindas com a senha temporária e o link de confirmação
5. Você confirma o e-mail, faz login e troca a senha

Se o e-mail não chegar, verifique o log da aplicação — as credenciais e o link de confirmação são logados em modo DEV.

---

## 2. Variáveis de Ambiente e o padrão `${VAR:default}`

Antes de entrar nas propriedades, é fundamental entender a sintaxe usada no arquivo.

### O que significa `${NOME_DA_VAR:valor_default}`?

Quarkus (e o MicroProfile Config) suporta esta sintaxe em qualquer valor do `application.properties`:

```
${NOME_DA_VARIAVEL_DE_AMBIENTE:valor_se_nao_encontrada}
```

**Exemplos:**

```properties
# Se a variável de ambiente DB_URL existir, usa ela.
# Se não existir, usa jdbc:postgresql://localhost:5432/meudb
quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5432/meudb}

# Se SMTP_PASSWORD existir, usa ela. Se não existir, usa string vazia.
seedgrid.security.mailer.password=${SMTP_PASSWORD:}

# Sem default: se JWT_ISSUER não existir, a aplicação falha ao subir.
seedgrid.security.jwt.issuer=${JWT_ISSUER}
```

**Por que usar variáveis de ambiente?**

O arquivo `application.properties` vai para o Git. Senhas, chaves e URLs de banco de dados **não devem ir para o Git**. A solução é:

1. No `application.properties`: usar `${NOME_DA_VAR}` sem valor default
2. No servidor/CI: definir a variável de ambiente com o valor real

**Em desenvolvimento local** você pode usar um arquivo `.env` ou definir os valores direto no `application.properties` (sem `${}`) — mas nunca comite credenciais reais.

### Ordens de prioridade do MicroProfile Config

Quando a aplicação resolve uma propriedade, ela verifica nesta ordem (maior prioridade primeiro):

1. Variáveis de ambiente do sistema operacional (`export VAR=valor`)
2. Propriedades de sistema JVM (`-Dvar=valor`)
3. Arquivo `application.properties` no classpath
4. Defaults dos `@WithDefault` nas interfaces de configuração

---

## 3. Configurações Quarkus Base

Propriedades gerais do Quarkus que afetam o comportamento da aplicação mas não são específicas do módulo de segurança.

---

#### `quarkus.application.name`

| Tipo | Default |
|---|---|
| `String` | `quarkus` |

**O que é:** O nome da aplicação. Aparece em logs, métricas, health checks e na UI do Swagger.

```properties
quarkus.application.name=${APP_NAME:minha-aplicacao}
```

---

#### `quarkus.http.port`

| Tipo | Default |
|---|---|
| `int` | `8080` |

**O que é:** A porta TCP em que a aplicação escuta requisições HTTP. Em desenvolvimento, `8080` é padrão. Se você tiver múltiplos serviços rodando localmente, mude para evitar conflito (ex: `8085`, `8090`).

```properties
quarkus.http.port=${HTTP_PORT:8080}
```

---

#### `quarkus.swagger-ui.always-include`

| Tipo | Default |
|---|---|
| `boolean` | `false` |

**O que é:** Por padrão, o Swagger UI só é incluído no build de desenvolvimento (`./mvnw quarkus:dev`). Com `true`, ele é incluído também no build de produção (JAR final).

**Quando usar `true`:** Ambientes de staging onde a equipe de QA e frontend precisam testar a API sem ter o projeto local. **Em produção pública, considere manter `false`** — expor a documentação da API facilita o trabalho de atacantes.

```properties
quarkus.swagger-ui.always-include=true
```

A URL do Swagger ficará em: `http://localhost:{porta}/q/swagger-ui`

---

#### `quarkus.devservices.enabled`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** O Quarkus tem uma feature chamada "Dev Services" — quando você não configura um banco de dados, ele sobe automaticamente um container Docker com PostgreSQL, Redis, etc. para desenvolvimento.

**Por que o módulo define `false`:** Quando você já tem um banco de dados configurado explicitamente (via `quarkus.datasource.*`), o Dev Services se torna confuso — às vezes o Quarkus ignora suas configurações e usa o container automático. Definindo `false`, você garante que **apenas** o datasource que você configurou será usado.

```properties
# Desabilite sempre que tiver datasource configurado manualmente:
quarkus.devservices.enabled=false
```

---

## 4. Banco de Dados — Datasource e Hibernate

O módulo requer um banco PostgreSQL para armazenar usuários, tokens, roles, histórico de senhas, etc.

---

#### `quarkus.datasource.db-kind`

| Tipo | Valor para PostgreSQL |
|---|---|
| `String` | `postgresql` |

**O que é:** Informa ao Quarkus qual driver JDBC usar. Com `postgresql`, ele inclui automaticamente o driver correto. Outros valores possíveis: `mysql`, `mariadb`, `h2`, `oracle`.

```properties
quarkus.datasource.db-kind=postgresql
```

---

#### `quarkus.datasource.jdbc.url`

| Tipo | Exemplo |
|---|---|
| `String` | `jdbc:postgresql://localhost:5432/meudb` |

**O que é:** A URL de conexão com o banco de dados. O formato é:
```
jdbc:postgresql://{host}:{porta}/{nome-do-banco}
```

**Partes da URL:**
- `jdbc:postgresql://` — protocolo fixo
- `localhost` — endereço do servidor do banco (use o hostname do container Docker se aplicável, ex: `postgres` ou `db`)
- `5432` — porta padrão do PostgreSQL (não mude a menos que necessário)
- `meudb` — nome do banco de dados (deve existir previamente — o Flyway cria as tabelas, mas não o banco)

**Criar o banco antes de subir a aplicação:**
```sql
-- Execute no psql ou em qualquer client:
CREATE DATABASE meudb;
```

```properties
quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5432/meudb}
```

---

#### `quarkus.datasource.username` e `quarkus.datasource.password`

| Tipo | Default |
|---|---|
| `String` | — |

**O que é:** Usuário e senha do PostgreSQL para autenticar a conexão.

```properties
quarkus.datasource.username=${DB_USERNAME:postgres}
quarkus.datasource.password=${DB_PASSWORD:postgres}
```

> **Em produção:** nunca use o usuário `postgres` (superusuário). Crie um usuário dedicado com permissões apenas nas tabelas necessárias:
> ```sql
> CREATE USER app_user WITH PASSWORD 'senha-forte';
> GRANT ALL PRIVILEGES ON DATABASE meudb TO app_user;
> ```

---

#### `quarkus.datasource.jdbc.min-size`

| Tipo | Default |
|---|---|
| `int` | `0` |

**O que é:** O número mínimo de conexões mantidas abertas no pool de conexões, mesmo quando a aplicação está ociosa.

**O que é um pool de conexões:** Abrir uma conexão com o banco é caro (handshake, autenticação, alocação de memória). Em vez de abrir e fechar uma conexão por requisição, a aplicação mantém um conjunto de conexões abertas e reutilizáveis — o "pool". Quando uma requisição chega, pega uma conexão do pool; quando termina, devolve.

```properties
quarkus.datasource.jdbc.min-size=2
```

---

#### `quarkus.datasource.jdbc.max-size`

| Tipo | Default |
|---|---|
| `int` | `20` |

**O que é:** O número máximo de conexões simultâneas no pool. Se todas as conexões estiverem em uso e uma nova requisição chegar, ela aguarda até uma conexão ser liberada.

**Como dimensionar:** Uma regra prática é: `max-size = número de núcleos do servidor × 2 + número de discos`. Para um servidor de 4 núcleos: `4 × 2 + 1 = 9`, arredonde para `10`.

```properties
quarkus.datasource.jdbc.max-size=10
```

---

#### `quarkus.hibernate-orm.database.generation`

| Tipo | Default | Valores |
|---|---|---|
| `String` | `none` | `none`, `create`, `drop-and-create`, `validate` |

**O que é:** Define o que o Hibernate faz com o schema do banco ao iniciar.

- **`none`**: Hibernate não toca no schema — **use sempre em produção**. O Flyway cuida das migrations.
- **`create`**: Hibernate cria as tabelas (apaga se já existirem). Útil para testes unitários com H2.
- **`drop-and-create`**: Apaga tudo e recria. **Perigoso** — use apenas em dev local isolado.
- **`validate`**: Verifica se o schema bate com as entidades, falha se houver divergência.

```properties
quarkus.hibernate-orm.database.generation=none
```

---

#### `quarkus.hibernate-orm.physical-naming-strategy`

| Tipo | Default |
|---|---|
| `String` | estratégia padrão do Hibernate |

**O que é:** Define como o Hibernate converte nomes de classes e campos Java para nomes de tabelas e colunas SQL.

**Com `CamelCaseToUnderscoresNamingStrategy`:**
- Campo `emailConfirmedAt` → coluna `email_confirmed_at`
- Campo `changePasswordRequired` → coluna `change_password_required`

Isso segue a convenção SQL (snake_case) enquanto o código Java usa camelCase — você não precisa anotar cada campo com `@Column(name = "...")`.

```properties
quarkus.hibernate-orm.physical-naming-strategy=org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
```

---

#### `quarkus.hibernate-orm.dialect`

| Tipo | Valor para PostgreSQL |
|---|---|
| `String` | `org.hibernate.dialect.PostgreSQLDialect` |

**O que é:** Informa ao Hibernate qual "dialeto" SQL usar para gerar queries otimizadas para o banco específico. Cada banco tem peculiaridades (tipos de dados, funções, paginação) — o dialeto lida com essas diferenças.

```properties
quarkus.hibernate-orm.dialect=org.hibernate.dialect.PostgreSQLDialect
```

---

#### `quarkus.hibernate-orm.packages`

| Tipo | Obrigatório |
|---|---|
| `String` (lista separada por vírgula) | **Sim** |

**O que é:** Lista de pacotes Java onde o Hibernate vai procurar classes anotadas com `@Entity`. Sem isso, o Hibernate não sabe quais classes mapear para tabelas.

**Por que é obrigatório aqui:** Em projetos Quarkus simples com apenas um persistence unit, o Hibernate varre automaticamente todo o classpath. Mas quando existem **múltiplos persistence units** (como neste módulo que gerencia seu próprio PU de auditoria), é obrigatório declarar explicitamente os pacotes de cada PU.

**Se o seu projeto tem entidades próprias:** Adicione o pacote delas aqui separado por vírgula. (Campos extras do usuário não precisam de entidade: vão em `additionalAttributes` — ver seção 17.4.)

```properties
# Apenas o módulo de segurança:
quarkus.hibernate-orm.packages=br.com.seedgrid.security.entities

# Com entidades próprias do host:
quarkus.hibernate-orm.packages=br.com.seedgrid.security.entities,br.com.minha-empresa.entities
```

---

## 5. CORS — Cross-Origin Resource Sharing

### O que é CORS?

Quando seu frontend (rodando em `http://localhost:3000`) tenta fazer uma requisição para sua API (rodando em `http://localhost:8080`), o navegador bloqueia a requisição por segurança — são origens diferentes (portas diferentes contam como origens diferentes).

O CORS é o mecanismo pelo qual o servidor informa ao navegador quais origens têm permissão para fazer requisições. **Isso é aplicado pelo navegador** — ferramentas como Postman, curl e backends não são afetados por CORS.

---

#### `seedgrid.web.cors.allow-origins`

| Tipo | Default |
|---|---|
| `String` (lista separada por vírgula) | — |

**O que é:** Lista de origens (protocolo + domínio + porta) autorizadas a fazer requisições para a API.

**Formato de uma origem:** `protocolo://domínio:porta`
- `http://localhost:3000` — frontend local na porta 3000
- `https://app.minha-empresa.com.br` — frontend em produção (HTTPS na porta 443 implícita)
- `http://admin.minha-empresa.com.br:8081` — painel admin em porta específica

**Por que incluir a porta?** `http://localhost` e `http://localhost:3000` são origens diferentes para o navegador. Você precisa especificar a porta exata do seu frontend.

```properties
# Desenvolvimento (apenas frontend local):
seedgrid.web.cors.allow-origins=http://localhost:3000

# Produção com múltiplos frontends:
seedgrid.web.cors.allow-origins=https://app.minha-empresa.com.br,https://admin.minha-empresa.com.br

# Desenvolvimento com vários desenvolvedores em portas diferentes:
seedgrid.web.cors.allow-origins=http://localhost:3000,http://localhost:3001,http://localhost:4200
```

> **Nunca use `*` (wildcard) em produção com autenticação.** Wildcard significa "qualquer origem pode fazer requisições" — qualquer site malicioso poderia fazer chamadas autenticadas em nome dos seus usuários.

---

## 20. Logging e Diagnóstico

O Quarkus usa o framework de logging do JBoss (JBoss Logging), que é compatível com SLF4J. As propriedades abaixo controlam o que aparece no console.

### Níveis de log

Do mais silencioso ao mais verboso:

| Nível | Quando aparece |
|---|---|
| `OFF` | Nada |
| `ERROR` | Apenas erros críticos |
| `WARN` | Erros + avisos |
| `INFO` | Erros + avisos + informações de operação normal |
| `DEBUG` | Tudo acima + detalhes de depuração |
| `TRACE` | Tudo acima + rastreamento linha a linha (muito verboso) |

---

#### `quarkus.log.level`

| Tipo | Default |
|---|---|
| `String` | `INFO` |

**O que é:** O nível de log padrão para **toda** a aplicação. Logs de pacotes específicos podem ser configurados individualmente abaixo.

```properties
# Produção: INFO ou WARN (menos ruído)
quarkus.log.level=${LOG_LEVEL:INFO}

# Desenvolvimento: DEBUG (mais detalhes)
quarkus.log.level=DEBUG
```

---

#### `quarkus.log.category."br.com.seedgrid".level`

| Tipo | Default recomendado |
|---|---|
| `String` | `DEBUG` em dev, `INFO` em prod |

**O que é:** Define o nível de log especificamente para o pacote `br.com.seedgrid` — ou seja, todo o código do módulo de segurança SeedGrid.

**Por que ter um nível separado:** Em produção, você quer `INFO` global, mas `DEBUG` para o módulo de segurança para investigar problemas de autenticação sem afogar os logs com debug de todo o resto.

```properties
quarkus.log.category."br.com.seedgrid".level=${LOG_SEEDGRID_LEVEL:DEBUG}
```

---

#### `quarkus.log.category."br.com.seedgrid.filemanagement".level`

**O que é:** Nível de log específico para o módulo de gerenciamento de arquivos (avatar storage). Em `DEBUG`, mostra cada operação de upload/download/delete com detalhes do storage usado.

```properties
# DEBUG para ver detalhes de upload/download de avatares:
quarkus.log.category."br.com.seedgrid.filemanagement".level=${LOG_FILEMANAGEMENT_LEVEL:DEBUG}
```

---

#### `quarkus.log.category."io.minio".level`

**O que é:** Log do cliente MinIO. Em `DEBUG`, mostra cada requisição HTTP feita ao MinIO (método, URL, status de resposta).

```properties
# Use DEBUG apenas quando estiver depurando problemas com MinIO:
quarkus.log.category."io.minio".level=${LOG_MINIO_LEVEL:INFO}
```

---

#### `quarkus.log.category."okhttp3".level`

**O que é:** OkHttp é a biblioteca HTTP usada internamente pelo cliente MinIO e por outros clientes HTTP. Em `DEBUG`, loga cada requisição e resposta HTTP, incluindo headers — muito verboso.

```properties
quarkus.log.category."okhttp3".level=${LOG_OKHTTP_LEVEL:INFO}
```

---

#### `quarkus.log.category."org.hibernate".level`

**O que é:** Log do Hibernate ORM. Em `DEBUG`, mostra cada query SQL gerada com os parâmetros — essencial para depurar problemas de performance ou queries incorretas.

```properties
# INFO em produção (não logar todas as SQLs):
quarkus.log.category."org.hibernate".level=${LOG_HIBERNATE_LEVEL:INFO}

# DEBUG em desenvolvimento para ver as SQLs:
# quarkus.log.category."org.hibernate".level=DEBUG
```

---

#### `quarkus.log.category."io.vertx.ext.mail".level` e `"io.quarkus.mailer".level`

**O que são:** Logs do subsistema de envio de e-mails do Quarkus. Em `TRACE`, mostram o conteúdo completo dos e-mails enviados, incluindo headers SMTP, conteúdo HTML e a comunicação com o servidor de e-mail.

```properties
# INFO: só erros e confirmações de envio
quarkus.log.category."io.vertx.ext.mail".level=${LOG_VERTX_MAIL_LEVEL:INFO}
quarkus.log.category."io.quarkus.mailer".level=${LOG_QUARKUS_MAILER_LEVEL:INFO}

# TRACE: debug completo do SMTP (use quando e-mails não chegam)
# quarkus.log.category."io.vertx.ext.mail".level=TRACE
# quarkus.log.category."io.quarkus.mailer".level=TRACE
```

---

#### `seedgrid.show-dev-message`

| Tipo | Default |
|---|---|
| `boolean` | `false` |

**O que é:** Quando `true`, o módulo exibe mensagens extras no log durante o desenvolvimento — como as credenciais do primeiro admin e links de confirmação (útil quando o e-mail não está configurado e você precisa do link para testar).

```properties
# Ative durante desenvolvimento para ver links e tokens no console:
seedgrid.show-dev-message=true

# Desative em produção:
# seedgrid.show-dev-message=false
```

### Configuração de log recomendada por ambiente

**Desenvolvimento:**
```properties
quarkus.log.level=INFO
quarkus.log.category."br.com.seedgrid".level=DEBUG
quarkus.log.category."org.hibernate".level=DEBUG
quarkus.log.category."io.vertx.ext.mail".level=TRACE
seedgrid.show-dev-message=true
```

**Produção:**
```properties
quarkus.log.level=WARN
quarkus.log.category."br.com.seedgrid".level=INFO
quarkus.log.category."org.hibernate".level=WARN
quarkus.log.category."io.vertx.ext.mail".level=INFO
seedgrid.show-dev-message=false
```

---

## 1. Conceitos Fundamentais

Antes de configurar qualquer propriedade, é importante entender o que acontece por baixo dos panos.

### O que é autenticação baseada em token?

Quando um usuário faz login com e-mail e senha, o servidor não guarda uma "sessão" (como faziam sistemas antigos). Em vez disso, ele gera um **token** — uma string assinada criptograficamente — e devolve para o cliente. Nas próximas requisições, o cliente envia esse token no header `Authorization: Bearer {token}`. O servidor valida a assinatura e sabe quem é o usuário **sem consultar o banco de dados a cada requisição**.

### O que é RSA?

RSA é um algoritmo de criptografia **assimétrica**: usa duas chaves relacionadas matematicamente.

- **Chave privada** (`private.pem`): fica no servidor, é usada para **assinar** os tokens. Nunca sai do servidor.
- **Chave pública** (`public.pem`): pode ser distribuída para qualquer um, é usada para **verificar** se a assinatura é válida.

Isso significa que qualquer sistema que receber a chave pública consegue validar um token, mas apenas o servidor com a chave privada consegue criar tokens válidos.

### O que é BCrypt?

BCrypt é um algoritmo de hash especialmente desenhado para senhas. Diferente de MD5 ou SHA-256 (rápidos), o BCrypt é **propositalmente lento**, dificultando ataques de força bruta. Ele também incorpora um "salt" aleatório automaticamente, então duas senhas idênticas geram hashes diferentes.

---

## 2. JWT — Autenticação por Token

Um JWT (JSON Web Token) é composto de três partes separadas por `.`:
- **Header**: algoritmo usado (ex: `RS256`)
- **Payload**: dados do usuário (claims)
- **Signature**: assinatura RSA que prova que o token é autêntico

Exemplo de payload decodificado:
```json
{
  "iss": "https://minha-app.com.br",
  "sub": "uuid-do-usuario",
  "upn": "joao@empresa.com",
  "groups": ["ADMIN", "USER"],
  "exp": 1714500000,
  "kid": "minha-app-key"
}
```

### Propriedades

---

#### `seedgrid.security.jwt.issuer`

| Tipo | Default |
|---|---|
| `String` | `https://seedgrid.com.br` |

**O que é:** O "emissor" do token. É um identificador — geralmente a URL da sua aplicação — que fica gravado no campo `iss` (issuer) dentro do JWT.

**Para que serve:** Quando outro sistema receber um token, ele pode verificar se o `iss` bate com o emissor esperado. Se alguém tentar usar um token emitido por outra aplicação, a validação falha porque o `iss` será diferente.

**Exemplo prático:** Imagine que você tem dois sistemas — `app-clinica.com.br` e `app-escritorio.com.br`. Cada um tem seu próprio issuer. Um token emitido pelo primeiro não será aceito pelo segundo, porque o `iss` não bate.

```properties
seedgrid.security.jwt.issuer=https://app.minha-empresa.com.br
```

---

#### `seedgrid.security.jwt.kid`

| Tipo | Default |
|---|---|
| `String` | *(opcional)* |

**O que é:** O "Key ID" — um apelido para identificar qual par de chaves RSA foi usado para assinar o token. Fica no campo `kid` do header do JWT.

**Para que serve:** Quando você precisar **rotacionar as chaves** (trocar o par RSA por segurança), pode ter múltiplos pares ativos ao mesmo tempo. O `kid` diz ao validador qual chave usar para verificar cada token. Tokens antigos (assinados com a chave antiga) continuam válidos enquanto a chave antiga ainda existir.

**Na prática para iniciantes:** Se você tem apenas um par de chaves e nunca vai trocar, o `kid` é apenas um nome descritivo. Use o nome da sua aplicação.

```properties
seedgrid.security.jwt.kid=minha-clinica-key-2024
```

---

#### `seedgrid.security.jwt.ttl`

| Tipo | Default |
|---|---|
| `Duration` (ISO 8601) | `PT8H` |

**O que é:** O "Time to Live" — quanto tempo o access token dura antes de expirar.

**Formato ISO 8601 de duração:**
- `PT15M` = 15 minutos
- `PT1H` = 1 hora
- `PT8H` = 8 horas
- `P1D` = 1 dia

**Para que serve:** Tokens JWT não podem ser "invalidados" pelo servidor (não existe uma blacklist centralizada no design padrão). Então usamos tokens com vida curta. Se um token for roubado, o atacante só consegue usá-lo pelo tempo restante de vida.

**O dilema do TTL:**
- TTL muito curto (ex: `PT5M`): mais seguro, mas o usuário precisa renovar o token frequentemente, o que exige um bom sistema de refresh token.
- TTL muito longo (ex: `P7D`): mais conveniente, mas um token roubado dá acesso por muito tempo.

**Recomendação:** `PT8H` (8 horas) é um bom equilíbrio para sistemas internos. Para aplicações públicas com dados sensíveis, use `PT15M` combinado com refresh token.

```properties
seedgrid.security.jwt.ttl=PT8H
```

---

#### `seedgrid.security.jwt.private-key-location`

| Tipo | Default |
|---|---|
| `String` | `keys/private.pem` |

**O que é:** O caminho para o arquivo da chave privada RSA que assina os tokens.

**Como o módulo resolve o caminho:**
1. Primeiro tenta como caminho no sistema de arquivos (absoluto ou relativo ao diretório de execução)
2. Se não encontrar, tenta no classpath (dentro do JAR, em `src/main/resources/`)

**NUNCA comite a chave privada no Git.** Use variáveis de ambiente ou um cofre de segredos (Vault, AWS Secrets Manager, etc.).

**Como gerar o par RSA:**
```bash
# Gerar chave privada RSA 2048 bits
openssl genrsa -out private_temp.pem 2048

# Converter para formato PKCS#8 (exigido pelo módulo)
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
  -in private_temp.pem -out private.pem

# Extrair chave pública
openssl rsa -in private.pem -pubout -out public.pem

# Remover o arquivo temporário
rm private_temp.pem
```

```properties
# Desenvolvimento (arquivo dentro do projeto — APENAS para dev!)
seedgrid.security.jwt.private-key-location=src/main/resources/keys/private.pem

# Produção (arquivo fora do projeto, injetado pelo ambiente)
seedgrid.security.jwt.private-key-location=/etc/secrets/private.pem
```

---

#### `seedgrid.security.jwt.public-key-location`

| Tipo | Default |
|---|---|
| `String` | `keys/public.pem` |

**O que é:** O caminho para a chave pública RSA usada para **verificar** a assinatura dos tokens.

**A chave pública pode ser compartilhada** — não tem problema colocá-la no classpath do projeto. Ela só serve para verificar, não para criar tokens.

```properties
seedgrid.security.jwt.public-key-location=src/main/resources/keys/public.pem
```

---

## 3. Refresh Token

### Por que precisamos de dois tokens?

O access token (JWT) tem vida curta por segurança. Mas não queremos pedir a senha do usuário a cada 8 horas. A solução é o **refresh token**: um token de longa duração, opaco (sem informações visíveis), armazenado no banco de dados, usado exclusivamente para emitir novos access tokens.

**Fluxo:**
```
Login → [access token: 8h] + [refresh token: 30 dias]
                  ↓ (após 8h)
POST /auth/refresh com o refresh token
   → [novo access token: 8h] + [novo refresh token: 30 dias]
```

---

#### `seedgrid.security.refresh-token.ttl`

| Tipo | Default |
|---|---|
| `Duration` | `P30D` |

**O que é:** Quanto tempo o refresh token dura. A cada renovação bem-sucedida, o novo token recebe um TTL renovado a partir de agora (sliding window).

**O que é "token family" e por que importa:**

Cada vez que um refresh token é usado, ele é **marcado como consumido** e um novo é emitido. Os tokens de um mesmo login formam uma "família" encadeada.

**Detecção de roubo:** Se um atacante roubar um refresh token e usá-lo, o token é marcado como consumido. Quando o usuário legítimo tentar usar o token original (que agora o servidor já marcou como consumido), o sistema detecta **reuso de token consumido** e **revoga toda a família** — forçando o usuário e o atacante a fazer login novamente.

```properties
seedgrid.security.refresh-token.ttl=P30D
```

---

## 4. Política de Senha

As senhas são armazenadas com hash BCrypt. Antes de aplicar o hash, o módulo concatena o `publicId` do usuário com a senha: `hash(publicId + senha)`. Isso significa que mesmo que dois usuários tenham a mesma senha, os hashes serão completamente diferentes — e um hash roubado de um usuário não pode ser reutilizado em outro.

---

#### `seedgrid.security.password.min-size`

| Tipo | Default |
|---|---|
| `int` | `8` |

**O que é:** O número mínimo de caracteres que a senha deve ter.

**Por que importa:** Senhas curtas são vulneráveis a ataques de força bruta. Com 8 caracteres e usando letras maiúsculas, minúsculas, números e símbolos, existem mais de 6 quadrilhões de combinações possíveis.

```properties
seedgrid.security.password.min-size=10
```

---

#### `seedgrid.security.password.upper-case-required`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** Exige que a senha contenha pelo menos uma letra maiúscula (A-Z).

**Por que importa:** Aumenta o espaço de possibilidades. Uma senha só de minúsculas tem 26 opções por posição; com maiúsculas e minúsculas, passa para 52.

```properties
seedgrid.security.password.upper-case-required=true
```

---

#### `seedgrid.security.password.lower-case-required`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** Exige pelo menos uma letra minúscula (a-z).

```properties
seedgrid.security.password.lower-case-required=true
```

---

#### `seedgrid.security.password.special-character-required`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** Exige pelo menos um caractere especial — como `!@#$%^&*()_+-=[]{}|;':,./<>?`.

**Por que importa:** Caracteres especiais expandem o "alfabeto" da senha de 62 para mais de 90 símbolos, tornando ataques de força bruta exponencialmente mais difíceis.

```properties
seedgrid.security.password.special-character-required=true
```

---

#### `seedgrid.security.password.prohibits-repeated-characters-in-sequence`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** Proíbe sequências de caracteres repetidos consecutivos, como `aaa`, `111` ou `!!!`.

**Por que importa:** Usuários costumam usar padrões como `Senha111!` para cumprir os requisitos de forma fácil. Bloquear repetições força senhas mais aleatórias.

```properties
seedgrid.security.password.prohibits-repeated-characters-in-sequence=true
```

---

#### `seedgrid.security.password.expiration-days`

| Tipo | Default |
|---|---|
| `int` | `90` |

**O que é:** Após quantos dias a senha expira e o usuário é obrigado a criar uma nova. Use `0` para nunca expirar.

**Por que importa:** Se uma senha for comprometida sem que o usuário saiba, a expiração limita o tempo de acesso do atacante. Com 90 dias, o dano máximo é de 3 meses.

**O que acontece quando a senha expira:** Na próxima autenticação, o servidor retorna `changePasswordRequired: true` na resposta do login. O frontend deve redirecionar o usuário para a tela de troca de senha.

```properties
seedgrid.security.password.expiration-days=90
# Para nunca expirar:
# seedgrid.security.password.expiration-days=0
```

---

#### `seedgrid.security.password.max-history`

| Tipo | Default |
|---|---|
| `int` | `5` |

**O que é:** O número de senhas anteriores que o sistema guarda no histórico. O usuário não pode reutilizar nenhuma dessas senhas ao criar uma nova.

**Por que importa:** Sem histórico, o usuário pode fazer: senha A → troca para B → troca de volta para A. O histórico evita essa "rotação circular" e garante que a troca de senha seja de fato uma melhoria de segurança.

**Como funciona tecnicamente:** O hash de cada senha usada é guardado na tabela `password_history` com a data de criação. Ao trocar a senha, o módulo compara a nova senha com cada entrada do histórico.

```properties
seedgrid.security.password.max-history=5
```

---

## 5. Dois Fatores (2FA)

### O que é autenticação de dois fatores?

É uma camada extra de segurança: mesmo que alguém descubra sua senha, ainda precisa de um segundo "fator" para entrar. Os fatores são:

- **Algo que você sabe**: senha
- **Algo que você tem**: código gerado pelo celular (TOTP) ou código enviado por e-mail
- **Algo que você é**: biometria (não implementado)

### TOTP — Time-based One-Time Password

O TOTP (usado pelo Google Authenticator, Authy, etc.) funciona assim:

1. O servidor gera um **segredo compartilhado** (uma string aleatória)
2. O usuário escaneia um QR code com o app autenticador — o segredo é transferido para o celular
3. A cada 30 segundos, o app e o servidor calculam o mesmo código de 6 dígitos aplicando o algoritmo TOTP ao segredo + timestamp atual
4. O código que o app mostra e o código que o servidor espera são idênticos (sem nenhuma comunicação entre eles) — porque usam o mesmo segredo e o mesmo timestamp

---

#### `seedgrid.security.2f.enabled`

| Tipo | Default |
|---|---|
| `boolean` | `false` |

**O que é:** Liga ou desliga o módulo de 2FA globalmente na aplicação.

**Importante:** Quando `false`, todos os endpoints de 2FA retornam erro. O 2FA só fica disponível para os usuários se estiver `true` aqui.

```properties
seedgrid.security.2f.enabled=true
```

---

#### `seedgrid.security.2f.allowed-methods`

| Tipo | Default |
|---|---|
| `List<String>` | `EMAIL,TOTP` |

**O que é:** Lista dos métodos de 2FA que os usuários podem escolher. Os valores possíveis nativos são `EMAIL` e `TOTP`. Você pode adicionar métodos customizados via SPI (ver seção 17).

**Como funciona:** Se você colocar apenas `TOTP`, os usuários que tentarem habilitar 2FA por e-mail receberão um erro. Use para restringir quais métodos são aceitos na sua aplicação.

```properties
# Apenas TOTP (mais seguro, requer app autenticador)
seedgrid.security.2f.allowed-methods=TOTP

# TOTP e E-mail (mais flexível)
seedgrid.security.2f.allowed-methods=EMAIL,TOTP
```

---

#### `seedgrid.security.2f.default-method`

| Tipo | Default |
|---|---|
| `String` | `TOTP` |

**O que é:** O método sugerido pelo sistema quando o usuário vai habilitar o 2FA pela primeira vez.

```properties
seedgrid.security.2f.default-method=TOTP
```

---

#### `seedgrid.security.2f.totp.issuer`

| Tipo | Default |
|---|---|
| `String` | `SeedGrid` |

**O que é:** O nome que aparece no app autenticador (Google Authenticator, Authy, etc.) ao lado do código TOTP do usuário.

**Por que importa:** O usuário pode ter 2FA em dezenas de aplicações diferentes no mesmo app. O issuer é o nome que aparece na lista para ele saber qual código usar para qual sistema.

**Exemplo de como aparece no Google Authenticator:**
```
MinhaEmpresa (joao@empresa.com)
  ● 123 456  (29s)
```

```properties
seedgrid.security.2f.totp.issuer=MinhaEmpresa
```

---

#### `seedgrid.security.2f.totp.digits`

| Tipo | Default |
|---|---|
| `int` | `6` |

**O que é:** O número de dígitos que o código TOTP terá.

**Por que quase sempre é 6:** A grande maioria dos apps autenticadores exibe 6 dígitos. Usar 8 pode gerar problemas de compatibilidade com alguns apps. Mantenha `6` a menos que tenha um requisito muito específico.

```properties
seedgrid.security.2f.totp.digits=6
```

---

#### `seedgrid.security.2f.totp.period`

| Tipo | Default |
|---|---|
| `int` (segundos) | `30` |

**O que é:** A cada quantos segundos um novo código TOTP é gerado. Após esse tempo, o código atual expira e o app mostra um novo.

**Por que 30 segundos:** É o padrão universal (RFC 6238). Um período muito curto não dá tempo para o usuário digitar; muito longo deixa a janela de ataque maior caso alguém veja o código.

**O que acontece na prática:** Você abre o Google Authenticator, vê o código `847 291` e uma barra de progresso. Quando a barra zerar (após 30s), o código muda para `392 058`. Se você demorar para digitar e o código mudar, é só usar o novo.

```properties
seedgrid.security.2f.totp.period=30
```

---

#### `seedgrid.security.2f.totp.window`

| Tipo | Default |
|---|---|
| `int` | `1` |

**O que é:** A "janela de tolerância" — quantos períodos antes e depois do atual o servidor aceita como válidos.

**Por que existe:** Os relógios do celular e do servidor podem estar levemente dessincronizados (drift de relógio). Com `window=1`, o servidor aceita o código do período anterior e do próximo, além do atual — dando uma margem de ±30 segundos.

**Exemplo:** Com `period=30` e `window=1`, o servidor aceita códigos de até 90 segundos atrás ou 90 segundos à frente.

```properties
seedgrid.security.2f.totp.window=1
```

---

#### `seedgrid.security.2f.otp-qrcode.branding.enabled`

| Tipo | Default |
|---|---|
| `boolean` | `false` |

**O que é:** Quando `true`, sobrepõe o logo da sua empresa no centro do QR code gerado durante o setup TOTP.

**Por que importa:** É apenas estético — torna a tela de configuração do 2FA mais profissional e com a identidade da empresa.

```properties
seedgrid.security.2f.otp-qrcode.branding.enabled=true
```

---

#### `seedgrid.security.2f.otp-qrcode.logo`

| Tipo | Default |
|---|---|
| `String` | *(opcional)* |

**O que é:** O caminho para o arquivo de logo que será sobreposto no QR code. Use `classpath:` para arquivo dentro do JAR.

```properties
seedgrid.security.2f.otp-qrcode.logo=classpath:/images/logo-qrcode.png
# ou caminho absoluto:
# seedgrid.security.2f.otp-qrcode.logo=/etc/assets/logo.png
```

---

#### `seedgrid.security.2f.otp-qrcode.size`

| Tipo | Default |
|---|---|
| `int` (pixels) | `256` |

**O que é:** O tamanho em pixels (largura e altura) da imagem PNG do QR code retornada pela API.

**Dica:** Use pelo menos `300` para garantir que o QR code seja escaneável mesmo em telas menores.

```properties
seedgrid.security.2f.otp-qrcode.size=350
```

---

#### `seedgrid.security.2f.verify.max-attempts`

| Tipo | Default |
|---|---|
| `int` | `5` |

**O que é:** Quantas tentativas erradas de código 2FA o usuário tem antes de a sessão temporária de 2FA ser revogada.

**O que acontece ao esgotar:** A sessão (identificada pelo `tempToken`) é destruída. O usuário precisa fazer login novamente (e-mail + senha) para iniciar uma nova sessão de 2FA.

**Por que isso protege:** Sem esse limite, um atacante que roubou a senha poderia tentar todos os 1.000.000 de combinações de um código de 6 dígitos até acertar.

```properties
seedgrid.security.2f.verify.max-attempts=5
```

---

#### `seedgrid.security.2f.verify.lockout-duration`

| Tipo | Default |
|---|---|
| `Duration` | `PT30M` |

**O que é:** Quanto tempo o usuário fica bloqueado após esgotar as tentativas de verificação 2FA.

```properties
seedgrid.security.2f.verify.lockout-duration=PT30M
```

---

#### `seedgrid.security.2f.session.ttl`

| Tipo | Default |
|---|---|
| `Duration` | `PT5M` |

**O que é:** O tempo de vida da sessão temporária de 2FA — o `tempToken` retornado após login com e-mail/senha quando o 2FA está ativo.

**O que é o `tempToken`:** Após validar e-mail e senha, o servidor não emite o JWT ainda. Em vez disso, cria uma "sessão de 2FA" temporária identificada por um UUID (`tempToken`). O cliente usa esse `tempToken` para chamar `/public/2fa/verify` com o código. Se o `tempToken` expirar (após `session.ttl`), o usuário precisa fazer login novamente.

**Por que é curto (5 minutos):** Se alguém roubar o `tempToken`, tem 5 minutos para tentar quebrar o 2FA. Após isso, o token expira automaticamente.

```properties
seedgrid.security.2f.session.ttl=PT5M
```

---

#### `seedgrid.security.2f.email.code-ttl`

| Tipo | Default |
|---|---|
| `Duration` | `PT5M` |

**O que é:** Por quanto tempo o código de 6 dígitos enviado por e-mail (método 2FA EMAIL) fica válido.

**Por que 5 minutos:** Tempo suficiente para o usuário verificar o e-mail e digitar o código. E-mails raramente demoram mais que 1-2 minutos para chegar.

```properties
seedgrid.security.2f.email.code-ttl=PT5M
```

---

#### `seedgrid.security.2f.backup-codes.count`

| Tipo | Default |
|---|---|
| `int` | `10` |

**O que é:** Quantos backup codes são gerados quando o usuário habilita o 2FA.

**O que são backup codes:** São códigos de emergência para quando o usuário perde acesso ao seu método principal de 2FA (celular roubado, formatado, etc.). Cada código só pode ser usado **uma vez** — após usar, ele é marcado como consumido e não pode ser reutilizado.

**Instrução para o usuário:** "Guarde esses códigos num lugar seguro (gerenciador de senhas, papel na carteira). Se perder o acesso ao app autenticador, use um desses códigos para entrar e redefinir o 2FA."

```properties
seedgrid.security.2f.backup-codes.count=10
```

---

#### `seedgrid.security.2f.backup-codes.length`

| Tipo | Default |
|---|---|
| `int` | `6` |

**O que é:** O comprimento de cada backup code (número de caracteres alfanuméricos).

```properties
seedgrid.security.2f.backup-codes.length=6
```

---

### 2FA via WhatsApp

O módulo suporta envio do código de verificação via WhatsApp como terceiro método além de `EMAIL` e `TOTP`. Para isso, é preciso escolher um **provedor de WhatsApp** e configurar as propriedades específicas dele.

**O que é um "provedor" de WhatsApp?**

O WhatsApp não permite que qualquer software se conecte diretamente às suas mensagens. Existem duas formas legítimas de enviar mensagens:

| Provedor | Valor | O que é |
|---|---|---|
| **Evolution API** | `evolution` | Servidor open-source self-hosted que conecta via WhatsApp Web (engenharia reversa). Gratuito, sem aprovação formal, mas tecnicamente fora dos termos do WhatsApp. Ótimo para uso interno/corporativo. |
| **WhatsApp Cloud API (Oficial)** | `whatsapp-cloud` | API oficial do Meta/Facebook. Requer aprovação, conta business e um número dedicado. Pago por mensagem, dentro dos termos. Recomendado para envio em escala. |

---

#### `seedgrid.security.2f.allowed-methods` (com WhatsApp)

Para habilitar o WhatsApp como método de 2FA, adicione `WHATSAPP` à lista:

```properties
seedgrid.security.2f.allowed-methods=EMAIL,TOTP,WHATSAPP
```

**Pré-requisito:** O usuário só pode habilitar 2FA via WhatsApp se o campo `whatsApp` do seu perfil estiver preenchido com o número de telefone.

---

#### `seedgrid.security.2f.whatsapp.channel-id`

| Tipo | Obrigatório |
|---|---|
| `String` | Sim (quando WHATSAPP está em `allowed-methods`) |

**O que é:** O identificador do "canal" ou número de telefone remetente das mensagens, que tem significado diferente em cada provedor:

- **Evolution:** é o nome da instância criada no servidor Evolution (ex: `"minha-empresa-crm"`)
- **WhatsApp Cloud:** é o `Phone Number ID` do número cadastrado no Meta for Developers (ex: `"123456789012345"`)

```properties
# Para Evolution (nome da instância):
seedgrid.security.2f.whatsapp.channel-id=minha-empresa-crm

# Para WhatsApp Cloud (Phone Number ID do Meta):
seedgrid.security.2f.whatsapp.channel-id=123456789012345
```

---

#### `seedgrid.security.2f.whatsapp.code-ttl`

| Tipo | Default |
|---|---|
| `Duration` | `PT5M` |

**O que é:** Por quanto tempo o código de 6 dígitos enviado via WhatsApp fica válido.

```properties
seedgrid.security.2f.whatsapp.code-ttl=PT5M
```

---

#### `seedgrid.whatsapp.api`

| Tipo | Default | Valores |
|---|---|---|
| `String` | `default` | `evolution`, `whatsapp-cloud` |

**O que é:** Qual provedor de WhatsApp a aplicação vai usar para enviar as mensagens.

- `default` → modo "sem operação" — apenas loga no console, não envia nada. Útil para testes sem WhatsApp real.
- `evolution` → usa o servidor Evolution API (self-hosted)
- `whatsapp-cloud` → usa a API Oficial do Meta

```properties
seedgrid.whatsapp.api=evolution
# ou:
seedgrid.whatsapp.api=whatsapp-cloud
```

---

### Configuração completa — Evolution API

O **Evolution API** é um servidor que você instala na sua infraestrutura (Docker, VPS) e que conecta ao WhatsApp via WhatsApp Web. Funciona como se fosse um "WhatsApp no computador" automatizado.

**Arquitetura:**
```
Aplicação → Evolution API (seu servidor) → WhatsApp Web → Destinatário
```

**Propriedades:**

| Propriedade | Descrição | Obrigatório |
|---|---|---|
| `seedgrid.whatsapp.api` | Deve ser `evolution` | Sim |
| `seedgrid.security.2f.whatsapp.channel-id` | Nome da instância no Evolution | Sim |
| `seedgrid.whatsapp.rest-client.evolution.url` | URL do seu servidor Evolution | Sim |
| `seedgrid.whatsapp.evolution.api-key` | API Key configurada no Evolution | Sim |

```properties
# Seleciona o provedor
seedgrid.whatsapp.api=evolution

# WhatsApp 2FA
seedgrid.security.2f.allowed-methods=EMAIL,TOTP,WHATSAPP
seedgrid.security.2f.whatsapp.channel-id=minha-empresa-instancia
seedgrid.security.2f.whatsapp.code-ttl=PT5M

# Evolution API
seedgrid.whatsapp.rest-client.evolution.url=${SEEDGRID_WHATSAPP_REST_CLIENT_EVOLUTION_URL:http://evolution-api:8080}
seedgrid.whatsapp.evolution.api-key=${SEEDGRID_WHATSAPP_EVOLUTION_API_KEY}
```

**O que é `channel-id` no Evolution?** Quando você sobe o Evolution API e conecta um número de WhatsApp, você cria uma "instância" com um nome (ex: `minha-empresa-crm`). Esse nome é o `channel-id`. Você acessa o painel do Evolution, cria a instância e escaneia o QR code com o celular que vai enviar as mensagens.

**O que é `api-key`?** O Evolution API tem uma chave global de autenticação configurada no arquivo de configuração do servidor (`AUTHENTICATION_API_KEY`). Você usa essa mesma chave aqui.

---

### Configuração completa — WhatsApp Cloud API (Oficial)

A **WhatsApp Cloud API** é a API oficial do Meta para envio de mensagens. Requer:
1. Conta no [Meta for Developers](https://developers.facebook.com)
2. Criação de um App do tipo "Business"
3. Um número de telefone dedicado aprovado pelo Meta
4. Aprovação de templates de mensagem (para mensagens fora da janela de 24h)

**Arquitetura:**
```
Aplicação → Meta Graph API (https://graph.facebook.com) → Destinatário
```

**Propriedades:**

| Propriedade | Descrição | Obrigatório |
|---|---|---|
| `seedgrid.whatsapp.api` | Deve ser `whatsapp-cloud` | Sim |
| `seedgrid.security.2f.whatsapp.channel-id` | Phone Number ID do Meta for Developers | Sim |
| `seedgrid.whatsapp.cloud.access-token` | Token de acesso permanente do Meta | Sim |
| `seedgrid.whatsapp.rest-client.cloud.url` | URL base da API (raramente muda) | Não (tem default) |

```properties
# Seleciona o provedor
seedgrid.whatsapp.api=whatsapp-cloud

# WhatsApp 2FA
seedgrid.security.2f.allowed-methods=EMAIL,TOTP,WHATSAPP
seedgrid.security.2f.whatsapp.channel-id=${SEEDGRID_SECURITY_2F_WHATSAPP_CHANNEL_ID}
seedgrid.security.2f.whatsapp.code-ttl=PT5M

# WhatsApp Cloud API
seedgrid.whatsapp.cloud.access-token=${SEEDGRID_WHATSAPP_CLOUD_ACCESS_TOKEN}
# URL raramente muda — só altere se o Meta versionar a API
seedgrid.whatsapp.rest-client.cloud.url=${SEEDGRID_WHATSAPP_REST_CLIENT_CLOUD_URL:https://graph.facebook.com/v21.0}
```

**Como obter o `channel-id` (Phone Number ID):**
1. Acesse [Meta for Developers](https://developers.facebook.com) → Seu App → WhatsApp → Configuração
2. Em "Números de telefone", copie o **Phone Number ID** (é um número longo, ex: `123456789012345`)
3. Esse valor vai para `seedgrid.security.2f.whatsapp.channel-id`

**Como obter o `access-token`:**
1. No mesmo painel → Configurações do App → Básico → copie o **App Secret**
2. Gere um token de acesso permanente via Facebook Business Manager ou via API
3. Esse valor vai para `seedgrid.whatsapp.cloud.access-token`

---

### Mensagem enviada ao usuário

Independentemente do provedor escolhido, o usuário recebe uma mensagem no seguinte formato:

```
SeedGrid: seu codigo de verificacao e 847291.
Ele expira em 5 minuto(s).
Se voce nao solicitou este acesso, ignore esta mensagem.
```

O número de WhatsApp do usuário é lido do campo `whatsApp` do perfil. O número é normalizado automaticamente:
- **Evolution:** converte para formato internacional com `+` (ex: `+5511999999999`)
- **WhatsApp Cloud:** converte para formato internacional sem `+` (ex: `5511999999999`)

---

### Comparativo Evolution vs WhatsApp Cloud

| Critério | Evolution API | WhatsApp Cloud (Oficial) |
|---|---|---|
| **Custo** | Gratuito (servidor próprio) | Pago por conversa/mensagem |
| **Aprovação** | Nenhuma | Requer aprovação do Meta |
| **Infraestrutura** | Você hospeda o servidor | Meta hospeda |
| **Termos do WhatsApp** | Tecnicamente fora dos termos | Dentro dos termos |
| **Escalabilidade** | Limitada (1 número por instância) | Alta (múltiplos números) |
| **Ideal para** | Uso interno, pequenas equipes | Produção pública, escala |
| **Templates** | Não exige | Exige para mensagens iniciadas pela empresa |
| **Complexidade de setup** | Média (Docker + QR code) | Alta (conta Meta, aprovações) |

---

## 6. RBAC — Controle de Acesso

RBAC (Role-Based Access Control) é o modelo onde permissões são agrupadas em **roles** (papéis), e os usuários recebem roles — não permissões diretamente.

**Exemplo:**
- Role `ADMIN`: tem permissões `users:read`, `users:write`, `reports:read`
- Role `FINANCEIRO`: tem permissões `reports:read`, `invoices:write`
- Usuário João: tem roles `ADMIN` e `FINANCEIRO` → tem todas as permissões das duas roles

---

#### `seedgrid.security.rbac.cache-ttl`

| Tipo | Default |
|---|---|
| `Duration` | `PT5M` |

**O que é:** Por quanto tempo as roles e permissões de um usuário ficam em cache em memória.

**Por que cachear:** Consultar roles e permissões no banco de dados a cada requisição autenticada seria muito lento. O cache evita isso.

**O trade-off:** Se você remover uma permissão de um usuário, ele continuará tendo acesso por até `cache-ttl` antes de o cache expirar e a mudança ser refletida. Com `PT5M`, a latência máxima é 5 minutos.

**Para sistemas onde mudanças de permissão precisam ser imediatas:** Reduza para `PT30S` ou `PT1M`, com impacto moderado na performance.

```properties
seedgrid.security.rbac.cache-ttl=PT5M
```

---

## 7. URLs do Frontend

O módulo de segurança envia e-mails transacionais com links que direcionam o usuário para páginas do frontend (confirmação de e-mail, reset de senha, etc.). Essas propriedades definem como esses links são montados.

**Como o link é construído:**
```
{base-url}{path}?token={token}
```

Exemplo: `https://app.empresa.com.br/auth/confirm-email?token=abc123`

---

#### `seedgrid.security.frontend.base-url`

| Tipo | Obrigatório |
|---|---|
| `String` | Sim |

**O que é:** A URL raiz do seu frontend — protocolo + domínio + porta (se não for 80/443).

```properties
# Produção
seedgrid.security.frontend.base-url=https://app.minha-empresa.com.br

# Desenvolvimento local
seedgrid.security.frontend.base-url=http://localhost:3000
```

---

#### `seedgrid.security.frontend.login-path`

| Tipo | Default |
|---|---|
| `String` | `/login` |

**O que é:** O caminho da página de login no frontend. Usado nos e-mails de aprovação de self-enter e boas-vindas para o botão "Acessar a Plataforma".

```properties
seedgrid.security.frontend.login-path=/login
```

---

#### `seedgrid.security.frontend.password-reset-path`

| Tipo | Default |
|---|---|
| `String` | `/auth/forgot-password/confirm` |

**O que é:** O caminho da página que recebe o token de redefinição de senha. O frontend lê o `?token=...` da URL e chama a API `GET /auth/reset-password?token=...` automaticamente.

```properties
seedgrid.security.frontend.password-reset-path=/auth/forgot-password/confirm
```

---

#### `seedgrid.security.frontend.email-confirmation-path`

| Tipo | Default |
|---|---|
| `String` | `/auth/confirm-email` |

**O que é:** O caminho da página de confirmação de e-mail. Quando o usuário clica no link do e-mail de boas-vindas ou confirmação, é direcionado para essa página, que então chama a API.

```properties
seedgrid.security.frontend.email-confirmation-path=/auth/confirm-email
```

---

#### `seedgrid.security.frontend.welcome-path`

| Tipo | Default |
|---|---|
| `String` | `/auth/welcome` |

**O que é:** Caminho de uma página de boas-vindas opcional, usado no e-mail enviado para novos usuários.

```properties
seedgrid.security.frontend.welcome-path=/auth/welcome
```

---

#### `seedgrid.security.frontend.self-enter-path`

| Tipo | Default |
|---|---|
| `String` | — |

**O que é:** Caminho da página de cadastro/convite. Usado nos e-mails de convite (invitation) para o botão "Concluir Cadastro".

```properties
seedgrid.security.frontend.self-enter-path=/auth/register
```

---

## 8. Criptografia em Repouso

**O que significa "em repouso" (at rest):** Os dados estão armazenados em disco, no banco de dados — não estão sendo transmitidos. Criptografia em repouso protege os dados caso alguém obtenha acesso físico ao banco (dump de banco, backup roubado, etc.).

**Por que o segredo TOTP precisa ser criptografado:** Para verificar um código TOTP, o servidor precisa ter o **segredo original** (não um hash). Se o banco for comprometido e os segredos TOTP estiverem em texto puro, o atacante consegue recriar os códigos de qualquer usuário. Com criptografia AES-256, o segredo fica ilegível sem a chave.

---

#### `seedgrid.security.encryption.key`

| Tipo | Obrigatório |
|---|---|
| `String` (Base64) | Sim |

**O que é:** A chave usada pelo algoritmo AES-256 para criptografar os segredos TOTP. Deve ser uma string Base64 que ao decodificar resulte em exatamente **32 bytes** (256 bits).

**AES-256:** É um dos algoritmos de criptografia simétrica mais seguros existentes. "256" significa que a chave tem 256 bits. Com AES-256, são necessárias mais tentativas do que átomos no universo observável para quebrar por força bruta.

**Criptografia simétrica:** A mesma chave é usada para criptografar e descriptografar. Por isso é fundamental que essa chave:
1. Nunca seja commitada no Git
2. Seja guardada em variável de ambiente ou cofre de segredos
3. Seja diferente entre ambientes (dev, staging, produção)
4. Seja feito backup — se perder a chave, os segredos TOTP de todos os usuários ficam ilegíveis

**Como gerar uma chave válida:**
```bash
# Gera 32 bytes aleatórios e codifica em Base64
openssl rand -base64 32
# Exemplo de saída: K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

```properties
seedgrid.security.encryption.key=${SECURITY_ENCRYPTION_KEY}
```

> **Em desenvolvimento** você pode colocar uma chave diretamente no properties. Em produção, use sempre variável de ambiente.

---

## 9. Mailer — Envio de E-mails

O módulo envia e-mails transacionais (confirmação, reset de senha, 2FA, boas-vindas). Ele cria um cliente de e-mail interno chamado `"security"` configurado com as propriedades abaixo.

---

#### `seedgrid.security.mailer.from`

| Tipo | Default |
|---|---|
| `String` | `noreply@example.com` |

**O que é:** O endereço de e-mail que aparece no campo "De:" de todos os e-mails enviados pelo módulo.

**Boas práticas:**
- Use um domínio que você controla (não um e-mail pessoal)
- Configure SPF, DKIM e DMARC no DNS do domínio para os e-mails não caírem no spam

```properties
seedgrid.security.mailer.from=noreply@minha-empresa.com.br
```

---

#### `seedgrid.security.mailer.host`

| Tipo | Default |
|---|---|
| `String` | — |

**O que é:** O endereço do servidor SMTP que será usado para enviar os e-mails.

**Servidores comuns:**
| Serviço | Host |
|---|---|
| Gmail | `smtp.gmail.com` |
| AWS SES | `email-smtp.us-east-1.amazonaws.com` |
| SendGrid | `smtp.sendgrid.net` |
| Mailgun | `smtp.mailgun.org` |

```properties
seedgrid.security.mailer.host=smtp.gmail.com
```

---

#### `seedgrid.security.mailer.port`

| Tipo | Default |
|---|---|
| `int` | — |

**O que é:** A porta do servidor SMTP.

**Portas comuns:**
| Porta | Uso |
|---|---|
| `587` | STARTTLS (recomendado) |
| `465` | SSL/TLS |
| `25` | SMTP simples (não use — bloqueado pela maioria dos provedores) |

```properties
seedgrid.security.mailer.port=587
```

---

#### `seedgrid.security.mailer.start-tls`

| Tipo | Default | Valores |
|---|---|---|
| `String` | — | `REQUIRED`, `OPTIONAL`, `NEVER` |

**O que é:** Define como o STARTTLS (upgrade da conexão para TLS criptografado) será negociado.

- `REQUIRED`: a conexão **deve** ser upgradada para TLS. Se o servidor não suportar, a conexão falha. Use sempre que possível.
- `OPTIONAL`: tenta o upgrade mas continua em texto puro se não conseguir.
- `NEVER`: nunca faz o upgrade. **Evite** — e-mails trafegam sem criptografia.

```properties
seedgrid.security.mailer.start-tls=REQUIRED
```

---

#### `seedgrid.security.mailer.username` e `seedgrid.security.mailer.password`

| Tipo | Default |
|---|---|
| `String` | — |

**O que é:** As credenciais para autenticação SMTP.

**Atenção para Gmail:** O Gmail não aceita mais senha comum para SMTP. Você precisa gerar uma **"Senha de app"** nas configurações de segurança da conta Google (requer 2FA habilitado na conta Google).

```properties
seedgrid.security.mailer.username=${SMTP_USER}
seedgrid.security.mailer.password=${SMTP_PASSWORD}
```

---

## 10. Avatar Storage

O módulo suporta 7 backends diferentes para armazenar os avatares dos usuários. O arquivo é sempre salvo com um nome UUID único para evitar conflitos.

#### `seedgrid.security.avatar.storage`

| Tipo | Default | Valores |
|---|---|---|
| `String` | `filesystem` | `filesystem`, `minio`, `s3`, `azure-blob`, `gcs`, `google-drive`, `onedrive` |

**O que é:** Qual backend será usado para armazenar os avatares.

**Como escolher:**
| Backend | Quando usar |
|---|---|
| `filesystem` | Desenvolvimento local ou servidor único (não escala horizontalmente) |
| `minio` | Produção self-hosted, equivalente ao S3 rodando na sua infraestrutura |
| `s3` | Produção na AWS |
| `azure-blob` | Produção no Azure |
| `gcs` | Produção no Google Cloud |
| `google-drive` | Quando já usa Google Workspace e quer armazenar arquivos no Drive |
| `onedrive` | Quando já usa Microsoft 365 |

---

#### `seedgrid.security.avatar.user-prefix`

| Tipo | Default |
|---|---|
| `String` | `avatars/users` |

**O que é:** O prefixo (pasta/diretório) onde os avatares dos usuários são armazenados dentro do storage. O arquivo final fica em `{user-prefix}/{publicId}/{uuid}.{ext}`.

```properties
seedgrid.security.avatar.user-prefix=avatars/users
```

---

#### `seedgrid.security.avatar.default-prefix`

| Tipo | Default |
|---|---|
| `String` | `avatars/defaults` |

**O que é:** Onde ficam os avatares padrão (gerados pelo sistema) que são atribuídos aleatoriamente quando um usuário não tem avatar próprio.

```properties
seedgrid.security.avatar.default-prefix=avatars/defaults
```

---

#### `seedgrid.security.avatar.public-read`

| Tipo | Default |
|---|---|
| `boolean` | `false` |

**O que é:** Quando `true`, os arquivos são armazenados com ACL pública (qualquer um pode acessar a URL diretamente, sem autenticação). Quando `false`, o acesso é feito sempre através da API da aplicação.

**Quando usar `true`:** Se você quiser expor URLs diretas dos avatares (ex: `https://meu-bucket.s3.amazonaws.com/avatars/users/uuid/foto.jpg`) sem passar pela sua API. Mais rápido, mas os arquivos ficam publicamente acessíveis.

**Quando usar `false`:** Se quiser controlar o acesso (ex: só usuários autenticados podem ver avatares). O download passa pela API, que verifica autenticação.

```properties
seedgrid.security.avatar.public-read=false
```

---

### Configuração por Backend

#### `filesystem`

Armazena em diretório local. Simples para desenvolvimento, mas não funciona em ambientes com múltiplas instâncias (cada instância teria seus próprios arquivos).

```properties
seedgrid.security.avatar.storage=filesystem
seedgrid.security.avatar.filesystem.base-path=/var/seedgrid/files
```

| Propriedade | Descrição | Default |
|---|---|---|
| `seedgrid.security.avatar.filesystem.base-path` | Diretório raiz onde os arquivos são salvos. Deve ter permissão de escrita para o processo da aplicação. | `/var/seedgrid/files` |

---

#### `minio`

MinIO é um serviço de object storage compatível com a API do Amazon S3, mas que você roda na sua própria infraestrutura. Ótimo para quem quer S3-like sem depender da AWS.

```properties
seedgrid.security.avatar.storage=minio
seedgrid.security.avatar.minio.endpoint=http://minio:9000
seedgrid.security.avatar.minio.bucket=avatars
seedgrid.security.avatar.minio.region=us-east-1
seedgrid.security.avatar.minio.access-key=${MINIO_ACCESS_KEY}
seedgrid.security.avatar.minio.secret-key=${MINIO_SECRET_KEY}
```

| Propriedade | Descrição |
|---|---|
| `minio.endpoint` | URL completa do servidor MinIO, incluindo protocolo e porta |
| `minio.bucket` | Nome do bucket onde os arquivos serão armazenados (deve existir previamente) |
| `minio.region` | Região configurada no MinIO (pode ser qualquer string, ex: `us-east-1`) |
| `minio.access-key` | Chave de acesso (equivalente ao usuário) |
| `minio.secret-key` | Chave secreta (equivalente à senha) |

---

#### `s3`

Amazon S3 é o serviço de object storage da AWS.

```properties
seedgrid.security.avatar.storage=s3
seedgrid.security.avatar.s3.bucket=minha-empresa-avatars
seedgrid.security.avatar.s3.region=sa-east-1
seedgrid.security.avatar.s3.access-key=${AWS_ACCESS_KEY_ID}
seedgrid.security.avatar.s3.secret-key=${AWS_SECRET_ACCESS_KEY}
```

| Propriedade | Descrição |
|---|---|
| `s3.bucket` | Nome do bucket S3 (deve existir previamente e a IAM role deve ter acesso) |
| `s3.region` | Região AWS onde o bucket está (`sa-east-1` = São Paulo, `us-east-1` = N. Virginia) |
| `s3.access-key` | AWS Access Key ID de uma IAM user com permissão `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` |
| `s3.secret-key` | AWS Secret Access Key correspondente |
| `s3.endpoint` | Endpoint customizado (opcional). Use para LocalStack em testes ou S3-compatíveis |

---

#### `azure-blob`

Azure Blob Storage é o serviço de object storage do Microsoft Azure.

```properties
seedgrid.security.avatar.storage=azure-blob

# Opção 1: connection string (mais fácil)
seedgrid.security.avatar.azure-blob.connection-string=${AZURE_STORAGE_CONNECTION_STRING}
seedgrid.security.avatar.azure-blob.container=avatars

# Opção 2: account name + key
seedgrid.security.avatar.azure-blob.account-name=minhacontastorage
seedgrid.security.avatar.azure-blob.account-key=${AZURE_STORAGE_ACCOUNT_KEY}
seedgrid.security.avatar.azure-blob.container=avatars
```

| Propriedade | Descrição |
|---|---|
| `azure-blob.connection-string` | String de conexão completa encontrada no portal Azure > Storage Account > Access keys |
| `azure-blob.container` | Nome do container (equivalente ao bucket no S3) |
| `azure-blob.account-name` | Nome da Storage Account (alternativa à connection string) |
| `azure-blob.account-key` | Chave de acesso da Storage Account |

---

#### `gcs`

Google Cloud Storage é o serviço de object storage do Google Cloud Platform.

```properties
seedgrid.security.avatar.storage=gcs
seedgrid.security.avatar.gcs.bucket=minha-empresa-avatars
seedgrid.security.avatar.gcs.project-id=meu-projeto-gcp

# Opção 1: arquivo de service account
seedgrid.security.avatar.gcs.credentials-file=/etc/secrets/gcs-service-account.json

# Opção 2: JSON inline via variável de ambiente
seedgrid.security.avatar.gcs.credentials-json=${GCS_CREDENTIALS_JSON}
```

| Propriedade | Descrição |
|---|---|
| `gcs.bucket` | Nome do bucket GCS |
| `gcs.project-id` | ID do projeto GCP |
| `gcs.credentials-file` | Caminho para o arquivo JSON da service account com roles `Storage Object Creator` e `Storage Object Viewer` |
| `gcs.credentials-json` | Conteúdo completo do JSON da service account (útil quando não pode montar arquivos no container) |

---

#### `google-drive`

Armazena arquivos numa pasta específica do Google Drive usando uma service account.

```properties
seedgrid.security.avatar.storage=google-drive
seedgrid.security.avatar.google-drive.root-folder-id=1AbCdEfGhIjKlMnOpQrStUvWxYz
seedgrid.security.avatar.google-drive.credentials-file=/etc/secrets/gdrive-sa.json
```

| Propriedade | Descrição |
|---|---|
| `google-drive.root-folder-id` | ID da pasta raiz no Google Drive. Você encontra o ID na URL quando abre a pasta: `drive.google.com/drive/folders/{ID}` |
| `google-drive.credentials-file` | Arquivo JSON da service account com permissão na pasta |
| `google-drive.credentials-json` | Conteúdo JSON da service account (alternativa ao arquivo) |

---

#### `onedrive`

Armazena arquivos no Microsoft OneDrive usando um app registrado no Azure AD.

```properties
seedgrid.security.avatar.storage=onedrive
seedgrid.security.avatar.onedrive.client-id=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
seedgrid.security.avatar.onedrive.client-secret=${ONEDRIVE_CLIENT_SECRET}
seedgrid.security.avatar.onedrive.tenant-id=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
seedgrid.security.avatar.onedrive.root-folder-path=/seedgrid/avatars
```

| Propriedade | Descrição |
|---|---|
| `onedrive.client-id` | Application (client) ID do app registrado no Azure Active Directory |
| `onedrive.client-secret` | Client secret criado para o app no Azure AD |
| `onedrive.tenant-id` | Directory (tenant) ID da organização no Azure AD |
| `onedrive.root-folder-path` | Caminho da pasta raiz no OneDrive onde os arquivos serão armazenados (default: `/seedgrid/files`) |

---

## 11. Self-Enter — Auto-Cadastro

O Self-Enter permite que usuários solicitem acesso à aplicação sem que um admin precise criá-los manualmente. Existem dois fluxos:

1. **Público:** O usuário preenche um formulário, confirma o e-mail com código OTP, e aguarda aprovação do admin.
2. **Convite:** O admin envia um convite para um e-mail específico; o convidado completa o cadastro pelo link.

---

#### `seedgrid.security.self-enter.enabled`

| Tipo | Default |
|---|---|
| `boolean` | `false` |

**O que é:** Liga o módulo de self-enter como um todo. Se `false`, nenhum dos endpoints de self-enter funciona.

```properties
seedgrid.security.self-enter.enabled=true
```

---

#### `seedgrid.security.self-enter.public-enabled`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** Permite cadastro público — qualquer pessoa com acesso ao frontend pode solicitar uma conta.

**Quando desabilitar:** Se você quiser apenas o fluxo de convite (só entra quem o admin convidar explicitamente).

```properties
seedgrid.security.self-enter.public-enabled=true
```

---

#### `seedgrid.security.self-enter.invite-enabled`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** Habilita o fluxo de convite — o admin pode enviar e-mails de convite para usuários específicos.

```properties
seedgrid.security.self-enter.invite-enabled=true
```

---

#### `seedgrid.security.self-enter.request-ttl`

| Tipo | Default |
|---|---|
| `Duration` | `PT48H` |

**O que é:** O tempo máximo que uma solicitação de entrada tem para ser completamente processada — desde a criação até a aprovação pelo admin.

**O que acontece após expirar:** A solicitação é marcada como expirada pelo job de limpeza. O usuário precisaria fazer uma nova solicitação.

**Esse TTL cobre todo o processo:**
```
Usuário cria pedido → confirma e-mail → admin aprova
|←————————————— request-ttl (48h) ————————————————→|
```

```properties
seedgrid.security.self-enter.request-ttl=PT48H
```

---

#### `seedgrid.security.self-enter.email-token-ttl`

| Tipo | Default |
|---|---|
| `Duration` | `PT30M` |

**O que é:** Por quanto tempo o código de confirmação de e-mail (código OTP de 6 dígitos) fica válido após ser enviado.

**Diferença para o `request-ttl`:** O `request-ttl` é o prazo total da solicitação. O `email-token-ttl` é o prazo apenas do código enviado por e-mail. Se o código expirar, o usuário pode pedir reenvio (`POST /public/self-enter/resend/{requestId}`) sem precisar criar uma nova solicitação.

```properties
seedgrid.security.self-enter.email-token-ttl=PT30M
```

---

#### `seedgrid.security.self-enter.invite-ttl`

| Tipo | Default |
|---|---|
| `Duration` | `PT168H` |

**O que é:** Por quanto tempo um link de convite fica válido após ser enviado pelo admin. `PT168H` = 7 dias.

**Por que mais longo que o `request-ttl`:** Convites são enviados para pessoas externas que podem não verificar o e-mail imediatamente. 7 dias dá tempo suficiente.

```properties
seedgrid.security.self-enter.invite-ttl=PT168H
```

---

#### `seedgrid.security.self-enter.default-role`

| Tipo | Default |
|---|---|
| `String` | `USER` |

**O que é:** A role atribuída automaticamente ao usuário quando o admin aprova a solicitação.

**Importante:** A role com esse nome deve existir no banco de dados. As migrations criam algumas roles padrão, mas verifique se `USER` está entre elas.

```properties
seedgrid.security.self-enter.default-role=USER
```

---

#### `seedgrid.security.self-enter.cleanup.enabled`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** Habilita o job automático que remove solicitações expiradas do banco de dados.

**Por que é necessário:** Sem limpeza, solicitações expiradas acumulam no banco. A limpeza também libera o e-mail para uma nova solicitação caso o usuário queira tentar de novo.

```properties
seedgrid.security.self-enter.cleanup.enabled=true
```

---

#### `seedgrid.security.self-enter.cleanup.cron`

| Tipo | Default |
|---|---|
| `String` (Quartz cron) | `0 */10 * * * ?` |

**O que é:** Expressão cron que define quando o job de limpeza executa.

**Lendo a expressão `0 */10 * * * ?`:**
- `0` = no segundo 0
- `*/10` = a cada 10 minutos
- `*` = qualquer hora
- `*` = qualquer dia do mês
- `*` = qualquer mês
- `?` = qualquer dia da semana

Ou seja: executa no segundo zero de cada décimo minuto (00:00, 00:10, 00:20...).

```properties
seedgrid.security.self-enter.cleanup.cron=0 */10 * * * ?
```

---

#### `seedgrid.security.self-enter.cleanup.purge-after`

| Tipo | Default |
|---|---|
| `Duration` | `PT168H` |

**O que é:** Quando o job de limpeza roda, ele remove solicitações que foram criadas há mais tempo que este valor. `PT168H` = 7 dias.

**Cenário:** Uma solicitação com `request-ttl=PT48H` expira após 48 horas. O job de limpeza (com `purge-after=PT168H`) só vai removê-la do banco após 7 dias. Isso dá uma "janela de graça" para diagnóstico ou recuperação manual.

```properties
seedgrid.security.self-enter.cleanup.purge-after=PT168H
```

---

## 12. Auditoria

O módulo registra eventos de segurança (logins, trocas de senha, mudanças de permissão, etc.) para fins de conformidade e investigação de incidentes.

---

#### `seedgrid.audit.persistence.strategy`

| Tipo | Default | Valores |
|---|---|---|
| `String` | `JPA` | `JPA`, `MONGODB` |

**O que é:** Define onde os logs de auditoria são armazenados.

- **`JPA`**: salva no mesmo banco PostgreSQL da aplicação. Simples, sem infraestrutura adicional.
- **`MONGODB`**: salva num banco MongoDB dedicado. Melhor para grandes volumes de logs e queries analíticas, já que MongoDB é mais eficiente para documentos não estruturados.

---

#### Estratégia JPA (PostgreSQL)

```properties
seedgrid.audit.persistence.strategy=JPA

# Para projetos standalone (uma única instância, sem multitenancy):
seedgrid.audit.jpa.db-kind=postgresql
seedgrid.audit.jpa.jdbc.url=${SEEDGRID_AUDIT_DB_URL}
seedgrid.audit.jpa.username=${SEEDGRID_AUDIT_DB_USERNAME}
seedgrid.audit.jpa.password=${SEEDGRID_AUDIT_DB_PASSWORD}
```

> **ATENÇÃO em projetos multitenancy:** Não configure `seedgrid.audit.jpa.*`. O módulo usará o datasource do tenant atual automaticamente. Configurar esses campos em multitenancy causa conflito de datasource.

---

#### Estratégia MongoDB

```properties
seedgrid.audit.persistence.strategy=MONGODB
seedgrid.audit.mongodb.connection-string=mongodb://usuario:senha@localhost:27017/audit?authSource=admin
```

| Propriedade | Descrição |
|---|---|
| `seedgrid.audit.mongodb.connection-string` | URI de conexão do MongoDB no formato padrão. O banco e a coleção são criados automaticamente se não existirem. |

---

## 13. Primeiro Usuário Admin

Quando a aplicação sobe pela primeira vez com o banco de dados vazio, o módulo cria automaticamente um usuário administrador inicial com as configurações abaixo.

---

#### `seedgrid.security.first-admin-user.email`

| Tipo | Obrigatório |
|---|---|
| `String` | Sim |

**O que é:** O e-mail do usuário administrador inicial. Deve ser um e-mail real que você acessa, pois o link de confirmação e as credenciais serão enviados para ele.

```properties
seedgrid.security.first-admin-user.email=admin@minha-empresa.com.br
```

---

#### `seedgrid.security.first-admin-user.name`

| Tipo | Default |
|---|---|
| `String` | — |

**O que é:** O nome de exibição do usuário administrador inicial.

```properties
seedgrid.security.first-admin-user.name=Administrador
```

---

#### `seedgrid.security.first-admin-user.whatsapp`

| Tipo | Default |
|---|---|
| `String` | *(opcional)* |

**O que é:** O número de WhatsApp do primeiro administrador, no formato internacional (com código do país, sem `+`, sem espaços ou traços).

**Para que serve:** Se o módulo de 2FA via WhatsApp estiver habilitado (`seedgrid.security.2f.allowed-methods=WHATSAPP`), o admin inicial já terá o número pré-cadastrado no perfil e poderá habilitar o 2FA via WhatsApp imediatamente após o primeiro login, sem precisar atualizar o perfil manualmente.

```properties
# Exemplos de formato aceito:
seedgrid.security.first-admin-user.whatsapp=5511999999999
# 55 = Brasil, 11 = DDD, 999999999 = número
```

> Se não for usar 2FA via WhatsApp, deixe essa propriedade de fora — ela é totalmente opcional.

---

#### `seedgrid.security.first-admin-user.initial-password`

| Tipo | Default |
|---|---|
| `String` | *(senha gerada automaticamente)* |

**O que é:** A senha inicial do administrador.

**Se omitido:** O módulo gera uma senha aleatória forte e a envia por e-mail junto com o e-mail de boas-vindas.

**Se configurado:** A senha especificada aqui é usada, mas o usuário ainda será obrigado a trocá-la no primeiro login (`changePasswordRequired = true`).

```properties
# Deixe comentado para senha automática (recomendado):
# seedgrid.security.first-admin-user.initial-password=SenhaInicial@123
```

---

## 14. Flyway — Migrações de Banco

O Flyway é uma ferramenta que gerencia a evolução do schema do banco de dados. Ele aplica scripts SQL numerados em sequência e garante que cada script seja executado apenas uma vez.

```properties
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration/security,classpath:db/migration/audit
```

| Propriedade | Descrição |
|---|---|
| `quarkus.flyway.migrate-at-start=true` | Executa as migrations automaticamente quando a aplicação sobe. Sem isso, você precisaria rodar manualmente. |
| `quarkus.flyway.locations` | Lista de caminhos onde o Flyway procura scripts SQL. O módulo fornece dois: `security` (tabelas de usuários, roles, tokens) e `audit` (tabelas de auditoria). |

**O que as migrations criam:** `users`, `roles`, `permissions`, `user_roles`, `user_companies`, `refresh_tokens`, `password_history`, `backup_codes`, `entry_requests`, `entry_request_validation_tokens`, tabelas de auditoria.

---

## 15. Rate Limiting de Login

Limita quantas tentativas de login podem ser feitas em um intervalo de tempo. Protege contra ataques de força bruta onde um atacante tenta milhares de senhas automaticamente.

---

#### `seedgrid.security.rate-limit.login.enabled`

| Tipo | Default |
|---|---|
| `boolean` | `true` |

**O que é:** Liga o rate limiting no endpoint `POST /auth/login`.

```properties
seedgrid.security.rate-limit.login.enabled=true
```

---

#### `seedgrid.security.rate-limit.login.max-requests`

| Tipo | Default |
|---|---|
| `int` | `100` |

**O que é:** Número máximo de requisições de login permitidas dentro da janela de tempo.

**Escolhendo o valor:** Para uso humano normal, 10 tentativas por minuto é mais que suficiente. 100 é generoso e adequado para ambientes de teste ou APIs consumidas por outros sistemas.

```properties
seedgrid.security.rate-limit.login.max-requests=10
```

---

#### `seedgrid.security.rate-limit.login.window`

| Tipo | Default |
|---|---|
| `Duration` | `PT1M` |

**O que é:** A janela de tempo em que o contador de requisições é mantido. Após esse período, o contador é zerado.

**Exemplo:** Com `max-requests=10` e `window=PT1M`, é possível fazer até 10 tentativas de login por minuto por IP. Na tentativa 11, o servidor retorna `429 Too Many Requests`.

```properties
seedgrid.security.rate-limit.login.window=PT1M
```

---

## 16. Fluxos de Negócio

### 16.1 Primeira Subida da Aplicação

```
APLICAÇÃO SOBE
     │
     ▼
Flyway roda migrations
→ Cria todas as tabelas no banco de dados
     │
     ▼
FirstAdminBootstrapService verifica se a tabela "users" está vazia
     │
     ├─ NÃO está vazia → pula o bootstrap (já foi feito antes)
     │
     └─ ESTÁ vazia → cria o primeiro admin:
          • email = first-admin-user.email
          • name = first-admin-user.name
          • isRoot = true
          • changePasswordRequired = true  ← obrigado a trocar na 1ª vez
          • emailConfirmedAt = null  ← e-mail ainda não confirmado
          • senha = initial-password OU senha gerada automaticamente
          │
          ▼
     Envia e-mail "welcome-email" com:
          • Senha temporária (em texto puro, só dessa vez)
          • Link de confirmação de e-mail
          • Link para acessar a plataforma
```

**Ação do admin após receber o e-mail:**

```
1. Clicar no link de confirmação de e-mail
   → GET /auth/confirm-email?token={token}
   → emailConfirmedAt = agora ✓

2. Fazer login com o e-mail e a senha do e-mail
   → POST /auth/login
   → Resposta: { changePasswordRequired: true }

3. O frontend redireciona para a tela de troca de senha obrigatória
   → PUT /users/change-password
   → changePasswordRequired = false ✓
   → Todas as sessões são revogadas
   → Admin faz login novamente com a nova senha
```

---

### 16.2 Login Padrão (email + senha)

**Endpoint:** `POST /auth/login`

```json
// Request
{ "login": "usuario@empresa.com", "password": "MinhaS3nha!" }
```

**Sequência interna:**
```
1. Busca usuário pelo e-mail
2. Verifica se o usuário está ativo
3. Valida a senha: bcrypt_verify(publicId + senhaFornecida, hashArmazenado)
4. Verifica se o e-mail foi confirmado
   └─ Se não → erro 403 "EmailNotConfirmedException"
5. Verifica se o usuário tem 2FA habilitado
   ├─ SEM 2FA:
   │    • Emite refresh token (armazenado no banco)
   │    • Emite JWT com claims do usuário
   │    • Retorna: { accessToken, refreshToken, changePasswordRequired }
   │
   └─ COM 2FA:
        • Cria sessão temporária de 2FA (salva no cache com TTL)
        • Retorna: { requires2FA: true, tempToken: "uuid", method: "TOTP" }
        • ← NÃO emite JWT ainda! Aguarda validação do 2FA
```

**Resposta sem 2FA:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "a1b2c3d4...",
  "tokenType": "Bearer",
  "changePasswordRequired": false
}
```

**Resposta com 2FA:**
```json
{
  "requires2FA": true,
  "tempToken": "550e8400-e29b-41d4-a716-446655440000",
  "method": "TOTP"
}
```

---

### 16.3 Renovação de Token (Refresh)

**Endpoint:** `POST /auth/refresh`

```json
// Request
{ "refreshToken": "a1b2c3d4..." }

// Response
{
  "accessToken": "eyJ... (novo)",
  "refreshToken": "e5f6g7h8... (novo)",
  "tokenType": "Bearer"
}
```

**O que acontece internamente:**
```
1. Hash do refreshToken fornecido
2. Busca no banco pelo hash (com lock for update — evita race conditions)
3. Verifica se o token está dentro do TTL
4. Verifica se o token já foi consumido
   ├─ Se JÁ consumido (reuso):
   │    → REVOGA TODA A FAMÍLIA DE TOKENS
   │    → Usuário é deslogado de todas as sessões
   │    → Retorna 401 — roubo de token detectado!
   │
   └─ Se NÃO consumido:
        → Marca o token atual como consumido
        → Gera novo refresh token (mesma família, novo ID)
        → Gera novo JWT
        → Retorna novo par de tokens
```

---

### 16.4 Confirmação de E-mail

**Endpoint:** `GET /auth/confirm-email?token={token}`

O link completo é enviado no e-mail e tem o formato:
```
https://app.empresa.com.br/auth/confirm-email?token=abc123xyz
```

O frontend lê o `token` da URL e chama o endpoint do backend.

**O que acontece:**
```
1. Busca usuário pelo emailConfirmationToken
2. Token inválido ou não encontrado → 404
3. Marca o e-mail como confirmado:
   • emailConfirmedAt = agora
   • emailConfirmationToken = null (invalidado)
4. Usuário agora pode fazer login ✓
```

> **Token expirado:** Por padrão os tokens de confirmação de e-mail não têm TTL próprio — eles ficam válidos até serem usados. Se quiser expiração, use o fluxo de reenvio de confirmação.

---

### 16.5 Recuperação de Senha

```
ETAPA 1 — Solicitação
─────────────────────────────────────────────────────────────
POST /auth/forgot-password
{ "email": "usuario@empresa.com", "newPassword": "NovaSenha@1" }

O que acontece internamente:
• Busca o usuário pelo e-mail
• Pré-hasheia a nova senha: bcrypt(publicId + novaSenha)
• Armazena o hash como tempNewPassword (não substitui a senha ainda!)
• Gera um resetToken único (UUID)
• Define expiração do token: agora + 1 hora
• Envia e-mail "password-reset" com link contendo o token

Por que pré-hashear? Para não guardar a nova senha em texto puro.
O link do e-mail é o "consentimento" — confirma que o usuário
realmente quer trocar. Só então a senha é efetivada.

─────────────────────────────────────────────────────────────
ETAPA 2 — Confirmação (usuário clica no link)
─────────────────────────────────────────────────────────────
GET /auth/reset-password?token=abc123

O que acontece internamente:
• Busca usuário pelo passwordResetToken
• Verifica se o token não expirou
• Move tempNewPassword → password (nova senha efetivada)
• Limpa: tempNewPassword = null, resetToken = null, expiração = null
• Atualiza a expiração da nova senha (+ expiration-days)
• REVOGA todos os refresh tokens do usuário
  → Todas as sessões ativas são encerradas
  → O atacante (se houver) perde o acesso imediatamente

Usuário pode fazer login com a nova senha ✓
```

---

### 16.6 Troca de Senha (autenticado)

**Endpoint:** `PUT /users/change-password` *(requer JWT)*

Este endpoint é anotado com `@SgPermitWithoutPasswordChange` — ou seja, funciona mesmo quando `changePasswordRequired = true`, que é exatamente quando é mais necessário.

```json
{
  "currentPassword": "SenhaAtual@1",
  "newPassword": "NovaSenha@2"
}
```

**Sequência:**
```
1. Extrai o e-mail do JWT
2. Valida a senha atual: bcrypt_verify(publicId + senhaAtual, hashArmazenado)
3. Valida a nova senha contra a política (tamanho, maiúsculas, etc.)
4. Verifica se a nova senha está no histórico das últimas max-history senhas
   └─ Se estiver → erro: "senha usada recentemente"
5. Salva a senha atual no histórico (com data e motivo "USER_CHANGE")
6. Hasheia e salva a nova senha
7. Atualiza passwordExpiresAt = agora + expiration-days
8. REVOGA todos os refresh tokens do usuário
9. Limpa changePasswordRequired = false
```

---

### 16.7 Habilitar 2FA — E-mail

**Endpoint:** `POST /2fa/enable` *(requer JWT)*

```json
{ "method": "EMAIL" }
```

```
1. Verifica: seedgrid.security.2f.enabled = true
2. Verifica: "EMAIL" está em allowed-methods
3. Verifica: e-mail do usuário está confirmado (pré-requisito)
4. Habilita: twoFactorEnabled = true, twoFactorPrimaryMethod = "EMAIL"
5. Gera backup-codes.count códigos de backup:
   • Cada código é gerado aleatoriamente
   • Cada código é hasheado (BCrypt) e salvo no banco
   • Os códigos em texto puro são retornados APENAS AGORA
6. Retorna os backup codes ← exibir ao usuário e pedir para guardar!
```

**A partir desse momento:** No próximo login, após validar e-mail e senha, um código de 6 dígitos será enviado por e-mail. O usuário tem `email.code-ttl` para usá-lo.

---

### 16.8 Habilitar 2FA — TOTP

**Endpoint:** `POST /2fa/enable` *(requer JWT)*

```json
{ "method": "TOTP" }
```

**Fase 1 — Iniciar setup:**
```
1. Verifica: seedgrid.security.2f.enabled = true e "TOTP" em allowed-methods
2. Gera segredo TOTP: string aleatória Base32 (ex: "JBSWY3DPEHPK3PXP")
3. Criptografa o segredo com AES-256 usando a encryption.key
4. Salva o segredo criptografado em twoFactorTotpSecret
5. Constrói a URL OTP Auth:
   otpauth://totp/MinhaApp:joao@empresa.com?secret=JBSWY3D...&issuer=MinhaApp
6. Gera QR code PNG em Base64 a partir da URL
7. Retorna: { secret: "JBSWY3D...", qrCodeBase64: "data:image/png;base64,..." }
```

O 2FA **ainda não está habilitado** — o usuário precisa escanear o QR code e confirmar.

**Fase 2 — Confirmar TOTP:** `POST /2fa/totp/confirm`

```json
{ "code": "123456" }
```

```
1. Busca o segredo TOTP do usuário (descriptografa com AES-256)
2. Calcula o código esperado: TOTP(segredo, timestamp_atual)
3. Verifica se o código fornecido bate com o esperado (com tolerância de window)
4. Se válido:
   • twoFactorEnabled = true
   • twoFactorPrimaryMethod = "TOTP"
   • twoFactorEnabledAt = agora
   • Gera backup codes → retorna ao usuário ← guardar!
5. Se inválido: erro, o usuário pode tentar novamente
```

---

### 16.9 Login com 2FA Ativo

```
POST /auth/login  ←  e-mail + senha válidos, 2FA habilitado
     │
     ▼
Resposta: { requires2FA: true, tempToken: "uuid", method: "TOTP" }
     │
     ├── método TOTP:       ├── método EMAIL:              ├── método WHATSAPP:
     │   Usuário abre o   │   Código enviado por e-mail │   Mensagem enviada via
     │   app autenticador │   (template:                │   WhatsApp (template:
     │   e vê o código    │   two-factor-code.html)     │   two-factor-code-whatsapp.txt)
     │                                   │
     └───────────────┬───────────────────┘
                     ▼
     POST /public/2fa/verify
     { "tempToken": "uuid", "code": "847291" }
          │
          ▼
     Tenta validar com método primário
          │
          ├── VÁLIDO → emite access token + refresh token ✓
          │
          └── INVÁLIDO → tenta como backup code
               │
               ├── backup code válido → emite tokens ✓ (marca backup code como usado)
               │
               └── backup code inválido → incrementa tentativas
                    │
                    └── atingiu max-attempts → sessão revogada
                         → usuário precisa fazer login novamente

# Se método EMAIL e código não chegou:
POST /public/2fa/resend
{ "tempToken": "uuid" }
→ reenvia código para o e-mail
```

---

### 16.10 Desabilitar 2FA

**Endpoint:** `POST /2fa/disable` *(requer JWT)*

O usuário deve provar a identidade antes de desabilitar. Aceita uma das opções:

```json
// Opção A: código TOTP atual do app autenticador
{ "verificationCode": "847291" }

// Opção B: um dos backup codes
{ "backupCode": "abc123" }

// Opção C: senha atual do usuário
{ "password": "MinhaS3nha@1" }
```

```
Se verificação bem-sucedida:
• twoFactorEnabled = false
• twoFactorPrimaryMethod = null
• twoFactorTotpSecret = null (segredo apagado)
• twoFactorEnabledAt = null
• Todos os backup codes removidos do banco
```

---

### 16.11 Self-Enter — Cadastro Público

Fluxo completo desde a solicitação até o primeiro login:

```
USUÁRIO                          BACKEND                          ADMIN
   │                                │                               │
   │  POST /public/self-enter/      │                               │
   │  requests                      │                               │
   │  { email, name, password }     │                               │
   │──────────────────────────────→ │                               │
   │                                │ Cria EntryRequest             │
   │                                │ status: PENDING_EMAIL         │
   │                                │ Valida: e-mail não existe     │
   │                                │ Valida: não há pedido ativo   │
   │                                │                               │
   │                                │ Envia e-mail com código OTP   │
   │  E-mail: "Confirme seu e-mail" │ ← entry-request-              │
   │  Código: 847 291               │    confirmation-code.html     │
   │  Expira em: 30 minutos         │                               │
   │                                │                               │
   │  POST /public/self-enter/      │                               │
   │  confirm-email/{requestId}     │                               │
   │  { "code": "847291" }          │                               │
   │──────────────────────────────→ │                               │
   │                                │ Valida código + TTL           │
   │                                │ status: PENDING_APPROVAL      │
   │  ← 200 OK                      │                               │
   │                                │ Notifica admin (opcional)     │
   │                                │ ──────────────────────────→  │
   │                                │                               │ Admin vê pedido
   │                                │                               │ no painel
   │                                │                               │
   │                                │                   Admin aprova│
   │                                │ ←─────────────────────────── │
   │                                │                               │
   │                                │ Cria User                     │
   │                                │ status: APPROVED              │
   │                                │ role: default-role            │
   │                                │                               │
   │  E-mail: "Sua conta foi        │ Envia e-mail de aprovação     │
   │  aprovada!"                    │ ← entry-request-approved.html │
   │  Link: Fazer Login             │                               │
   │                                │                               │
   │  POST /auth/login              │                               │
   │  { email, password }           │                               │
   │──────────────────────────────→ │                               │
   │  ← access token + refresh ✓    │                               │
```

---

### 16.12 Self-Enter — Convite

```
ADMIN                            BACKEND                          CONVIDADO
  │                                 │                                 │
  │ Cria convite (admin endpoint)   │                                 │
  │ { email: "joao@empresa.com" }   │                                 │
  │───────────────────────────────→ │                                 │
  │                                 │ Cria EntryRequest               │
  │                                 │ origin: INVITE                  │
  │                                 │ Gera token único (UUID)         │
  │                                 │ Hash do token salvo no banco    │
  │                                 │                                 │
  │                                 │ Envia e-mail de convite         │
  │                                 │ ──────────────────────────────→ │
  │                                 │ ← entry-request-invitation.html │
  │                                 │   Link: {base-url}/register     │
  │                                 │         ?token={token}          │
  │                                 │                                 │
  │                                 │  GET /public/self-enter/        │
  │                                 │  invitations/{token}            │
  │                                 │ ←────────────────────────────── │
  │                                 │ Retorna: { name, email }        │
  │                                 │ ──────────────────────────────→ │
  │                                 │                                 │
  │                                 │  POST /public/self-enter/       │
  │                                 │  invitations/{token}/complete   │
  │                                 │  { "password": "..." }          │
  │                                 │ ←────────────────────────────── │
  │                                 │ Hash senha salvo no pedido      │
  │                                 │ status: PENDING_APPROVAL        │
  │                                 │ ──────────────────────────────→ │
  │                                 │
  │ Admin aprova                    │
  │───────────────────────────────→ │
  │                                 │ Cria User ✓                     │
  │                                 │ Envia e-mail de aprovação       │
  │                                 │ ──────────────────────────────→ │
```

---

### 16.13 Avatar do Usuário

**Upload:** `POST /users/{publicId}/avatar`

```json
{
  "content": "/9j/4AAQSkZJRgAB...",
  "contentType": "image/jpeg",
  "filename": "minha-foto.jpg"
}
```

O `content` deve ser o arquivo em Base64 (sem o prefixo `data:image/jpeg;base64,`).

**Internamente:**
```
1. Decodifica o Base64 para bytes
2. Gera nome único: {uuid}.{ext}
3. Salva em: {user-prefix}/{publicId}/{uuid}.{ext}
4. Se havia avatar anterior: deleta do storage
5. Atualiza user.avatarStorageKey com a URI do novo arquivo
   Ex: filesystem:///var/seedgrid/files/avatars/users/abc/def.jpg
       s3://meu-bucket/avatars/users/abc/def.jpg
```

**Download:** `GET /users/{publicId}/avatar`
- Retorna o arquivo binário com headers `Content-Type` e `Content-Disposition`

**Reset:** `DELETE /users/{publicId}/avatar`
- Remove o avatar customizado
- Atribui aleatoriamente um dos avatares padrão da pasta `{default-prefix}`

---

### 16.14 Permissões e Perfil

**Endpoint:** `GET /me/permissions` *(requer JWT)*

```json
// Response
{
  "roles": ["ADMIN", "FINANCEIRO"],
  "permissions": ["users:read", "users:write", "reports:read", "invoices:write"]
}
```

As permissões são a **união** de todas as permissões de todas as roles do usuário. O resultado fica em cache por `rbac.cache-ttl`.

---

## 17. SPIs — Pontos de Extensão

O módulo usa o padrão CDI `@DefaultBean`: toda implementação padrão é marcada com `@DefaultBean`. Se você criar um bean com a mesma interface mas sem `@DefaultBean` (apenas `@ApplicationScoped`), o Quarkus automaticamente usa o seu bean no lugar do padrão.

**Regra geral:** basta criar uma classe com `@ApplicationScoped` implementando a interface desejada. Sem `@DefaultBean`, sem `@Alternative`, sem `@Priority`. O CDI cuida do resto.

---

### 17.1 `TemplateSecurityProvider`

**Propósito:** Controla o conteúdo HTML de todos os e-mails transacionais do módulo. Por padrão, usa os arquivos `.html` embutidos no JAR. Implemente para usar Qute, Thymeleaf, banco de dados, API externa, etc.

```java
public interface TemplateSecurityProvider {
    String emailConfirmation(User user, String confirmationLink);
    String passwordReset(User user, String resetLink);
    String entryRequestConfirmation(User user, String confirmationLink);
    String entryRequestConfirmationCode(User user, String code, int expiresInMinutes);
    String entryRequestInvitation(User user, String invitationLink, int expiresInHours, String invitedBy);
    String entryRequestApproved(User user, String loginLink);
    String welcomeEmail(User user, String temporaryPassword, String confirmationLink);
    String twoFactorCode(User user, String code, int expiresInMinutes);
}
```

**Exemplo com Qute:**
```java
@ApplicationScoped
public class MeuTemplateProvider implements TemplateSecurityProvider {

    @Location("emails/boas-vindas")
    Template boasVindasTemplate;

    @Override
    public String welcomeEmail(User user, String temporaryPassword, String confirmationLink) {
        return boasVindasTemplate
            .data("nome", user.getName())
            .data("email", user.getEmail())
            .data("senha", temporaryPassword)
            .data("link", confirmationLink)
            .render();
    }

    // Os demais métodos podem chamar this.emailConfirmation() padrão
    // ou ter seus próprios templates. Você precisa implementar TODOS.
}
```

**Alternativa mais simples (sem Java):** Coloque arquivos HTML em `src/main/resources/templates/security/` com os mesmos nomes dos padrão. O `DefaultTemplateSecurityProvider` prioriza essa pasta. Os placeholders `{name}`, `{confirmationLink}`, etc. são substituídos automaticamente.

---

### 17.2 `JwtTokenCustomizer`

**Propósito:** Adiciona claims extras ao JWT gerado em cada login. Útil para sistemas multi-tenant (adicionar tenant ID), sistemas com planos (adicionar `plan: "PRO"`) ou qualquer informação que precisa estar disponível sem consultar o banco.

```java
public interface JwtTokenCustomizer {
    Map<String, Object> customizeClaims(User user, List<String> roles, String hostHeader);
}
```

**Parâmetros:**
- `user`: o usuário que está fazendo login — você tem acesso a todos os campos de `User` (inclusive `additionalAttributes`)
- `roles`: lista de nomes das roles do usuário
- `hostHeader`: o valor do header HTTP `Host` da requisição — útil para identificar o tenant em sistemas multi-tenant (ex: `empresa-a.app.com.br`)

**Exemplo — adicionar tenantId e plano ao JWT:**
```java
@ApplicationScoped
public class TenantJwtCustomizer implements JwtTokenCustomizer {

    @Inject
    TenantService tenantService;

    @Override
    public Map<String, Object> customizeClaims(User user, List<String> roles, String hostHeader) {
        String tenantId = tenantService.resolveFromHost(hostHeader);
        String plano = tenantService.getPlano(tenantId);

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenantId", tenantId);
        claims.put("plan", plano);
        // Esses campos estarão disponíveis no JWT de todos os usuários
        return claims;
    }
}
```

---

### 17.3 `JwtTokenValidator`

**Propósito:** Valida claims customizados em **cada requisição autenticada**. Complementa o `JwtTokenCustomizer` — se você adiciona claims na emissão, valida na verificação.

```java
public interface JwtTokenValidator {
    void validateCustomClaims(JWTClaimsSet claims, String hostHeader);
    // Lança TokenValidationException para rejeitar a requisição
}
```

**Exemplo — validar que o tenant do token bate com o host da requisição:**
```java
@ApplicationScoped
public class TenantJwtValidator implements JwtTokenValidator {

    @Override
    public void validateCustomClaims(JWTClaimsSet claims, String hostHeader) {
        String tokenTenant = (String) claims.getClaim("tenantId");
        String requestTenant = extrairTenantDoHost(hostHeader);

        if (!Objects.equals(tokenTenant, requestTenant)) {
            // Token de outro tenant sendo usado aqui — rejeitar!
            throw new TokenValidationException("Token inválido para este tenant");
        }
    }

    private String extrairTenantDoHost(String host) {
        // "empresa-a.app.com.br" → "empresa-a"
        return host.split("\\.")[0];
    }
}
```

---

### 17.4 `UserAdditionalFieldsValidator` + `additionalAttributes` ⭐ Campos próprios do usuário

**Propósito:** Guardar campos específicos da sua aplicação no usuário — `cpf`, `departamento`,
`dataNascimento`, preferências — **sem** criar subclasse de entidade nem migration de colunas.

A partir do security `2026.09.25`, `User` é uma entidade **concreta** (não existe mais `BaseUser` nem
`UserFactoryInterface`). Os campos da aplicação vão em `additionalAttributes`
(`Map<String, JsonNode>`), gravado na coluna `users.additional_attributes` (`jsonb`, migration `V232`
do módulo).

#### Passo 1 — Envie os campos na API

`POST /users`:

```json
{
  "email": "ana@empresa.com",
  "name": "Ana Silva",
  "roleIds": [1],
  "additionalAttributes": {
    "cpf": "123.456.789-00",
    "departamento": "Financeiro",
    "preferencias": { "tema": "escuro" }
  }
}
```

As respostas de `/users` sempre trazem `additionalAttributes` (`{}` quando vazio).

`PATCH /users/{publicId}` faz **merge** (JSON Merge Patch — RFC 7386): chaves ausentes são
preservadas, objetos são mesclados, arrays são substituídos e `null` remove a chave:

```json
{ "additionalAttributes": { "preferencias": { "tema": "claro" }, "cpf": null } }
```

#### Passo 2 — Valide as regras da sua aplicação

A implementação padrão (`DefaultUserAdditionalFieldsValidator`) não impõe regras. Para validar, crie um
bean `@ApplicationScoped` (sem `@DefaultBean` — ele substitui o padrão):

```java
// src/main/java/br/com/minhaempresa/security/AppUserAttributesValidator.java
package br.com.minhaempresa.security;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.exceptions.InvalidUserAdditionalAttributesException;
import br.com.seedgrid.security.spis.UserAdditionalFieldsValidator;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AppUserAttributesValidator implements UserAdditionalFieldsValidator {

    private static final Set<String> PERMITIDOS = Set.of("cpf", "departamento", "preferencias");

    @Override
    public void validate(User user, Map<String, JsonNode> atributos) {
        if (atributos == null) {
            return;
        }
        for (String chave : atributos.keySet()) {
            if (!PERMITIDOS.contains(chave)) {
                throw new InvalidUserAdditionalAttributesException("Atributo nao permitido: " + chave);
            }
        }
        JsonNode cpf = atributos.get("cpf");
        if (cpf != null && !cpf.asText().matches("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}")) {
            throw new InvalidUserAdditionalAttributesException("cpf invalido");
        }
    }
}
```

O validador recebe o mapa **final** (no PATCH, já mesclado). `InvalidUserAdditionalAttributesException`
vira **HTTP 400** e nada é gravado. Antes dele, o módulo aplica limites configuráveis:

```properties
seedgrid.security.user.additional-attributes.max-size-bytes=16384
seedgrid.security.user.additional-attributes.max-depth=5
seedgrid.security.user.additional-attributes.max-keys=100
```

#### Passo 3 — Leia os campos no seu código

```java
@Inject
UserRepository userRepository;

public void exemplo(String publicId) {
    User user = userRepository.findByPublicId(publicId).orElseThrow();
    JsonNode cpf = user.getAdditionalAttributes().get("cpf");
    String departamento = user.getAdditionalAttributes().get("departamento").asText();
}
```

Para filtrar por atributo, use SQL nativo (`additional_attributes->>'departamento' = :dep`) e, se for
filtro frequente, crie um índice de expressão ou GIN numa migration da sua aplicação.

> **Vindo da versão antiga (subclasse de `BaseUser` + `UserFactoryInterface`)?** Troque os imports
> `BaseUser*` → `User*`, remova a subclasse e a factory, e mova os dados das colunas próprias para
> `additional_attributes` numa migration. O passo a passo com SQL está no `docs/USAGE.md` do
> `seedgrid-quarkus-ext-security`.

**Campos já disponíveis em `User` (não precisa adicionar):**

| Campo | Tipo | Descrição |
|---|---|---|
| `email` | `String` | E-mail do usuário (único) |
| `name` | `String` | Nome de exibição |
| `whatsApp` | `String` | Número de WhatsApp |
| `avatarStorageKey` | `String` | URI do avatar no storage |
| `roles` | `List<Role>` | Roles do usuário |
| `companies` | `List<Company>` | Empresas vinculadas |
| `isRoot` | `boolean` | Usuário raiz (admin supremo) |
| `changePasswordRequired` | `boolean` | Deve trocar a senha |
| `passwordExpiresAt` | `Instant` | Quando a senha expira |
| `emailConfirmedAt` | `Instant` | Quando o e-mail foi confirmado |
| `twoFactorEnabled` | `boolean` | 2FA habilitado |
| `twoFactorPrimaryMethod` | `String` | Método 2FA ativo (`EMAIL`, `TOTP` ou `WHATSAPP`) |
| `additionalAttributes` | `Map<String, JsonNode>` | Campos próprios da aplicação (jsonb) |
| `publicId` | `String` (UUID) | Identificador público (herdado de `BaseEntity`) |

---

### 17.5 `TwoFactorStrategy`

**Propósito:** Adicionar métodos de 2FA além dos padrão (EMAIL e TOTP). Por exemplo: SMS, WhatsApp, push notification.

```java
public interface TwoFactorStrategy {
    String getMethodCode();       // Código único: "WHATSAPP", "SMS", etc.
    boolean canInitiate(User user);  // Quais pré-condições o usuário precisa ter?
    TwoFactorInitResult initiate(User user, TwoFactorContext context);  // Inicia o 2FA (envia o código)
    TwoFactorVerifyResult verify(User user, String code, TwoFactorContext context);  // Verifica o código
}
```

**Exemplo completo — 2FA via WhatsApp:**
```java
@ApplicationScoped
public class WhatsAppTwoFactorStrategy implements TwoFactorStrategy {

    @Inject
    WhatsAppService whatsApp;  // Seu serviço de envio (Twilio, Z-API, etc.)

    @Inject
    CacheService cache;

    @Override
    public String getMethodCode() {
        return "WHATSAPP";  // Identificador único para este método
    }

    @Override
    public boolean canInitiate(User user) {
        // O usuário só pode usar este método se tiver WhatsApp cadastrado
        return user.getWhatsApp() != null && !user.getWhatsApp().isBlank();
    }

    @Override
    public TwoFactorInitResult initiate(User user, TwoFactorContext context) {
        // Gera código de 6 dígitos
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        // Salva no cache com TTL de 5 minutos
        String cacheKey = "2fa:whatsapp:" + user.getPublicId();
        cache.put(cacheKey, code, Duration.ofMinutes(5));

        // Envia via WhatsApp
        whatsApp.sendMessage(user.getWhatsApp(),
            "Seu código de verificação: " + code + "\nVálido por 5 minutos.");

        return TwoFactorInitResult.success("Código enviado para o WhatsApp");
    }

    @Override
    public TwoFactorVerifyResult verify(User user, String code, TwoFactorContext context) {
        String cacheKey = "2fa:whatsapp:" + user.getPublicId();
        String stored = cache.get(cacheKey);

        if (code.equals(stored)) {
            cache.evict(cacheKey);  // Código usado: remove do cache
            return TwoFactorVerifyResult.success();
        }

        return TwoFactorVerifyResult.failure("Código inválido ou expirado");
    }
}
```

**Depois:** adicione `WHATSAPP` às propriedades:
```properties
seedgrid.security.2f.allowed-methods=EMAIL,TOTP,WHATSAPP
```

---

### 17.6 `EmailServiceInterface`

**Propósito:** Substitui completamente o envio de e-mails do módulo. Use quando quiser usar SendGrid SDK, AWS SES SDK, ou qualquer serviço que não seja SMTP padrão.

```java
public interface EmailServiceInterface {
    void sendEmailConfirmation(User user, String confirmationToken);
    void sendPasswordReset(User user, String resetToken);
    void sendWelcomeEmail(User user, String temporaryPassword, String confirmationToken);
    void sendHtmlEmail(String to, String subject, String htmlBody, String textBody);
    String generateToken();  // Gera o token único (por padrão: UUID.randomUUID().toString())
}
```

**Exemplo com SendGrid:**
```java
@ApplicationScoped
public class SendGridEmailService implements EmailServiceInterface {

    @ConfigProperty(name = "sendgrid.api-key")
    String apiKey;

    @Inject
    TemplateSecurityProvider templates;

    @Inject
    SecurityLinkBuilder links;

    @Override
    public void sendWelcomeEmail(User user, String temporaryPassword, String confirmationToken) {
        String confirmationLink = links.buildEmailConfirmationLink(confirmationToken);
        String html = templates.welcomeEmail(user, temporaryPassword, confirmationLink);
        sendHtmlEmail(user.getEmail(), "Bem-vindo!", html, null);
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlBody, String textBody) {
        // Integração com SendGrid SDK
        SendGrid sg = new SendGrid(apiKey);
        // ... configurar e enviar
    }

    @Override
    public String generateToken() {
        return UUID.randomUUID().toString();
    }

    // ... outros métodos
}
```

---

### 17.7 `TwoFactorEmailTemplateProvider`

**Propósito:** Customiza **apenas** o template do e-mail de código 2FA, sem precisar sobrescrever toda a `TemplateSecurityProvider`.

```java
public interface TwoFactorEmailTemplateProvider {
    EmailTemplate buildCodeEmail(User user, String code, int expiresInMinutes);

    class EmailTemplate {
        // subject: assunto do e-mail
        // htmlBody: corpo HTML
        // textBody: corpo texto puro (fallback para clientes que não suportam HTML)
        public EmailTemplate(String subject, String htmlBody, String textBody) { ... }
    }
}
```

**Exemplo:**
```java
@ApplicationScoped
public class Meu2FATemplate implements TwoFactorEmailTemplateProvider {

    @Override
    public EmailTemplate buildCodeEmail(User user, String code, int expiresInMinutes) {
        String subject = "Código de Acesso — MinhaApp";
        String html = """
            <h2>Olá, %s!</h2>
            <p>Seu código de verificação é:</p>
            <h1 style="font-size:48px; letter-spacing:8px; color:#333;">%s</h1>
            <p>Válido por <strong>%d minutos</strong>.</p>
            <p style="color:red;">Nunca compartilhe este código com ninguém.</p>
            """.formatted(user.getName(), code, expiresInMinutes);
        return new EmailTemplate(subject, html, "Código: " + code);
    }
}
```

---

### 17.8 `SecurityScopeResolverProvider`

**Propósito:** Resolve o "escopo de segurança" atual. Por padrão retorna `"default"`. Em sistemas multi-tenant, implemente para retornar o identificador do tenant atual — usado internamente para isolar caches e contextos.

```java
public interface SecurityScopeResolverProvider {
    String currentScope();
}
```

**Exemplo multi-tenant:**
```java
@ApplicationScoped
public class TenantScopeResolver implements SecurityScopeResolverProvider {

    @Inject
    TenantContext tenantContext;

    @Override
    public String currentScope() {
        return tenantContext.getCurrentTenantId();  // ex: "empresa-abc"
    }
}
```

---

## 18. Templates de E-mail

Os templates padrão estão dentro do JAR do módulo em `templates/security-default/html/`, escritos em português.

### Tabela completa

| Arquivo | Quando é enviado | Variáveis |
|---|---|---|
| `welcome-email.html` | Primeiro admin (bootstrap) e criação de usuário | `{name}`, `{email}`, `{temporaryPassword}`, `{loginLink}` |
| `email-confirmation.html` | Pedido de confirmação de e-mail avulso | `{name}`, `{confirmationLink}` |
| `password-reset.html` | Recuperação de senha | `{name}`, `{resetLink}` |
| `two-factor-code.html` | Código 2FA via **e-mail** | `{name}`, `{code}`, `{expiresInMinutes}` |
| `two-factor-code-whatsapp.txt` | Código 2FA via **WhatsApp** | `{name}`, `{code}`, `{expiresInMinutes}` |
| `entry-request-confirmation-code.html` | Self-enter público — código OTP de 6 dígitos | `{name}`, `{code}`, `{expiresInMinutes}` |
| `entry-request-confirmation.html` | Self-enter — link de confirmação (alternativa ao código) | `{name}`, `{confirmationLink}` |
| `entry-request-invitation.html` | Convite enviado pelo admin | `{name}`, `{invitedBy}`, `{invitationLink}`, `{expiresInHours}` |
| `entry-request-approved.html` | Aprovação do pedido de self-enter | `{name}`, `{email}`, `{loginLink}` |

---

### Template WhatsApp — `two-factor-code-whatsapp.txt`

**Por que `.txt` e não `.html`?**

O WhatsApp não renderiza HTML. Mensagens enviadas via API chegam como **texto puro**. Por isso o template do WhatsApp é um arquivo `.txt` — qualquer tag HTML que você colocar vai aparecer literalmente na tela do usuário como `<b>código</b>` em vez de **código**.

**Conteúdo padrão do template:**

```
Olá {name},

Seu código de verificação é: {code}
Ele expira em {expiresInMinutes} minuto(s).

Se você não solicitou este acesso, ignore esta mensagem.
```

**Variáveis disponíveis:**

| Variável | Valor substituído |
|---|---|
| `{name}` | Nome do usuário (campo `name` do `User`) |
| `{code}` | Código numérico de 6 dígitos gerado para esta sessão |
| `{expiresInMinutes}` | Tempo de validade em minutos (derivado de `seedgrid.security.2f.whatsapp.code-ttl`) |

**Localização:** O template está em `src/main/resources/templates/security/two-factor-code-whatsapp.txt` — ou seja, já está **na pasta de override do host** (`templates/security/`), não dentro do JAR do módulo. Isso significa que este arquivo **é o seu template customizado** e já tem prioridade sobre qualquer padrão interno do módulo.

**Como personalizar:** Edite diretamente o arquivo `src/main/resources/templates/security/two-factor-code-whatsapp.txt` no seu projeto. Mantenha os placeholders `{name}`, `{code}` e `{expiresInMinutes}` onde quiser que apareçam.

**Exemplo personalizado:**

```
🔐 *{name}*, seu código de acesso é:

   *{code}*

⏱ Válido por {expiresInMinutes} minuto(s).
⚠ Nunca compartilhe este código com ninguém.
```

> O WhatsApp suporta formatação básica de texto com asteriscos: `*negrito*`, `_itálico_`, `~tachado~` e ` ``` ` para código monoespaçado. Esses caracteres chegam formatados no app do usuário.

---

### Como sobrescrever um template

**Opção 1 — Arquivo no projeto (mais simples):**

Crie `src/main/resources/templates/security/{nome-exato}.html` no seu projeto.

```
src/main/resources/
  templates/
    security/
      welcome-email.html           ← substitui o padrão do módulo
      password-reset.html          ← substitui o padrão do módulo
```

Os outros templates (não sobrescritos) continuam usando os padrão do módulo. Use as mesmas variáveis `{name}`, `{confirmationLink}`, etc. — elas são substituídas automaticamente.

**Opção 2 — Via SPI `TemplateSecurityProvider`:**

Implemente a interface conforme a [seção 17.1](#171-templatesecurityprovider) para controle total: Qute, banco de dados, API externa.

---

## 19. Configuração Completa de Referência

Copie este bloco e ajuste os valores para o seu projeto. Remova os blocos que não usar.

```properties
# ============================================================
# QUARKUS — GERAL
# ============================================================
quarkus.swagger-ui.always-include=true
quarkus.application.name=minha-aplicacao
quarkus.http.port=8080
quarkus.devservices.enabled=false

# ============================================================
# QUARKUS — DATASOURCE (PostgreSQL)
# ============================================================
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=${DB_URL:jdbc:postgresql://localhost:5432/meudb}
quarkus.datasource.username=${DB_USERNAME:postgres}
quarkus.datasource.password=${DB_PASSWORD:postgres}
quarkus.datasource.jdbc.min-size=2
quarkus.datasource.jdbc.max-size=10

quarkus.hibernate-orm.database.generation=none
quarkus.hibernate-orm.physical-naming-strategy=org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
# Inclua o pacote das entidades próprias do host, se houver:
quarkus.hibernate-orm.packages=br.com.seedgrid.security.entities

# ============================================================
# FLYWAY — MIGRAÇÕES
# ============================================================
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration/security,classpath:db/migration/audit

# ============================================================
# JWT
# ============================================================
seedgrid.security.jwt.issuer=${JWT_ISSUER:https://app.minha-empresa.com.br}
seedgrid.security.jwt.kid=${JWT_KID:minha-empresa-key}
seedgrid.security.jwt.ttl=${JWT_TTL:PT8H}
seedgrid.security.jwt.private-key-location=src/main/resources/keys/private.pem
seedgrid.security.jwt.public-key-location=src/main/resources/keys/public.pem

# ============================================================
# REFRESH TOKEN
# ============================================================
seedgrid.security.refresh-token.ttl=${REFRESH_TTL:P30D}

# ============================================================
# POLÍTICA DE SENHA
# ============================================================
seedgrid.security.password.min-size=8
seedgrid.security.password.upper-case-required=true
seedgrid.security.password.lower-case-required=true
seedgrid.security.password.special-character-required=true
seedgrid.security.password.prohibits-repeated-characters-in-sequence=true
seedgrid.security.password.expiration-days=90
seedgrid.security.password.max-history=5

# ============================================================
# 2FA — DOIS FATORES
# ============================================================
seedgrid.security.2f.enabled=true
seedgrid.security.2f.allowed-methods=EMAIL,TOTP
seedgrid.security.2f.default-method=TOTP

seedgrid.security.2f.totp.issuer=MinhaEmpresa
seedgrid.security.2f.totp.digits=6
seedgrid.security.2f.totp.period=30
seedgrid.security.2f.totp.window=1

seedgrid.security.2f.otp-qrcode.branding.enabled=false
# seedgrid.security.2f.otp-qrcode.logo=classpath:/images/logo.png
seedgrid.security.2f.otp-qrcode.size=350

seedgrid.security.2f.verify.max-attempts=5
seedgrid.security.2f.verify.lockout-duration=PT30M
seedgrid.security.2f.session.ttl=PT5M
seedgrid.security.2f.email.code-ttl=PT5M
seedgrid.security.2f.backup-codes.count=10
seedgrid.security.2f.backup-codes.length=6

## WhatsApp 2FA (descomente e preencha se WHATSAPP estiver em allowed-methods)
# seedgrid.security.2f.allowed-methods=EMAIL,TOTP,WHATSAPP
# seedgrid.security.2f.whatsapp.channel-id=${SEEDGRID_SECURITY_2F_WHATSAPP_CHANNEL_ID}
# seedgrid.security.2f.whatsapp.code-ttl=PT5M

## Provedor WhatsApp: evolution | whatsapp-cloud | default (sem envio, só log)
# seedgrid.whatsapp.api=evolution

## --- Evolution API (self-hosted) ---
# seedgrid.whatsapp.rest-client.evolution.url=${SEEDGRID_WHATSAPP_REST_CLIENT_EVOLUTION_URL:http://evolution-api:8080}
# seedgrid.whatsapp.evolution.api-key=${SEEDGRID_WHATSAPP_EVOLUTION_API_KEY}

## --- WhatsApp Cloud API (Oficial Meta) ---
# seedgrid.whatsapp.api=whatsapp-cloud
# seedgrid.whatsapp.cloud.access-token=${SEEDGRID_WHATSAPP_CLOUD_ACCESS_TOKEN}
# seedgrid.whatsapp.rest-client.cloud.url=${SEEDGRID_WHATSAPP_REST_CLIENT_CLOUD_URL:https://graph.facebook.com/v21.0}

# ============================================================
# RBAC
# ============================================================
seedgrid.security.rbac.cache-ttl=PT5M

# ============================================================
# FRONTEND — URLs dos e-mails
# ============================================================
seedgrid.security.frontend.base-url=${FRONTEND_URL:https://app.minha-empresa.com.br}
seedgrid.security.frontend.login-path=/login
seedgrid.security.frontend.password-reset-path=/auth/forgot-password/confirm
seedgrid.security.frontend.email-confirmation-path=/auth/confirm-email
seedgrid.security.frontend.welcome-path=/auth/welcome
seedgrid.security.frontend.self-enter-path=/auth/register

# ============================================================
# CRIPTOGRAFIA (AES-256 para segredos TOTP)
# Gerar: openssl rand -base64 32
# ============================================================
seedgrid.security.encryption.key=${ENCRYPTION_KEY}

# ============================================================
# MAILER — SMTP
# ============================================================
seedgrid.security.mailer.from=${MAILER_FROM:noreply@minha-empresa.com.br}
seedgrid.security.mailer.host=${MAILER_HOST:smtp.gmail.com}
seedgrid.security.mailer.port=${MAILER_PORT:587}
seedgrid.security.mailer.start-tls=${MAILER_TLS:REQUIRED}
seedgrid.security.mailer.username=${MAILER_USER}
seedgrid.security.mailer.password=${MAILER_PASSWORD}

# ============================================================
# AVATAR — escolha UM storage
# ============================================================
seedgrid.security.avatar.storage=${AVATAR_STORAGE:filesystem}
seedgrid.security.avatar.user-prefix=avatars/users
seedgrid.security.avatar.default-prefix=avatars/defaults
seedgrid.security.avatar.public-read=false

## filesystem (default)
seedgrid.security.avatar.filesystem.base-path=/var/seedgrid/files

## minio (descomente para usar)
# seedgrid.security.avatar.storage=minio
# seedgrid.security.avatar.minio.endpoint=${MINIO_ENDPOINT:http://minio:9000}
# seedgrid.security.avatar.minio.bucket=${MINIO_BUCKET:avatars}
# seedgrid.security.avatar.minio.region=us-east-1
# seedgrid.security.avatar.minio.access-key=${MINIO_ACCESS_KEY}
# seedgrid.security.avatar.minio.secret-key=${MINIO_SECRET_KEY}

## s3 (descomente para usar)
# seedgrid.security.avatar.storage=s3
# seedgrid.security.avatar.s3.bucket=${S3_BUCKET}
# seedgrid.security.avatar.s3.region=${S3_REGION:sa-east-1}
# seedgrid.security.avatar.s3.access-key=${AWS_ACCESS_KEY_ID}
# seedgrid.security.avatar.s3.secret-key=${AWS_SECRET_ACCESS_KEY}

## azure-blob (descomente para usar)
# seedgrid.security.avatar.storage=azure-blob
# seedgrid.security.avatar.azure-blob.connection-string=${AZURE_STORAGE_CONNECTION_STRING}
# seedgrid.security.avatar.azure-blob.container=avatars

## gcs (descomente para usar)
# seedgrid.security.avatar.storage=gcs
# seedgrid.security.avatar.gcs.bucket=${GCS_BUCKET}
# seedgrid.security.avatar.gcs.project-id=${GCS_PROJECT_ID}
# seedgrid.security.avatar.gcs.credentials-file=/etc/secrets/gcs-sa.json

## google-drive (descomente para usar)
# seedgrid.security.avatar.storage=google-drive
# seedgrid.security.avatar.google-drive.root-folder-id=${GDRIVE_FOLDER_ID}
# seedgrid.security.avatar.google-drive.credentials-file=/etc/secrets/gdrive-sa.json

## onedrive (descomente para usar)
# seedgrid.security.avatar.storage=onedrive
# seedgrid.security.avatar.onedrive.client-id=${ONEDRIVE_CLIENT_ID}
# seedgrid.security.avatar.onedrive.client-secret=${ONEDRIVE_CLIENT_SECRET}
# seedgrid.security.avatar.onedrive.tenant-id=${ONEDRIVE_TENANT_ID}
# seedgrid.security.avatar.onedrive.root-folder-path=/seedgrid/avatars

# ============================================================
# SELF-ENTER — auto-cadastro
# ============================================================
seedgrid.security.self-enter.enabled=true
seedgrid.security.self-enter.public-enabled=true
seedgrid.security.self-enter.invite-enabled=true
seedgrid.security.self-enter.request-ttl=PT48H
seedgrid.security.self-enter.email-token-ttl=PT30M
seedgrid.security.self-enter.invite-ttl=PT168H
seedgrid.security.self-enter.default-role=USER
seedgrid.security.self-enter.cleanup.enabled=true
seedgrid.security.self-enter.cleanup.cron=0 */10 * * * ?
seedgrid.security.self-enter.cleanup.purge-after=PT168H

# ============================================================
# AUDITORIA
# ============================================================
seedgrid.audit.persistence.strategy=${AUDIT_STRATEGY:JPA}

## JPA standalone (não use em multitenancy!)
# seedgrid.audit.jpa.db-kind=postgresql
# seedgrid.audit.jpa.jdbc.url=${AUDIT_DB_URL}
# seedgrid.audit.jpa.username=${AUDIT_DB_USERNAME}
# seedgrid.audit.jpa.password=${AUDIT_DB_PASSWORD}

## MongoDB (descomente se strategy=MONGODB)
# seedgrid.audit.persistence.strategy=MONGODB
# seedgrid.audit.mongodb.connection-string=${AUDIT_MONGODB_URI}

# ============================================================
# PRIMEIRO ADMIN
# ============================================================
seedgrid.security.first-admin-user.email=${SEEDGRID_SECURITY_FIRST_ADMIN_USER_EMAIL:admin@minha-empresa.com.br}
seedgrid.security.first-admin-user.name=${SEEDGRID_SECURITY_FIRST_ADMIN_USER_NAME:Administrador}
# seedgrid.security.first-admin-user.whatsapp=${SEEDGRID_SECURITY_FIRST_ADMIN_USER_WHATSAPP}  ← opcional
# seedgrid.security.first-admin-user.initial-password=${SEEDGRID_SECURITY_FIRST_ADMIN_USER_INITIAL_PASSWORD}

# ============================================================
# RATE LIMITING
# ============================================================
seedgrid.security.rate-limit.login.enabled=true
seedgrid.security.rate-limit.login.max-requests=10
seedgrid.security.rate-limit.login.window=PT1M

# ============================================================
# LOGGING (ajuste conforme necessidade)
# ============================================================
quarkus.log.level=INFO
quarkus.log.category."br.com.seedgrid".level=DEBUG
```

---

## 20. Verificação End-to-End

Use este checklist para confirmar que a configuração está funcionando corretamente após instalar o módulo.

### Configuração básica
- [ ] Aplicação sobe sem erros no log
- [ ] Flyway executa as migrations sem falhar
- [ ] E-mail de boas-vindas chega na caixa de `first-admin-user.email`
- [ ] E-mail contém senha temporária e link de confirmação

### Fluxo do primeiro acesso
- [ ] Clicar no link de confirmação confirma o e-mail
- [ ] Login com e-mail + senha temporária retorna `{ changePasswordRequired: true }`
- [ ] Endpoint de troca de senha funciona (nova senha cumpre a política)
- [ ] Novo login com a nova senha retorna `{ changePasswordRequired: false }`

### Autenticação e tokens
- [ ] `GET /me/permissions` retorna roles e permissões do usuário logado
- [ ] `POST /auth/refresh` renova os tokens
- [ ] Usar um refresh token duas vezes revoga a família (teste: faça refresh, guarde os dois tokens — use o antigo novamente e verifique se o usuário é deslogado)

### 2FA — TOTP
- [ ] `POST /2fa/enable` com `{ "method": "TOTP" }` retorna `secret` e `qrCodeBase64`
- [ ] Escanear o QR code com Google Authenticator funciona
- [ ] `POST /2fa/totp/confirm` com o código do app habilita o 2FA e retorna backup codes
- [ ] Fazer logout e login novamente retorna `requires2FA: true`
- [ ] `POST /public/2fa/verify` com código TOTP retorna tokens
- [ ] Usar um backup code no lugar do TOTP funciona
- [ ] 5 tentativas erradas revogam a sessão

### 2FA — E-mail
- [ ] Habilitar 2FA com `{ "method": "EMAIL" }` funciona
- [ ] No próximo login, o código chega por e-mail
- [ ] `POST /public/2fa/resend` reenvia o código

### Recuperação de senha
- [ ] `POST /auth/forgot-password` com e-mail válido dispara e-mail de reset
- [ ] Clicar no link de reset aplica a nova senha
- [ ] Sessões anteriores são revogadas após o reset

### Self-enter público
- [ ] `POST /public/self-enter/requests` cria pedido e envia código OTP
- [ ] Confirmar com o código correto avança para PENDING_APPROVAL
- [ ] `POST /public/self-enter/resend` reenvia o código
- [ ] Após aprovação pelo admin, usuário pode fazer login

### Avatar
- [ ] Upload de avatar em Base64 funciona
- [ ] `GET /users/{publicId}/avatar` retorna a imagem
- [ ] `DELETE /users/{publicId}/avatar` reseta para avatar padrão

### Customização (se aplicável)
- [ ] Campos próprios em `additionalAttributes` (e `UserAdditionalFieldsValidator`, se houver regras) persistem e voltam em `GET /users/{publicId}`
- [ ] Template de e-mail customizado em `templates/security/`: e-mails usam o template do host
- [ ] `JwtTokenCustomizer` implementado: claims extras aparecem no JWT decodificado

### Storage de avatar (se diferente de filesystem)
- [ ] Trocar `avatar.storage` para `minio`: upload salva no MinIO, download funciona via API
