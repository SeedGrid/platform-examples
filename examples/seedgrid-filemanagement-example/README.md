# seedgrid-filemanagement-example

App de exemplo, rodável, do `seedgrid-filemanagement`: **upload / download / metadata / delete** de
arquivos via a SPI `FileStorage` / `FileStorageFactory`, resolvida pelo `FileStorageRegistry`, usando
o backend **`filesystem`** (`FilesystemStorage`) — o único que roda 100% local, sem credenciais de
nuvem. **Os endpoints de documentos não precisam de banco; os anexos e o consentimento (seção abaixo) precisam do
Postgres do `infra/compose.yml`**, e os testes sobem um Postgres descartável (Dev Services; precisa de Podman/Docker).

## O princípio central: uma SPI, vários backends

Seu código de negócio nunca fala com S3/Azure/disco direto. Ele pede um `FileStorage` ao
`FileStorageRegistry` por um **type** (`"filesystem"`, `"s3"`, `"azure-blob"`, …) e usa sempre a
mesma API (`store` / `retrieve` / `stream` / `delete` / `exists` / `getMetadata`). Trocar de backend
é trocar o `type` e o mapa de configuração — nenhuma linha do resource muda.

```java
// DocumentStorageService: escolhe o backend por config e monta o mapa que a factory exige.
FileStorage storage = registry.get("filesystem", Map.of("base-path", basePath));
StorageKey key = storage.store("contrato.pdf", bytes, StorageOptions.withPrefix("uploads/2026"));
byte[] back = storage.retrieve(key);   // mesmos bytes
```

O registry faz **lazy-init** e cacheia por **type + config** (igualdade por conteúdo do mapa —
configs diferentes resolvem instâncias diferentes); a `FilesystemStorageFactory` valida a config,
cria o diretório-base se não existir e devolve um `FilesystemStorage`.

## Anexos e consentimento do usuário (plano 111)

Este exemplo agora roda com **anexos ligados** e mostra o **consentimento do usuário**. Os endpoints antigos
(`/documents`) seguem iguais; os novos ficam em `/attachments` e `/consent`.

### Anexos: a tabela `sg_attachments`

Com `seedgrid.filemanagement.registry-table.enabled=true`, cada `store` grava também uma linha ligando o arquivo ao seu
dono (`owner-key`), e `delete` apaga o arquivo e a linha. O produto passa a guardar o **ID do anexo** em vez do caminho.
O que o host precisa fazer (tudo está no `application.properties` e no `pom.xml` deste exemplo):

1. **Ligar a chave** `seedgrid.filemanagement.registry-table.enabled=true`. Ela é decidida em **build**: tem que estar
   no `application.properties` quando o app é compilado.
2. **Declarar o que a lib não traz**: `quarkus-hibernate-orm`, `quarkus-jdbc-postgresql`, `quarkus-flyway`,
   `quarkus-flyway-postgresql` e `seedgrid-commons` (a lib os marca `optional`, para não arrastá-los a quem não usa banco).
3. **Nada de mapeamento**: a extension mapeia a entidade `Attachment` sozinha quando a chave está ligada (um *build step*, como
   `cities` e `security`). O host **não** configura `quarkus.hibernate-orm.mapping-files`, `quarkus.hibernate-orm.packages`,
   `quarkus.index-dependency` nem `quarkus.hibernate-orm.validate-in-dev-mode` (a extension traz `false` como padrão).
4. **Deixar a migration com a lib**: o *runner* da lib aplica a `V1300` na subida, com histórico próprio
   (`sg_flyway_schema_history_filemanagement`). Este app não liga o Flyway do Quarkus. Um host com várias bases usa
   `seedgrid.migrations.host-managed=true` e chama `FileManagementMigrations.migrate(dataSource)` em cada base.

```java
// AttachmentService: grava pelo registry, o anexo nasce sozinho; ler/apagar parte do ID do anexo.
FileStorage storage = registry.get("filesystem", Map.of("base-path", basePath, "owner-key", "empresa-1"));
StorageKey key = storage.store("nota.txt", bytes, StorageOptions.builder().prefix("anexos").build());
```

### Consentimento: gravar no armazenamento do próprio usuário

`auth-mode=consent` faz o storage usar o token **do usuário** (Google Drive, OneDrive, Dropbox). O host entrega o refresh
token por um bean `StorageCredentialSource`; a **lib renova** o token de acesso. Aqui:

