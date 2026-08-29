package br.com.seedgrid.privacyexample.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.privacyexample.dto.CustomerRequest;
import br.com.seedgrid.privacyexample.dto.CustomerResponse;
import br.com.seedgrid.privacyexample.service.CustomerService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * API de exemplo da lib seedgrid-privacy. Sem autenticacao — o foco e' demonstrar criptografia
 * at-rest, busca por hash e mascaramento de PII.
 */
@Path("/api/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Customers", description = "PII cifrada at-rest, busca por hash e mascaramento (LGPD)")
public class CustomerResource {

    @Inject
    CustomerService service;

    @POST
    @Operation(summary = "Cria um cliente (PII cifrada at-rest de forma transparente)")
    public Response create(@Valid CustomerRequest request) {
        CustomerResponse response = CustomerResponse.from(service.create(request));
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Busca por id — devolve a PII em CLARO (decifrada pelo JPA na leitura)")
    public Response findById(@PathParam("id") Long id) {
        return service.findById(id)
                .map(c -> Response.ok(CustomerResponse.from(c)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{id}/masked")
    @Operation(summary = "Busca por id — devolve a PII MASCARADA (@PersonalData) para log/auditoria")
    public Response findByIdMasked(@PathParam("id") Long id) {
        return service.findById(id)
                .map(c -> Response.ok(service.mask(CustomerResponse.from(c))).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/search")
    @Operation(summary = "Busca por e-mail OU cpf via hash deterministico (sem descriptografar a tabela)")
    public Response search(@QueryParam("email") String email, @QueryParam("cpf") String cpf) {
        if (email != null && !email.isBlank()) {
            return service.findByEmail(email)
                    .map(c -> Response.ok(CustomerResponse.from(c)).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        }
        if (cpf != null && !cpf.isBlank()) {
            return service.findByCpf(cpf)
                    .map(c -> Response.ok(CustomerResponse.from(c)).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND).build());
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"informe ?email= ou ?cpf=\"}").build();
    }
}
