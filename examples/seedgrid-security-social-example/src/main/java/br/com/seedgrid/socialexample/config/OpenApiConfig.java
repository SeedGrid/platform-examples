package br.com.seedgrid.socialexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid Social Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-security-social: login social (Google, Facebook, Apple) sobre a seedgrid-security.

            Sobe com a lista de provedores VAZIA (modulo inerte): nenhuma credencial real no exemplo. Veja o README para ligar.

            Fluxo:
            1. GET /public/auth/social/providers - lista os provedores ligados (vazia aqui).
            2. GET /public/auth/social/google/login - 404 SOCIAL_PROVIDER_DISABLED enquanto o provedor nao for ligado.
            3. POST /public/auth/login (admin, bootstrap do security) - o login por senha segue valendo.
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
