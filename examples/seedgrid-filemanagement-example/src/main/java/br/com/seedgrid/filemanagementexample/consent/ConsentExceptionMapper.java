package br.com.seedgrid.filemanagementexample.consent;

import java.util.Map;

import br.com.seedgrid.filemanagement.spis.StorageConsentRequiredException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Acesso nunca concedido ou revogado: o produto leva o usuario a conceder de novo (aqui, 409 com um codigo). */
@Provider
public class ConsentExceptionMapper implements ExceptionMapper<StorageConsentRequiredException> {

    @Override
    public Response toResponse(StorageConsentRequiredException e) {
        return Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "consent_required")).build();
    }
}
