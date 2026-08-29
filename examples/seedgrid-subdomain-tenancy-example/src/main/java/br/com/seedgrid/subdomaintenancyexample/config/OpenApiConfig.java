package br.com.seedgrid.subdomaintenancyexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * Configuração do OpenAPI/Swagger com autenticação JWT Bearer.
 * 
 * Esta classe configura o Swagger UI para incluir automaticamente
 * o token JWT no header Authorization de todas as requisições.
 * 
 * IMPORTANTE: Isso é APENAS para documentação do Swagger!
 * A validação real é feita pelo JwtAuthenticationFilter do seedgrid-security.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid Test API",
        version = "1.0.0",
        description = """
            API para testar seedgrid-subdomain-tenancy, seedgrid-security e seedgrid-audit

            ⚠️ IMPORTANTE - Autenticação Multitenancy:

            Para obter um token válido, você DEVE fazer login usando o domínio completo do tenant:

            ✅ CORRETO:   http://lckconsultoria.kb.seedgrid.com.br:8080/public/auth/login
            ❌ INCORRETO: http://kb.seedgrid.com.br:8080/public/auth/login

            Tokens gerados no domínio master (sem tenant) NÃO funcionam em endpoints de tenant!

            Motivo: O token JWT inclui o claim 'subdomain' apenas quando gerado no domínio do tenant.
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
        1. Acesse: POST http://{tenant}.kb.seedgrid.com.br:8080/public/auth/login
        2. Body: { "login": "email@example.com", "password": "senha" }
        3. Copie o token da resposta
        4. Clique em "Authorize" acima e cole o token

        ⚠️ O token DEVE ser gerado no mesmo domínio onde será usado!
        """
)
public class OpenApiConfig extends Application {
}

