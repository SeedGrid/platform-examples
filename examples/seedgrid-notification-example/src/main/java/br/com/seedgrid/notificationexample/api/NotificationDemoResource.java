package br.com.seedgrid.notificationexample.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.seedgrid.notification.api.NotificationEvent;
import br.com.seedgrid.notification.api.NotificationPublisher;
import br.com.seedgrid.notification.config.Channel;
import br.com.seedgrid.notification.config.NotificationRule;
import br.com.seedgrid.notification.repositories.NotificationDeliveryRepository;
import br.com.seedgrid.notification.services.NotificationDispatcher;
import br.com.seedgrid.notification.services.NotificationRules;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Este host NAO usa seguranca (a extension e agnostica): a REST administrativa da notification
 * ({@code /admin/notifications}) nao existe sem {@code quarkus-security}, entao o proprio host conduz o fluxo chamando
 * os servicos da extension.
 */
@Path("/demo/notification")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationDemoResource {

    public record RuleRequest(List<String> emails) {
    }

    public record OrderRequest(String customer) {
    }

    private final NotificationRules rules;
    private final NotificationPublisher publisher;
    private final NotificationDispatcher dispatcher;
    private final NotificationDeliveryRepository deliveries;

    @ConfigProperty(name = "seedgrid.notification.scope")
    String scope;

    public NotificationDemoResource(NotificationRules rules, NotificationPublisher publisher,
            NotificationDispatcher dispatcher, NotificationDeliveryRepository deliveries) {
        this.rules = rules;
        this.publisher = publisher;
        this.dispatcher = dispatcher;
        this.deliveries = deliveries;
    }

    /** Liga o evento no canal e-mail para os destinatarios informados. */
    @PUT
    @Path("/rule")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveRule(RuleRequest body) {
        rules.save(OrderEvents.ORDER_CREATED,
                new NotificationRule(true, Set.of(Channel.EMAIL), body.emails(), List.of(), null));
        return Response.ok().build();
    }

    /** O host publica o evento; a extension decide quem recebe e enfileira as entregas. */
    @POST
    @Path("/orders")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createOrder(OrderRequest body) {
        publisher.publish(new NotificationEvent(UUID.randomUUID().toString(), OrderEvents.ORDER_CREATED,
                Map.of("customer", body.customer())));
        return Response.status(Response.Status.CREATED).build();
    }

    /** Roda o envio agora (em producao e' o agendamento da extension que faz isso). */
    @POST
    @Path("/dispatch")
    public Response dispatchNow() {
        dispatcher.drain();
        return Response.ok().build();
    }

    @GET
    @Path("/deliveries")
    public List<Map<String, Object>> listDeliveries() {
        return deliveries.pageByScope(scope, 0, 50).stream().map(d -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("recipient", d.getRecipient());
            out.put("channel", d.getChannel());
            out.put("subject", d.getSubject());
            out.put("status", d.getStatus());
            return out;
        }).toList();
    }
}
