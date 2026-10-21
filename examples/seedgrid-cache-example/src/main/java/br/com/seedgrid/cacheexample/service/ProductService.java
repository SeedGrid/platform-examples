package br.com.seedgrid.cacheexample.service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.seedgrid.cache.core.annotations.SgCacheInvalidate;
import br.com.seedgrid.cache.core.annotations.SgCacheInvalidateAll;
import br.com.seedgrid.cache.core.annotations.SgCacheKey;
import br.com.seedgrid.cache.core.annotations.SgCacheResult;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Cache DECLARATIVO com {@code @SgCacheResult}: o metodo nao fala com o cache, a annotation faz tudo (chave
 * {@code sgc:{cacheName}:{partes}}, TTL, serializacao, falha do cache = miss).
 *
 * <p>O TTL vem de {@code seedgrid.cache.annotations.default-ttl} (ou do membro {@code ttl}). No Quarkus (ArC) a
 * auto-chamada de metodo nao privado ({@link #findViaSelfCall}) TAMBEM e interceptada.</p>
 */
@ApplicationScoped
public class ProductService {

    public record ProductDto(String sku, String name, int stock) {
    }

    private final AtomicInteger runs = new AtomicInteger();
    private final AtomicInteger updates = new AtomicInteger();

    /** Quantas vezes o corpo dos metodos de fato rodou (HIT nao conta). Metodo, nao campo: o campo do proxy CDI e outro. */
    public int runs() {
        return runs.get();
    }

    public int updates() {
        return updates.get();
    }

    @SgCacheResult(cacheName = "products")
    public ProductDto find(@SgCacheKey String sku) {
        runs.incrementAndGet();
        return new ProductDto(sku, "Produto " + sku, 10);
    }

    @SgCacheResult(cacheName = "products-opt", ttl = "2m")
    public Optional<ProductDto> findOptional(String sku) {
        runs.incrementAndGet();
        return "none".equals(sku) ? Optional.empty() : Optional.of(new ProductDto(sku, "Produto " + sku, 1));
    }

    /** Anti-stampede: numa falta so uma chamada roda o corpo. */
    @SgCacheResult(cacheName = "products-slow", lock = true)
    public ProductDto findSlow(String sku) {
        runs.incrementAndGet();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new ProductDto(sku, "Lento " + sku, 3);
    }

    @SgCacheResult(cacheName = "products-fail")
    public ProductDto findOrFail(String sku) {
        runs.incrementAndGet();
        throw new IllegalArgumentException("sku invalido: " + sku);
    }

    /** Atualiza o produto e invalida a entrada dele: a proxima leitura recalcula (invalida DEPOIS do sucesso). */
    @SgCacheInvalidate(cacheName = "products")
    public void update(@SgCacheKey String sku, String newName) {
        updates.incrementAndGet();
    }

    /** Falha: nada e invalidado (o dado nao mudou). */
    @SgCacheInvalidate(cacheName = "products")
    public void updateAndFail(@SgCacheKey String sku) {
        throw new IllegalStateException("falhou ao atualizar " + sku);
    }

    /** Apaga o cache "products" inteiro (SCAN + UNLINK nos servidores; prefixo sgc:products:). */
    @SgCacheInvalidateAll(cacheName = "products")
    public void refreshAll() {
        updates.incrementAndGet();
    }

    /** Nao e anotado e chama {@link #find} de dentro do bean: no Quarkus a auto-chamada passa pelo cache. */
    public ProductDto findViaSelfCall(String sku) {
        return find(sku);
    }
}
