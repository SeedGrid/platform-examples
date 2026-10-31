package br.com.seedgrid.realtimeexample.notice;

import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import br.com.seedgrid.commons.events.SgUserEvent;
import br.com.seedgrid.commons.persistence.spis.CurrentUserProvider;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Um "processo de negocio" de mentira: ao terminar, avisa o usuario logado disparando o evento neutro
 * {@link SgUserEvent}. O processo NAO conhece o realtime nem o Pulse; quem escuta o evento e publica e' a extension.
 */
@Path("/notices")
@Tag(name = "Notices", description = "Dispara um aviso para o usuario logado")
public class NoticeResource {

    @Inject
    Event<SgUserEvent> userEvents;

    @Inject
    CurrentUserProvider currentUser;

    /** Avisa o usuario da SESSAO (nunca um usuario vindo do request) que o "relatorio" terminou. */
    @POST
    @Path("/report-finished")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    public Response reportFinished() {
        String user = currentUser.getCurrentUserPublicId();
        // sai depois do commit (AFTER_SUCCESS); sem transacao em curso, o CDI entrega na hora
        userEvents.fire(new SgUserEvent(user, "report", "report.finished.success",
                Map.of("reportPublicId", "rel-exemplo-1")));
        return Response.accepted(Map.of("notified", user)).build();
    }
}
