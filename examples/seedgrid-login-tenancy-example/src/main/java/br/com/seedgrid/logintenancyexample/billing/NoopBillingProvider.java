package br.com.seedgrid.logintenancyexample.billing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.seedgrid.billing.spis.BillingProvider;
import br.com.seedgrid.billing.exceptions.InvalidWebhookException;
import br.com.seedgrid.billing.exceptions.UnsupportedEventException;
import br.com.seedgrid.billing.dtos.BillingEvent;
import br.com.seedgrid.billing.dtos.BillingEventType;
import br.com.seedgrid.billing.dtos.BillingReference;
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

/**
 * Stub de billing do exemplo login-tenancy (nenhum gateway real; troque por seedgrid-billing-stripe num projeto real): checkout/portal/webhook devolvem valores
 * fixos e as operacoes de ciclo de vida da assinatura sao no-op. Mantido alinhado a SPI
 * {@link BillingProvider}.
 */
@ApplicationScoped
public class NoopBillingProvider implements BillingProvider {

    @Override
    public String providerId() {
        return "noop";
    }

    @Override
    public CheckoutResponse createCheckout(CheckoutRequest request) {
        String sessionId = "sess_" + UUID.randomUUID();
        return new CheckoutResponse(providerId(), "http://localhost/test-checkout/" + sessionId, sessionId);
    }

    @Override
    public PortalResponse createPortal(PortalRequest request) {
        return new PortalResponse(providerId(), "http://localhost/test-portal/" + UUID.randomUUID());
    }

    @Override
    public BillingEvent parseAndVerifyWebhook(RawWebhookRequest request)
            throws InvalidWebhookException, UnsupportedEventException {
        return new BillingEvent(
                "evt_" + UUID.randomUUID(),
                providerId(),
                BillingEventType.CHECKOUT_COMPLETED,
                Instant.now(),
                new BillingReference(null, null, null, null, null, null),
                Map.of(),
                Map.of());
    }

    @Override
    public void changeSubscriptionPlan(String subscriptionId, String newPriceId, java.util.List<String> newMeteredPriceIds) {
        // no-op
    }

    @Override
    public void scheduleSubscriptionPlanChangeAtPeriodEnd(
            String subscriptionId, String newPriceId, java.util.List<String> newMeteredPriceIds) {
        // no-op
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        // no-op
    }

    @Override
    public void reactivateSubscription(String subscriptionId) {
        // no-op
    }

    @Override
    public void pauseSubscription(String subscriptionId) {
        // no-op
    }

    @Override
    public void resumeSubscription(String subscriptionId) {
        // no-op
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
    public ProrationPreview previewPlanChange(String subscriptionId, String newPriceId, java.util.List<String> newMeteredPriceIds) {
        return null;
    }

    @Override
    public List<ProviderSubscription> findActiveSubscriptionsByEmail(String email) {
        return List.of();
    }

    @Override
    public void cancelScheduledPlanChange(String subscriptionId) {
        // no-op
    }

    @Override
    public void cancelSubscriptionImmediately(String subscriptionId) {
        // no-op
    }
}
