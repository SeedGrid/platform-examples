package br.com.seedgrid.filemanagementexample.config;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(
    info = @Info(
        title = "SeedGrid FileManagement Example API",
        version = "1.0.0",
        description = """
            API de exemplo para o seedgrid-filemanagement: armazenamento de arquivos via a SPI
            FileStorage / FileStorageFactory, resolvida pelo FileStorageRegistry, usando o backend
            "filesystem" (FilesystemStorage) - o unico que roda 100% local, sem credenciais de nuvem.

            Fluxo:
            1. POST /documents - recebe um arquivo em base64 e o armazena via FileStorage.store(...).
               Devolve a StorageKey (campo "key", que e o path relativo) + metadata (tamanho,
               contentType).
            2. GET /documents/{key} - faz FileStorage.retrieve(...) e devolve o conteudo original
               (mesmos bytes). Se o arquivo nao existir -> 404.
            3. GET /documents/_meta/{key} - FileStorage.getMetadata(...): tamanho, contentType,
               ultima modificacao.
            4. DELETE /documents/{key} - FileStorage.delete(...). Um GET posterior devolve 404.

            O backend, o diretorio-base e o prefixo padrao sao configuraveis em application.properties
            (app.storage.*). Trocar "filesystem" por "s3"/"azure-blob"/etc. e so mudar o type e passar
            as credenciais no mapa de config - nenhuma linha do resource muda.
            """
    )
)
public class OpenApiConfig extends Application {
}
