package br.com.seedgrid.billingexample.entity;

import java.time.Instant;

import br.com.seedgrid.commons.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Assinatura de uma {@link br.com.seedgrid.security.entities.Company} a um {@link Plan}. Uma
 * linha por company (unique em company_id) — o exemplo mantem 1 assinatura ativa por vez, criada
 * e mantida em dia pelo {@code SubscriptionBillingEventHandler} conforme os webhooks de billing
 * chegam. O seedgrid-billing-core so persiste eventos brutos (BillingEventEntity); Plan/Subscription
 * sao responsabilidade da app host, como documentado no SPI.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseEntity {

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Column(name = "provider_subscription_id", length = 255)
    private String providerSubscriptionId;

    @Column(name = "provider_customer_id", length = 255)
    private String providerCustomerId;

    @Column(name = "provider_checkout_session_id", length = 255)
    private String providerCheckoutSessionId;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    public void activate(Long planId, String providerSubscriptionId, String providerCustomerId,
            Instant currentPeriodEnd) {
        this.planId = planId;
        this.status = SubscriptionStatus.ACTIVE;
        this.providerSubscriptionId = providerSubscriptionId;
        this.providerCustomerId = providerCustomerId;
        this.currentPeriodEnd = currentPeriodEnd;
        this.canceledAt = null;
    }

    public void cancel(Instant canceledAt) {
        this.status = SubscriptionStatus.CANCELED;
        this.canceledAt = canceledAt;
    }
}
