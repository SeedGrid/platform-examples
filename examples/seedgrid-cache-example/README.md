# seedgrid-cache-example

App de exemplo, rodável, do `seedgrid-cache-core`: **cache-aside** usando o SPI
`SeedGridCacheProvider`, sem depender de nenhuma outra extensão SeedGrid, nem de banco, nem de
Redis. Você baixa, roda e adapta.

## O princípio central: o SPI `SeedGridCacheProvider` e o backend que "se resolve sozinho"

A lib expõe **uma interface** — `SeedGridCacheProvider` — e você programa contra ela. Qual
implementação atende em runtime depende só do classpath:

- **Sem** provider Redis/Valkey/Dragonfly, o `DefaultSeedGridCache` (in-memory, `@ApplicationScoped`
  `@DefaultBean`) assume automaticamente — é o caso deste exemplo (instância única, zero
  infraestrutura).
- **Com** `seedgrid-cache-redis` (ou `-valkey`/`-dragonfly`) no classpath + `seedgrid.cache.hosts`
  configurado, o provider distribuído passa a atender — **sem trocar uma linha do código do app**.

> Por que o in-memory é descoberto: o jar `seedgrid-cache-core-2026.07.02.jar` **empacota**
> `META-INF/jandex.idx`, então o Quarkus ArC indexa e descobre o `DefaultSeedGridCache` mesmo sendo
> uma dependency direta. (Contraste: `seedgrid-resilience-core` **não** tem jandex.idx e por isso
> aquele exemplo precisa de `quarkus.index-dependency.seedgrid-resilience-core.*`. Aqui **não é
> necessário** — confirmado.)

## A API do `SeedGridCacheProvider` (assinaturas reais)

O provider é **tipado por tipo de valor** — não há `put`/`get` genérico `<T>`. Serialize objetos
para String/bytes antes de gravar:

```java
Optional<String>   getString(String key);
void               putString(String key, String value, Duration ttl);

Optional<byte[]>   getBytes(String key);
void               putBytes(String key, byte[] value, Duration ttl);

OptionalLong       getLong(String key);
void               putLong(String key, long value, Duration ttl);

long               increment(String key);
long               increment(String key, Duration ttl);   // INCR + EXPIRE atômico (janela deslizante)

void               delete(String key);                    // <- este é o "evict"

<T> T              withLock(String key, Duration ttl, Supplier<T> fn); // lock por chave
```

Notas importantes:
- **Evict = `delete(key)`** (não existe método chamado `evict`).
- **TTL** é um `java.time.Duration` passado no `put`. `Duration.ZERO`, negativo ou `null` = **nunca
  expira** (no in-memory). No Redis, mapeia para `EXPIRE`.
- `getBytes` devolve **cópia defensiva** (byte[] é mutável).
- `getString`/`getLong`/`getBytes` fazem *cleanup best-effort* de entradas expiradas na leitura.

## Endpoints de exemplo

| Método | Rota | O que faz |
|---|---|---|
| `GET` | `/items/{id}` | Lê via cache-aside. Resposta inclui `hit` (true=cache, false=rodou a operação real) |
| `GET` | `/items/_stats` | `realExecutions`: quantas vezes a operação **cara** rodou de verdade |
| `DELETE` | `/items/{id}` | Evict (`SeedGridCacheProvider.delete`): próxima leitura volta a ser MISS |

### Cache-aside no `CatalogService`

```java
public ItemLookup get(String id) {
    String key = "catalog:item:" + id;
    Optional<String> cached = cache.getString(key);
    if (cached.isPresent()) {
        return new ItemLookup(id, cached.get(), /*hit*/ true, realExecutions.get());
    }
    String value = expensiveLookup(id);          // operação cara (sleep + contador)
    cache.putString(key, value, ttl);            // popula o cache com TTL
    return new ItemLookup(id, value, /*hit*/ false, realExecutions.get());
}
```

