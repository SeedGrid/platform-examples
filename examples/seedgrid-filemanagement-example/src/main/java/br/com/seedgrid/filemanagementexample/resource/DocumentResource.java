package br.com.seedgrid.filemanagementexample.resource;

import java.util.Base64;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.filemanagement.domains.FileMetadata;
import br.com.seedgrid.filemanagement.domains.StorageKey;
import br.com.seedgrid.filemanagement.spi.StorageException;
import br.com.seedgrid.filemanagement.spi.StorageNotFoundException;
import br.com.seedgrid.filemanagementexample.dto.DocumentMetadataResponse;
import br.com.seedgrid.filemanagementexample.dto.StoreDocumentRequest;
import br.com.seedgrid.filemanagementexample.dto.StoredDocumentResponse;
import br.com.seedgrid.filemanagementexample.service.DocumentStorageService;
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
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoints de exemplo do seedgrid-filemanagement: upload (store), download (retrieve), metadata e
 * delete de documentos, tudo delegado a SPI FileStorage via {@link DocumentStorageService}.
 *
 * <p>O {@code {key}} nas rotas e o path relativo devolvido pela StorageKey no POST (pode conter "/",
 * por isso o pattern {@code {key:.+}}).</p>
 */
@Path("/documents")
@Tag(name = "Documents", description = "Upload/download/metadata/delete via seedgrid-filemanagement (backend filesystem)")
public class DocumentResource {

    @Inject
    DocumentStorageService storageService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Armazena um arquivo (conteudo em base64) e devolve a StorageKey + metadata")
    public Response store(StoreDocumentRequest request) {
        if (request == null || request.filename() == null || request.filename().isBlank()) {
            throw new BadRequestException("filename e obrigatorio");
        }
        if (request.contentBase64() == null) {
            throw new BadRequestException("contentBase64 e obrigatorio");
        }

        byte[] content;
        try {
            content = Base64.getDecoder().decode(request.contentBase64());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("contentBase64 nao e um base64 valido");
        }

        try {
            StorageKey key = storageService.store(
                    request.filename(), content, request.prefix(), request.contentType());
            FileMetadata metadata = storageService.metadata(key);

            StoredDocumentResponse body = new StoredDocumentResponse(
                    key.getPath(),
                    key.toUri(),
                    metadata.getFilename(),
                    metadata.getSizeBytes(),
                    metadata.getContentType());

            return Response.status(Response.Status.CREATED).entity(body).build();
        } catch (StorageException e) {
            throw new ServerErrorException("Falha ao armazenar arquivo", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @GET
    @Path("/{key:.+}")
    @Operation(summary = "Baixa o conteudo original do arquivo (retrieve). 404 se nao existir")
    public Response download(@PathParam("key") String key) {
        StorageKey storageKey = storageService.keyOf(key);

        // Desde a lib 2026.07.08, retrieve()/getMetadata() de arquivo inexistente lancam
        // StorageNotFoundException (subtipo de StorageException): capturamos o subtipo e mapeamos
        // pra 404, de forma atomica - sem checar exists() antes (workaround da versao 2026.07.02).
        try {
            byte[] content = storageService.retrieve(storageKey);
            FileMetadata metadata = storageService.metadata(storageKey);
            String contentType = metadata.getContentType() != null
                    ? metadata.getContentType()
                    : MediaType.APPLICATION_OCTET_STREAM;
            return Response.ok(content, contentType)
                    .header("Content-Disposition", "attachment; filename=\"" + metadata.getFilename() + "\"")
                    .build();
        } catch (StorageNotFoundException e) {
            throw new NotFoundException("Documento nao encontrado: " + key, e);
        } catch (StorageException e) {
            throw new ServerErrorException("Falha ao ler arquivo", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @GET
    @Path("/_meta/{key:.+}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Metadata do arquivo (tamanho, contentType, ultima modificacao). 404 se nao existir")
    public DocumentMetadataResponse metadata(@PathParam("key") String key) {
        StorageKey storageKey = storageService.keyOf(key);
        try {
            FileMetadata m = storageService.metadata(storageKey);
            return new DocumentMetadataResponse(
                    storageKey.getPath(),
                    storageKey.toUri(),
                    m.getFilename(),
                    m.getSizeBytes(),
                    m.getContentType(),
                    m.getLastModifiedAt());
        } catch (StorageNotFoundException e) {
            throw new NotFoundException("Documento nao encontrado: " + key, e);
        } catch (StorageException e) {
            throw new ServerErrorException("Falha ao ler metadata", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }

    @DELETE
    @Path("/{key:.+}")
    @Operation(summary = "Remove o arquivo. 404 se nao existir; 204 se removido")
    public Response delete(@PathParam("key") String key) {
        StorageKey storageKey = storageService.keyOf(key);
        try {
            // Desde a lib 2026.07.08, delete() de arquivo inexistente lanca StorageNotFoundException
            // (ate 2026.07.02 era warn + no-op no FilesystemStorage) - o 404 sai direto do catch.
            storageService.delete(storageKey);
            return Response.noContent().build();
        } catch (StorageNotFoundException e) {
            throw new NotFoundException("Documento nao encontrado: " + key, e);
        } catch (StorageException e) {
            throw new ServerErrorException("Falha ao remover arquivo", Response.Status.INTERNAL_SERVER_ERROR, e);
        }
    }
}
