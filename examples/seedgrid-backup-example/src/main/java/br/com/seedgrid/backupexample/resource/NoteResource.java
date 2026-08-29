package br.com.seedgrid.backupexample.resource;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.backupexample.dto.NoteDTO;
import br.com.seedgrid.backupexample.entity.Note;
import br.com.seedgrid.backupexample.service.NoteService;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Dominio de exemplo deste app: anotacoes pessoais do usuario logado. O ponto do exemplo nao e
 * este CRUD em si, mas usa-lo como "dado real" para demonstrar o fluxo do seedgrid-backup: o
 * usuario cria notas, depois pede um export (scope=DOMAIN) e baixa um ZIP com um notes.csv.
 */
@Path("/api/notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Notes", description = "Dominio de exemplo: anotacoes pessoais, exportadas via seedgrid-backup")
@SecurityRequirement(name = "bearerAuth")
public class NoteResource {

    @Inject
    NoteService service;

    @GET
    @PermissionsAllowed("NOTE_READ")
    @Operation(summary = "List notes")
    public List<NoteDTO> listAll() {
        return service.listAll().stream().map(NoteResource::toDTO).toList();
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed("NOTE_READ")
    @Operation(summary = "Get a note by id")
    public NoteDTO getById(@PathParam("id") Long id) {
        return toDTO(service.findById(id));
    }

    @POST
    @PermissionsAllowed("NOTE_CREATE")
    @Operation(summary = "Create a note")
    public Response create(@Valid NoteDTO dto) {
        NoteDTO created = toDTO(service.create(dto));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    private static NoteDTO toDTO(Note note) {
        return new NoteDTO(note.getId(), note.getTitle(), note.getContent());
    }
}
