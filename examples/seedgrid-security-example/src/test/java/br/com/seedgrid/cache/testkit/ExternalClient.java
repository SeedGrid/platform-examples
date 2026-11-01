package br.com.seedgrid.cache.testkit;

import java.time.Duration;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.runtime.datasource.BlockingRedisDataSourceImpl;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.redis.client.Redis;
import io.vertx.mutiny.redis.client.RedisAPI;

/**
 * Cliente Redis independente (Vert.x proprio) para CONFERIR o que realmente esta no servidor, sem passar
 * pelo provider/failover sob teste. Ex.: "as escritas da queda chegaram ao container".
 *
 * <p>Feche com {@link #close()}; o cliente reconecta sozinho se o container for parado e religado.</p>
 */
public final class ExternalClient implements AutoCloseable {

    private final Vertx vertx;
    private final Redis redis;
    private final RedisDataSource ds;

    private ExternalClient(Vertx vertx, Redis redis, RedisDataSource ds) {
        this.vertx = vertx;
        this.redis = redis;
        this.ds = ds;
    }

    /**
     * Cria um cliente para o endereco.
     *
     * @param endpoint URL {@code redis://host:porta} (ver {@link CacheContainer#endpoint()})
     * @param timeout tempo maximo de cada comando
     * @return cliente aberto
     */
    public static ExternalClient connect(String endpoint, Duration timeout) {
        Vertx vertx = Vertx.vertx();
        Redis redis = Redis.createClient(vertx, endpoint);
        RedisDataSource ds = new BlockingRedisDataSourceImpl(vertx, redis, RedisAPI.api(redis), timeout);
        return new ExternalClient(vertx, redis, ds);
    }

    /**
     * Data source bloqueante deste cliente.
     *
     * @return data source
     */
    public RedisDataSource ds() {
        return ds;
    }

    /** Fecha o cliente e o Vert.x. */
    @Override
    public void close() {
        redis.close();
        vertx.closeAndAwait();
    }
}
