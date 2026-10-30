package br.com.seedgrid.filemanagementexample.consent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.com.seedgrid.filemanagement.domains.StorageCredential;
import br.com.seedgrid.filemanagement.spis.StorageCredentialSource;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * StorageCredentialSource DE EXEMPLO: guarda o refresh token na memoria. Este e' o papel do host.
 *
 * <p>Num produto de verdade isto leria/gravaria o refresh token CIFRADO no banco do produto, por dono ({@code ownerKey}).
 * A lib nunca guarda nem enxerga mais do que esta interface devolve.</p>
 */
@ApplicationScoped
public class ExampleCredentialSource implements StorageCredentialSource {

    private final Map<String, String> refreshTokens = new ConcurrentHashMap<>();

    @Override
    public Optional<StorageCredential> resolve(String provider, String ownerKey) {
        return Optional.ofNullable(refreshTokens.get(key(provider, ownerKey))).map(StorageCredential::ofRefreshToken);
    }

    /** O provedor rotaciona o refresh token a cada renovacao: o novo substitui o antigo. */
    @Override
    public void renewed(String provider, String ownerKey, StorageCredential credential) {
        if (credential.hasRefreshToken()) {
            refreshTokens.put(key(provider, ownerKey), credential.refreshToken());
        }
    }

    /** Chamado quando o usuario conclui a concessao (aqui, simulada). */
    public void grant(String provider, String ownerKey, String refreshToken) {
        refreshTokens.put(key(provider, ownerKey), refreshToken);
    }

    /** Chamado quando o usuario desconecta. */
    public Optional<String> revoke(String provider, String ownerKey) {
        return Optional.ofNullable(refreshTokens.remove(key(provider, ownerKey)));
    }

    private static String key(String provider, String ownerKey) {
        return provider + "|" + ownerKey;
    }
}
