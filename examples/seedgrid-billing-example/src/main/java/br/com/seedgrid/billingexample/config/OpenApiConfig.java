package br.com.seedgrid.billingexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid Billing Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-billing-core: checkout, webhook e assinatura por
            company, com um FakeBillingProvider (sem Stripe de verdade).

            Fluxo:
            1. POST /public/auth/login (admin, bootstrap do security) — obtem um JWT humano.
            2. POST /billing/checkout {"planCode": "PRO", "customerEmail": "..."} — cria uma
               sessao de checkout fake (URL nao navegavel de verdade).
            3. POST /public/billing/webhook (header x-fake-signature) com um payload sintetico
               tipo CHECKOUT_COMPLETED/SUBSCRIPTION_CREATED — simula o "pagamento confirmado".
            4. GET /api/subscriptions/me — mostra a assinatura ativa da company do usuario logado.
            5. POST /public/billing/webhook com um payload SUBSCRIPTION_CANCELED — a assinatura
               fica CANCELED.
            """
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT Bearer Token do seedgrid-security."
)
public class OpenApiConfig extends Application {
}
