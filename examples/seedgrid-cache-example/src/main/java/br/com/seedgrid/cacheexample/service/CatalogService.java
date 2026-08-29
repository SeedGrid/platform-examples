package br.com.seedgrid.cacheexample.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.seedgrid.cache.core.commons.spi.SeedGridCacheProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Servico de catalogo que memoiza uma operacao "cara" via cache-aside, usando o SPI
 * {@link SeedGridCacheProvider} do seedgrid-cache-core.
 *
 * <p>Padrao cache-aside (lazy loading):</p>
 * <ol>
 *   <li>{@code getString(key)} - tenta ler do cache.</li>
 *   <li>HIT: retorna o valor cacheado sem tocar na fonte cara.</li>
 *   <li>MISS: executa a operacao real (aqui simulada com sleep + contador),
 *       grava com {@code putString(key, value, ttl)} e retorna.</li>
 * </ol>
 *
 * <p>O provider so armazena String/byte[]/long (nao ha put/get generico). Como o valor deste
 * exemplo e textual, usamos {@code getString}/{@code putString}. Para objetos, serialize
 * para JSON/String antes de gravar.</p>
 */
@ApplicationScoped
public class CatalogService {

    private static final Logger LOG = Logger.getLogger(CatalogService.class);

    /** Prefixo de namespace da chave - evita colisao com outras chaves no mesmo cache. */
    private static final String KEY_PREFIX = "catalog:item:";

    /** Conta quantas vezes a operacao real (cara) de fato rodou. HIT nao incrementa. */
    private final AtomicInteger realExecutions = new AtomicInteger(0);

    private final SeedGridCacheProvider cache;
    private final Duration ttl;

    @Inject
    public CatalogService(
            SeedGridCacheProvider cache,
            @ConfigProperty(name = "seedgrid.cache.example.item-ttl", defaultValue = "PT30S") Duration ttl) {
        this.cache = cache;
        this.ttl = ttl;
    }

    /**
     * Le um item aplicando cache-aside. Retorna, alem do valor, se foi HIT ou MISS e o total de
     * execucoes reais - o que permite ao endpoint/teste provar que o HIT nao roda a operacao cara.
     */
    public ItemLookup get(String id) {
        String key = KEY_PREFIX + id;

        Optional<String> cached = cache.getString(key);
        if (cached.isPresent()) {
            LOG.debugf("HIT  id=%s (cache)", id);
            return new ItemLookup(id, cached.get(), true, realExecutions.get());
        }

        // MISS: roda a operacao cara e popula o cache.
        String value = expensiveLookup(id);
        cache.putString(key, value, ttl);
        LOG.debugf("MISS id=%s -> computado e cacheado (ttl=%s)", id, ttl);
        return new ItemLookup(id, value, false, realExecutions.get());
    }

    /** Remove o item do cache (evict). A proxima leitura do mesmo id volta a ser MISS. */
    public void evict(String id) {
        cache.delete(KEY_PREFIX + id);
        LOG.debugf("EVICT id=%s", id);
    }

    /** Quantas vezes a operacao real (cara) rodou desde o start. Usado por GET /items/_stats. */
    public long realExecutions() {
        return realExecutions.get();
    }

    /**
     * Simula uma consulta cara (ex.: chamada a servico externo lento ou query pesada).
     * Cada invocacao incrementa o contador e paga um pequeno custo de tempo.
     */
    private String expensiveLookup(String id) {
        realExecutions.incrementAndGet();
        try {
            Thread.sleep(50); // custo simulado
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long price = 1000 + Math.abs(id.hashCode() % 9000);
        return "Produto-" + id + " @ R$" + price;
    }
}
