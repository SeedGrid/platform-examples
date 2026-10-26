package br.com.seedgrid.filemanagementexample;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.seedgrid.filemanagement.services.FileStorageRegistry;
import br.com.seedgrid.filemanagement.spis.FileStorage;
import br.com.seedgrid.filemanagement.spis.StorageNotFoundException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Prova que o FileStorageRegistry (bean CDI da lib, auto-descoberto via jandex.idx) enxerga a
 * FilesystemStorageFactory e resolve o backend "filesystem" a partir da config - e que pedir um
 * type inexistente lanca StorageNotFoundException.
 */
@QuarkusTest
class StorageRegistryTest {

    @Inject
    FileStorageRegistry registry;

    @TempDir
    Path tempDir;

    // Sem clearCache() no @BeforeEach: desde a lib 2026.07.08 o registry cacheia por type+config,
    // entao o @TempDir desta classe (config propria) nao colide com o storage de outras classes.

    @Test
    void registry_discoversFilesystemFactory() {
        assertAll("factory filesystem registrada",
                () -> assertTrue(registry.has("filesystem"),
                        "o FilesystemStorageFactory deve ter sido descoberto"),
                () -> assertTrue(registry.getAvailableTypes().contains("filesystem"),
                        "types disponiveis: " + registry.getAvailableTypes()));
    }

    @Test
    void registry_resolvesFilesystemBackendFromConfig() {
        FileStorage storage = registry.get("filesystem", Map.of("base-path", tempDir.toString()));

        assertAll("backend filesystem resolvido",
                () -> assertNotNull(storage),
                () -> assertEquals("filesystem", storage.typeIdentifier()),
                () -> assertEquals("FilesystemStorage", storage.getClass().getSimpleName()));
    }

    @Test
    void registry_unknownType_throwsStorageNotFound() {
        StorageNotFoundException ex = assertThrows(StorageNotFoundException.class,
                () -> registry.get("dropbox", Map.of("base-path", tempDir.toString())));
        assertTrue(ex.getMessage().contains("No FileStorageFactory found for type: dropbox"),
                "mensagem: " + ex.getMessage());
    }
}
