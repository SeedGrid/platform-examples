package br.com.seedgrid.usernotificationsexample.api;

import java.util.Map;

import br.com.seedgrid.commons.persistence.spis.CurrentUserProvider;
import br.com.seedgrid.usernotifications.services.UserNotifier;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Quem notifica e o host (ou outra extension): injeta o {@link UserNotifier} e chama notify. A leitura, o marcar como lida
 * e o excluir ja vem da extension em {@code /user-notifications}.
 */
@Path("/demo/notify")
@Produces(MediaType.APPLICATION_JSON)
public class NotifyDemoResource {

    private final UserNotifier notifier;
    private final CurrentUserProvider currentUser;

    public NotifyDemoResource(UserNotifier notifier, CurrentUserProvider currentUser) {
        this.notifier = notifier;
        this.currentUser = currentUser;
    }

    /** Cria uma notificacao para o usuario corrente; o payload leva so o identificador de um relatorio ficticio. */
    @POST
    public Response send(@QueryParam("title") String title) {
        String publicId = notifier.notify(currentUser.getCurrentUserPublicId(), "demo.hello",
                title == null || title.isBlank() ? "Ola da user-notifications" : title, "Notificacao de exemplo",
                Map.of("reportId", "demo-1"));
        return Response.status(Response.Status.CREATED).entity(Map.of("publicId", publicId)).build();
    }
}
