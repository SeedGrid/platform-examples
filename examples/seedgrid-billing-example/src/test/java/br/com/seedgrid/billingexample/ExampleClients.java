package br.com.seedgrid.billingexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import br.com.seedgrid.security.entities.Company;
import br.com.seedgrid.security.entities.User;
import br.com.seedgrid.security.entities.UserCompany;
import br.com.seedgrid.security.repositories.CompanyRepository;
import br.com.seedgrid.security.repositories.RoleRepository;
import br.com.seedgrid.security.repositories.UserRepository;
import br.com.seedgrid.security.services.PasswordService;
import jakarta.enterprise.context.ApplicationScoped;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;

/**
 * Cria no banco uma CONTA CLIENTE (empresa + dono) sem passar pelo signup, que nao faz parte do
 * {@code seedgrid-security} sozinho (e' do login-tenancy/subdomain-tenancy). O dono e' o responsavel
 * da conta: root da empresa e ADMIN so' no vinculo, exatamente o cenario do signup.
 */
@ApplicationScoped
public class ExampleClients {

    public static final String PASSWORD = "Senha@1234";

    /** Login pela API publica; devolve o token do usuario. */
    public static String login(String email, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("login", email, "password", password))
                .when()
                .post("/public/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"))
                .extract().path("token");
    }

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final PasswordService passwordService;

    public ExampleClients(UserRepository userRepository, RoleRepository roleRepository,
            CompanyRepository companyRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.companyRepository = companyRepository;
        this.passwordService = passwordService;
    }

    /** Devolve o e-mail de login do dono da nova conta cliente. */
    @Transactional
    public String newClientOwnerEmail() {
        Company company = new Company();
        company.setCpfCnpj(String.format("%014d", ThreadLocalRandom.current().nextLong(10_000_000_000_000L)));
        company.setCorporateName("Cliente " + UUID.randomUUID());
        company.setTradeName("Cliente Exemplo");
        companyRepository.persist(company);

        User owner = new User();
        owner.setEmail("owner-" + UUID.randomUUID() + "@billing-example.test");
        owner.setName("Dono " + UUID.randomUUID().toString().substring(0, 8));
        owner.setWhatsApp("11999990000");
        owner.setPassword("placeholder");
        owner.activate();
        owner.confirmEmail();
        owner.clearChangePasswordRequired();
        owner.setRoles(new ArrayList<>());
        owner.addCompany(company);
        userRepository.persist(owner);
        UserCompany membership = owner.membershipFor(company.getId()).orElseThrow();
        membership.setRoot(true);
        membership.getRoles().add(roleRepository.findByName("ADMIN").orElseThrow());
        owner.setPassword(passwordService.encode(owner.getPublicId() + PASSWORD));
        return owner.getEmail();
    }
}
