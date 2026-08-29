package br.com.seedgrid.backupexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid Backup Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-backup: export/backup assincrono de dados do tenant,
            com download do artefato gerado.

            Fluxo:
            1. POST /public/auth/login (admin, bootstrap do security) — obtem um JWT.
            2. POST /api/notes {"title": "...", "content": "..."} — cria uma anotacao pessoal
               (dominio de exemplo deste app).
            3. POST /exports {"format": "CSV_ZIP", "scope": "DOMAIN"} — pede um export assincrono
               (endpoint pronto, vem do seedgrid-backup). Retorna 201 com status QUEUED.
            4. GET /exports/{jobId} — acompanha o progresso ate status=DONE (execucao acontece
               em background numa virtual thread).
            5. GET /exports/{jobId}/download — baixa o ZIP (manifest.json + checksums.txt +
               data/Note.csv com as anotacoes do usuario).
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
