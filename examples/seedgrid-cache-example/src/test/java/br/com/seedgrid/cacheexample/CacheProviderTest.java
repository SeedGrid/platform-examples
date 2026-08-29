package br.com.seedgrid.cacheexample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import br.com.seedgrid.cache.core.commons.spi.SeedGridCacheProvider;
import br.com.seedgrid.cache.core.commons.spi.impl.DefaultSeedGridCache;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Exercita a API do SeedGridCacheProvider diretamente (injetado pelo CDI). Confirma que, sem
 * provider Redis/Valkey/Dragonfly no classpath, o bean resolvido e o DefaultSeedGridCache
 * (in-memory, @DefaultBean), e cobre put/get, TTL (expiracao deterministica) e delete/increment.
 */
@QuarkusTest
class CacheProviderTest {

    @Inject
    SeedGridCacheProvider cache;

    @Test
    void resolvedBeanIsInMemoryDefault() {
        assertTrue(cache instanceof DefaultSeedGridCache,
                "sem backend Redis/Valkey/Dragonfly, o @DefaultBean in-memory deve assumir");
    }

    @Test
    void putGetDelete_string() {
        String key = "direct:string:1";
        assertTrue(cache.getString(key).isEmpty(), "chave inexistente = Optional.empty");

        cache.putString(key, "hello", Duration.ZERO); // Duration.ZERO = nunca expira
        assertEquals("hello", cache.getString(key).orElseThrow());

        cache.delete(key);
        assertTrue(cache.getString(key).isEmpty(), "apos delete, a chave some");
    }

    @Test
    void ttlExpiresEntry() throws InterruptedException {
        String key = "direct:ttl:1";
        cache.putString(key, "temp", Duration.ofMillis(300));

        // Logo apos o put, ainda esta la.
        assertTrue(cache.getString(key).isPresent(), "dentro do TTL o valor persiste");

        // Depois do TTL, expira e getString retorna empty.
        Thread.sleep(450);
        assertTrue(cache.getString(key).isEmpty(), "apos o TTL, o valor deve expirar");
    }

    @Test
    void incrementAndGetLong() {
        String key = "direct:counter:1";
        assertTrue(cache.getLong(key).isEmpty());

        assertEquals(1L, cache.increment(key));
        assertEquals(2L, cache.increment(key));

        assertTrue(cache.getLong(key).isPresent());
        assertEquals(2L, cache.getLong(key).getAsLong());

        cache.delete(key);
        assertFalse(cache.getLong(key).isPresent());
    }
}
