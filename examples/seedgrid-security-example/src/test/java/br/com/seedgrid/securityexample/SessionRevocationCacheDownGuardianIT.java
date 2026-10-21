package br.com.seedgrid.securityexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import br.com.seedgrid.cache.testkit.CacheContainer;
import br.com.seedgrid.cache.testkit.ExternalClient;
import br.com.seedgrid.cache.core.failover.CacheFailoverStatus;
import br.com.seedgrid.cache.core.failover.FailoverCache;
import br.com.seedgrid.cache.testkit.FailoverAwait;
import br.com.seedgrid.security.controllers.RestControllerPath;
import br.com.seedgrid.security.entities.Company;
import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.exceptions.SessionRevokedException;
import br.com.seedgrid.security.repositories.CompanyRepository;
import br.com.seedgrid.security.repositories.RoleRepository;
import br.com.seedgrid.security.repositories.UserRepository;
import br.com.seedgrid.security.services.PasswordService;
import br.com.seedgrid.security.services.SessionRevocationService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * GUARDIAO DA REVOGACAO DE SESSAO COM O REDIS FORA DO AR (TC2.0, E12 ponta a ponta) - NAO ALTERAR, ENFRAQUECER NEM
 * DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> o marcador de revogacao ({@link SessionRevocationService}) mora no Redis. Com o Redis REAL
 * (Testcontainers, container parado e religado no meio do teste) e HTTP/login reais:</p>
 * <ul>
 *   <li>login, revoga, derruba o Redis: o token ANTIGO responde 401 {@code SESSION_REVOKED} nos tres modos de
 *       {@code seedgrid.cache.if-provider-is-down} ({@code WARN}, {@code ERROR}, {@code FAILOVER}) - leitura estrita,
 *       falha fechada; um token valido (nunca revogado) tambem nao vale enquanto o marcador e' ilegivel;</li>
 *   <li>depois da volta do Redis o token anterior a revogacao segue 401;</li>
 *   <li>so' em {@code FAILOVER}: login NOVO durante a queda funciona; a revogacao feita durante a queda derruba o token
 *       emitido durante a queda e VOLTA ao Redis pelo journal (a chave existe no servidor, com TTL), e o token anterior
 *       continua 401 depois da volta.</li>
 * </ul>
 * <p><b>Por que existe:</b> antes (E12) o modo WARN devolvia "sem marcador" com o Redis fora e o token revogado voltava a
 * valer; em FAILOVER a memoria local nao tem os marcadores gravados antes da queda. So' havia teste unitario.</p>
 * <p><b>Dano se quebrar:</b> ex-funcionario / usuario desativado / empresa inativada volta a acessar durante uma queda do
 * cache (ou depois dela), ate o fim do TTL do access token (8 h).</p>
 *
 * <p>Cada modo e' uma subclasse com o seu {@code @TestProfile} (o modo e' lido na subida da aplicacao). O {@code iat}
 * do JWT tem resolucao de segundo: as pausas de 1,1 s garantem que o login NOVO e' posterior ao marcador / a queda.</p>
 */
@Tag("chaos")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// Global (vale para todos os testes do modulo): com seedgrid-cache-redis no classpath de teste, todo teste que sobe a
// aplicacao passa a usar o provider Redis, entao todos precisam de um Redis de verdade para falar.
@QuarkusTestResource(CacheContainerTestResource.class)
public abstract class SessionRevocationCacheDownGuardianIT {

    static final String PASSWORD = "Sup3r#Secret!";
    static final String PROBE_PATH = RestControllerPath.ME_COMPANIES_FULL;
    /** Pausa para o {@code iat} (segundo) do login novo ficar depois do marcador / da queda. */
    static final long IAT_PAUSE_MS = 1_100;

    @Inject
    SessionRevocationService revocation;
    @Inject
    CacheFailoverStatus failoverStatus;
    @Inject
    UserRepository userRepository;
    @Inject
    RoleRepository roleRepository;
    @Inject
    CompanyRepository companyRepository;
    @Inject
    PasswordService passwordService;
    @TestHTTPResource
    java.net.URL appUrl;

    /** O container Redis desta aplicacao (injetado por {@link CacheContainerTestResource}). */
    protected abstract CacheContainer container();

    @BeforeAll
    void port() {
        // Porta real do app (licoes de teste do plano): sem isto o RestAssured pode falar com outro processo local.
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = appUrl.getPort();
    }

    @AfterEach
    void redisIsUpAgain() {
        if (!container().isRunning()) {
            container().start();
        }
    }

    // ---------------------------------------------------------------- cenario comum (WARN, ERROR e FAILOVER)

    @Test
    void oldTokenStaysRevokedWhileRedisIsDownAndAfterItComesBack() throws Exception {
        Victim victim = newVictim();
        Victim bystander = newVictim();
        String oldToken = login(victim.email());
        String neverRevoked = login(bystander.email());
        assertAlive(oldToken);
        assertAlive(neverRevoked);

        revocation.revokeUser(victim.publicId());
        assertRevoked(oldToken);
        flushToDisk(); // o Redis de producao tem persistencia: o marcador gravado antes da queda sobrevive ao restart

        container().stop();
        try {
            assertDeniedDuringOutage(oldToken); // 401 SESSION_REVOKED com o Redis fora (a primeira leitura pode disparar o failover)
            assertDeniedDuringOutage(oldToken);
            // falha fechada: sem poder ler o marcador, nem o token nunca revogado se prova valido
            assertDeniedDuringOutage(neverRevoked);
            afterOutageIsObserved();
        } finally {
            container().start();
        }
        awaitRedisBackInService(victim.email());

        assertRevoked(oldToken); // o token anterior a revogacao segue 401 depois da volta
    }

