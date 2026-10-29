package br.com.seedgrid.businessdaysexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid Business Days Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-business-days: feriados, perfil da semana e calculo de dias uteis.

            Fluxo:
            1. POST /public/auth/login (admin, bootstrap do security) - obtem um JWT.
            2. GET /business-days/check?date=2026-04-21 - Tiradentes: nao e dia util (feriado nacional semeado).
            3. POST /business-days/holidays {"name": "Aniversario da cidade", "month": 5, "day": 12} - feriado da empresa.
            4. GET /business-days/count?from=2026-04-20&to=2026-04-24 - conta dias uteis no intervalo.
            5. GET /business-days/add?from=2026-04-20&days=2 - anda 2 dias uteis a partir da data.
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
