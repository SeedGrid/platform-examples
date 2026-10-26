package br.com.seedgrid.logintenancyexample.probe;

import br.com.seedgrid.security.services.CompanyRequestContext;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Recurso SO DE TESTE: devolve a empresa corrente vista pelo servidor (prova o escopo da credencial). */
@Path("/probe")
@Produces(MediaType.TEXT_PLAIN)
@Blocking
public class ProbeResource {

    @Inject
    CompanyRequestContext companyRequestContext;

    @GET
    @Path("/permission")
    @PermissionsAllowed("PROBE_READ")
    public String withPermission() {
        return String.valueOf(companyRequestContext.getCurrentCompanyId());
    }
}
