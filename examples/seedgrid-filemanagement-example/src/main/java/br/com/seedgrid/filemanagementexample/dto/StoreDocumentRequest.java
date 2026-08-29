package br.com.seedgrid.filemanagementexample.dto;

/**
 * Corpo do POST /documents. O conteudo vem em base64 (mais simples que multipart para um exemplo
 * e sem precisar de config extra do quarkus-rest).
 *
 * @param filename      nome do arquivo (ex.: "contrato.pdf")
 * @param prefix        subpasta/prefixo opcional dentro do base-path (ex.: "uploads/2026"). Pode ser null.
 * @param contentType   MIME type opcional (ex.: "application/pdf"). Pode ser null.
 * @param contentBase64 conteudo do arquivo codificado em base64
 */
public record StoreDocumentRequest(
        String filename,
        String prefix,
        String contentType,
        String contentBase64) {
}
