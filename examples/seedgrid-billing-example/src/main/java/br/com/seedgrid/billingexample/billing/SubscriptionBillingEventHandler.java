package br.com.seedgrid.billingexample.billing;

import java.time.Duration;
import java.time.Instant;

import org.jboss.logging.Logger;

import br.com.seedgrid.billing.entities.PlanEntity;
import br.com.seedgrid.billing.BillingEventHandler;
import br.com.seedgrid.billing.domain.BillingEvent;
import br.com.seedgrid.billing.domain.BillingReference;
import br.com.seedgrid.billingexample.entity.Subscription;
import br.com.seedgrid.billingexample.repository.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Bean CDI chamado pelo {@code BillingEventDispatcher} (seedgrid-billing-core) apos verificar a
 * assinatura do webhook e checar idempotencia. Recebe o {@link BillingEvent} JA normalizado e
 * decide o que fazer no dominio da app host: criar/ativar ou cancelar a {@link Subscription} da
 * {@link br.com.seedgrid.security.entities.Company} referenciada.
 *
 * <p>Roda dentro da MESMA transacao do dispatcher ({@code BillingEventDispatcher.processWebhook}
 * e {@code @Transactional}) — se lancar excecao, o evento fica marcado como
 * {@code processed=false} com o erro em {@code processingError} (para retry/investigacao), sem
 * perder o registro do evento bruto.</p>
 */
@ApplicationScoped
public class SubscriptionBillingEventHandler implements BillingEventHandler {

    private static final Logger LOG = Logger.getLogger(SubscriptionBillingEventHandler.class);

    // Ciclo fake de 30 dias, so para o exemplo ter uma data de "current_period_end" plausivel.
    private static final Duration FAKE_PERIOD = Duration.ofDays(30);

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional
    public void handle(BillingEvent event) {
        switch (event.type()) {
            case CHECKOUT_COMPLETED, SUBSCRIPTION_CREATED -> activateSubscription(event);
            case SUBSCRIPTION_CANCELED -> cancelSubscription(event);
            default -> LOG.infof("[billing-example] Evento %s ignorado (fora do escopo deste exemplo)",
                    event.type());
        }
    }

    private void activateSubscription(BillingEvent event) {
        BillingReference reference = event.reference();
        Long companyId = parseCompanyId(reference);
        String planCode = event.metadata() == null ? null : event.metadata().get("planCode");

        if (companyId == null || planCode == null) {
            throw new IllegalArgumentException(
                    "Evento de billing sem companyId (reference.externalReference) ou planCode (metadata)");
        }

        // Desde o billing 2026.09.10 o plano e' do core (PlanEntity, Panache active record); este
        // exemplo nao tem mais tabela nem repositorio proprios de plano.
        PlanEntity plan = PlanEntity.<PlanEntity>find("code", planCode).firstResultOptional()
                .orElseThrow(() -> new IllegalArgumentException("Plano desconhecido: " + planCode));

        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseGet(() -> {
                    Subscription created = new Subscription();
                    created.setCompanyId(companyId);
                    return created;
                });

        subscription.activate(
                plan.getId(),
                reference.subscriptionId(),
                reference.customerId(),
                event.occurredAt().plus(FAKE_PERIOD));

        if (subscription.getId() == null) {
            subscriptionRepository.persist(subscription);
        }

        LOG.infof("[billing-example] Subscription ativada: company=%d plan=%s providerSubscription=%s",
                companyId, planCode, reference.subscriptionId());
    }

    private void cancelSubscription(BillingEvent event) {
        String providerSubscriptionId = event.reference() == null ? null : event.reference().subscriptionId();
        if (providerSubscriptionId == null) {
            throw new IllegalArgumentException("Evento SUBSCRIPTION_CANCELED sem subscriptionId de referencia");
        }

        subscriptionRepository.findByProviderSubscriptionId(providerSubscriptionId)
                .ifPresentOrElse(
                        subscription -> {
                            Instant occurredAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
                            subscription.cancel(occurredAt);
                            LOG.infof("[billing-example] Subscription cancelada: providerSubscription=%s",
                                    providerSubscriptionId);
                        },
                        () -> LOG.warnf(
                                "[billing-example] SUBSCRIPTION_CANCELED para providerSubscription=%s "
                                        + "sem assinatura local correspondente (ignorado)",
                                providerSubscriptionId));
    }

    private Long parseCompanyId(BillingReference reference) {
        if (reference == null || reference.externalReference() == null) {
            return null;
        }
        try {
            return Long.valueOf(reference.externalReference());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
