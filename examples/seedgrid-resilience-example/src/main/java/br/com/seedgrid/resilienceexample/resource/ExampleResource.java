package br.com.seedgrid.resilienceexample.resource;

import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.resilience.core.runtime.ratelimiter.annotations.SeedGridRateLimit;
import br.com.seedgrid.resilience.core.runtime.ratelimiter.enums.RateLimitAlgorithm;
import br.com.seedgrid.resilienceexample.service.CatalogService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoints de exemplo do seedgrid-resilience-core: rate limiting (janela fixa e token bucket) e
 * circuit breaker com fallback, ambos declarativos via anotacao.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Resilience", description = "Exemplos de rate limit e circuit breaker do seedgrid-resilience-core")
public class ExampleResource {

    @Inject
    CatalogService catalogService;

    @GET
    @Path("/greeting")
    @Operation(summary = "Janela fixa: 3 chamadas / 10s por IP - a 4a recebe 429")
    @SeedGridRateLimit(
            configKey = "greeting",
            permits = 3,
            window = 10,
            windowUnit = ChronoUnit.SECONDS,
            algorithm = RateLimitAlgorithm.FIXED_WINDOW)
    public Response greeting() {
        return Response.ok(Map.of("message", "hello")).build();
    }

    @GET
    @Path("/checkout")
    @Operation(summary = "Token bucket: 2 permits/min com rajada de ate 5 - permite bursts curtos")
    @SeedGridRateLimit(
            configKey = "checkout",
            permits = 2,
            window = 1,
            windowUnit = ChronoUnit.MINUTES,
            algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            burstCapacity = 5)
    public Response checkout() {
        return Response.ok(Map.of("status", "ok")).build();
    }

    @GET
    @Path("/catalog/{id}")
    @Operation(summary = "Busca um produto via CatalogService, protegido por circuit breaker")
    public Response catalog(@PathParam("id") String id) {
        return Response.ok(Map.of("result", catalogService.buscarProduto(id))).build();
    }

    @POST
    @Path("/catalog/chaos/{enabled}")
    @Operation(summary = "Liga/desliga falhas simuladas no CatalogService (uso didatico/teste)")
    public Response chaos(@PathParam("enabled") boolean enabled) {
        catalogService.setChaos(enabled);
        return Response.ok(Map.of("chaos", enabled)).build();
    }

    @GET
    @Path("/catalog/_stats")
    @Operation(summary = "Quantidade de chamadas reais (nao-fallback) feitas ao CatalogService")
    public Response stats() {
        return Response.ok(Map.of("realCalls", catalogService.realCallCount())).build();
    }
}
