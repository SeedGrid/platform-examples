package br.com.seedgrid.whatsappexample.resource;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.whatsappexample.dto.NotificationRequestDTO;
import br.com.seedgrid.whatsappexample.dto.NotificationResponseDTO;
import br.com.seedgrid.whatsappexample.entity.Notification;
import br.com.seedgrid.whatsappexample.entity.NotificationStatus;
import br.com.seedgrid.whatsappexample.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint de exemplo: envia uma notificacao de texto via WhatsApp (seedgrid-whatsapp,
 * provider Evolution) e guarda o resultado localmente. Nao ha autenticacao neste exemplo —
 * a extension whatsapp e' standalone e nao exige seedgrid-security.
 */
@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Notifications", description = "Envio de notificacoes via seedgrid-whatsapp")
public class NotificationResource {

    @Inject
    NotificationService service;

    @POST
    @Operation(summary = "Envia uma notificacao de texto via WhatsApp e registra o resultado")
    public Response send(@Valid NotificationRequestDTO dto) {
        Notification notification = service.send(dto.phoneNumber(), dto.message());
        Response.Status status = notification.getStatus() == NotificationStatus.SENT
                ? Response.Status.CREATED
                : Response.Status.BAD_GATEWAY;
        return Response.status(status).entity(toDTO(notification)).build();
    }

    @GET
    @Operation(summary = "Lista o historico de notificacoes enviadas (sucesso e falha)")
    public List<NotificationResponseDTO> listAll() {
        return service.listAll().stream().map(NotificationResource::toDTO).toList();
    }

    private static NotificationResponseDTO toDTO(Notification notification) {
        return new NotificationResponseDTO(
                notification.getId(),
                notification.getPhoneNumber(),
                notification.getMessage(),
                notification.getChannelId(),
                notification.getStatus(),
                notification.getProviderMessageId(),
                notification.getErrorMessage(),
                notification.getCreatedAt());
    }
}
