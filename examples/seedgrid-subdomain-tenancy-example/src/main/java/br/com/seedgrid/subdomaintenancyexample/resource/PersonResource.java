package br.com.seedgrid.subdomaintenancyexample.resource;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.subdomaintenancy.annotations.SgTenantOnly;
import br.com.seedgrid.subdomaintenancyexample.dto.PersonDTO;
import br.com.seedgrid.subdomaintenancyexample.service.PersonService;
import io.smallrye.common.annotation.Blocking;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST Resource for Person (TENANT database)
 * Accessible from {tenant}.seedgrid.com.br domains
 * The tenant is automatically resolved from the Host header
 *
 * This resource is marked with @SgTenantOnly, so it will only appear
 * in the Swagger UI when accessed from a tenant domain.
 */
@Path("/api/persons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Persons", description = "Person management (TENANT)")
@Tag(name = "tenant-only", description = "Endpoints disponíveis apenas no contexto de tenant")
@SecurityRequirement(name = "bearerAuth")
@SgTenantOnly
@Blocking
public class PersonResource {

    final PersonService service;
    public PersonResource(PersonService service) {
        this.service = service;
    }


    @GET
    @Operation(summary = "List all persons")
    @Tag(name = "tenant-only")
    public List<PersonDTO> listAll() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get person by ID")
    @Tag(name = "tenant-only")
    public PersonDTO getById(@PathParam("id") Long id) {
        return service.findById(id);
    }

    @GET
    @Path("/search")
    @Operation(summary = "Search persons by name")
    @Tag(name = "tenant-only")
    public List<PersonDTO> searchByName(@QueryParam("name") String name) {
        return service.findByName(name);
    }

    @POST
    @Operation(summary = "Create a new person")
    @Tag(name = "tenant-only")
    public Response create(@Valid PersonDTO dto) {
        PersonDTO created = service.create(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a person")
    @Tag(name = "tenant-only")
    public PersonDTO update(@PathParam("id") Long id, @Valid PersonDTO dto) {
        return service.update(id, dto);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a person")
    @Tag(name = "tenant-only")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
