package br.com.seedgrid.cacheexample.resource;

import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.Operation;

import br.com.seedgrid.cacheexample.service.CatalogService;
import br.com.seedgrid.cacheexample.service.ItemLookup;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/items")
@Produces(MediaType.APPLICATION_JSON)
public class ExampleResource {

    @Inject
    CatalogService catalog;

    @GET
    @Path("/{id}")
    @Operation(summary = "Le um item via cache-aside. 1a chamada = MISS, 2a com mesmo id = HIT.")
    public ItemLookup get(@PathParam("id") String id) {
        return catalog.get(id);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Evict: remove o item do cache. A proxima leitura volta a ser MISS.")
    public Response evict(@PathParam("id") String id) {
        catalog.evict(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/_stats")
    @Operation(summary = "Quantas vezes a operacao real (cara) rodou. No HIT esse numero NAO sobe.")
    public Map<String, Long> stats() {
        return Map.of("realExecutions", catalog.realExecutions());
    }
}
