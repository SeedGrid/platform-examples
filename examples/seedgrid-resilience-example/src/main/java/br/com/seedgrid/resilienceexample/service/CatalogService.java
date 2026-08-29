package br.com.seedgrid.resilienceexample.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.seedgrid.resilience.core.runtime.circuitbreaker.annotations.SeedGridCircuitBreaker;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Simula uma integracao instavel (ex.: catalogo de produtos de um parceiro externo). O
 * {@link #chaos} funciona como um toggle de "caos" pra demonstrar o circuit breaker em testes/demo
 * sem precisar derrubar um servico de verdade: com chaos=true, toda chamada real falha.
 *
 * <p>{@link #realCalls} conta quantas vezes o corpo real do metodo rodou (nao o fallback) - e a
 * prova de que, com o circuito ABERTO, o {@code CircuitBreakerInterceptor} para de invocar
 * {@link #buscarProduto(String)} e vai direto pro fallback.</p>
 */
@Slf4j
@ApplicationScoped
public class CatalogService {

    private final AtomicBoolean chaos = new AtomicBoolean(false);
    private final AtomicInteger realCalls = new AtomicInteger();

    public void setChaos(boolean enabled) {
        chaos.set(enabled);
        log.info("catalog chaos toggle -> {}", enabled);
    }

    public int realCallCount() {
        return realCalls.get();
    }

    @SeedGridCircuitBreaker(
            name = "catalog-api",
            slidingWindowSize = 4,
            minimumNumberOfCalls = 4,
            failureRateThreshold = 50.0,
            openStateDurationMs = 3_000,
            permittedCallsInHalfOpen = 2,
            fallbackMethod = "catalogFallback")
    public String buscarProduto(String id) {
        realCalls.incrementAndGet();
        if (chaos.get()) {
            throw new IllegalStateException("catalogo indisponivel (id=" + id + ")");
        }
        return "produto-" + id;
    }

    public String catalogFallback(String id, Exception cause) {
        log.warn("fallback do catalogo acionado para id={} (causa: {})", id, cause.getMessage());
        return "produto-indisponivel:" + id;
    }
}
