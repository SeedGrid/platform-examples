package br.com.seedgrid.subdomaintenancyexample;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import br.com.seedgrid.commons.core.enums.StateEnum;
import br.com.seedgrid.subdomaintenancy.dtos.TenantSignupRequest;
import br.com.seedgrid.subdomaintenancy.repositories.TenantRepository;
import br.com.seedgrid.subdomaintenancy.services.TenantService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.hibernate.orm.PersistenceUnit;
import java.time.Instant;
import java.time.LocalDate;
import br.com.seedgrid.subdomaintenancy.entities.Tenant;
import br.com.seedgrid.subdomaintenancy.services.TenantProvisioningService;
import br.com.seedgrid.subdomaintenancy.utils.Utils;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * GUARDIAO DE ISOLAMENTO - NAO ALTERAR, ENFRAQUECER NEM DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> a mesma role (ADMIN) existe em dois tenants com permissoes DIFERENTES; o cache de
 * RBAC nao pode misturar as permissoes de um tenant no outro, qualquer que seja a ordem das chamadas.</p>
 * <p><b>Por que existe:</b> o augmentor de RBAC roda antes dos filtros de requisicao e o escopo caia em "master"
 * para todos os tenants (vazamento confirmado em 2026-10-18). O escopo agora vem da claim {@code subdomain} do JWT.</p>
 * <p><b>Dano se quebrar:</b> um usuario enxerga/executa permissoes concedidas em OUTRO tenant.</p>
 * Postgres real (Testcontainers), dois tenants provisionados, HTTP real com {@code Host} de cada tenant.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(RbacTenantIsolationGuardianIT.Profile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RbacTenantIsolationGuardianIT {

    static final String SUFFIX = "crm.seedgrid.com.br";
    static final String HOST_ALFA = "alfa." + SUFFIX;
    static final String HOST_BETA = "beta." + SUFFIX;
    static final String HOST_MASTER = SUFFIX;
    static final String PASSWORD = "Teste@123456";
    static final String ROLE = "PROBE_ROLE";

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "seedgrid.subdomain-tenancy.domain-suffix", SUFFIX,
                    // Se o header estiver configurado, ele VENCE o host na escolha do banco (QuarkusTenantResolver).
                    "seedgrid.subdomain-tenancy.tenant-header", "X-Tenant",
                    // Padrao do security (proactive=true): o guardiao ProactiveAuthGuardianIT cobre os dois modos.
                    "quarkus.http.auth.proactive", "true");
        }
    }

    @Inject
    TenantProvisioningService provisioning;
    @Inject
    TenantService tenantService;
    @Inject
    @PersistenceUnit("master")
    TenantRepository tenantRepository;

    @ConfigProperty(name = "seedgrid.subdomain-tenancy.tenant.datasource.jdbc.url")
    String tenantBaseUrl;
    @ConfigProperty(name = "seedgrid.subdomain-tenancy.tenant.datasource.username")
    String dbUser;
    @ConfigProperty(name = "seedgrid.subdomain-tenancy.tenant.datasource.password")
    String dbPass;
    @ConfigProperty(name = "seedgrid.subdomain-tenancy.product-prefix")
    Optional<String> productPrefix;

    @io.quarkus.test.common.http.TestHTTPResource
    java.net.URL appUrl;

    String alfaKey;
    String betaKey;
    String alfaToken;
    String betaToken;
    String alfaDb;
    String betaDb;

    @BeforeAll
    void provisionTwoTenants() throws Exception {
        // Porta real do app de teste (test-port=0): sem isto o RestAssured cai na porta padrao e pode falar com
        // OUTRO processo da maquina (um servidor de dev) em vez do app sob teste.
        io.restassured.RestAssured.baseURI = "http://localhost";
        io.restassured.RestAssured.port = appUrl.getPort();
        alfaDb = Utils.buildTenantDatabaseName("alfa", productPrefix);
        betaDb = Utils.buildTenantDatabaseName("beta", productPrefix);
        provision("alfa", "11222333000181");
        provision("beta", "11444777000161");
        seedIntegrationRole(alfaDb);
        seedIntegrationRole(betaDb);
        // Mesma role (ADMIN) nos dois tenants, com permissoes DIFERENTES: alfa tem PROBE_ALFA, beta tem PROBE_BETA.
        grantToAdmin(alfaDb, "PROBE_ALFA");
        grantToAdmin(betaDb, "PROBE_BETA");

        alfaToken = login(HOST_ALFA, "admin@alfa.test");
        betaToken = login(HOST_BETA, "admin@beta.test");
        alfaKey = createKey(HOST_ALFA, alfaToken);
        betaKey = createKey(HOST_BETA, betaToken);
    }

    // ---------------------------------------------------------------- cache de RBAC entre tenants

    /**
     * ACHADO a reproduzir (passo 0 do plano): a role ADMIN existe nos dois tenants com permissoes diferentes. Se o
     * cache de RBAC for compartilhado entre tenants, quem chama primeiro "contamina" o outro. Aqui o alfa chama
     * primeiro; depois o beta tenta a permissao do alfa (deve ser 403) e usa a sua propria (deve ser 200).
     * O resultado de HOJE e' impresso; a expectativa e' a CORRETA (isolamento).
     */
    @Test
    void rbacCacheDoesNotLeakPermissionsOfTheSameRoleBetweenTenants() {
        int alfaOwn = given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).get("/probe/only-alfa").statusCode();
        int betaOnAlfa = given().header("Host", HOST_BETA).auth().oauth2(betaToken).get("/probe/only-alfa").statusCode();
        int betaOwn = given().header("Host", HOST_BETA).auth().oauth2(betaToken).get("/probe/only-beta").statusCode();
        int alfaOnBeta = given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).get("/probe/only-beta").statusCode();
        System.out.println("[X8] cache RBAC: alfa->only-alfa=" + alfaOwn + " beta->only-alfa=" + betaOnAlfa
                + " beta->only-beta=" + betaOwn + " alfa->only-beta=" + alfaOnBeta);
        assertEquals(200, alfaOwn, "alfa deve ter a permissao do proprio tenant");
        assertEquals(403, betaOnAlfa, "beta NAO pode ter a permissao concedida so no alfa");
        assertEquals(200, betaOwn, "beta deve ter a permissao do proprio tenant");
        assertEquals(403, alfaOnBeta, "alfa NAO pode ter a permissao concedida so no beta");
    }

    // ---------------------------------------------------------------- helpers

    private static org.hamcrest.Matcher<String> equalsTo(String expected) {
        return org.hamcrest.Matchers.equalTo(expected);
    }

    private Response probe(String path, String host, String key) {
        return given().header("Host", host).header("x-api-key", key).get(path);
    }

    private String login(String host, String email) {
        Response r = given().header("Host", host).contentType(ContentType.JSON)
                .body(Map.of("login", email, "password", PASSWORD)).post("/public/auth/login");
        assertEquals(200, r.statusCode(), "login " + email + ": " + r.body().asString());
        String token = r.jsonPath().getString("token");
        assertNotNull(token, "login sem token: " + r.body().asString());
        return token;
    }

    private String createKey(String host, String token) {
        Response r = given().header("Host", host).auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("name", "k-" + UUID.randomUUID(), "expiresDays", 30))
                .post("/apikeys/role/" + ROLE);
        assertEquals(201, r.statusCode(), "criar chave: " + r.body().asString());
        return r.jsonPath().getString("apiKey");
    }

    /**
     * Cadastro real (TenantService.save grava o tenant PENDENTE no master e faz o hash da senha) e, no lugar do
     * codigo de e-mail, ativa o tenant como o validateTenant faria: provisiona o banco e marca ativo. Sem o
     * registro ativo no master o resolvedor de conexao responde "tenant inexistente ou inativo".
     */
    private void provision(String subdomain, String cnpj) throws Exception {
        tenantService.save(TenantSignupRequest.builder()
                .subdomain(subdomain)
                .cnpj(cnpj)
                .corporateName(subdomain + " LTDA")
                .tradeName(subdomain)
                .address(TenantSignupRequest.AddressDTO.builder().street("Rua Teste").number("123").city("Sao Paulo")
                        .state(StateEnum.SP).postalCode("01234567").district("Centro").phone("1133334444").build())
                .legalRepresentative(TenantSignupRequest.LegalRepresentativeDTO.builder().firstName("Admin")
                        .lastName(subdomain).cpf(subdomain.equals("alfa") ? "12345678909" : "11144477735")
                        .birthDate(LocalDate.of(1990, 1, 1)).phone("11999998888").whatsapp("11999998888").build())
                .rootEmail("admin@" + subdomain + ".test")
                .plainRootPassword(PASSWORD)
                .build());

        Tenant tenant = QuarkusTransaction.requiringNew().call(() -> {
            Tenant t = tenantRepository.findBySubdomain(subdomain).orElseThrow();
            t.setEmailValidatedAt(Instant.now());
            return t;
        });
        provisioning.createTenantWithAdmin(tenant);
        QuarkusTransaction.requiringNew().run(() ->
                tenantRepository.findBySubdomain(subdomain).orElseThrow().setActive(true));
    }

    private void seedIntegrationRole(String db) throws Exception {
        exec(db, "INSERT INTO permissions (name, description, public_id) VALUES ('PROBE_READ', 'probe', '"
                + UUID.randomUUID() + "') ON CONFLICT DO NOTHING");
        exec(db, "INSERT INTO roles (name, description, public_id, for_integration) VALUES ('" + ROLE
                + "', 'probe', '" + UUID.randomUUID() + "', TRUE) ON CONFLICT DO NOTHING");
        exec(db, "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                + "WHERE r.name = '" + ROLE + "' AND p.name = 'PROBE_READ' ON CONFLICT DO NOTHING");
    }

    private void grantToAdmin(String db, String permission) throws Exception {
        exec(db, "INSERT INTO permissions (name, description, public_id) VALUES ('" + permission + "', 'probe', '"
                + UUID.randomUUID() + "') ON CONFLICT DO NOTHING");
        exec(db, "INSERT INTO role_permissions (role_id, permission_id) SELECT r.id, p.id FROM roles r, permissions p "
                + "WHERE r.name = 'ADMIN' AND r.company_id IS NULL AND p.name = '" + permission + "' ON CONFLICT DO NOTHING");
    }

    private void exec(String db, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(tenantBaseUrl + "/" + db, dbUser, dbPass);
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
