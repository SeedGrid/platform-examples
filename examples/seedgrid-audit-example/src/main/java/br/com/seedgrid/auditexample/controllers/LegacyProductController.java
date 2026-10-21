package br.com.seedgrid.auditexample.controllers;

import br.com.seedgrid.audit.core.annotations.SgAudit;
import br.com.seedgrid.audit.core.annotations.SgAuditLookup;
import br.com.seedgrid.audit.core.enums.AuditOperationEnum;
import br.com.seedgrid.auditexample.entities.LegacyProduct;
import br.com.seedgrid.auditexample.services.LegacyProductService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/legacy-products")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LegacyProductController {

    private static final String LEGACY_PRODUCT_FQCN = "br.com.seedgrid.auditexample.entities.LegacyProduct";

    private final LegacyProductService legacyProductService;

    public LegacyProductController(LegacyProductService legacyProductService) {
        this.legacyProductService = legacyProductService;
    }

    @GET
    @Path("/{externalCode}")
    @SgAudit(
            operation = AuditOperationEnum.READ,
            entityFqcn = LEGACY_PRODUCT_FQCN,
            description = "Get legacy product by external code",
            lookups = {
                    @SgAuditLookup(field = "externalCode")
            })
    public LegacyProduct findByExternalCode(@PathParam("externalCode") String externalCode) {
        return legacyProductService.findByExternalCode(externalCode);
    }

    @POST
    @SgAudit(
            operation = AuditOperationEnum.CREATE,
            entityFqcn = LEGACY_PRODUCT_FQCN,
            description = "Create legacy product")
    public LegacyProduct create(LegacyProduct legacyProduct) {
        return legacyProductService.create(legacyProduct);
    }

    @DELETE
    @Path("/{externalCode}")
    @SgAudit(
            operation = AuditOperationEnum.DELETE,
            entityFqcn = LEGACY_PRODUCT_FQCN,
            description = "Delete legacy product by external code",
            lookups = {
                    @SgAuditLookup(field = "externalCode")
            })
    public Response delete(@PathParam("externalCode") String externalCode) {
        legacyProductService.deleteByExternalCode(externalCode);
        return Response.noContent().build();
    }
}
