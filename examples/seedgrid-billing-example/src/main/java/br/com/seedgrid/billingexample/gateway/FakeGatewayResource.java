package br.com.seedgrid.billingexample.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.seedgrid.billing.dtos.BillingEvent;
import br.com.seedgrid.billing.dtos.BillingEventType;
import br.com.seedgrid.billing.dtos.BillingReference;
import br.com.seedgrid.billing.dtos.CheckoutRequest;
import br.com.seedgrid.billing.dtos.RawWebhookRequest;
import br.com.seedgrid.billing.exceptions.InvalidWebhookException;
import br.com.seedgrid.billing.exceptions.UnsupportedEventException;
import br.com.seedgrid.billing.services.BillingEventDispatcher;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * A "pagina de pagamento" do gateway fake. Abrir a URL que o checkout devolveu confirma o pagamento: o
 * gateway monta o {@code CHECKOUT_COMPLETED}, assina e entrega ao mesmo processamento do webhook publico
 * (o que o Stripe faria chamando {@code POST /public/billing/webhook}). Nao existe em producao.
 */
@Path(FakeGatewayResource.PATH)
@Produces(MediaType.APPLICATION_JSON)
public class FakeGatewayResource {

    public static final String PATH = "/public/fake-gateway";

    private final FakeBillingProvider provider;
    private final BillingEventDispatcher webhook;
    private final ObjectMapper objectMapper;

    public FakeGatewayResource(FakeBillingProvider provider, BillingEventDispatcher webhook, ObjectMapper objectMapper) {
        this.provider = provider;
        this.webhook = webhook;
        this.objectMapper = objectMapper;
    }

    @GET
    @Path("/checkout/{sessionId}")
    public Map<String, String> pay(@PathParam("sessionId") String sessionId)
            throws JsonProcessingException, InvalidWebhookException, UnsupportedEventException {
        CheckoutRequest checkout = provider.checkout(sessionId)
                .orElseThrow(() -> new NotFoundException("Checkout nao encontrado: " + sessionId));
        String subscriptionId = provider.nextId("sub");
        BillingEvent event = new BillingEvent(
                provider.nextId("evt"),
                FakeBillingProvider.PROVIDER_ID,
                BillingEventType.CHECKOUT_COMPLETED,
                Instant.now(),
                new BillingReference(sessionId, subscriptionId, provider.nextId("in"), null, null,
                        provider.nextId("cus")),
                checkout.metadata(),
                Map.of("amountPaid", "0"));
        byte[] body = objectMapper.writeValueAsBytes(event);
        webhook.processWebhook(new RawWebhookRequest(body,
                Map.of(FakeBillingProvider.SIGNATURE_HEADER, List.of(provider.sign(body)),
                        "content-type", List.of(MediaType.APPLICATION_JSON)),
                "/public/billing/webhook", "POST"));
        return Map.of("status", "PAID", "subscriptionId", subscriptionId,
                "webhookBody", new String(body, StandardCharsets.UTF_8));
    }

    @GET
    @Path("/portal")
    public Map<String, String> portal() {
        return Map.of("status", "Portal do gateway fake: nada a gerenciar aqui.");
    }
}
