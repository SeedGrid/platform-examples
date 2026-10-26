package br.com.seedgrid.logintenancyexample.resource;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.logintenancyexample.dto.ProductDTO;
import br.com.seedgrid.logintenancyexample.entity.Product;
import br.com.seedgrid.logintenancyexample.service.ProductService;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint de exemplo para o modelo login-tenancy: uma unica aplicacao/base,
 * onde cada company autenticada so enxerga seus proprios registros (isolamento
 * logico via CompanyRequestContext, nao ha @SgTenantOnly/@SgMasterOnly aqui).
 */
@Path("/api/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products", description = "Exemplo de dominio escopado por company (login-tenancy)")
@SecurityRequirement(name = "bearerAuth")
@Blocking
public class ProductResource {

    @Inject
    ProductService service;

    @GET
    @Operation(summary = "List products of the current company")
    public List<ProductDTO> listAll() {
        return service.listAll().stream().map(ProductResource::toDTO).toList();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a product by id (scoped to the current company)")
    public ProductDTO getById(@PathParam("id") Long id) {
        return toDTO(service.findById(id));
    }

    @POST
    @Operation(summary = "Create a product for the current company")
    public Response create(@Valid ProductDTO dto) {
        ProductDTO created = toDTO(service.create(dto));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a product (scoped to the current company)")
    public ProductDTO update(@PathParam("id") Long id, @Valid ProductDTO dto) {
        return toDTO(service.update(id, dto));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a product (scoped to the current company)")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }

    private static ProductDTO toDTO(Product product) {
        return new ProductDTO(product.getId(), product.getName(), product.getSku(), product.getPrice());
    }
}
