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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import br.com.seedgrid.commons.core.enums.StateEnum;
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
 * GUARDIAO DE SESSAO (Fase 2, T2.3) - NAO ALTERAR, ENFRAQUECER NEM DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> no subdomain-tenancy (banco por tenant), o access token (JWT, sem consulta ao banco) deixa
 * de valer NA HORA em que o acesso e' cortado: desativar o usuario, remover o vinculo com a empresa e trocar a senha
 * gravam o marcador de revogacao no {@code seedgrid-cache} (chave com o tenant da claim {@code subdomain}), e o token
 * emitido antes dele responde 401 {@code SESSION_REVOKED}. O marcador de um tenant nao derruba o token de outro, e um
 * token novo (login depois da revogacao) segue valendo.</p>
 * <p><b>Por que existe:</b> antes (livro de regras X8, {@code ApiKeyIsolationGuardianIT}) o token antigo seguia
 * valendo ate 8 h depois do corte; decisao do dono (plano de escopo/acesso, 4.2): invalidar de imediato, com o
 * provider do cache como fonte da verdade.</p>
 * <p><b>Dano se quebrar:</b> ex-funcionario ou usuario desativado continua lendo e gravando dados do tenant por ate 8
 * horas.</p>
 * Postgres real (Testcontainers), dois tenants provisionados, HTTP real com o {@code Host} de cada tenant.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(SessionRevocationGuardianIT.Profile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionRevocationGuardianIT {

    static final String SUFFIX = "crm.seedgrid.com.br";
    static final String HOST_ALFA = "alfa." + SUFFIX;
    static final String HOST_BETA = "beta." + SUFFIX;
    static final String PASSWORD = "Teste@123456";
    static final String PROBE_PATH = "/probe/me";

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "seedgrid.subdomain-tenancy.domain-suffix", SUFFIX,
                    // Se o header estiver configurado, ele VENCE o host na escolha do banco (QuarkusTenantResolver).
                    "seedgrid.subdomain-tenancy.tenant-header", "X-Tenant",
                    // Padrao do security (proactive=true): a revogacao tem de valer no caminho padrao.
                    "quarkus.http.auth.proactive", "true");
        }

        /**
         * Este exemplo nao carrega as entidades de auditoria (o @SgAudit de POST/PATCH /users estoura 500: ver
         * TenantAdminTwoCompaniesGuardianIT); estes substitutos descartam a escrita da auditoria SO' neste teste e
         * deixam o resto do fluxo (inclusive o marcador de revogacao) real.
         */
        @Override
        public java.util.Set<Class<?>> getEnabledAlternatives() {
            return java.util.Set.of(NoAudit.Log.class, NoAudit.Entities.class, NoAudit.Users.class);
        }
    }

    static final class NoAudit {
        @jakarta.enterprise.inject.Alternative
        @jakarta.enterprise.context.Dependent
        public static class Log extends br.com.seedgrid.audit.core.services.AuditLogService {
            @Inject
            public Log(br.com.seedgrid.audit.core.repositories.AuditLogRepository repository) {
                super(repository);
            }

            @Override
            public void persist(br.com.seedgrid.audit.core.entities.AuditLog auditLog) {
                // descartado
            }
        }

        @jakarta.enterprise.inject.Alternative
        @jakarta.enterprise.context.Dependent
        public static class Entities extends br.com.seedgrid.audit.core.services.AuditLogEntitiesService {
            @Inject
            public Entities(br.com.seedgrid.audit.core.repositories.AuditLogEntitiesRepository repository) {
                super(repository);
            }

            @Override
            public void insertEntityIfNotFound(String entityName) {
                // descartado
            }
        }

        @jakarta.enterprise.inject.Alternative
        @jakarta.enterprise.context.Dependent
        public static class Users extends br.com.seedgrid.audit.core.services.AuditLogUsersService {
            @Inject
            public Users(br.com.seedgrid.audit.core.repositories.AuditLogUsersRepository repository) {
                super(repository);
            }

            @Override
            public void insertUserIfNotFound(String userName) {
                // descartado
            }
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

    String alfaDb;
    String betaDb;
    String alfaAdminToken;
    String betaAdminToken;
    String betaCompanyPublicId;
    String betaAdminRolePublicId;

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

        alfaAdminToken = login(HOST_ALFA, "admin@alfa.test", PASSWORD);
        betaAdminToken = login(HOST_BETA, "admin@beta.test", PASSWORD);
        betaCompanyPublicId = given().header("Host", HOST_BETA).auth().oauth2(betaAdminToken).get("/me/companies")
                .then().statusCode(200).extract().jsonPath().getString("[0].publicId");
        assertNotNull(betaCompanyPublicId, "o tenant beta precisa ter uma empresa");
        betaAdminRolePublicId = query(betaDb, "SELECT public_id FROM roles WHERE name = 'ADMIN' AND company_id IS NULL");
    }

    @Test
    void deactivatingTheUserRevokesTheOldTokenAtOnceAndOtherTenantsAreUntouched() {
        Member member = newMemberOfBeta();
        given().header("Host", HOST_BETA).auth().oauth2(member.token()).get(PROBE_PATH).then().statusCode(200);

        given().header("Host", HOST_BETA).auth().oauth2(betaAdminToken).contentType(ContentType.JSON)
                .body(Map.of("active", false)).patch("/users/" + member.publicId()).then().statusCode(200);

        assertRevoked(HOST_BETA, member.token());
        // o proprio admin do tenant e o admin de OUTRO tenant seguem valendo
        given().header("Host", HOST_BETA).auth().oauth2(betaAdminToken).get(PROBE_PATH).then().statusCode(200);
        given().header("Host", HOST_ALFA).auth().oauth2(alfaAdminToken).get(PROBE_PATH).then().statusCode(200);
    }

    @Test
    void removingTheMembershipRevokesTheOldTokenAtOnce() {
        Member member = newMemberOfBeta();
        given().header("Host", HOST_BETA).auth().oauth2(member.token()).get(PROBE_PATH).then().statusCode(200);

        given().header("Host", HOST_BETA).auth().oauth2(betaAdminToken)
                .delete("/users/" + member.publicId() + "/memberships/" + betaCompanyPublicId)
                .then().statusCode(org.hamcrest.Matchers.lessThan(300));

        assertRevoked(HOST_BETA, member.token());
        given().header("Host", HOST_BETA).auth().oauth2(betaAdminToken).get(PROBE_PATH).then().statusCode(200);
    }

    @Test
    void changingThePasswordRevokesTheOldTokenAndANewLoginWorks() throws Exception {
        Member member = newMemberOfBeta();

        given().header("Host", HOST_BETA).auth().oauth2(member.token()).contentType(ContentType.JSON)
                .body(Map.of("currentPassword", PASSWORD, "newPassword", "Nova@Senha859"))
                .put("/users/change-password").then().statusCode(200);

        assertRevoked(HOST_BETA, member.token());
        Thread.sleep(1100); // o iat do JWT tem resolucao de segundo
        String fresh = login(HOST_BETA, member.email(), "Nova@Senha859");
        given().header("Host", HOST_BETA).auth().oauth2(fresh).get(PROBE_PATH).then().statusCode(200);
    }

    // ---------------------------------------------------------------- helpers

    private record Member(String email, String publicId, String token) {
    }

    private static void assertRevoked(String host, String token) {
        given().header("Host", host).auth().oauth2(token).get(PROBE_PATH)
                .then().statusCode(401)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("SESSION_REVOKED"));
    }

    /** Cria o usuario pela API (admin do tenant beta), com e-mail ja' confirmado, e faz o login dele. */
    private Member newMemberOfBeta() {
        String email = "membro-" + UUID.randomUUID() + "@beta.test";
        Response created = given().header("Host", HOST_BETA).auth().oauth2(betaAdminToken)
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "name", "Membro Beta", "whatsApp", "11999997777",
                        "password", PASSWORD, "emailPreConfirmed", true, "changePasswordRequired", false,
                        "rolePublicIds", List.of(betaAdminRolePublicId),
                        "memberships", List.of(Map.of("companyPublicId", betaCompanyPublicId,
                                "rolePublicIds", List.of()))))
                .post("/users");
        assertEquals(201, created.statusCode(), "criar usuario: " + created.body().asString());
        return new Member(email, created.jsonPath().getString("publicId"), login(HOST_BETA, email, PASSWORD));
    }

    private String login(String host, String email, String password) {
        Response r = given().header("Host", host).contentType(ContentType.JSON)
                .body(Map.of("login", email, "password", password)).post("/public/auth/login");
        assertEquals(200, r.statusCode(), "login " + email + ": " + r.body().asString());
        String token = r.jsonPath().getString("token");
        assertNotNull(token, "login sem token: " + r.body().asString());
        return token;
    }

    /**
     * Cadastro real (TenantService.save grava o tenant PENDENTE no master e faz o hash da senha) e, no lugar do
     * codigo de e-mail, ativa o tenant como o validateTenant faria: provisiona o banco e marca ativo.
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

    private String query(String db, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(tenantBaseUrl + "/" + db, dbUser, dbPass);
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
