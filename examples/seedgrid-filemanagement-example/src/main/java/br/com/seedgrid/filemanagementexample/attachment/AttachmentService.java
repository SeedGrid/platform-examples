package br.com.seedgrid.filemanagementexample.attachment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.seedgrid.filemanagement.domains.Attachment;
import br.com.seedgrid.filemanagement.domains.StorageKey;
import br.com.seedgrid.filemanagement.domains.StorageOptions;
import br.com.seedgrid.filemanagement.services.FileStorageRegistry;
import br.com.seedgrid.filemanagement.spis.FileStorage;
import br.com.seedgrid.filemanagement.spis.StorageException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Grava arquivos com ANEXOS ligados: a lib registra a linha em {@code sg_attachments} sozinha, e o produto guarda o ID do
 * anexo em vez do caminho do arquivo. Para ler/apagar, parte do ID: acha o anexo, reconstroi o storage e a chave.
 */
@ApplicationScoped
public class AttachmentService {

    /** Provider em modo consentimento usado pelo exemplo (o servidor falso fala o protocolo do Dropbox). */
    public static final String CONSENT_PROVIDER = "dropbox";

    private static final String FILESYSTEM = "filesystem";

    @Inject
    FileStorageRegistry registry;

    @Inject
    EntityManager em;

    @ConfigProperty(name = "app.storage.filesystem.base-path")
    String basePath;

    @ConfigProperty(name = "app.fake-provider.base-url")
    String fakeProviderUrl;

    /** O mapa de configuracao de cada modo. {@code owner-key} e' o que a lib grava no anexo e passa a fonte de credencial. */
    FileStorage storageFor(String type, String ownerKey) {
        Map<String, String> config = new HashMap<>();
        if (ownerKey != null && !ownerKey.isBlank()) {
            config.put("owner-key", ownerKey);
        }
        if (CONSENT_PROVIDER.equals(type)) {
            config.put("auth-mode", "consent");
            // o app do PRODUTO no provedor (cadastrado uma vez); aqui, valores falsos
            config.put("app-key", "app-key-do-produto");
            config.put("app-secret", "app-secret-do-produto");
            config.put("token-url", fakeProviderUrl + "/fake-provider/oauth/token");
            config.put("api-url", fakeProviderUrl + "/fake-provider");
            config.put("content-url", fakeProviderUrl + "/fake-provider");
            config.put("root-folder-path", "/exemplo");
        } else {
            config.put("base-path", basePath);
        }
        return registry.get(type, config);
    }

    public Attachment store(String type, String ownerKey, String filename, String contentType, byte[] content)
            throws StorageException {
        StorageKey key = storageFor(type, ownerKey).store(filename, content,
                StorageOptions.builder().prefix("anexos").contentType(contentType).build());
        // a lib acabou de gravar a linha; o produto a acha pelo que o store devolveu
        return em.createQuery("from Attachment a where a.storageType = :t and a.storagePath = :p", Attachment.class)
                .setParameter("t", key.getType()).setParameter("p", key.toUri()).getResultStream().findFirst()
                .orElseThrow(() -> new IllegalStateException("anexo nao registrado para " + key));
    }

    public Attachment find(String publicId) {
        return em.createQuery("from Attachment a where a.publicId = :id", Attachment.class)
                .setParameter("id", publicId).getResultStream().findFirst().orElse(null);
    }

    public List<Attachment> byOwner(String ownerKey) {
        return em.createQuery("from Attachment a where a.ownerKey = :o order by a.id", Attachment.class)
                .setParameter("o", ownerKey).getResultList();
    }

    public byte[] read(Attachment a) throws StorageException {
        return storageFor(a.getStorageType(), a.getOwnerKey()).retrieve(keyOf(a));
    }

    public void delete(Attachment a) throws StorageException {
        storageFor(a.getStorageType(), a.getOwnerKey()).delete(keyOf(a));
    }

    /**
     * Reconstroi a chave a partir do que a lib gravou em storage_path (o URI da StorageKey). Para o filesystem o
     * StorageKey.fromUri serve; nos provedores de nuvem a chave e' (pasta raiz, id do arquivo), por exemplo
     * dropbox:///exemplo/id:1, e fromUri nao devolve a mesma chave (a raiz tem barra inicial e o id tem ':'), entao o
     * exemplo separa na ultima barra. Ver 'Achados' no README.
     */
    private static StorageKey keyOf(Attachment a) {
        String uri = a.getStoragePath();
        if (FILESYSTEM.equals(a.getStorageType())) {
            return StorageKey.fromUri(uri);
        }
        String rest = uri.substring(uri.indexOf("://") + 3);
        int slash = rest.lastIndexOf('/');
        return StorageKey.of(a.getStorageType(), rest.substring(0, slash), rest.substring(slash + 1));
    }
}
