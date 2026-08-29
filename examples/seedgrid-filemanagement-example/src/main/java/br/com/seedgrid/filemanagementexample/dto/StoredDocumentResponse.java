package br.com.seedgrid.filemanagementexample.dto;

/**
 * Resposta do POST /documents.
 *
 * @param key         path relativo devolvido pela StorageKey - use-o em GET/DELETE /documents/{key}
 * @param uri         representacao URI da StorageKey (ex.: "filesystem://uploads/contrato.pdf")
 * @param filename    nome do arquivo armazenado
 * @param sizeBytes   tamanho em bytes (lido de volta via getMetadata)
 * @param contentType MIME type detectado/armazenado (pode ser null)
 */
public record StoredDocumentResponse(
        String key,
        String uri,
        String filename,
        Long sizeBytes,
        String contentType) {
}
