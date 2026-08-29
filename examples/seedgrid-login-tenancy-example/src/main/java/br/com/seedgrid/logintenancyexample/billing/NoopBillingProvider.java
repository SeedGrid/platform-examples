package br.com.seedgrid.logintenancyexample.billing;

import java.util.List;

import br.com.seedgrid.billing.BillingProvider;
import br.com.seedgrid.billing.domain.BillingEvent;
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
 * O seedgrid-billing-core e provider-agnostico e exige que a aplicacao host
 * forneca um BillingProvider via CDI. Este exemplo nao integra nenhum gateway
 * de pagamento de verdade — troque por seedgrid-billing-stripe (ou outro
 * provider) num projeto real.
 */
@ApplicationScoped
public class NoopBillingProvider implements BillingProvider {

    @Override
    public String providerId() {
        return "noop";
    }

    @Override
    public CheckoutResponse createCheckout(CheckoutRequest request) {
        return null;
    }

    @Override
    public PortalResponse createPortal(PortalRequest request) {
        return null;
    }

    @Override
    public BillingEvent parseAndVerifyWebhook(RawWebhookRequest request) {
        return null;
    }

    @Override
    public void changeSubscriptionPlan(String subscriptionId, String newPriceId, String newMeteredPriceId) {
        // no-op
    }

    @Override
    public void scheduleSubscriptionPlanChangeAtPeriodEnd(
            String subscriptionId, String newPriceId, String newMeteredPriceId) {
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
    public ProrationPreview previewPlanChange(String subscriptionId, String newPriceId, String newMeteredPriceId) {
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
