package br.com.seedgrid.billingexample.gateway;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.seedgrid.billing.dtos.BillingEvent;
import br.com.seedgrid.billing.dtos.CheckoutRequest;
import br.com.seedgrid.billing.dtos.CheckoutResponse;
import br.com.seedgrid.billing.dtos.CreateMeterRequest;
import br.com.seedgrid.billing.dtos.CreateMeteredPriceRequest;
import br.com.seedgrid.billing.dtos.CreatePriceRequest;
import br.com.seedgrid.billing.dtos.CreateProductRequest;
import br.com.seedgrid.billing.dtos.InvoiceSummary;
import br.com.seedgrid.billing.dtos.MeterInfo;
import br.com.seedgrid.billing.dtos.PortalRequest;
import br.com.seedgrid.billing.dtos.PortalResponse;
import br.com.seedgrid.billing.dtos.PriceInfo;
import br.com.seedgrid.billing.dtos.ProductInfo;
import br.com.seedgrid.billing.dtos.ProrationPreview;
import br.com.seedgrid.billing.dtos.ProviderSubscription;
import br.com.seedgrid.billing.dtos.RawWebhookRequest;
import br.com.seedgrid.billing.dtos.UpcomingInvoice;
import br.com.seedgrid.billing.dtos.UsageEventRequest;
import br.com.seedgrid.billing.exceptions.InvalidWebhookException;
import br.com.seedgrid.billing.exceptions.UnsupportedEventException;
import br.com.seedgrid.billing.spis.BillingCatalogProvider;
import br.com.seedgrid.billing.spis.BillingProvider;
import br.com.seedgrid.billing.spis.BillingUsageProvider;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Gateway de pagamento FAKE, para rodar o exemplo sem Stripe. Em producao use o
 * {@code seedgrid-billing-stripe}, que implementa estas mesmas tres interfaces: o host nao escreve nada.
 *
 * <p>Checkout, precos e medidor sao so' contadores em memoria. O "pagamento" acontece em
 * {@link FakeGatewayResource}: abrir a URL do checkout gera o evento {@code CHECKOUT_COMPLETED}, assinado
 * com HMAC-SHA256 no header {@value #SIGNATURE_HEADER}, e o entrega ao MESMO processamento do webhook
 * publico ({@code POST /public/billing/webhook}), que verifica a assinatura aqui.</p>
 */
@ApplicationScoped
public class FakeBillingProvider implements BillingProvider, BillingCatalogProvider, BillingUsageProvider {

    public static final String PROVIDER_ID = "fake";
    public static final String SIGNATURE_HEADER = "x-fake-signature";

    private final ObjectMapper objectMapper;
    private final String webhookSecret;
    private final String publicBaseUrl;
    private final AtomicInteger sequence = new AtomicInteger();
    private final Map<String, CheckoutRequest> checkouts = new ConcurrentHashMap<>();

    public FakeBillingProvider(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "seedgrid.billing.fake.webhook-secret") String webhookSecret,
            @ConfigProperty(name = "seedgrid.billing.fake.public-base-url") String publicBaseUrl) {
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
        this.publicBaseUrl = publicBaseUrl;
    }

    /** O checkout que a extension montou (plano, conta e term no metadata), para o gateway fake cobrar. */
    public Optional<CheckoutRequest> checkout(String sessionId) {
        return Optional.ofNullable(checkouts.get(sessionId));
    }

    /** Assinatura HMAC-SHA256 (hex) do corpo: o que um gateway real manda junto com o webhook. */
    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponivel", e);
        }
    }

    public String nextId(String prefix) {
        return prefix + "_fake_" + sequence.incrementAndGet();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public CheckoutResponse createCheckout(CheckoutRequest request) {
        String sessionId = nextId("cs");
        checkouts.put(sessionId, request);
        return new CheckoutResponse(PROVIDER_ID, publicBaseUrl + FakeGatewayResource.PATH + "/checkout/" + sessionId,
                sessionId);
    }

    @Override
    public PortalResponse createPortal(PortalRequest request) {
        return new PortalResponse(PROVIDER_ID, publicBaseUrl + FakeGatewayResource.PATH + "/portal");
    }

    @Override
    public BillingEvent parseAndVerifyWebhook(RawWebhookRequest request)
            throws InvalidWebhookException, UnsupportedEventException {
        String signature = request.headers().entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(SIGNATURE_HEADER))
                .flatMap(header -> header.getValue().stream())
                .findFirst()
                .orElseThrow(() -> new InvalidWebhookException("assinatura ausente"));
        byte[] expected = sign(request.rawBody()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookException("assinatura invalida");
        }
        try {
            BillingEvent event = objectMapper.readValue(request.rawBody(), BillingEvent.class);
            if (event.type() == null) {
                throw new UnsupportedEventException("tipo de evento nao suportado");
            }
            return event;
        } catch (java.io.IOException e) {
            throw new InvalidWebhookException("payload invalido", e);
        }
    }

    // ---- assinatura no gateway: o fake so' aceita (nao ha cobranca de verdade para mexer) ----

    @Override
    public void changeSubscriptionPlan(String subscriptionId, String newPriceId, java.util.List<String> newMeteredPriceIds) {
    }

    @Override
    public void scheduleSubscriptionPlanChangeAtPeriodEnd(String subscriptionId, String newPriceId,
            java.util.List<String> newMeteredPriceIds) {
    }

    @Override
    public void cancelScheduledPlanChange(String subscriptionId) {
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
    }

    @Override
    public void cancelSubscriptionImmediately(String subscriptionId) {
    }

    @Override
    public void reactivateSubscription(String subscriptionId) {
    }

    @Override
    public void pauseSubscription(String subscriptionId) {
    }

    @Override
    public void resumeSubscription(String subscriptionId) {
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
    public ProrationPreview previewPlanChange(String subscriptionId, String newPriceId, java.util.List<String> newMeteredPriceIds) {
        return new ProrationPreview(0L, 0L, "brl", 0L);
    }

    // ---- catalogo: publicar um term cria "precos" no gateway ----

    @Override
    public String createProduct(CreateProductRequest request) {
        return nextId("prod");
    }

    @Override
    public String ensureProduct(String productId, String name) {
        return productId;
    }

    @Override
    public ProductInfo getProduct(String productId) {
        return new ProductInfo(productId, productId, true);
    }

    @Override
    public String createPrice(CreatePriceRequest request) {
        return nextId("price");
    }

    @Override
    public void archivePrice(String priceId) {
    }

    @Override
    public void setPriceActive(String priceId, boolean active) {
    }

    @Override
    public PriceInfo getPrice(String priceId) {
        return null;
    }

    // ---- medidor do excedente ----

    @Override
    public String createMeter(CreateMeterRequest request) {
        return "meter_" + request.eventName();
    }

    @Override
    public void deactivateMeter(String meterId) {
    }

    @Override
    public MeterInfo getMeter(String meterId) {
        return null;
    }

    @Override
    public MeterInfo findMeterByEventName(String eventName) {
        return null;
    }

    @Override
    public String createMeteredPrice(CreateMeteredPriceRequest request) {
        return nextId("mprice");
    }

    @Override
    public void reportUsage(UsageEventRequest request) {
    }
}
