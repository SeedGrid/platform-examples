package br.com.seedgrid.securityexample;

import java.util.Map;

import org.junit.jupiter.api.Tag;

import br.com.seedgrid.cache.testkit.CacheContainer;
import br.com.seedgrid.securityexample.CacheContainerTestResource.InjectCacheContainer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/** Guardiao TC2.0 com {@code seedgrid.cache.if-provider-is-down=ERROR} (ver {@link SessionRevocationCacheDownGuardianIT}). */
@QuarkusTest
@TestProfile(SessionRevocationCacheDownErrorGuardianIT.Profile.class)
@Tag("chaos")
class SessionRevocationCacheDownErrorGuardianIT extends SessionRevocationCacheDownGuardianIT {

    @InjectCacheContainer
    CacheContainer container;

    @Override
    protected CacheContainer container() {
        return container;
    }

    /**
     * ERROR (fail-closed) com o Redis fora: 503 SESSION_STORE_UNAVAILABLE com Retry-After. O acesso e' negado (nunca
     * 200), mas como indisponibilidade: 401 faria o cliente deslogar e o 500 pareceria bug da aplicacao.
     */
    @Override
    protected void assertDeniedDuringOutage(String token) {
        io.restassured.RestAssured.given().header("Authorization", "Bearer " + token).get(PROBE_PATH)
                .then().statusCode(503)
                .header("Retry-After", String.valueOf(
                        br.com.seedgrid.security.exceptions.SessionStoreUnavailableException.RETRY_AFTER_SECONDS))
                .body("code", org.hamcrest.Matchers.equalTo(
                        br.com.seedgrid.security.exceptions.SessionStoreUnavailableException.CODE));
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("seedgrid.cache.if-provider-is-down", "ERROR");
        }
    }
}
