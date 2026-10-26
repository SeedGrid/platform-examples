package br.com.seedgrid.filemanagementexample.service;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.seedgrid.filemanagement.domains.FileMetadata;
import br.com.seedgrid.filemanagement.domains.StorageKey;
import br.com.seedgrid.filemanagement.domains.StorageOptions;
import br.com.seedgrid.filemanagement.services.FileStorageRegistry;
import br.com.seedgrid.filemanagement.spis.FileStorage;
import br.com.seedgrid.filemanagement.spis.StorageException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fachada fina sobre o {@link FileStorageRegistry} do seedgrid-filemanagement.
 *
 * <p>Concentra num unico ponto a escolha do backend ({@code app.storage.type}) e a montagem do
 * mapa de configuracao que a factory exige. Para o backend "filesystem" a unica chave necessaria e
 * {@code base-path} (ver {@code FilesystemStorageFactory}); para trocar por s3/azure-blob/etc.
 * bastaria mudar o type e preencher outras chaves de config - o resto do app nao muda.</p>
 *
 * <p>O {@link FileStorage} e resolvido pelo registry, que faz lazy-init e (desde a lib 2026.07.08)
 * <strong>cacheia por type + config</strong>: mapas de config com o mesmo conteudo resolvem a mesma
 * instancia; configs diferentes, instancias diferentes. Reconstruir o Map.of(...) a cada chamada e
 * barato e sempre acerta o cache.</p>
 */
@ApplicationScoped
public class DocumentStorageService {

    @Inject
    FileStorageRegistry registry;

    @ConfigProperty(name = "app.storage.type", defaultValue = "filesystem")
    String storageType;

    @ConfigProperty(name = "app.storage.filesystem.base-path")
    String basePath;

    /**
     * Resolve o backend de storage configurado. Para "filesystem" passa o base-path no mapa de
     * config que a {@code FilesystemStorageFactory} le da chave "base-path".
     */
    public FileStorage storage() {
        Map<String, String> config = Map.of("base-path", basePath);
        return registry.get(storageType, config);
    }

    /** Armazena o conteudo e devolve a StorageKey (o path relativo dentro do base-path). */
    public StorageKey store(String filename, byte[] content, String prefix, String contentType)
            throws StorageException {
        StorageOptions options = StorageOptions.builder()
                .prefix(prefix)
                .contentType(contentType)
                .build();
        return storage().store(filename, content, options);
    }

    /** Reconstroi a StorageKey do backend filesystem a partir do path relativo vindo da URL. */
    public StorageKey keyOf(String relativePath) {
        return StorageKey.of(storageType, relativePath);
    }

    public byte[] retrieve(StorageKey key) throws StorageException {
        return storage().retrieve(key);
    }

    public FileMetadata metadata(StorageKey key) throws StorageException {
        return storage().getMetadata(key);
    }

    public void delete(StorageKey key) throws StorageException {
        storage().delete(key);
    }
}
