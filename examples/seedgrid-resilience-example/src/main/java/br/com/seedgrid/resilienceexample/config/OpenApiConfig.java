package br.com.seedgrid.resilienceexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid Resilience Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-resilience-core: rate limiting e circuit breaker
            declarativos via @SgRateLimit / @SgCircuitBreaker, sem depender de
            nenhuma outra extensao SeedGrid.

            Fluxo:
            1. GET /api/greeting - rate limit por janela fixa (3 chamadas / 10s por IP). A 4a
               chamada dentro da janela recebe 429 com headers X-RateLimit-*.
            2. GET /api/checkout - rate limit por token bucket (2 permits/min, burst de 5) -
               permite rajadas curtas mantendo uma taxa media sustentavel.
            3. GET /api/catalog/{id} - chama um "servico instavel" simulado (CatalogService),
               protegido por @SgCircuitBreaker com fallbackMethod. Use
               POST /api/catalog/chaos/{enabled} pra ligar/desligar falhas simuladas e
               GET /api/catalog/_stats pra provar que, com o circuito aberto, as chamadas
               reais param de acontecer (o fallback responde sem tocar o servico real).
            """
    )
)
public class OpenApiConfig extends Application {
}
