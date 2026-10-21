package br.com.seedgrid.auditexample.controllers;

import br.com.seedgrid.audit.core.annotations.SgAudit;
import br.com.seedgrid.audit.core.enums.AuditOperationEnum;
import br.com.seedgrid.auditexample.entities.BusinessUnit;
import br.com.seedgrid.auditexample.services.BusinessUnitService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/business-units")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BusinessUnitController {

    private static final String BUSINESS_UNIT_FQCN = "br.com.seedgrid.auditexample.entities.BusinessUnit";

    private final BusinessUnitService businessUnitService;

    public BusinessUnitController(BusinessUnitService businessUnitService) {
        this.businessUnitService = businessUnitService;
    }

    @GET
    @Path("/{publicId}")
    @SgAudit(
            operation = AuditOperationEnum.READ,
            entityFqcn = BUSINESS_UNIT_FQCN,
            description = "Get business unit by public id")
    public BusinessUnit findByPublicId(@PathParam("publicId") String publicId) {
        return businessUnitService.findByPublicId(publicId);
    }

    @POST
    @SgAudit(
            operation = AuditOperationEnum.CREATE,
            entityFqcn = BUSINESS_UNIT_FQCN,
            description = "Create business unit")
    public BusinessUnit create(BusinessUnit businessUnit) {
        return businessUnitService.create(businessUnit);
    }

    @DELETE
    @Path("/{publicId}")
    @SgAudit(
            operation = AuditOperationEnum.DELETE,
            entityFqcn = BUSINESS_UNIT_FQCN,
            description = "Delete business unit")
    public Response delete(@PathParam("publicId") String publicId) {
        businessUnitService.delete(publicId);
        return Response.noContent().build();
    }
}
