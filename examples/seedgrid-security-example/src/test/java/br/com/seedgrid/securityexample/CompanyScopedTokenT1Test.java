package br.com.seedgrid.securityexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.entities.Company;
import br.com.seedgrid.security.entities.Permission;
import br.com.seedgrid.security.entities.Role;
import br.com.seedgrid.security.entities.UserCompany;
import br.com.seedgrid.security.services.PasswordService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * IT do modelo T1 (token escopado por empresa) contra Postgres real (Testcontainers).
 *
 * <p>Semente: 1 usuario com membership em 2 companies (A = role REPORT_VIEWER, nao-root; B = role
 * REPORT_ADMIN, root) e uma 3a company C SEM membership. Prova, pelo fluxo HTTP real:</p>
 * <ul>
 *   <li>login ja nasce ESCOPADO numa das companies do usuario (claim {@code companyId}), com
 *       {@code allowedCompanies} = [A, B] e {@code groups} = roles EFETIVAS daquela company;</li>
 *   <li>{@code POST /me/select-company} troca o escopo: o novo token vem com o {@code companyId} da
 *       company escolhida e as roles/{@code companyRoot} EFETIVOS dela;</li>
 *   <li>selecionar a company C (sem acesso) retorna 403.</li>
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CompanyScopedTokenT1Test {

    private static final String RAW_PASSWORD = "Sup3r#Secret!";
    private static final String EMAIL = "t1user@test.com";

    @Inject
    EntityManager em;

    @Inject
    PasswordService passwordService;

    private Seed seed;

    record Seed(String userPublicId, Long companyAId, String companyAPublicId,
            Long companyBId, String companyBPublicId, String companyCPublicId) {
    }

    @BeforeEach
    void setUp() {
        seed = QuarkusTransaction.requiringNew().call(this::seedData);
    }

    private Seed seedData() {
        // Idempotente: se ja semeado por um teste anterior, reusa.
        Long existing = (Long) em.createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :e")
                .setParameter("e", EMAIL).getSingleResult();
        if (existing != null && existing > 0) {
            return loadSeed();
        }

        // Roles exigem >= 1 permissao (validacao). Reusa uma do catalogo ja seeded pelas migrations.
        Permission anyPermission = (Permission) em.createQuery("SELECT p FROM Permission p ORDER BY p.id")
                .setMaxResults(1).getSingleResult();

        Role viewer = new Role("REPORT_VIEWER", "Le relatorios");
        Role admin = new Role("REPORT_ADMIN", "Administra relatorios");
        viewer.addPermission(anyPermission);
        admin.addPermission(anyPermission);
        em.persist(viewer);
        em.persist(admin);

        Company a = company("11111111000111", "Empresa A");
        Company b = company("22222222000122", "Empresa B");
        Company c = company("33333333000133", "Empresa C");
        em.persist(a);
        em.persist(b);
        em.persist(c);

        User user = new User();
        String publicId = UUID.randomUUID().toString();
        user.setUserPublicId(publicId);
        user.setEmail(EMAIL);
        user.setName("T1 User");
        user.setWhatsApp("5511988887777");
        user.setPassword(passwordService.encode(publicId + RAW_PASSWORD));
        user.confirmEmail();
        em.persist(user);
        em.flush();

        em.persist(new UserCompany(user, a, false, List.of(viewer)));
        em.persist(new UserCompany(user, b, true, List.of(admin)));
        em.flush();

        return new Seed(user.getPublicId(), a.getId(), a.getPublicId(),
                b.getId(), b.getPublicId(), c.getPublicId());
    }

    private Seed loadSeed() {
        User user = (User) em.createQuery("SELECT u FROM User u WHERE u.email = :e")
                .setParameter("e", EMAIL).getSingleResult();
        Company a = companyByCnpj("11111111000111");
        Company b = companyByCnpj("22222222000122");
        Company c = companyByCnpj("33333333000133");
        return new Seed(user.getPublicId(), a.getId(), a.getPublicId(),
                b.getId(), b.getPublicId(), c.getPublicId());
    }

    private Company companyByCnpj(String cnpj) {
        return (Company) em.createQuery("SELECT c FROM Company c WHERE c.cnpj = :c")
                .setParameter("c", cnpj).getSingleResult();
    }

    private Company company(String cnpj, String name) {
        Company c = new Company();
        c.setCnpj(cnpj);
        c.setCorporateName(name);
        c.setTradeName(name);
        return c;
    }

    @Test
    void loginScopesToAllowedCompanyAndSelectCompanySwapsScope() throws Exception {
        // 1) LOGIN — com MAIS de uma company escopavel, a sessao nasce NAO-escopada
        // (companyId = null): o cliente escolhe via POST /me/select-company. So ha
        // auto-escopo no login quando o usuario tem exatamente 1 company escopavel.
        String token = login();
        JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();

        List<Long> allowed = asLongList(claims.getClaim("allowedCompanies"));
        assertEquals(2, allowed.size(), "allowedCompanies = [A, B]");
        assertTrue(allowed.contains(seed.companyAId()) && allowed.contains(seed.companyBId()));

        Long scoped = asLong(claims.getClaim("companyId"));
        assertNull(scoped, "com >1 company escopavel o login NAO escopa (escolha via select-company)");

        // 2) SELECT COMPANY B — escopa a sessao na B (root, REPORT_ADMIN).
        String tokenB = selectCompany(token, seed.companyBPublicId());
        JWTClaimsSet claimsB = SignedJWT.parse(tokenB).getJWTClaimsSet();
        assertEquals(seed.companyBId(), asLong(claimsB.getClaim("companyId")));
        assertEquals(List.of("REPORT_ADMIN"), claimsB.getStringListClaim("groups"));
        assertEquals(Boolean.TRUE, claimsB.getBooleanClaim("companyRoot"), "B = root efetivo");

        // 3) SELECT COMPANY A — troca para a A (nao-root, REPORT_VIEWER).
        String tokenA = selectCompany(tokenB, seed.companyAPublicId());
        JWTClaimsSet claimsA = SignedJWT.parse(tokenA).getJWTClaimsSet();
        assertEquals(seed.companyAId(), asLong(claimsA.getClaim("companyId")));
        assertEquals(List.of("REPORT_VIEWER"), claimsA.getStringListClaim("groups"));
        Boolean rootA = claimsA.getBooleanClaim("companyRoot");
        assertFalse(rootA != null && rootA, "A nao e root");
    }

    @Test
    void selectCompanyWithoutMembershipIsForbidden() {
        String token = login();
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{\"companyPublicId\":\"" + seed.companyCPublicId() + "\"}")
                .when()
                .post("/me/select-company")
                .then()
                .statusCode(403);
    }

    private String login() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"login\":\"" + EMAIL + "\",\"password\":\"" + RAW_PASSWORD + "\"}")
                .when()
                .post("/public/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"))
                .extract().path("token");
    }

    private String selectCompany(String bearer, String companyPublicId) {
        return given()
                .header("Authorization", "Bearer " + bearer)
                .contentType(ContentType.JSON)
                .body("{\"companyPublicId\":\"" + companyPublicId + "\"}")
                .when()
                .post("/me/select-company")
                .then()
                .statusCode(200)
                .extract().path("token");
    }

    @SuppressWarnings("unchecked")
    private List<Long> asLongList(Object claim) {
        List<Object> raw = (List<Object>) claim;
        return raw.stream().map(this::asLong).toList();
    }

    private Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
