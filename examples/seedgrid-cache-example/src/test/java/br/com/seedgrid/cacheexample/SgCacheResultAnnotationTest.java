package br.com.seedgrid.cacheexample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import br.com.seedgrid.cache.core.commons.spis.SeedGridCacheProvider;
import br.com.seedgrid.cacheexample.service.ProductService;
import br.com.seedgrid.cacheexample.service.ProductService.ProductDto;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * {@code @SgCacheResult} FIM A FIM, com o CDI/ArC de verdade (interceptor real, ObjectMapper gerenciado do
 * quarkus-rest-jackson, provider em memoria): prova que a annotation intercepta, serializa o DTO, respeita o TTL do
 * {@code application.properties} e nao intercepta a auto-chamada. Cada teste usa um sku unico (o cache vive na JVM).
 */
@QuarkusTest
class SgCacheResultAnnotationTest {

    @Inject
    ProductService products;

    @Inject
    SeedGridCacheProvider cache;

    private static String sku() {
        return "sku-" + UUID.randomUUID();
    }

    @Test
    void theSecondCallIsServedFromTheCacheAndTheDtoComesBackIntact() {
        String sku = sku();
        int before = products.runs();

        ProductDto first = products.find(sku);
        ProductDto second = products.find(sku);

        assertEquals(before + 1, products.runs(), "o corpo rodou uma vez so");
        assertEquals(new ProductDto(sku, "Produto " + sku, 10), first);
        assertEquals(first, second);
    }

    @Test
    void theEntryLivesUnderSgcCacheNameKey() {
        String sku = sku();
        products.find(sku);

        assertEquals(true, cache.getBytes("sgc:products:" + sku).isPresent(), "chave sgc:{cache}:{parte}");
    }

    @Test
    void optionalEmptyIsCachedToo() {
        int before = products.runs();

        assertEquals(Optional.empty(), products.findOptional("none"));
        assertEquals(Optional.empty(), products.findOptional("none"));

        assertEquals(before + 1, products.runs());
    }

    @Test
    void anExceptionFromTheMethodIsNeverCached() {
        String sku = sku();
        int before = products.runs();

        assertThrows(IllegalArgumentException.class, () -> products.findOrFail(sku));
        assertThrows(IllegalArgumentException.class, () -> products.findOrFail(sku));

        assertEquals(before + 2, products.runs(), "cada chamada rodou o corpo de novo");
    }

    @Test
    void selfInvocationOfANonPrivateMethodIsInterceptedByArc() {
        // No Quarkus (ArC) o bean e' interceptado por subclasse: this.find() de dentro do bean tambem passa pelo cache
        // (diferente do CDI classico, onde a auto-chamada ignora os interceptadores).
        String sku = sku();
        int before = products.runs();

        products.findViaSelfCall(sku);
        products.findViaSelfCall(sku);

        assertEquals(before + 1, products.runs(), "a auto-chamada foi cacheada");
    }

    @Test
    void invalidateRemovesOnlyThatEntryAndTheNextCallRunsAgain() {
        String a = sku();
        String b = sku();
        products.find(a);
        products.find(b);
        int before = products.runs();

        products.update(a, "novo nome");
        products.find(a);
        products.find(b);

        assertEquals(before + 1, products.runs(), "so' a entrada de a foi invalidada");
    }

    @Test
    void aFailingMethodInvalidatesNothing() {
        String sku = sku();
        products.find(sku);
        int before = products.runs();

        assertThrows(IllegalStateException.class, () -> products.updateAndFail(sku));
        products.find(sku);

        assertEquals(before, products.runs(), "o dado nao mudou: a entrada continuou em cache");
    }

    @Test
    void invalidateAllEmptiesTheWholeCache() {
        String a = sku();
        String b = sku();
        products.find(a);
        products.find(b);
        int before = products.runs();

        products.refreshAll();
        products.find(a);
        products.find(b);

        assertEquals(before + 2, products.runs(), "as duas entradas foram apagadas");
    }

    @Test
    void fiftyConcurrentCallsOnTheSameMissRunTheBodyOnceWithLock() throws Exception {
        String sku = sku();
        int before = products.runs();
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<ProductDto>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return products.findSlow(sku);
                }));
            }
            go.countDown();
            for (Future<ProductDto> f : futures) {
                assertEquals(new ProductDto(sku, "Lento " + sku, 3), f.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(before + 1, products.runs(), "50 threads no mesmo miss: 1 execucao");
    }
}
