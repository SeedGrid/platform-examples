package br.com.seedgrid.apikeysexample.resource;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.apikeysexample.dto.ReportDTO;
import br.com.seedgrid.apikeysexample.entity.Report;
import br.com.seedgrid.apikeysexample.service.ReportService;
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
 * Endpoint de exemplo protegido por permissao RBAC (nao por dono/company): tanto um usuario
 * humano autenticado via JWT quanto uma integracao autenticada via API key (header x-api-key)
 * acessam da MESMA forma, desde que a role por tras da identidade carregue a permissao exigida.
 * E o padrao recomendado pelo seedgrid-apikeys: a API key nao tem escopo proprio, ela herda as
 * permissions da role atribuida a ela.
 */
@Path("/api/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reports", description = "Exemplo de recurso acessivel por JWT humano OU API key (RBAC)")
@SecurityRequirement(name = "bearerAuth")
public class ReportResource {

    @Inject
    ReportService service;

    @GET
    @PermissionsAllowed("REPORT_READ")
    @Operation(summary = "List reports (JWT admin ou API key com role REPORT_READ)")
    public List<ReportDTO> listAll() {
        return service.listAll().stream().map(ReportResource::toDTO).toList();
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed("REPORT_READ")
    @Operation(summary = "Get a report by id")
    public ReportDTO getById(@PathParam("id") Long id) {
        return toDTO(service.findById(id));
    }

    @POST
    @PermissionsAllowed("REPORT_CREATE")
    @Operation(summary = "Create a report (tipicamente so o ADMIN humano tem essa permissao)")
    public Response create(@Valid ReportDTO dto) {
        ReportDTO created = toDTO(service.create(dto));
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    private static ReportDTO toDTO(Report report) {
        return new ReportDTO(report.getId(), report.getTitle(), report.getContent());
    }
}
