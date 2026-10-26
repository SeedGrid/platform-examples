package br.com.seedgrid.logintenancyexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import br.com.seedgrid.commons.persistence.embeddables.Contact;
import br.com.seedgrid.logintenancy.entities.SignupRequestEntity;
import br.com.seedgrid.logintenancy.repositories.SignupRequestRepository;
import br.com.seedgrid.security.dtos.CompanyDTOPatch;
import br.com.seedgrid.security.dtos.UserDTOPatch;
import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.repositories.CompanyRepository;
import br.com.seedgrid.security.repositories.RoleRepository;
import br.com.seedgrid.security.repositories.UserRepository;
import br.com.seedgrid.security.services.CompanyService;
import br.com.seedgrid.security.services.PasswordService;
import br.com.seedgrid.security.services.SecretEncryptionService;
import br.com.seedgrid.security.services.UserService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * GUARDIAO DE SESSAO (Fase 2, T2.3) - NAO ALTERAR, ENFRAQUECER NEM DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> no login-tenancy (banco compartilhado, varias empresas), o access token (JWT, sem consulta
 * ao banco) deixa de valer NA HORA em que o acesso e' cortado: desativar o vinculo do usuario com a empresa, desativar
 * o usuario, trocar a senha e inativar a empresa gravam o marcador de revogacao no {@code seedgrid-cache}, e o token
 * emitido antes dele responde 401 {@code SESSION_REVOKED}. O escopo e' exato: o corte do vinculo/da empresa A nao
 * derruba quem e' de outra empresa, e um token novo (login depois da revogacao) segue valendo.</p>
 * <p><b>Por que existe:</b> antes (livro de regras X8, {@code ApiKeyIsolationGuardianIT}) o token antigo seguia
 * valendo ate 8 h depois do corte; decisao do dono (plano de escopo/acesso, 4.2): invalidar de imediato, com o
 * provider do cache como fonte da verdade.</p>
 * <p><b>Dano se quebrar:</b> ex-funcionario, usuario desativado por inadimplencia ou empresa inativada continua
 * lendo e gravando dados por ate 8 horas.</p>
 * Postgres real (Testcontainers), HTTP real, login real.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionRevocationGuardianIT {

    static final String PASSWORD = "Sup3r#Secret!";
    static final String PROBE_PATH = "/me/permissions";

    @Inject
    SecretEncryptionService secretEncryptionService;
    @Inject
    SignupRequestRepository signupRequestRepository;
    @Inject
    UserRepository userRepository;
    @Inject
    RoleRepository roleRepository;
    @Inject
    CompanyRepository companyRepository;
    @Inject
    PasswordService passwordService;
    @Inject
    UserService userService;
    @Inject
    CompanyService companyService;
    @Inject
    EntityManager em;
    @TestHTTPResource
    java.net.URL appUrl;

    @BeforeAll
    void port() {
        // Porta real do app (licoes de teste do plano): sem isto o RestAssured pode falar com outro processo local.
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = appUrl.getPort();
    }

    @Test
    void deactivatingTheMembershipRevokesTheOldTokenAtOnce() throws Exception {
        Company a = signupCompany("Empresa A");
        Company control = signupCompany("Empresa Controle");
        Member member = newMember(a);
        String memberToken = login(member.email());
        String ownerOfAToken = login(a.ownerEmail());
        String controlToken = login(control.ownerEmail());
        given().header("Authorization", "Bearer " + memberToken).get(PROBE_PATH).then().statusCode(200);

        userService.deactivateUserInCompany(member.publicId(), a.publicId(), "teste-guardiao");

        assertRevoked(memberToken);
        // escopo exato: o dono da empresa A e quem e' de outra empresa nao sao derrubados
        given().header("Authorization", "Bearer " + ownerOfAToken).get(PROBE_PATH).then().statusCode(200);
        given().header("Authorization", "Bearer " + controlToken).get(PROBE_PATH).then().statusCode(200);
    }

    @Test
    void deactivatingTheUserRevokesTheOldTokenAtOnce() {
        Company a = signupCompany("Empresa U");
        Member member = newMember(a);
        String memberToken = login(member.email());
        given().header("Authorization", "Bearer " + memberToken).get(PROBE_PATH).then().statusCode(200);

        UserDTOPatch patch = new UserDTOPatch();
        patch.setActive(false);
        userService.patchUserByPublicId(member.publicId(), patch);

        assertRevoked(memberToken);
    }

    @Test
    void changingThePasswordRevokesTheOldTokenAndANewLoginWorks() throws Exception {
        Company a = signupCompany("Empresa S");
        Member member = newMember(a);
        String memberToken = login(member.email());

        io.restassured.response.Response changed = given().header("Authorization", "Bearer " + memberToken)
                .contentType(ContentType.JSON)
                .body(Map.of("currentPassword", PASSWORD, "newPassword", "Nova#Senha859"))
                .put("/users/change-password");
        org.junit.jupiter.api.Assertions.assertEquals(200, changed.statusCode(), changed.body().asString());

        assertRevoked(memberToken);
        Thread.sleep(1100); // o iat do JWT tem resolucao de segundo
        String fresh = given().contentType(ContentType.JSON)
                .body(Map.of("login", member.email(), "password", "Nova#Senha859"))
                .post("/public/auth/login").then().statusCode(200).extract().path("token");
        given().header("Authorization", "Bearer " + fresh).get(PROBE_PATH).then().statusCode(200);
    }

    @Test
    void inactivatingTheCompanyRevokesEveryUserOfThatCompanyOnly() {
        Company a = signupCompany("Empresa I");
        Company control = signupCompany("Empresa Controle 2");
        String ownerToken = login(a.ownerEmail());
        String memberToken = login(newMember(a).email());
        String controlToken = login(control.ownerEmail());

        CompanyDTOPatch patch = new CompanyDTOPatch();
        patch.setActive(false);
        companyService.patch(a.publicId(), patch);

        assertRevoked(ownerToken);
        assertRevoked(memberToken);
        given().header("Authorization", "Bearer " + controlToken).get(PROBE_PATH).then().statusCode(200);
    }

    // ---------------------------------------------------------------- helpers

    private record Company(long id, String publicId, String ownerEmail) {
    }

    private record Member(String email, String publicId) {
    }

    private static void assertRevoked(String token) {
        given().header("Authorization", "Bearer " + token).get(PROBE_PATH)
                .then().statusCode(401)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("SESSION_REVOKED"));
    }

    private Member newMember(Company company) {
        String email = "member-" + UUID.randomUUID() + "@test.com";
        return QuarkusTransaction.requiringNew().call(() -> {
            User user = new User();
            user.setEmail(email);
            user.setName("Membro Teste");
            user.setWhatsApp("11966665555");
            user.setPassword("placeholder");
            user.activate();
            user.confirmEmail();
            user.clearChangePasswordRequired();
            user.getRoles().add(roleRepository.findByName("ADMIN").orElseThrow());
            user.addCompany(companyRepository.findById(company.id()).orElseThrow());
            userRepository.persist(user);
            user.setPassword(passwordService.encode(user.getPublicId() + PASSWORD));
            return new Member(email, user.getPublicId());
        });
    }

    private Company signupCompany(String name) {
        String email = "owner-" + UUID.randomUUID() + "@test.com";
        String cnpj = String.format("%014d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L));
        String token = String.format("%012d",
                Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L));
        QuarkusTransaction.requiringNew().run(() -> {
            SignupRequestEntity s = new SignupRequestEntity();
            s.email = email;
            s.name = name;
            s.corporateName = name + " LTDA";
            s.tradeName = name;
            s.cpfCnpj = cnpj;
            s.verificationToken = token;
            s.passwordHash = secretEncryptionService.encrypt(PASSWORD);
            s.legalRepresentative = Contact.builder().firstName("Owner").lastName(name).email(email).build();
            signupRequestRepository.persist(s);
        });
        given().contentType(ContentType.JSON).body(Map.of("token", token)).post("/public/signup/confirm-email")
                .then().statusCode(200);
        Object[] row = QuarkusTransaction.requiringNew().call(() -> (Object[]) em
                .createNativeQuery("SELECT id, public_id FROM sg_companies WHERE cpf_cnpj = :c").setParameter("c", cnpj)
                .getSingleResult());
        return new Company(((Number) row[0]).longValue(), (String) row[1], email);
    }

    private String login(String email) {
        String t = given().contentType(ContentType.JSON).body(Map.of("login", email, "password", PASSWORD))
                .post("/public/auth/login").then().statusCode(200).extract().path("token");
        assertNotNull(t);
        return t;
    }
}