    /**
     * Resposta esperada com o Redis fora: 401 SESSION_REVOKED (WARN e FAILOVER). No modo ERROR (fail-closed) a
     * revogacao e' checada ANTES do RBAC e a falha do cache sai como 503 SESSION_STORE_UNAVAILABLE (ver a subclasse).
     */
    protected void assertDeniedDuringOutage(String token) {
        assertRevoked(token);
    }

    /** Gancho do modo (FAILOVER aguarda o estado FAILOVER; WARN/ERROR nao tem estado). */
    protected void afterOutageIsObserved() {
    }

    // ---------------------------------------------------------------- helpers

    protected record Victim(String email, String publicId) {
    }

    protected static void assertRevoked(String token) {
        given().header("Authorization", "Bearer " + token).get(PROBE_PATH)
                .then().statusCode(401)
                .body("code", equalTo(SessionRevokedException.CODE));
    }

    protected static void assertAlive(String token) {
        given().header("Authorization", "Bearer " + token).get(PROBE_PATH).then().statusCode(200);
    }

    protected static String login(String email) {
        String token = given().contentType(ContentType.JSON)
                .body("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                .post(RestControllerPath.AUTH_LOGIN_FULL)
                .then().statusCode(200).extract().path("token");
        assertNotNull(token);
        return token;
    }

    protected static void pauseForIatResolution() throws InterruptedException {
        Thread.sleep(IAT_PAUSE_MS);
    }

    /** Forca o Redis a gravar em disco antes de ser parado (equivale a RDB/AOF ligado em producao). */
    protected void flushToDisk() {
        try (ExternalClient external = ExternalClient.connect(container().endpoint(), Duration.ofSeconds(5))) {
            external.ds().execute("SAVE");
        }
    }

    /**
     * Espera a aplicacao voltar a ler o Redis: um login NOVO (posterior a qualquer marcador) so' vale quando a leitura
     * do marcador funciona, entao o 200 prova que o provider esta de volta.
     */
    protected void awaitRedisBackInService(String email) throws InterruptedException {
        pauseForIatResolution();
        Awaitility.await("aplicacao lendo o Redis de novo").atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500)).until(() -> {
                    try {
                        String token = login(email);
                        return given().header("Authorization", "Bearer " + token).get(PROBE_PATH)
                                .statusCode() == 200;
                    } catch (RuntimeException | AssertionError e) {
                        return false;
                    }
                });
    }

    protected Victim newVictim() {
        String email = "down-" + UUID.randomUUID() + "@test.com";
        return QuarkusTransaction.requiringNew().call(() -> {
            Company company = new Company();
            company.setCpfCnpj(String.format("%014d",
                    Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L)));
            String name = "Empresa " + UUID.randomUUID().toString().substring(0, 8);
            company.setCorporateName(name);
            company.setTradeName(name);
            companyRepository.persist(company);

            User user = new User();
            user.setEmail(email);
            user.setName("Cache Down User");
            user.setWhatsApp("11955554444");
            user.setPassword("placeholder");
            user.activate();
            user.confirmEmail();
            user.clearChangePasswordRequired();
            user.getRoles().add(roleRepository.findByName("ADMIN").orElseThrow());
            user.addCompany(companyRepository.findById(company.getId()).orElseThrow());
            userRepository.persist(user);
            user.setPassword(passwordService.encode(user.getPublicId() + PASSWORD));
            return new Victim(email, user.getPublicId());
        });
    }

    // ---------------------------------------------------------------- subclasses finas, uma por modo

    /** Chaves de marcador do usuario no servidor, lidas por um cliente independente do app. */
    protected List<String> markerKeys(ExternalClient external, String userPublicId) {
        return external.ds().key(String.class).keys("sg:session-revoked:*:" + userPublicId);
    }

    protected void assertMarkerBackInRedis(Victim victim, long revokedAtLo, long revokedAtHi) {
        try (ExternalClient external = ExternalClient.connect(container().endpoint(), Duration.ofSeconds(5))) {
            List<String> keys = markerKeys(external, victim.publicId());
            assertFalse(keys.isEmpty(), "a revogacao feita durante a queda voltou ao Redis pelo journal");
            String key = keys.get(0);
            long value = Long.parseLong(external.ds().value(String.class).get(key));
            assertTrue(value >= revokedAtLo && value <= revokedAtHi,
                    "marcador no Redis = instante da revogacao da queda (" + value + " em [" + revokedAtLo + ", "
                            + revokedAtHi + "])");
            long ttl = external.ds().key(String.class).ttl(key);
            assertTrue(ttl > 0 && ttl <= SessionRevocationService.DEFAULT_TOKEN_TTL.toSeconds(),
                    "marcador com TTL restante (" + ttl + " s)");
            assertEquals(1, keys.size(), "uma unica chave de usuario");
        }
    }

    protected void awaitNormalAgain() {
        FailoverAwait.awaitState(failoverStatus, FailoverCache.State.NORMAL, Duration.ofSeconds(60));
    }
}
