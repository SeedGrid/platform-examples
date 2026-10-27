package br.com.seedgrid.cacheexample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import br.com.seedgrid.cache.core.commons.spis.SeedGridCacheProvider;
import br.com.seedgrid.cacheexample.service.ProductService.ProductDto;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * API tipada do provider ({@code get(key, Type)} / {@code put(key, value, ttl)}) com o provider injetado pelo CDI
 * (inclui o decorator de failover): prova que o Quarkus injeta o serializador Jackson do host e que o
 * {@code get}/{@code put} atravessam o decorator.
 */
@QuarkusTest
class CacheTypedApiTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Inject
    SeedGridCacheProvider cache;

    @Test
    void serializadorJacksonDoHostEInjetadoNoProvider() {
        // o CDI entrega um proxy do bean; o que importa e' o comportamento: so' o Jackson suporta um DTO
        assertTrue(cache.valueSerializer().supports(ProductDto.class));
    }

    @Test
    void dtoIdaEVolta_pelaInjecaoCdi() {
        ProductDto dto = new ProductDto("sku-typed-1", "Produto", 4);
        cache.put("typed:dto:1", dto, TTL);
        assertEquals(Optional.of(dto), cache.get("typed:dto:1", ProductDto.class));
    }

    @Test
    void listaGenerica_voltaComElementosTipados() {
        Type tipo = new TypeReference<List<ProductDto>>() { }.getType();
        List<ProductDto> lista = List.of(new ProductDto("a", "A", 1), new ProductDto("b", "B", 2));
        cache.put("typed:list:1", lista, tipo, TTL);

        Optional<List<ProductDto>> lida = cache.get("typed:list:1", tipo);

        assertEquals(lista, lida.orElseThrow());
        assertTrue(lida.get().get(0) instanceof ProductDto);
    }

    @Test
    void putNullApaga_eAusenteEVazio() {
        cache.put("typed:null:1", new ProductDto("x", "X", 1), TTL);
        cache.put("typed:null:1", null, TTL);
        assertTrue(cache.get("typed:null:1", ProductDto.class).isEmpty());
        assertTrue(cache.get("typed:nunca:1", ProductDto.class).isEmpty());
    }
}
