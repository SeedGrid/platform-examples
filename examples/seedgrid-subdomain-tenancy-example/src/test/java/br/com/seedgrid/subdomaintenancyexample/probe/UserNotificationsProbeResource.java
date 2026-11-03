package br.com.seedgrid.subdomaintenancyexample.probe;

import br.com.seedgrid.usernotifications.services.UserNotifier;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Usado pelo guardiao UserNotificationsTenantGuardianIT (plano 130): notifica com o tenant da requisicao ativo. */
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
            String id = notifier.notify("cccccccc-cccc-cccc-cccc-cccccccccccc", "probe.tenant", "Aviso do probe", null,
                    null);
            return Response.ok(id).build();
        } catch (Throwable t) {
            return Response.serverError().entity("FAIL " + t.getClass().getName() + ": " + t.getMessage()).build();
        }
    }
}
