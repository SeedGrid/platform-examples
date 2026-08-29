package br.com.seedgrid.apikeysexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid API Keys Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-apikeys: autenticacao machine-to-machine via API key
            (header x-api-key), sem depender de um usuario humano logado.

            Fluxo:
            1. POST /public/auth/login (admin, bootstrap do security) — obtem um JWT humano.
            2. POST /apikeys/role/REPORT_READER {"name": "job-noturno", "expiresDays": 90} — cria
               uma API key atrelada a role REPORT_READER (permissao REPORT_READ apenas).
            3. GET /api/reports com header x-api-key: <chave retornada> — acessa como a integracao,
               sem JWT nenhum.
            4. POST /api/reports com a MESMA api key falha (403): a role REPORT_READER nao tem
               REPORT_CREATE.
            """
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT Bearer Token (uso humano) OU header x-api-key (uso M2M) — ambos aceitos pelos endpoints protegidos por @PermissionsAllowed."
)
public class OpenApiConfig extends Application {
}
