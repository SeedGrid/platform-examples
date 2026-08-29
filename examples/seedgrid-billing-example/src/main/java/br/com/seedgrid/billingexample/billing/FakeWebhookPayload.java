package br.com.seedgrid.billingexample.billing;

/**
 * Formato SINTETICO (definido por este exemplo, nao pelo Stripe de verdade) do corpo do webhook
 * que o {@link FakeBillingProvider} sabe decodificar. Quem simula o "pagamento confirmado" no
 * fluxo didatico monta este JSON a mao e chama {@code POST /public/billing/webhook} com ele.
 *
 * @param eventId               id unico do evento (usado para idempotencia em billing_events)
 * @param type                  nome exato de um {@link br.com.seedgrid.billing.domain.BillingEventType}
 *                              (ex.: "CHECKOUT_COMPLETED", "SUBSCRIPTION_CREATED",
 *                              "SUBSCRIPTION_CANCELED")
 * @param companyId             id (numerico) da company da app host dona da assinatura
 * @param planCode              codigo do {@link br.com.seedgrid.billingexample.entity.Plan} (ex.: "PRO")
 * @param subscriptionId        id fake da assinatura no "provider" (ex.: "sub_abc123")
 * @param customerId            id fake do cliente no "provider" (ex.: "cus_abc123")
 * @param checkoutSessionId     (opcional) id da sessao de checkout que originou o evento
 * @param occurredAtEpochSecond (opcional) instante do evento em epoch segundos; default = agora
 */
public record FakeWebhookPayload(
        String eventId,
        String type,
        Long companyId,
        String planCode,
        String subscriptionId,
        String customerId,
        String checkoutSessionId,
        Long occurredAtEpochSecond) {
}