| Peça | Classe | Papel |
|---|---|---|
| Fonte de credencial | `ExampleCredentialSource` | guarda o refresh token por `provider + owner-key` (na memória; num produto, cifrado no banco) e recebe o token rotacionado em `renewed(...)` |
| Servidor **falso** do provedor | `FakeProviderResource` (`/fake-provider/...`) | OAuth (`/oauth/token`, rotaciona o refresh token) e um subconjunto da API do Dropbox. **Nenhuma chave real**; num produto as URLs apontam para o provedor |
| Concessão/revogação simuladas | `ConsentResource` (`/consent/{owner}`) | no produto seria o redirecionamento ao provedor e a volta (a lib não faz o redirect) |
| Consentimento necessário | `ConsentExceptionMapper` | acesso nunca concedido ou revogado vira `409 {"error":"consent_required"}` |

O token do servidor falso vale 30 s (`app.fake-provider.expires-in`), menos que a folga de 60 s da lib, então **cada
operação renova**: dá para ver a renovação e a rotação do refresh token funcionando.

### Passos para rodar

```bash
# 1. Postgres do repositório (porta 5439)
podman compose -f infra/compose.yml up -d postgres       # ou: docker compose ...
#    Se o volume do Postgres já existia antes deste exemplo, o banco não foi criado; crie uma vez:
podman exec seedgrid-examples-postgres psql -U postgres -c "CREATE DATABASE filemanagement_example"

# 2. O app (porta 8089). A migration da lib cria sg_attachments na subida.
cd examples/seedgrid-filemanagement-example
./run-dev.sh
```

```bash
B=http://localhost:8089; B64=$(printf 'ola anexo' | base64)

# Anexo no filesystem: grava, lê pelo ID do anexo, apaga
curl -s -X POST $B/attachments -H 'Content-Type: application/json' \
  -d "{\"ownerKey\":\"empresa-1\",\"storage\":\"filesystem\",\"filename\":\"nota.txt\",\"contentBase64\":\"$B64\"}"
# -> {"id":"01a1...","filename":"nota.txt","storage":"filesystem","ownerKey":"empresa-1","sizeBytes":9}
curl -s $B/attachments/<id>/content                      # ola anexo
curl -s -X DELETE $B/attachments/<id>                    # 204; some o arquivo e a linha

# Consentimento: concede, grava no "Dropbox" falso do usuário, lê, revoga
curl -s -X POST $B/consent/empresa-1                     # 201 (o usuário autorizou)
curl -s -X POST $B/attachments -H 'Content-Type: application/json' \
  -d "{\"ownerKey\":\"empresa-1\",\"storage\":\"dropbox\",\"filename\":\"c.txt\",\"contentBase64\":\"$B64\"}"
curl -s $B/attachments/<id>/content                      # ola anexo (token renovado pela lib)
curl -s -X DELETE $B/consent/empresa-1                   # 204 (o usuário desconectou)
curl -s -w ' %{http_code}\n' $B/attachments/<id>/content # {"error":"consent_required"} 409
```

Conferir no banco: `podman exec seedgrid-examples-postgres psql -U postgres -d filemanagement_example -c "select * from sg_attachments"`.

### Achados montando este exemplo (provados aqui)

- **A8 — `quarkus.index-dependency` não liga a entidade `Attachment` (resolvido na lib).** A lib deixa `Attachment` fora do
  índice Jandex de propósito (hosts que não ligaram o recurso não precisam da tabela), e o Quarkus, ao achar um
  `META-INF/jandex.idx` no jar, **usa esse índice e não reindexa** o jar, mesmo listado em `index-dependency`. A solução
  é da extension: o build step `FileManagementProcessor` indexa a classe por `AdditionalIndexedClassesBuildItem` só com
  `registry-table.enabled=true` (como `cities` e `security`). Provado: `EntidadeMapeadaSozinhaTest` vê `Attachment` sem
  nenhuma propriedade de mapeamento; rodando o mesmo teste com `-Dseedgrid.filemanagement.registry-table.enabled=false`
  a unidade de persistência fica sem entidade alguma.
- **A4 — Flyway padrão enxerga a `V1300` como pendente.** `FlywayPadraoVeV1300Test` mostra, só lendo (`info()`), que a
  location padrão `classpath:db/migration` traz a `V1300` entre as pendentes e que listar só as locations do host a
  esconde. Um host com `quarkus.flyway.migrate-at-start=true` e a location padrão criaria `sg_attachments` mesmo com o
  recurso desligado: por isso um host que liga o Flyway do Quarkus aponta o próprio `quarkus.flyway.locations` (a migration é da extension e roda no runner dela).
- **A9 — a chave gravada não volta inteira para os provedores de nuvem (resolvido na lib).** `StorageKey.fromUri` não
  reconstrói a chave desses provedores (a pasta raiz tem barra inicial e o id tem `:`); a lib agora guarda o id do arquivo
  em `sg_attachments.provider_file_id`, e o exemplo ainda separa o URI na última barra (`AttachmentService.keyOf`).
