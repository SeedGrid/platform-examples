# seedgrid-privacy-example

App de exemplo, rodável, da lib **`seedgrid-privacy`** (`br.com.seedgrid:seedgrid-privacy:2026.07.08`):
proteção de PII para conformidade **LGPD** em entidades JPA. Diferente das outras libs do programa,
`seedgrid-privacy` **não é uma extension Quarkus** — é uma lib pura (`jar`) que você usa por anotações
JPA (`@Convert`, `@EntityListeners`) e por um bean de mascaramento.

## O que a lib oferece (e este exemplo demonstra)

1. **Criptografia at-rest (AES-256-GCM), reversível** — `@SgEncryptedField` +
   `@Convert(EncryptedFieldAttributeConverter.class)` cifram/decifram um campo `String` de forma
   transparente. No banco fica `v1:iv:ciphertext`; via JPA você lê o valor em claro.
2. **Busca exata por hash (HMAC-SHA256), determinística** — o `EncryptedFieldListener`
   (`@PrePersist`/`@PreUpdate`) preenche um campo-irmão `*_hash` com o hash do valor normalizado.
   Você busca por igualdade com um lookup indexado, **sem descriptografar a tabela inteira**.
   A normalização (`SearchNormalization.EMAIL` / `CANONICAL`) faz a busca tolerar caixa e pontuação.
3. **Mascaramento de PII (`@SgPersonalData` + `PersonalDataSanitizer`)** — produz uma cópia do objeto
   com a PII mascarada (para log, auditoria, telas restritas), sem alterar a entidade original.

## Domínio do exemplo

Entidade `Customer` com PII cifrada: `name`, `email`, `cpf`, `phone`. `email` e `cpf` também são
pesquisáveis por hash (`email_hash`, `cpf_hash`). `name` e `phone` usam `@SgEncryptedField` **sem**
`searchField`: modo "cifrado mas não pesquisável" — nenhum hash é gerado e não é preciso
campo-irmão `*Hash` (comportamento desde a 2026.07.08).

- `POST /api/customers` `{"name":"Maria Silva","email":"maria@x.com","cpf":"111.222.333-44","phone":"11955554444"}`
  → cria o cliente (PII cifrada at-rest) e devolve **201** com a resposta **em claro**.
- `GET /api/customers/{id}` → devolve a PII **em claro** (decifrada pelo JPA na leitura).
- `GET /api/customers/{id}/masked` → devolve a PII **mascarada** (`email` → `***`,
  `cpf` → `***.***.***-**`, `name`/`phone` revelam só os últimos 4).
- `GET /api/customers/search?email=...` **ou** `?cpf=...` → busca por hash determinístico. Acha o
  registro mesmo que você informe o e-mail em outra caixa, ou o CPF sem pontuação.

## Configuração da criptografia (chaves)

A lib é **agnóstica por padrão** (`seedgrid.privacy.encryption.enabled=false`): grava PII em claro e
não exige chave. Este exemplo **liga** a criptografia, o que torna **obrigatórias** duas chaves
(fail-closed — sem elas o boot/persist falha, em vez de gravar PII em claro silenciosamente):

| Propriedade | Uso | Formato |
|---|---|---|
| `seedgrid.privacy.encryption.enabled` | liga a cripto at-rest | `true` |
| `seedgrid.privacy.crypto.key` | AES-256-GCM (reversível) | Base64 de ≥ 32 bytes, opcional prefixo `base64:` |
| `seedgrid.privacy.search.key` | HMAC-SHA256 (hash de busca) | Base64 de ≥ 32 bytes, ≠ da crypto.key |

Gere chaves de produção com `openssl rand -base64 32` e guarde num secret manager. As chaves do
`dev.env`/`application.properties` são só para desenvolvimento — **troque-as**.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (precisa de um Postgres em localhost:5432)
mvn test              # sobe Postgres via Testcontainers automaticamente
```

Swagger UI: http://localhost:8092/q/swagger-ui.

### Testando via curl

```bash
# Cria (PII cifrada at-rest de forma transparente)
curl -i -X POST http://localhost:8092/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"name":"Maria Silva","email":"maria@x.com","cpf":"111.222.333-44","phone":"11955554444"}'

# Le em claro / mascarado
curl http://localhost:8092/api/customers/1
curl http://localhost:8092/api/customers/1/masked

# Busca por hash (tolera caixa e pontuacao)
curl 'http://localhost:8092/api/customers/search?email=MARIA@x.com'
curl 'http://localhost:8092/api/customers/search?cpf=11122233344'
```

## Rodando os testes no Windows sem Docker Desktop (podman)

Os testes usam Testcontainers. Aponte o `DOCKER_HOST` para o pipe do podman e desligue o Ryuk:

```bash
export DOCKER_HOST='npipe:////./pipe/podman-machine-default'
export TESTCONTAINERS_RYUK_DISABLED=true
export TESTCONTAINERS_CHECKS_DISABLE=true
mvn -o test
```

## Testes

- `PrivacySchemaBootstrapTest` — a migration `V1000` (tabela `customers`) roda; a entidade
  persiste; os campos-irmão de hash são preenchidos pelo `EntityListener`; a leitura via JPA volta
  em claro; e o flag de criptografia está ligado.
- `CustomerEncryptionTest` — fluxo HTTP completo provando os quatro contratos: (1) coluna cifrada no
  banco (query nativa via JDBC mostra `v1:...`, nunca o CPF cru), (2) leitura em claro pelo JPA,
  (3) busca por hash tolerando caixa/pontuação, (4) mascaramento `@SgPersonalData` na resposta.

## O que trocar para adaptar

- Renomeie o pacote `br.com.seedgrid.privacyexample` e o domínio `Customer` para o seu.
- Ajuste `quarkus.hibernate-orm.packages` para incluir o **seu** pacote de entidades **e**
  `br.com.seedgrid.privacy` (onde vivem o converter e o `EntityListener`).
- Escolha a `SearchNormalization` certa por campo: `EMAIL` para e-mail, `CANONICAL` para
  documentos/telefone (CPF, CNPJ, CEP), `NONE` para hash exato byte-a-byte.
- **Troque as chaves** `seedgrid.privacy.crypto.key` e `seedgrid.privacy.search.key` por segredos de produção.
- Migrations da app começam em `V1000+` (a ordem de versão do Flyway é global).
