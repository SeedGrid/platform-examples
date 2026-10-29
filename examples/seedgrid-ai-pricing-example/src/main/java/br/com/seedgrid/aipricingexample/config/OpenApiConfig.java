package br.com.seedgrid.aipricingexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid AI Pricing Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-ai-pricing: catalogo de modelos de IA, historico de precos e robo
            de deteccao com aprovacao humana.

            Fluxo:
            1. POST /public/auth/login (admin, bootstrap do security) - obtem um JWT.
            2. POST /admin/ai/models {"provider": "acme", "model": "acme-small"} - cadastra o modelo.
            3. PUT /admin/ai/price-approvals/config {"sourceUrl": "http://localhost:8094/example/pricing-page"} -
               aponta o robo para a pagina de precos do proprio exemplo.
            4. POST /admin/ai/price-approvals/check - o robo le a pagina e propoe o preco novo (CHANGES).
            5. POST /admin/ai/price-approvals/{id}/approve - uma pessoa aprova e o preco passa a valer.
            """
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT Bearer Token obtido em /public/auth/login."
)
public class OpenApiConfig extends Application {
}