- **O exemplo mudou duas asserções antigas, de propósito:** com anexos ligados o registry devolve o storage decorado
  (`AttachmentTrackingFileStorage`, e não `FilesystemStorage`), e o tipo `dropbox` deixou de ser "inexistente" (o teste
  agora usa `tipo-inexistente`).

## Endpoints de exemplo

| Método | Rota | O que faz |
|---|---|---|
| `POST` | `/documents` | Recebe `{filename, prefix?, contentType?, contentBase64}` e armazena via `store(...)`. Devolve `201` com a `StorageKey` (`key` = path relativo) + metadata |
| `GET` | `/documents/{key}` | `retrieve(...)`: devolve o conteúdo original (mesmos bytes). `404` se não existir |
| `GET` | `/documents/_meta/{key}` | `getMetadata(...)`: tamanho, contentType, última modificação. `404` se não existir |
| `DELETE` | `/documents/{key}` | `delete(...)`. `204` se removido; `404` se não existir |

> `{key}` é o **path relativo** devolvido no POST (ex.: `uploads/2026/contrato.pdf`). Pode conter
> `/`, por isso a rota usa o pattern `{key:.+}`. O endpoint de metadata usa o prefixo literal
> `_meta/` para não colidir com o `{key:.+}` (que é guloso).

## Configuração (`application.properties`)

```properties
app.storage.type=filesystem
app.storage.filesystem.base-path=${STORAGE_BASE_PATH:${java.io.tmpdir}/seedgrid-filemanagement-example}
```

- `app.storage.type` — qual backend usar. Este exemplo só embute o `filesystem`.
- `app.storage.filesystem.base-path` — diretório-base. É exatamente o valor que a
  `FilesystemStorageFactory` lê da chave **`base-path`** do mapa de config. Tudo é gravado sob ele; o
  `prefix` do `StorageOptions` vira subpasta relativa.

### O que trocar para usar outro backend (s3, azure-blob, …)

1. Adicione ao classpath a extension/lib que traz a factory do backend desejado (ex.: o
   `S3StorageFactory` já vem no `seedgrid-filemanagement`, mas exige um cliente S3 configurado).
2. Mude `app.storage.type` para o type correspondente (`s3`, `azure-blob`, `minio`, …).
3. No `DocumentStorageService`, preencha o mapa de config com as chaves que a factory daquele backend
   exige (ex.: `bucket`, `region`, credenciais) em vez de `base-path`.

O `DocumentResource` não muda — ele só conhece a SPI.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (porta 8089)
mvn -o verify         # compila e roda os testes offline; os testes sobem um Postgres descartavel (Dev Services)
```

Swagger UI: http://localhost:8089/q/swagger-ui.

## Testando via curl

```bash
# 1. Upload (base64) de um arquivo
B64=$(printf 'ola mundo filemanagement' | base64)
curl -s -X POST http://localhost:8089/documents \
  -H 'Content-Type: application/json' \
  -d "{\"filename\":\"ola.txt\",\"prefix\":\"demo\",\"contentType\":\"text/plain\",\"contentBase64\":\"$B64\"}"
# -> {"key":"demo/ola.txt","uri":"filesystem://demo/ola.txt","filename":"ola.txt","sizeBytes":24,...}

# 2. Download - devolve os mesmos bytes
curl -s http://localhost:8089/documents/demo/ola.txt

# 3. Metadata
curl -s http://localhost:8089/documents/_meta/demo/ola.txt

