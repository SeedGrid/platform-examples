package br.com.seedgrid.logintenancyexample.probe;

import br.com.seedgrid.usernotifications.services.UserNotifier;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Recurso SO DE TESTE (plano 130): notifica com a empresa do token da requisicao como conta. */
@Path("/probe/user-notifications")
@Produces(MediaType.TEXT_PLAIN)
@Authenticated
@Blocking
public class UserNotificationsProbeResource {

    @Inject
    UserNotifier notifier;

    @POST
    @Path("/notify")
    public Response notifyUser() {
        try {
            return Response.ok(notifier.notify("cccccccc-cccc-cccc-cccc-cccccccccccc", "probe.company",
                    "Aviso do probe", null, null)).build();
        } catch (Throwable t) {
            return Response.serverError().entity("FAIL " + t.getClass().getName() + ": " + t.getMessage()).build();
        }
    }
}
