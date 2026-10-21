package br.com.seedgrid.securityexample;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import br.com.seedgrid.cache.testkit.CacheContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Sobe um Redis REAL (testkit do lib-cache: porta fixa, stop/start no mesmo endereco) e aponta o cliente do
 * {@code seedgrid-cache} para ele. O container e' injetado no teste com {@link InjectCacheContainer}.
 */
public class CacheContainerTestResource implements QuarkusTestResourceLifecycleManager {

    /** Marca o campo do teste que recebe o {@link CacheContainer}. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface InjectCacheContainer {
    }

    private CacheContainer container;

    @Override
    public Map<String, String> start() {
        container = CacheContainer.redis().start();
        return Map.of(
                "quarkus.redis.hosts", container.endpoint(),
                // com o servidor fora, nenhum comando fica preso por muito tempo
                "quarkus.redis.timeout", "3S");
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(container,
                new TestInjector.AnnotatedAndMatchesType(InjectCacheContainer.class, CacheContainer.class));
    }

    @Override
    public void stop() {
        if (container != null) {
            container.close();
        }
    }
}