# 4. Delete e prova de que sumiu (404)
curl -i -X DELETE http://localhost:8089/documents/demo/ola.txt
curl -i http://localhost:8089/documents/demo/ola.txt   # 404
```

## Testes

- `AttachmentFlowTest` — anexos ligados num host de verdade: a migration da lib criou `sg_attachments`; filesystem grava, lê pelo
  ID do anexo e apaga arquivo e linha; consentimento grava no provedor falso, renova o token, e a revogação pede consentimento
  de novo sem vazar token; donos diferentes não misturam anexos.
- `FlywayPadraoVeV1300Test` — prova do achado A4 (Flyway padrão vê a `V1300` como pendente).

- `FilesystemStorageRoundTripTest` — via a SPI direta (injeta `FileStorageRegistry`): `store` →
  `retrieve` devolve bytes **idênticos** (e o mesmo hash SHA-256); metadata correto (tamanho,
  filename, `lastModifiedAt`); `delete` → `retrieve`/`delete` seguintes lançam
  `StorageNotFoundException`.
- `StorageRegistryTest` — o registry descobre a `FilesystemStorageFactory`, resolve o backend
  `filesystem` a partir da config, e um type inexistente lança `StorageNotFoundException`.
- `DocumentResourceTest` — fluxo REST fim-a-fim: `POST` base64 → `GET` (mesmo conteúdo) → `_meta` →
  `DELETE` → `GET`/`DELETE` posteriores dão `404`; base64 inválido dá `400`.

## Pegadinhas encontradas montando este exemplo (úteis se for reaproveitar o padrão)

- **API real do `FileStorage`** (não assuma assinaturas):
  - `StorageKey store(String filename, byte[] content, StorageOptions options) throws StorageException`
    (há overload com `InputStream` e um `stream(key)` para leitura). Retorna `StorageKey`.
  - `byte[] retrieve(StorageKey)`, `void delete(StorageKey)`, `boolean exists(StorageKey)`,
    `FileMetadata getMetadata(StorageKey)`.
  - `StorageException` é **checked** (`extends Exception`).
- **Not-found de arquivo lança `StorageNotFoundException`** (corrigido na lib **2026.07.08**):
  `retrieve`/`stream`/`getMetadata` **e também `delete`** de arquivo inexistente lançam
  `StorageNotFoundException`, que agora **extends `StorageException`** — catches genéricos seguem
  funcionando, e o resource captura o subtipo e mapeia para `404` de forma atômica (via
  `jakarta.ws.rs.NotFoundException`, que dá 404 limpo). *Nota histórica (até 2026.07.02):* essas
  operações lançavam `StorageException` genérica (`"File not found"`), `delete` de inexistente era
  warn + no-op, e `StorageNotFoundException` era exclusiva do registry (type sem factory) — o
  workaround era checar `exists()` antes de cada operação.
- **Config key do `FilesystemStorageFactory` é `base-path`** (string). A factory cria o diretório se
  não existir e valida que é gravável (senão lança `StorageConfigurationException`, unchecked).
- **`FileStorageRegistry` cacheia por type + config** (corrigido na lib **2026.07.08**): a igualdade
  é por **conteúdo** do mapa (`Map.equals`), então reconstruir um `Map.of(...)` igual a cada chamada
  acerta o mesmo cache, e configs diferentes (ex.: `base-path` diferente) resolvem **instâncias
  diferentes**. Nos testes, cada `@TempDir` já ganha o próprio storage — **não** precisa mais de
  `registry.clearCache()` no `@BeforeEach` (`clearCache()` continua existindo, se você precisar
  descartar tudo). *Nota histórica (até 2026.07.02):* o cache era só por type e a **primeira** config
  vencia — mudar `base-path` não tinha efeito sem `clearCache()`, e testes com `@TempDir` eram
  obrigados a limpar o cache a cada teste.
- **`StorageKey`** (`@Value @Builder`, Lombok): campos `type`, `bucket` (opcional; null no
  filesystem), `path`. Fábricas úteis: `StorageKey.filesystem(path)`, `StorageKey.of(type, path)`.
  `toUri()` → `filesystem://<path>`. Para o filesystem, o `path` já embute o `prefix`
  (`StorageOptions.fullPath` = `prefix + "/" + filename`).
- **`FilesystemStorage` é `@Vetoed`** — não é bean CDI; quem o instancia é a factory. Você nunca o
  injeta direto; sempre passa pelo registry.
- **Sem arrasto de JPA no host** (corrigido na lib **2026.07.08**): a dependência `seedgrid-commons`
  foi **removida** do pom da lib (e hibernate-orm/jdbc-postgresql/flyway viraram `optional`), então
  ela não arrasta mais hibernate-orm/panache/agroal/narayana para apps sem banco. *Nota histórica
  (até 2026.07.02):* o stack JPA vinha transitivo via commons e este exemplo precisava de
  `quarkus.hibernate-orm.enabled=false` + `quarkus.devservices.enabled=false` para o modo dev não
  tentar subir um Postgres fantasma — ambos os workarounds saíram do `application.properties`.
- **jandex**: o jar `seedgrid-filemanagement-2026.07.08.jar` **JÁ empacota** `META-INF/jandex.idx`,
  então `FileStorageRegistry` e `FilesystemStorageFactory` são auto-descobertos pelo ArC — **não** é
  preciso `quarkus.index-dependency` (diferente do `seedgrid-resilience-core`, que precisa). O
  fallback está comentado no `application.properties` caso a lib pare de indexar no futuro.
- **Rota greedy**: `{key:.+}` captura barras; um segundo GET `/{key:.+}/metadata` seria ambíguo com o
  download. Por isso metadata fica em `/_meta/{key:.+}` (prefixo literal ganha do path-param).
- **pom**: `packaging=quarkus` + `<extensions>true</extensions>` + platform 3.34.6 funciona offline
  (`mvn -o`). Lombok **não** vem transitivo: precisa da dependency `provided` + `annotationProcessorPath`.
