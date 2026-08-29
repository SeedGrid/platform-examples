package br.com.seedgrid.logintenancyexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * IMPORTANTE: isto e apenas documentacao do Swagger; a validacao real do JWT
 * e feita pelo seedgrid-security.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid Login-Tenancy Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o modelo login-tenancy (isolamento logico por company,
            base/schema unicos), combinando seedgrid-login-tenancy + seedgrid-security +
            seedgrid-audit.

            Fluxo:
            1. POST /public/signup — cria a solicitacao de cadastro (empresa + usuario root).
            2. POST /public/signup/confirm-email — confirma o e-mail e cria Company + BaseUser ROOT.
            3. POST /public/auth/login — autentica e retorna o JWT (claim companyId).
            4. Use o token nos endpoints /api/products — cada company so ve seus proprios registros.
            """
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = """
        JWT Bearer Token

        Para obter o token:
        1. POST /public/auth/login com { "login": "email@example.com", "password": "senha" }
        2. Copie o token da resposta
        3. Clique em "Authorize" acima e cole o token
        """
)
public class OpenApiConfig extends Application {
}
