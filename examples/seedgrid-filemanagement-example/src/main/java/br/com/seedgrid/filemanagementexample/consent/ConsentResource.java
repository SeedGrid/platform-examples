package br.com.seedgrid.filemanagementexample.consent;

import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.filemanagement.services.FileStorageRegistry;
import br.com.seedgrid.filemanagementexample.attachment.AttachmentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * O que o HOST faz em volta da lib: levar o usuario ao provedor, receber a volta e guardar o refresh token.
 * Aqui o redirecionamento e' simulado pelo {@link FakeProviderResource}; a lib so' ve o resultado, pela
 * {@link ExampleCredentialSource}.
 */
@Path("/consent")
@Tag(name = "Consent", description = "Concessao e revogacao do acesso do usuario (simuladas)")
@Produces(MediaType.APPLICATION_JSON)
public class ConsentResource {

    @Inject
    ExampleCredentialSource source;

    @Inject
    FakeProviderResource provider;

    @Inject
    FileStorageRegistry registry;

    @POST
    @Path("/{owner}")
    @Operation(summary = "Simula a volta do redirect: o usuario autorizou e o host guarda o refresh token")
    public Response grant(@PathParam("owner") String owner) {
        source.grant(AttachmentService.CONSENT_PROVIDER, owner, provider.authorize());
        return Response.status(Response.Status.CREATED).entity(Map.of("owner", owner, "connected", true)).build();
    }

    @DELETE
    @Path("/{owner}")
    @Operation(summary = "O usuario desconecta: o provedor revoga e o host esquece o token e descarta a instancia")
    public Response revoke(@PathParam("owner") String owner) {
        source.revoke(AttachmentService.CONSENT_PROVIDER, owner).ifPresent(provider::revoke);
        registry.evictOwner(owner);
        return Response.noContent().build();
    }
}
