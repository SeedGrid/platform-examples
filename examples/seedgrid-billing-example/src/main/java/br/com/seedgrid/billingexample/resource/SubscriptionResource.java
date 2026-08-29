package br.com.seedgrid.billingexample.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.billingexample.dto.SubscriptionDTO;
import br.com.seedgrid.billingexample.service.SubscriptionService;
import br.com.seedgrid.security.services.CompanyRequestContext;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint proprio da app host: mostra o plano/assinatura ativa da company do usuario logado.
 * Nao exige nenhuma permissao alem de estar autenticado — e uma leitura do "meu proprio" estado
 * de billing, escopada pela company corrente resolvida pelo {@code CompanyContextFilter} do
 * seedgrid-security (claim {@code companyId} do JWT).
 */
@Path("/api/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Subscriptions", description = "Assinatura ativa da company do usuario logado")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionResource {

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    CompanyRequestContext companyRequestContext;

    @GET
    @Path("/me")
    @Authenticated
    @Operation(summary = "Plano/assinatura ativa da company do usuario logado")
    public Response me() {
        Long companyId = companyRequestContext.getCurrentCompanyId();
        if (companyId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Nao foi possivel resolver a company corrente do usuario logado")
                    .build();
        }

        return subscriptionService.findForCompany(companyId)
                .map(dto -> Response.ok(dto).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity("Nenhuma assinatura encontrada para esta company")
                        .build());
    }
}
