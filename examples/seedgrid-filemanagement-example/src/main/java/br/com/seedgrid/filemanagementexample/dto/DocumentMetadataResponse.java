package br.com.seedgrid.filemanagementexample.dto;

import java.time.Instant;

/**
 * Resposta do GET /documents/{key}/metadata (espelha o {@code FileMetadata} da lib).
 */
public record DocumentMetadataResponse(
        String key,
        String uri,
        String filename,
        Long sizeBytes,
        String contentType,
        Instant lastModifiedAt) {
}
