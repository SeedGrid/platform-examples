package br.com.seedgrid.cacheexample.service;

/**
 * Resultado de uma leitura via cache-aside.
 *
 * @param id     identificador consultado
 * @param value  valor resolvido (do cache no HIT, ou recem-computado no MISS)
 * @param hit    {@code true} se veio do cache (HIT), {@code false} se a operacao real rodou (MISS)
 * @param realExecutions quantas vezes a operacao "cara" rodou de verdade desde o start da app
 */
public record ItemLookup(String id, String value, boolean hit, long realExecutions) {
}
