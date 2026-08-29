package br.com.seedgrid.subdomaintenancyexample.resource;

import java.math.BigDecimal;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.PermissionsAllowed;
import io.smallrye.common.annotation.Blocking;
import br.com.seedgrid.subdomaintenancy.annotations.MasterOnly;
import br.com.seedgrid.subdomaintenancyexample.dto.PaymentPlanDTO;
import br.com.seedgrid.subdomaintenancyexample.service.PaymentPlanService;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST Resource for PaymentPlan (MASTER database)
 * Accessible from seedgrid.com.br domain
 *
 * This resource is marked with @MasterOnly, so it will only appear
 * in the Swagger UI when accessed from the master domain.
 */
@Path("/api/payment-plans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Payment Plans", description = "Payment plan management (MASTER)")
@MasterOnly
@Blocking
public class PaymentPlanResource {

    @Inject
    PaymentPlanService service;

    @GET
    @Operation(summary = "List all payment plans")
    @PermissionsAllowed("PAYMENT_PLANS_READ")
    public List<PaymentPlanDTO> listAll() {
        return service.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get payment plan by ID")
    @PermissionsAllowed("PAYMENT_PLANS_READ")
    public PaymentPlanDTO getById(@PathParam("id") Long id) {
        return service.findById(id);
    }

    @GET
    @Path("/search")
    @Operation(summary = "Search payment plans by description")
    @PermissionsAllowed("PAYMENT_PLANS_READ")
    public List<PaymentPlanDTO> searchByDescription(@QueryParam("description") String description) {
        return service.findByDescription(description);
    }

    @GET
    @Path("/by-max-value")
    @Operation(summary = "Find payment plans by max value")
    @PermissionsAllowed("PAYMENT_PLANS_READ")
    public List<PaymentPlanDTO> findByMaxValue(@QueryParam("maxValue") BigDecimal maxValue) {
        return service.findByMaxValue(maxValue);
    }

    @POST
    @Operation(summary = "Create a new payment plan")
    @PermissionsAllowed("PAYMENT_PLANS_CREATE")
    public Response create(@Valid PaymentPlanDTO dto) {
        PaymentPlanDTO created = service.create(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a payment plan")
    @PermissionsAllowed("PAYMENT_PLANS_UPDATE")
    public PaymentPlanDTO update(@PathParam("id") Long id, @Valid PaymentPlanDTO dto) {
        return service.update(id, dto);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a payment plan")
    @PermissionsAllowed("PAYMENT_PLANS_DELETE")
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
