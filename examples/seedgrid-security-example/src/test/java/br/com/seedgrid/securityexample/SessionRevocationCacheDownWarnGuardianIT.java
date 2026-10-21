package br.com.seedgrid.securityexample;

import java.util.Map;

import org.junit.jupiter.api.Tag;

import br.com.seedgrid.cache.testkit.CacheContainer;
import br.com.seedgrid.securityexample.CacheContainerTestResource.InjectCacheContainer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/** Guardiao TC2.0 com {@code seedgrid.cache.if-provider-is-down=WARN} (ver {@link SessionRevocationCacheDownGuardianIT}). */
@QuarkusTest
@TestProfile(SessionRevocationCacheDownWarnGuardianIT.Profile.class)
@Tag("chaos")
class SessionRevocationCacheDownWarnGuardianIT extends SessionRevocationCacheDownGuardianIT {

    @InjectCacheContainer
    CacheContainer container;

    @Override
    protected CacheContainer container() {
        return container;
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("seedgrid.cache.if-provider-is-down", "WARN");
        }
    }
}
