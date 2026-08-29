package br.com.seedgrid.billingexample.billing;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.seedgrid.billing.BillingProvider;
import br.com.seedgrid.billing.InvalidWebhookException;
import br.com.seedgrid.billing.UnsupportedEventException;
import br.com.seedgrid.billing.domain.BillingEvent;
import br.com.seedgrid.billing.domain.BillingEventType;
import br.com.seedgrid.billing.domain.BillingReference;
import br.com.seedgrid.billing.dtos.CheckoutRequest;
import br.com.seedgrid.billing.dtos.CheckoutResponse;
import br.com.seedgrid.billing.dtos.InvoiceSummary;
import br.com.seedgrid.billing.dtos.PortalRequest;
import br.com.seedgrid.billing.dtos.PortalResponse;
import br.com.seedgrid.billing.dtos.ProrationPreview;
import br.com.seedgrid.billing.dtos.ProviderSubscription;
import br.com.seedgrid.billing.dtos.RawWebhookRequest;
import br.com.seedgrid.billing.dtos.UpcomingInvoice;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementacao FAKE de {@link BillingProvider}, so para fins didaticos deste exemplo — nao fala
 * com nenhum gateway real. E obrigatoria: sem UM (e so um) bean {@code @ApplicationScoped}
 * implementando {@link BillingProvider}, o boot do Quarkus quebra por CDI (o seedgrid-billing-core
 * injeta {@code BillingProvider} direto no {@code BillingService}).
 *
 * <p>Checkout/portal geram URLs locais fake (nao navegaveis de verdade). O webhook usa um formato
 * de payload PROPRIO ({@link FakeWebhookPayload}), autenticado por uma assinatura HMAC-SHA256
 * simples (header {@value #SIGNATURE_HEADER}) — troque por {@code seedgrid-billing-stripe} (que
 * ja existe pronto) para verificacao real de assinatura Stripe em producao.</p>
 */
@ApplicationScoped
public class FakeBillingProvider implements BillingProvider {

    private static final Logger LOG = Logger.getLogger(FakeBillingProvider.class);
    private static final String PROVIDER_ID = "fake";
    public static final String SIGNATURE_HEADER = "x-fake-signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    @Inject
    public FakeBillingProvider(ObjectMapper objectMapper,
            @ConfigProperty(name = "seedgrid.billing.fake.webhook-secret") String webhookSecret) {
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public CheckoutResponse createCheckout(CheckoutRequest req) {
        String sessionId = "cs_fake_" + UUID.randomUUID();
        LOG.infof("[fake-billing] createCheckout plan=%s email=%s -> session=%s",
                req.planCode(), req.customerEmail(), sessionId);
        return new CheckoutResponse(PROVIDER_ID, "http://localhost/fake-checkout/" + sessionId, sessionId);
    }

    @Override
    public PortalResponse createPortal(PortalRequest req) {
        String portalId = "bps_fake_" + UUID.randomUUID();
        LOG.infof("[fake-billing] createPortal customerReference=%s -> portal=%s",
                req.customerReference(), portalId);
        return new PortalResponse(PROVIDER_ID, "http://localhost/fake-portal/" + portalId);
    }

    @Override
    public BillingEvent parseAndVerifyWebhook(RawWebhookRequest req)
            throws InvalidWebhookException, UnsupportedEventException {
        verifySignature(req);

        FakeWebhookPayload payload;
        try {
            payload = objectMapper.readValue(req.rawBody(), FakeWebhookPayload.class);
        } catch (Exception e) {
            throw new InvalidWebhookException("Payload de webhook fake invalido: " + e.getMessage());
        }

        BillingEventType type;
        try {
            type = BillingEventType.valueOf(payload.type());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnsupportedEventException("Tipo de evento nao suportado: " + payload.type());
        }

        Instant occurredAt = payload.occurredAtEpochSecond() != null
                ? Instant.ofEpochSecond(payload.occurredAtEpochSecond())
                : Instant.now();

        BillingReference reference = new BillingReference(
                payload.checkoutSessionId(),
                payload.subscriptionId(),
                null,
                null,
                payload.companyId() == null ? null : String.valueOf(payload.companyId()),
                payload.customerId());

        Map<String, String> metadata = payload.planCode() == null
                ? Map.of()
                : Map.of("planCode", payload.planCode());

        return new BillingEvent(payload.eventId(), PROVIDER_ID, type, occurredAt, reference, metadata, Map.of());
    }

    private void verifySignature(RawWebhookRequest req) throws InvalidWebhookException {
        String received = findHeaderIgnoreCase(req, SIGNATURE_HEADER);
        if (received == null || received.isBlank()) {
            throw new InvalidWebhookException("Header " + SIGNATURE_HEADER + " ausente");
        }

        String expected = hmacHex(req.rawBody());
        if (!expected.equalsIgnoreCase(received)) {
            throw new InvalidWebhookException("Assinatura do webhook fake invalida");
        }
    }

    // Headers HTTP chegam com o case original do cliente (o Map<String, List<String>> montado no
    // BillingWebhookController nao normaliza), entao a busca precisa ser case-insensitive.
    private String findHeaderIgnoreCase(RawWebhookRequest req, String headerName) {
        if (req.headers() == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : req.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)
                    && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private String hmacHex(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Falha ao calcular HMAC do webhook fake", e);
        }
    }

    // ===== Demais operacoes da SPI: no-op/stub, pois este exemplo demonstra so o fluxo de
    // checkout -> webhook -> subscription ativa/cancelada. Um provider real (ex.: Stripe)
    // implementaria de fato chamando a API do gateway. =====

    @Override
    public void changeSubscriptionPlan(String subscriptionId, String newPriceId, String newMeteredPriceId) {
        LOG.infof("[fake-billing] changeSubscriptionPlan (no-op) subscription=%s newPrice=%s",
                subscriptionId, newPriceId);
    }

    @Override
    public void scheduleSubscriptionPlanChangeAtPeriodEnd(
            String subscriptionId, String newPriceId, String newMeteredPriceId) {
        LOG.infof("[fake-billing] scheduleSubscriptionPlanChangeAtPeriodEnd (no-op) subscription=%s", subscriptionId);
    }

    @Override
    public void cancelScheduledPlanChange(String subscriptionId) {
        LOG.infof("[fake-billing] cancelScheduledPlanChange (no-op) subscription=%s", subscriptionId);
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        LOG.infof("[fake-billing] cancelSubscription (no-op) subscription=%s", subscriptionId);
    }

    @Override
    public void cancelSubscriptionImmediately(String subscriptionId) {
        LOG.infof("[fake-billing] cancelSubscriptionImmediately (no-op) subscription=%s", subscriptionId);
    }

    @Override
    public void reactivateSubscription(String subscriptionId) {
        LOG.infof("[fake-billing] reactivateSubscription (no-op) subscription=%s", subscriptionId);
    }

    @Override
    public void pauseSubscription(String subscriptionId) {
        LOG.infof("[fake-billing] pauseSubscription (no-op) subscription=%s", subscriptionId);
    }

    @Override
    public void resumeSubscription(String subscriptionId) {
        LOG.infof("[fake-billing] resumeSubscription (no-op) subscription=%s", subscriptionId);
    }

    @Override
    public List<InvoiceSummary> listInvoices(String customerId, int limit) {
        return List.of();
    }

    @Override
    public UpcomingInvoice getUpcomingInvoice(String customerId, String subscriptionId) {
        return null;
    }

    @Override
    public ProviderSubscription getSubscription(String subscriptionId) {
        return null;
    }

    @Override
    public List<ProviderSubscription> findActiveSubscriptionsByEmail(String email) {
        return List.of();
    }

    @Override
    public ProrationPreview previewPlanChange(String subscriptionId, String newPriceId, String newMeteredPriceId) {
        return null;
    }
}
