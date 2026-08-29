# seedgrid-resilience-example

App de exemplo, rodável, do `seedgrid-resilience-core`: **rate limiting** e **circuit breaker**
declarativos via `@SeedGridRateLimit` e `@SeedGridCircuitBreaker`, sem depender de nenhuma outra
extensão SeedGrid (não precisa de `seedgrid-security`, Postgres ou Redis).

## O princípio central: proteção declarativa, sem espalhar lógica pelo endpoint

Em vez de escrever contadores/locks manuais no código de negócio, você anota o método com o
limite/circuito desejado e a extensão cuida do resto: identidade (IP por padrão), algoritmo,
persistência do estado e resposta HTTP (`429` com headers `X-RateLimit-*`, ou fallback do circuit
breaker).

O estado fica no `SeedGridCacheProvider` (`seedgrid-cache-core`). Sem um provider Redis/Valkey/
Dragonfly no classpath, o `DefaultSeedGridCache` (in-memory, `@DefaultBean`) assume automaticamente
— suficiente pra uma instância única como este exemplo. Em produção com múltiplas instâncias,
adicione `seedgrid-cache-redis` (ou `-valkey`/`-dragonfly`) ao classpath e configure
`quarkus.redis.hosts` — nenhuma linha de código muda.

## Endpoints de exemplo

| Método | Rota | Proteção |
|---|---|---|
| `GET` | `/api/greeting` | Rate limit `FIXED_WINDOW`: 3 chamadas / 10s por IP |
| `GET` | `/api/checkout` | Rate limit `TOKEN_BUCKET`: 2 permits/min, rajada até 5 |
| `GET` | `/api/catalog/{id}` | Circuit breaker (`CatalogService.buscarProduto`), com fallback |
| `POST` | `/api/catalog/chaos/{enabled}` | Liga/desliga falhas simuladas no `CatalogService` (didático) |
| `GET` | `/api/catalog/_stats` | Quantas chamadas *reais* (não-fallback) o `CatalogService` recebeu |

### Rate limit — janela fixa (`/api/greeting`)

```java
@SeedGridRateLimit(configKey = "greeting", permits = 3, window = 10, windowUnit = ChronoUnit.SECONDS)
public Response greeting() { ... }
```

3 chamadas na janela de 10s passam; a 4ª recebe `429` com `X-RateLimit-Remaining: 0` e
`Retry-After`. `configKey` permite trocar `permits`/`window`/`enabled` via `application.properties`
sem recompilar (ver comentários no arquivo).

### Rate limit — token bucket (`/api/checkout`)

```java
@SeedGridRateLimit(configKey = "checkout", permits = 2, window = 1, windowUnit = ChronoUnit.MINUTES,
        algorithm = RateLimitAlgorithm.TOKEN_BUCKET, burstCapacity = 5)
public Response checkout() { ... }
```

O balde começa cheio (`burstCapacity = 5`): as 5 primeiras chamadas passam mesmo com uma taxa média
de só 2/min. A 6ª é negada até o balde repor pelo menos 1 token.

### Circuit breaker (`/api/catalog/{id}`)

`CatalogService.buscarProduto` simula uma integração instável. Um toggle de "caos"
(`POST /api/catalog/chaos/true`) força toda chamada real a falhar — sem precisar derrubar um
serviço de verdade pra testar o circuito:

```java
@SeedGridCircuitBreaker(
        name = "catalog-api",
        slidingWindowSize = 4,
        minimumNumberOfCalls = 4,
        failureRateThreshold = 50.0,
        openStateDurationMs = 3_000,
        permittedCallsInHalfOpen = 2,
        fallbackMethod = "catalogFallback")
public String buscarProduto(String id) { ... }
```

Com `chaos=true`: as 4 primeiras chamadas falham de verdade (o corpo real roda e lança) e, na 4ª,
`failureRate = 100% >= 50%` → circuito abre. A 5ª chamada em diante nem chega a executar o corpo
real — o `CircuitBreakerInterceptor` já responde via `catalogFallback` (`GET /api/catalog/_stats`
prova isso: o contador de chamadas reais para de subir). Depois de `openStateDurationMs` (3s), o
circuito vira `HALF_OPEN` e libera `permittedCallsInHalfOpen = 2` chamadas de sonda — se tiverem
sucesso (chaos desligado), o circuito fecha (`CLOSED`) de novo.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env (porta 8088)
mvn -o test           # roda os testes (sem Postgres/Redis - tudo in-memory)
```

## Testando via curl

```bash
# 1. Rate limit por janela fixa: 3 passam, a 4a leva 429
for i in 1 2 3 4; do curl -i http://localhost:8088/api/greeting; echo; done

# 2. Circuit breaker: liga o caos
curl -X POST http://localhost:8088/api/catalog/chaos/true

# 3. Chama 5x - as 4 primeiras falham "de verdade" (fallback), a 5a nem tenta (circuito aberto)
for i in 1 2 3 4 5; do curl -s http://localhost:8088/api/catalog/42; echo; done

