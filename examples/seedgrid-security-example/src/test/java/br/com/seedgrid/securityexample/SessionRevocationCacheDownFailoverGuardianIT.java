package br.com.seedgrid.securityexample;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import br.com.seedgrid.cache.core.failover.FailoverCache;
import br.com.seedgrid.cache.testkit.CacheContainer;
import br.com.seedgrid.cache.testkit.FailoverAwait;
import br.com.seedgrid.securityexample.CacheContainerTestResource.InjectCacheContainer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/** Guardiao TC2.0 com {@code seedgrid.cache.if-provider-is-down=FAILOVER} (ver {@link SessionRevocationCacheDownGuardianIT}). */
@QuarkusTest
@TestProfile(SessionRevocationCacheDownFailoverGuardianIT.Profile.class)
@Tag("chaos")
class SessionRevocationCacheDownFailoverGuardianIT extends SessionRevocationCacheDownGuardianIT {

    @InjectCacheContainer
    CacheContainer container;

    @Override
    protected CacheContainer container() {
        return container;
    }

    @Override
    protected void afterOutageIsObserved() {
        FailoverAwait.awaitState(failoverStatus, FailoverCache.State.FAILOVER, java.time.Duration.ofSeconds(30));
    }

    @Test
    void newLoginWorksDuringTheOutageAndTheRevocationMadeThereGoesBackToRedis() throws Exception {
        Victim victim = newVictim();
        String oldToken = login(victim.email());
        assertAlive(oldToken);
        revocation.revokeUser(victim.publicId());
        assertRevoked(oldToken);
        flushToDisk();

        String duringOutage;
        String afterOutageRevocation;
        long revokedAtLo;
        long revokedAtHi;
        container().stop();
        try {
            assertRevoked(oldToken); // a leitura que derruba o provider dispara o failover
            FailoverAwait.awaitState(failoverStatus, FailoverCache.State.FAILOVER, java.time.Duration.ofSeconds(30));
            pauseForIatResolution();

            // login NOVO durante a queda funciona (emitido depois do inicio da queda, sem marcador na memoria)
            duringOutage = login(victim.email());
            assertAlive(duringOutage);

            // revogacao feita durante a queda: grava na memoria (e no journal) e derruba o token emitido na queda
            revokedAtLo = System.currentTimeMillis();
            revocation.revokeUser(victim.publicId());
            revokedAtHi = System.currentTimeMillis();
            assertRevoked(duringOutage);
            assertRevoked(oldToken);

            pauseForIatResolution();
            afterOutageRevocation = login(victim.email());
            assertAlive(afterOutageRevocation);
        } finally {
            container().start();
        }
        awaitNormalAgain();

        // o journal foi reaplicado: a revogacao da queda esta no Redis (conferida por cliente independente)
        assertMarkerBackInRedis(victim, revokedAtLo, revokedAtHi);

        // depois da volta
        assertRevoked(oldToken);
        assertRevoked(duringOutage);
        assertAlive(afterOutageRevocation);
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("seedgrid.cache.if-provider-is-down", "FAILOVER",
                    "seedgrid.cache.failover.probe-interval", "PT0.3S");
        }
    }
}
