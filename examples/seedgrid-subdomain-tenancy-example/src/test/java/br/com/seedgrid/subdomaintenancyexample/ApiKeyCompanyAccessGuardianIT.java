package br.com.seedgrid.subdomaintenancyexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import br.com.seedgrid.commons.core.enums.StateEnum;
import br.com.seedgrid.security.entities.CompanyAccessMode;
import br.com.seedgrid.subdomaintenancy.dtos.TenantSignupRequest;
import br.com.seedgrid.subdomaintenancy.entities.Tenant;
import br.com.seedgrid.subdomaintenancy.repositories.TenantRepository;
import br.com.seedgrid.subdomaintenancy.services.TenantProvisioningService;
import br.com.seedgrid.subdomaintenancy.services.TenantService;
import br.com.seedgrid.subdomaintenancy.utils.Utils;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * GUARDIAO (Fase 5, T5.1 - criterio "API key obedece", plano 4.12) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem
 * decisao explicita do dono.
 *
 * <p><b>O que protege:</b> a API key REAL (extension apikeys, {@code x-api-key}) obedece ao estado de acesso da
 * empresa dela no subdomain-tenancy (banco POR tenant, {@code quarkus.http.auth.proactive=true}): {@code ACTIVE}
 * escreve e le; {@code READ_ONLY} le (200) mas a escrita e' 403 {@code COMPANY_READ_ONLY}; {@code DISABLED} e' 403
 * {@code COMPANY_DISABLED} ate' na leitura; voltar a {@code ACTIVE} restaura; e a chave de OUTRO tenant nao e'
 * afetada. Postgres real, dois tenants provisionados e requisicoes com o {@code Host} dos tenants.</p>
 * <p><b>Por que existe:</b> integracao por chave nao passa pelo login nem pelo marcador de sessao; o filtro de
 * acesso e' a UNICA barreira. Se a chave escapar dele (ordem dos filtros, empresa da chave nao publicada no contexto
 * do tenant), o cliente inadimplente segue escrevendo por API.</p>
 * <p><b>Dano se quebrar:</b> receita perdida (escrita liberada por chave em conta bloqueada) ou o tenant em dia
 * bloqueado por engano.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(ApiKeyCompanyAccessGuardianIT.Profile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiKeyCompanyAccessGuardianIT {

    static final String SUFFIX = "crm.seedgrid.com.br";
    static final String HOST_ALFA = "alfa." + SUFFIX;
    static final String HOST_BETA = "beta." + SUFFIX;
    static final String PASSWORD = "Teste@123456";
    static final String ROLE = "PROBE_ROLE";

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "seedgrid.subdomain-tenancy.domain-suffix", SUFFIX,
                    "seedgrid.subdomain-tenancy.tenant-header", "X-Tenant",
                    // Padrao do security (proactive=true), como no ApiKeyIsolationGuardianIT.
                    "quarkus.http.auth.proactive", "true",
                    // setMode e' @Audit: com audit JPA (default do teste) a entidade de audit precisa estar no PU do tenant.
                    "quarkus.hibernate-orm.packages", "${seedgrid.subdomain-tenancy.tenant.entity-packages},"
                            + "br.com.seedgrid.security.entities,br.com.seedgrid.backup.entities,"
                            + "br.com.seedgrid.apikeys.entities,br.com.seedgrid.audit.postgresql.entities");
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

    String alfaDb, betaDb, alfaKey, betaKey, alfaCompany, betaCompany;

    @BeforeAll
    void setUp() throws Exception {
        io.restassured.RestAssured.baseURI = "http://localhost";
        io.restassured.RestAssured.port = appUrl.getPort();
        alfaDb = Utils.buildTenantDatabaseName("alfa", productPrefix);
        betaDb = Utils.buildTenantDatabaseName("beta", productPrefix);
        provision("alfa", "11222333000181");
        provision("beta", "11444777000161");
        seedIntegrationRole(alfaDb);
        seedIntegrationRole(betaDb);
        alfaCompany = text(alfaDb, "SELECT public_id FROM companies ORDER BY id LIMIT 1");
        betaCompany = text(betaDb, "SELECT public_id FROM companies ORDER BY id LIMIT 1");

        // As chaves nascem ANTES de mudar o estado (mudar o estado derruba os JWT da empresa, nao as chaves).
        alfaKey = createKey(HOST_ALFA, login(HOST_ALFA, "admin@alfa.test"));
        betaKey = createKey(HOST_BETA, login(HOST_BETA, "admin@beta.test"));
    }

    @AfterEach
    void backToActive() {
        setMode("alfa", alfaCompany, CompanyAccessMode.ACTIVE);
        setMode("beta", betaCompany, CompanyAccessMode.ACTIVE);
    }

    @Test
    void activeCompanyKeyWritesAndReads() {
        write(HOST_ALFA, alfaKey).then().statusCode(200).body(equalTo(alfaDb));
        read(HOST_ALFA, alfaKey).then().statusCode(200).body(equalTo(alfaDb));
    }

    @Test
    void readOnlyCompanyKeyReadsButWriteIs403CompanyReadOnly() {
        setMode("alfa", alfaCompany, CompanyAccessMode.READ_ONLY);

        read(HOST_ALFA, alfaKey).then().statusCode(200);
        write(HOST_ALFA, alfaKey).then().statusCode(403)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("COMPANY_READ_ONLY"))
                .body("companyPublicId", equalTo(alfaCompany));
    }

    @Test
    void disabledCompanyKeyIs403CompanyDisabledEvenForRead() throws Exception {
        // A conta so' pode ter todas as empresas DISABLED se ao menos uma seguir ativa (409 ALL_DISABLED): o tenant
        // ganha uma segunda empresa, do mesmo administrador.
        addSecondCompanyToAlfaAdmin();
        setMode("alfa", alfaCompany, CompanyAccessMode.DISABLED);

        read(HOST_ALFA, alfaKey).then().statusCode(403).body("code", equalTo("COMPANY_DISABLED"));
        write(HOST_ALFA, alfaKey).then().statusCode(403).body("code", equalTo("COMPANY_DISABLED"));
    }

    @Test
    void backToActiveRestoresTheKey() {
        setMode("alfa", alfaCompany, CompanyAccessMode.READ_ONLY);
        write(HOST_ALFA, alfaKey).then().statusCode(403);
        setMode("alfa", alfaCompany, CompanyAccessMode.ACTIVE);

        write(HOST_ALFA, alfaKey).then().statusCode(200);
        read(HOST_ALFA, alfaKey).then().statusCode(200);
    }

    @Test
    void keyOfAnotherTenantIsNotAffected() {
        setMode("alfa", alfaCompany, CompanyAccessMode.READ_ONLY);
        write(HOST_BETA, betaKey).then().statusCode(200).body(equalTo(betaDb));
        read(HOST_BETA, betaKey).then().statusCode(200);
        // e o inverso: beta em READ_ONLY nao mexe na chave de alfa (ACTIVE)
        setMode("alfa", alfaCompany, CompanyAccessMode.ACTIVE);
        setMode("beta", betaCompany, CompanyAccessMode.READ_ONLY);
        write(HOST_ALFA, alfaKey).then().statusCode(200);
        write(HOST_BETA, betaKey).then().statusCode(403).body("code", equalTo("COMPANY_READ_ONLY"));
    }

    // ---------------------------------------------------------------- helpers

    private Response read(String host, String key) {
        return given().header("Host", host).header("x-api-key", key).get("/probe/permission");
    }

    private Response write(String host, String key) {
        return given().header("Host", host).header("x-api-key", key).contentType(ContentType.JSON)
                .post("/probe/write");
    }

    /** O servico de acesso roda numa requisicao real, no escopo do tenant do Host (como o billing faria). */
    private void setMode(String tenant, String companyPublicId, CompanyAccessMode mode) {
        given().header("Host", tenant + "." + SUFFIX).post("/probe/public/access-mode/" + companyPublicId + "/" + mode)
                .then().statusCode(200);
    }

    private void addSecondCompanyToAlfaAdmin() throws Exception {
        if (Integer.parseInt(text(alfaDb, "SELECT count(*) FROM companies")) > 1) {
            return;
        }
        exec(alfaDb, "INSERT INTO companies (corporate_name, trade_name, cpf_cnpj, person_type, public_id, active) "
                + "SELECT 'Segunda LTDA', 'Segunda', '99888777000166', person_type, '" + UUID.randomUUID()
                + "', TRUE FROM companies ORDER BY id LIMIT 1");
        exec(alfaDb, "INSERT INTO user_companies (user_id, company_id) SELECT u.id, c.id FROM users u, companies c "
                + "WHERE u.email = 'admin@alfa.test' AND c.cpf_cnpj = '99888777000166'");
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

    private void exec(String db, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(tenantBaseUrl + "/" + db, dbUser, dbPass);
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private String text(String db, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(tenantBaseUrl + "/" + db, dbUser, dbPass);
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }
}
