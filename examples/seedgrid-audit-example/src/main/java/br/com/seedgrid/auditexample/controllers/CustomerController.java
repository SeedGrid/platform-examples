package br.com.seedgrid.auditexample.controllers;

import java.util.List;

import br.com.seedgrid.audit.core.annotations.SgAudit;
import br.com.seedgrid.audit.core.enums.AuditOperationEnum;
import br.com.seedgrid.auditexample.entities.Customer;
import br.com.seedgrid.auditexample.services.CustomerService;
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

@Path("/customers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CustomerController {

    private static final String CUSTOMER_FQCN = "br.com.seedgrid.auditexample.entities.Customer";

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GET
    @SgAudit(operation = AuditOperationEnum.READ, entityFqcn = CUSTOMER_FQCN, description = "List customers")
    public List<Customer> listAll() {
        return customerService.listAll();
    }

    @GET
    @Path("/{publicId}")
    @SgAudit(operation = AuditOperationEnum.READ, entityFqcn = CUSTOMER_FQCN, description = "Get customer by public id")
    public Customer findByPublicId(@PathParam("publicId") String publicId) {
        return customerService.findByPublicId(publicId);
    }

    @POST
    @SgAudit(operation = AuditOperationEnum.CREATE, entityFqcn = CUSTOMER_FQCN, description = "Create customer")
    public Customer create(Customer customer) {
        return customerService.create(customer);
    }

    @PUT
    @Path("/{publicId}")
    @SgAudit(operation = AuditOperationEnum.UPDATE, entityFqcn = CUSTOMER_FQCN, description = "Update customer")
    public Customer update(@PathParam("publicId") String publicId, Customer customer) {
        return customerService.update(publicId, customer);
    }

    @DELETE
    @Path("/{publicId}")
    @SgAudit(operation = AuditOperationEnum.DELETE, entityFqcn = CUSTOMER_FQCN, description = "Delete customer")
    public Response delete(@PathParam("publicId") String publicId) {
        customerService.delete(publicId);
        return Response.noContent().build();
    }
}
