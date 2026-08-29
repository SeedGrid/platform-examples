package br.com.seedgrid.whatsappexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid WhatsApp Example API",
        version = "1.0.0",
        description = """
            App de exemplo para o seedgrid-quarkus-ext-whatsapp: envio de mensagens de texto via
            WhatsApp (provider Evolution API) atraves da interface comum WhatsAppApi injetada
            por CDI, com o resultado de cada tentativa registrado localmente.

            Fluxo:
            1. POST /api/notifications {"phoneNumber": "11999999999", "message": "Ola!"} — envia
               a mensagem via WhatsAppApi.sendTextMessage e grava o resultado (SENT/FAILED).
            2. GET /api/notifications — lista o historico de envios.
            """
    )
)
public class OpenApiConfig extends Application {
}
