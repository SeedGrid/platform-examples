package br.com.seedgrid.filemanagementexample.attachment;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.filemanagement.domains.Attachment;
import br.com.seedgrid.filemanagement.spis.StorageException;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Arquivos com anexo: o cliente so' conhece o ID do anexo, nunca o caminho do arquivo no storage. */
@Path("/attachments")
@Tag(name = "Attachments", description = "Gravar, ler e apagar arquivos pelo ID do anexo (sg_attachments)")
public class AttachmentResource {

    public record StoreRequest(String ownerKey, String storage, String filename, String contentType,
                               String contentBase64) {
    }

    @Inject
    AttachmentService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Grava o arquivo (storage = filesystem ou dropbox em modo consentimento) e devolve o anexo")
    public Response store(StoreRequest r) throws StorageException {
        if (r == null || r.filename() == null || r.filename().isBlank() || r.contentBase64() == null) {
            throw new BadRequestException("filename e contentBase64 sao obrigatorios");
        }
        String storage = r.storage() == null || r.storage().isBlank() ? "filesystem" : r.storage();
        byte[] content;
        try {
            content = Base64.getDecoder().decode(r.contentBase64());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("contentBase64 invalido");
        }
        Attachment a = service.store(storage, r.ownerKey(), r.filename(), r.contentType(), content);
        return Response.status(Response.Status.CREATED).entity(view(a)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lista os anexos de um dono")
    public List<Map<String, Object>> list(@QueryParam("ownerKey") String ownerKey) {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new BadRequestException("ownerKey e obrigatorio");
        }
        return service.byOwner(ownerKey).stream().map(AttachmentResource::view).toList();
    }

    @GET
    @Path("/{id}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Le o conteudo pelo ID do anexo")
    public byte[] content(@PathParam("id") String id) throws StorageException {
        return service.read(require(id));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Apaga o arquivo e a linha do anexo")
    public Response delete(@PathParam("id") String id) throws StorageException {
        service.delete(require(id));
        return Response.noContent().build();
    }

    private Attachment require(String id) {
        Attachment a = service.find(id);
        if (a == null) {
            throw new NotFoundException("anexo nao encontrado");
        }
        return a;
    }

    private static Map<String, Object> view(Attachment a) {
        return Map.of("id", a.getPublicId(), "storage", a.getStorageType(), "filename", a.getFileName(),
                "sizeBytes", a.getSizeBytes(), "ownerKey", a.getOwnerKey() == null ? "" : a.getOwnerKey());
    }
}
