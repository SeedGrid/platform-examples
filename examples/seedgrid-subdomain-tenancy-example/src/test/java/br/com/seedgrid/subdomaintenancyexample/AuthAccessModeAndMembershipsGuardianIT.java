package br.com.seedgrid.subdomaintenancyexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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
 * GUARDIAO (Fase 5, T5.12) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> no subdomain-tenancy (banco POR tenant), login e refresh devolvem
 * {@code currentCompany.accessMode} (empresa {@code READ_ONLY} loga e devolve {@code READ_ONLY}), e
 * {@code GET /users/{id}} devolve {@code memberships} (empresa + perfis) so' do banco do tenant do Host: o
 * usuario de OUTRO tenant nem e' encontrado (404). Postgres real e dois tenants provisionados.</p>
 * <p><b>Por que existe:</b> a faixa "somente leitura" e a tela de usuario do front leem esses campos sem /me/companies;
 * o isolamento entre tenants e' o que impede um tenant de ler vinculos do outro.</p>
 * <p><b>Dano se quebrar:</b> cliente escrevendo em conta somente leitura sem aviso, ou vazamento entre tenants.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(AuthAccessModeAndMembershipsGuardianIT.Profile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthAccessModeAndMembershipsGuardianIT {

    static final String SUFFIX = "crm.seedgrid.com.br";
    static final String HOST_ALFA = "alfa." + SUFFIX;
    static final String HOST_BETA = "beta." + SUFFIX;
    static final String PASSWORD = "Teste@123456";

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "seedgrid.subdomain-tenancy.domain-suffix", SUFFIX,
                    "seedgrid.subdomain-tenancy.tenant-header", "X-Tenant",
                    // Padrao do security (proactive=true), como no ApiKeyIsolationGuardianIT.
                    "quarkus.http.auth.proactive", "true",
                    // setMode e' @SgAudit: com audit JPA (default do teste) a entidade de audit precisa estar no PU do tenant.
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

    String alfaDb, betaDb, alfaCompany, betaCompany;

    @BeforeAll
    void setUp() throws Exception {
        io.restassured.RestAssured.baseURI = "http://localhost";
        io.restassured.RestAssured.port = appUrl.getPort();
        alfaDb = Utils.buildTenantDatabaseName("alfa", productPrefix);
        betaDb = Utils.buildTenantDatabaseName("beta", productPrefix);
        provision("alfa", "11222333000181");
        provision("beta", "11444777000161");
        alfaCompany = text(alfaDb, "SELECT public_id FROM sg_companies ORDER BY id LIMIT 1");
        betaCompany = text(betaDb, "SELECT public_id FROM sg_companies ORDER BY id LIMIT 1");
    }

    @AfterEach
    void backToActive() {
        setMode("alfa", alfaCompany, CompanyAccessMode.ACTIVE);
        setMode("beta", betaCompany, CompanyAccessMode.ACTIVE);
    }

    @Test
    void loginAndRefreshReturnTheAccessModeOfTheCurrentCompany() {
        Response active = loginResponse(HOST_ALFA, "admin@alfa.test");
        assertEquals("ACTIVE", active.jsonPath().getString("currentCompany.accessMode"));
        assertEquals(alfaCompany, active.jsonPath().getString("currentCompany.publicId"));

        setMode("alfa", alfaCompany, CompanyAccessMode.READ_ONLY);
        Response readOnly = loginResponse(HOST_ALFA, "admin@alfa.test");
        assertEquals("READ_ONLY", readOnly.jsonPath().getString("currentCompany.accessMode"));

        given().header("Host", HOST_ALFA).contentType(ContentType.JSON)
                .body(Map.of("refreshToken", readOnly.jsonPath().getString("refreshToken")))
                .post("/public/auth/refresh")
                .then().statusCode(200)
                .body("currentCompany.accessMode", equalTo("READ_ONLY"))
                .body("currentCompany.publicId", equalTo(alfaCompany));

        // o outro tenant segue ACTIVE
        assertEquals("ACTIVE", loginResponse(HOST_BETA, "admin@beta.test").jsonPath()
                .getString("currentCompany.accessMode"));
    }

    @Test
    void userMembershipsComeFromTheTenantDatabaseOnly() throws Exception {
        String alfaToken = login(HOST_ALFA, "admin@alfa.test");
        String alfaAdmin = text(alfaDb, "SELECT public_id FROM sg_users WHERE email = 'admin@alfa.test'");
        String betaAdmin = text(betaDb, "SELECT public_id FROM sg_users WHERE email = 'admin@beta.test'");

        given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).get("/users/" + alfaAdmin)
                .then().statusCode(200)
                .body("memberships", hasSize(1))
                .body("memberships[0].companyPublicId", equalTo(alfaCompany))
                .body("memberships[0].companyName", notNullValue())
                .body("memberships[0].active", equalTo(true))
                .body("memberships[0].roles.name", hasItem("ADMIN"))
                .body("memberships[0].roles[0].publicId", notNullValue());

        // usuario do outro tenant: nao existe no banco de alfa
        given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).get("/users/" + betaAdmin)
                .then().statusCode(404);
        // e no host de beta so' aparece a empresa de beta
        given().header("Host", HOST_BETA).auth().oauth2(login(HOST_BETA, "admin@beta.test"))
                .get("/users/" + betaAdmin)
                .then().statusCode(200)
                .body("memberships", hasSize(1))
                .body("memberships[0].companyPublicId", equalTo(betaCompany));
    }

    // ---------------------------------------------------------------- helpers

    /** O servico de acesso roda numa requisicao real, no escopo do tenant do Host (como o billing faria). */
    private void setMode(String tenant, String companyPublicId, CompanyAccessMode mode) {
        given().header("Host", tenant + "." + SUFFIX).post("/probe/public/access-mode/" + companyPublicId + "/" + mode)
                .then().statusCode(200);
    }

    private String login(String host, String email) {
        Response r = loginResponse(host, email);
        String token = r.jsonPath().getString("token");
        assertNotNull(token, "login sem token: " + r.body().asString());
        return token;
    }

    private Response loginResponse(String host, String email) {
        Response r = given().header("Host", host).contentType(ContentType.JSON)
                .body(Map.of("login", email, "password", PASSWORD)).post("/public/auth/login");
        assertEquals(200, r.statusCode(), "login " + email + ": " + r.body().asString());
        return r;
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