# 4. Prova que o circuito parou de chamar o servico real (fica em 4, nao sobe na 5a chamada)
curl -s http://localhost:8088/api/catalog/_stats

# 5. Desliga o caos e espera o circuito virar HALF_OPEN (openStateDurationMs=3s)
curl -X POST http://localhost:8088/api/catalog/chaos/false
sleep 4
curl -s http://localhost:8088/api/catalog/42   # sonda 1/2 (half-open) - sucesso real
curl -s http://localhost:8088/api/catalog/42   # sonda 2/2 - fecha o circuito
```

Swagger UI: http://localhost:8088/q/swagger-ui.

## Testes

- `RateLimitTest` — prova os dois algoritmos: janela fixa (`/api/greeting`, 3 passam + 429 na 4ª
  com headers) e token bucket (`/api/checkout`, burst de 5 + 429 na 6ª).
- `CircuitBreakerTest` — fluxo completo `CLOSED → OPEN → HALF_OPEN → CLOSED`: liga caos, prova que
  o circuito abre após `minimumNumberOfCalls` falhas e que chamadas seguintes não tocam mais o
  serviço real (via contador `/api/catalog/_stats`); desliga o caos, espera `openStateDurationMs`,
  e prova que as sondas em `HALF_OPEN` fecham o circuito de novo.

Ambos os testes rodam num único fluxo sequencial por classe (sem `@Order` nem reset de estado
entre métodos) porque o cache in-memory (`DefaultSeedGridCache`) e os beans `@ApplicationScoped`
vivem durante toda a JVM de teste — por isso `RateLimitTest` usa `configKey`s/endpoints distintos
por teste (sem interferência) e `CircuitBreakerTest` faz o ciclo de vida inteiro num único
`@Test`.

## Pegadinhas encontradas montando este exemplo (úteis se for reaproveitar o padrão)

- `seedgrid-resilience-core` **não depende** de `seedgrid-security` nem de Postgres — só de
  `seedgrid-cache-core` (transitivo). Sem um provider Redis/Valkey/Dragonfly no classpath, o
  `DefaultSeedGridCache` in-memory (`@DefaultBean`) assume sozinho: nenhuma config extra é
  necessária pra rodar local/testes de instância única.
- **Nota histórica (jandex):** até a versão `2026.07.02`, o jar do `seedgrid-resilience-core` não
  empacotava `META-INF/jandex.idx`, então o Quarkus ArC não descobria os beans/interceptors da
  extension e as anotações viravam no-op silencioso (sem 429, sem fallback). O workaround era
  declarar `quarkus.index-dependency.seedgrid-resilience-core.group-id/artifact-id` no
  `application.properties`. A partir da `2026.07.08` o jar traz o índice Jandex embutido e o
  workaround **não é mais necessário** (este exemplo já não o usa).
- **Nota histórica (429 com contrato ambíguo, corrigido na `2026.07.08`):** a lib empacotava *dois*
  `ExceptionMapper<RateLimitExceededException>` `@Provider` de mesma prioridade, e qual deles o
  Quarkus registrava era indefinido — a troca da forma de indexação (workaround `index-dependency`
  → `jandex.idx` embutido) chegou a trocar o vencedor e sumir com os headers `X-RateLimit-*` do
  429. O mapper duplicado (`exceptions.RateLimitExceededExceptionMapper`, body JSON sem
  `X-RateLimit-*`) foi removido: o contrato do 429 agora é estável — sem body, com
  `X-RateLimit-Limit/Remaining/Reset` + `Retry-After` (`http.RateLimitExceptionMapper`), e este
  exemplo o asserta por completo no `RateLimitTest`.
- `@SeedGridRateLimit`/`@SeedGridCircuitBreaker` são `@InterceptorBinding` de CDI — funcionam
  direto em métodos de recurso JAX-RS (que já são beans gerenciados), sem precisar de service
  intermediário. Só cuidado com **self-invocation**: chamar o método anotado de dentro da mesma
  classe (sem passar pelo proxy do CDI) não aciona o interceptor.
- Sem `fallbackMethod`, `CircuitBreakerOpenException` não tem `ExceptionMapper` registrado pela
  extension (ao contrário do rate limit, que sempre mapeia pra 429) — ela simplesmente propaga.
  Se o app não fornecer fallback nem um mapper próprio, o circuito aberto vira `500`.
- Para testar circuit breaker de forma determinística sem mexer em relógio: dá pra colocar um
  toggle de "caos" (`AtomicBoolean`) no service simulado, em vez de tentar derrubar uma dependência
  de verdade — muito mais simples e 100% reprodutível.
- `slidingWindowSize` do circuit breaker é o tamanho do ring buffer; pra abrir o circuito
  determinísticamente na chamada N, configure `slidingWindowSize = minimumNumberOfCalls` (ex.: os
  dois em 4) — assim o buffer enche exatamente quando o mínimo de chamadas é atingido.