Primeira leitura de um `id` = **MISS** (a operação cara roda, `realExecutions` sobe e o valor é
cacheado). Segunda leitura do mesmo `id` = **HIT** (vem do cache, `realExecutions` **não** sobe).
Um `DELETE /items/{id}` (evict) faz a próxima leitura voltar a ser MISS.

O TTL vem de `seedgrid.cache.example.item-ttl` (default `PT30S`) — troque sem recompilar via
`application.properties` ou a env `ITEM_TTL` (ver `dev.env`).

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (porta 8089)
mvn -o test           # roda os testes (sem Postgres/Redis - tudo in-memory)
```

Swagger UI: http://localhost:8089/q/swagger-ui.

## Testando via curl

```bash
# 1. MISS: primeira leitura de um id roda a operação cara
curl -s http://localhost:8089/items/42; echo

# 2. HIT: segunda leitura do mesmo id vem do cache (repare hit:true)
curl -s http://localhost:8089/items/42; echo

# 3. Prova: o contador de execuções reais ficou em 1 (o HIT não rodou a operação)
curl -s http://localhost:8089/items/_stats; echo

# 4. Evict e leia de novo: volta a ser MISS (contador sobe pra 2)
curl -s -X DELETE http://localhost:8089/items/42
curl -s http://localhost:8089/items/42; echo
curl -s http://localhost:8089/items/_stats; echo
```

## Testes

- `CacheHitMissTest` — via HTTP: prova MISS na 1ª chamada, HIT na 2ª (o contador de execuções
  reais **não** incrementa no HIT), e que o evict força MISS de novo. Asserts são sobre o **delta**
  do contador `/items/_stats` (o cache e o contador são `@ApplicationScoped`, vivem por toda a JVM
  de teste).
- `CacheProviderTest` — injeta o `SeedGridCacheProvider` e exercita a API direto: confirma que o
  bean resolvido é o `DefaultSeedGridCache` (in-memory), cobre put/get/delete de String, **TTL**
  (expiração determinística com `Duration.ofMillis(300)` + sleep) e `increment`/`getLong`.

## O que trocar pra adaptar ao seu caso

- **A operação cara** (`CatalogService.expensiveLookup`): troque o `sleep`/contador pela sua query
  ou chamada externa real.
- **O tipo do valor**: aqui é String. Para objetos, serialize (ex.: Jackson `writeValueAsString` /
  `readValue`) e use `getString`/`putString`, ou `getBytes`/`putBytes`.
- **O TTL**: `seedgrid.cache.example.item-ttl` (ou `Duration` direto no `putString`).
- **Ir distribuído**: adicione `seedgrid-cache-redis` (ou `-valkey`/`-dragonfly`) ao `pom.xml` e
  configure `seedgrid.cache.hosts=redis://host:6379` — o código do app não muda.

## Pegadinhas encontradas montando este exemplo

- `seedgrid-cache-core` **tem** `META-INF/jandex.idx` → o `DefaultSeedGridCache` é descoberto sem
  `quarkus.index-dependency` (ao contrário do `seedgrid-resilience-core`, que exige a linha).
- O `SeedGridCacheProvider` **não** tem `put`/`get` genérico nem método `evict` — é tipado
  (`getString`/`getLong`/`getBytes`) e o evict é `delete(key)`.
- `SeedGridCacheConfigRelocateInterceptor` (registrado via `META-INF/services`) apenas **reloca
  nomes de config** `quarkus.redis.*` → `seedgrid.cache.*` (fachada amigável, com precedência do
  nome `seedgrid.cache.*`). É inerte quando não há extensão Redis no classpath — nenhuma config é
  exigida pra rodar in-memory.
- Como o cache é `@ApplicationScoped` (estado vive por toda a JVM de teste), testes devem usar
  **chaves/ids distintos** e comparar **deltas** de contador, nunca valores absolutos.
