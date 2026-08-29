package br.com.seedgrid.billingexample.entity;

/**
 * Status de negocio da {@link Subscription} da app host (independente do status bruto do
 * provider, que fica em {@code ProviderSubscription.status()} quando reconciliando).
 */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    CANCELED
}
