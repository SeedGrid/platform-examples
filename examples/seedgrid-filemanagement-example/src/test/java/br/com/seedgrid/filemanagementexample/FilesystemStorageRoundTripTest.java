package br.com.seedgrid.filemanagementexample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.seedgrid.filemanagement.domains.FileMetadata;
import br.com.seedgrid.filemanagement.domains.StorageKey;
import br.com.seedgrid.filemanagement.domains.StorageOptions;
import br.com.seedgrid.filemanagement.services.FileStorageRegistry;
import br.com.seedgrid.filemanagement.spis.FileStorage;
import br.com.seedgrid.filemanagement.spis.StorageNotFoundException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prova o ciclo de vida completo do backend "filesystem" via a SPI FileStorage, resolvida pelo
 * FileStorageRegistry: store -> retrieve devolve os MESMOS bytes (e o mesmo hash SHA-256), metadata
 * bate (tamanho, filename, ultima modificacao) e delete faz o retrieve seguinte falhar.
 *
 * <p>Desde a lib 2026.07.08 o {@link FileStorageRegistry} cacheia por <strong>type + config</strong>:
 * cada {@code @TempDir} (config diferente) resolve a propria instancia de storage, sem precisar de
 * {@code registry.clearCache()} no {@code @BeforeEach} (workaround exigido ate 2026.07.02, quando o
 * cache era so por type e a primeira config vencia).</p>
 */
@QuarkusTest
class FilesystemStorageRoundTripTest {

    @Inject
    FileStorageRegistry registry;

    @TempDir
    Path tempDir;

    private FileStorage storage;

    @BeforeEach
    void setUp() {
        // Cache por type+config (lib >= 2026.07.08): este base-path resolve uma instancia propria.
        storage = registry.get("filesystem", Map.of("base-path", tempDir.toString()));
    }

    @Test
    void storeThenRetrieve_returnsIdenticalBytesAndHash() throws Exception {
        byte[] original = "conteudo do documento de exemplo - filemanagement".getBytes();
        StorageKey key = storage.store(
                "contrato.txt", original, StorageOptions.withPrefix("uploads/2026"));

        assertAll("StorageKey do backend filesystem",
                () -> assertEquals("filesystem", key.getType()),
                () -> assertEquals("uploads/2026/contrato.txt", key.getPath()),
                () -> assertTrue(storage.exists(key)));

        byte[] retrieved = storage.retrieve(key);

        assertAll("round-trip identico",
                () -> assertArrayEquals(original, retrieved, "bytes devem ser identicos"),
                () -> assertArrayEquals(sha256(original), sha256(retrieved), "hash SHA-256 deve bater"));
    }

    @Test
    void metadata_reportsCorrectSizeAndFilename() throws Exception {
        byte[] content = "1234567890".getBytes(); // 10 bytes
        StorageKey key = storage.store("nota.txt", content, StorageOptions.defaults());

        FileMetadata metadata = storage.getMetadata(key);

        assertAll("metadata do arquivo",
                () -> assertEquals("nota.txt", metadata.getFilename()),
                () -> assertEquals(10L, metadata.getSizeBytes()),
                () -> assertNotNull(metadata.getLastModifiedAt()));
    }

    @Test
    void deleteThenRetrieve_throwsStorageNotFoundException() throws Exception {
        StorageKey key = storage.store("efemero.txt", "bye".getBytes(), StorageOptions.defaults());
        assertTrue(storage.exists(key));

        storage.delete(key);

        // Desde a lib 2026.07.08, not-found de ARQUIVO (retrieve/stream/getMetadata/delete) lanca
        // StorageNotFoundException (que extends StorageException, entao catches genericos seguem
        // funcionando). O resource captura esse subtipo e mapeia pra 404, sem checar exists() antes.
        assertFalse(storage.exists(key));
        StorageNotFoundException ex =
                assertThrows(StorageNotFoundException.class, () -> storage.retrieve(key));
        assertTrue(ex.getMessage().contains("File not found"), "mensagem: " + ex.getMessage());

        // delete de arquivo inexistente TAMBEM lanca (ate 2026.07.02 era warn + no-op).
        assertThrows(StorageNotFoundException.class, () -> storage.delete(key));
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }
}
