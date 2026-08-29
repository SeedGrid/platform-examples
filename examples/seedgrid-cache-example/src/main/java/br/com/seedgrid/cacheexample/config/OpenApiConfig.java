package br.com.seedgrid.cacheexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid Cache Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-cache-core: cache-aside via o SPI
            SeedGridCacheProvider, sem depender de nenhuma outra extensao SeedGrid nem de banco.

            Sem provider Redis/Valkey/Dragonfly no classpath, o DefaultSeedGridCache (in-memory,
            @DefaultBean) e usado automaticamente.

            Fluxo:
            1. GET /items/{id} - le do CatalogService, que consulta o cache primeiro (cache-aside):
               1a chamada = MISS (executa a operacao "cara" e guarda no cache com TTL); a 2a com o
               mesmo id = HIT (vem do cache, a operacao real NAO roda de novo).
            2. GET /items/_stats - quantas vezes a operacao real (cara) rodou de verdade. Prova o
               efeito do cache: no HIT esse contador NAO sobe.
            3. DELETE /items/{id} - faz evict (SeedGridCacheProvider.delete): a proxima leitura do
               mesmo id volta a ser MISS.
            """
    )
)
public class OpenApiConfig extends Application {
}
