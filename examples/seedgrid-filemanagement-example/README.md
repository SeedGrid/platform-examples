# seedgrid-filemanagement-example

App de exemplo, rodável, do `seedgrid-filemanagement`: **upload / download / metadata / delete** de
arquivos via a SPI `FileStorage` / `FileStorageFactory`, resolvida pelo `FileStorageRegistry`, usando
o backend **`filesystem`** (`FilesystemStorage`) — o único que roda 100% local, sem credenciais de
nuvem. Não precisa de Postgres, Redis nem Testcontainers.

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
mvn -o test           # roda os testes (offline; sem Postgres/Redis/Testcontainers)
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
