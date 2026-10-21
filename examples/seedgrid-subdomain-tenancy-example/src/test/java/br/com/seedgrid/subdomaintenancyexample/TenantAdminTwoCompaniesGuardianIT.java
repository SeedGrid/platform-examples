package br.com.seedgrid.subdomaintenancyexample;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * GUARDIAO (Fase 1, T1.8) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> no subdomain-tenancy ({@code dedicated-database}) o ADMIN de um tenant que passou a ter
 * 2 empresas continua administrando o proprio tenant: {@code /users}, {@code /roles}, {@code /companies} e
 * {@code /permissions} respondem 200 (o {@code @SgPlatformScope} e isento nesses recursos).</p>
 * <p><b>Por que existe:</b> com 2+ empresas no banco, a trava de plataforma (pensada para banco compartilhado entre
 * contas) passava a negar 403 ao dono do tenant em endpoints do proprio tenant.</p>
 * <p><b>Dano se quebrar:</b> tenant com varias empresas perde a administracao de usuarios, perfis e empresas.</p>
 * Postgres real, dois tenants provisionados, HTTP real com o {@code Host} de cada tenant.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(TenantAdminTwoCompaniesGuardianIT.Profile.class)
class TenantAdminTwoCompaniesGuardianIT extends ProactiveAuthGuardianBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return overrides(true);
        }
    }

    @Test
    void adminOfATenantWithTwoCompaniesStillAdministersTheTenant() throws Exception {
        // alfa nasce com 1 empresa; a segunda entra direto no banco do tenant (o @SgAudit do POST /companies exige as
        // entidades de auditoria, que este exemplo nao carrega - nao e' o objeto do teste).
        exec(alfaDb, "INSERT INTO companies (cpf_cnpj, corporate_name, trade_name, public_id) VALUES ('11444777000161', "
                + "'Filial LTDA', 'Filial', '" + java.util.UUID.randomUUID() + "')");

        for (String path : new String[] { "/users", "/roles", "/companies", "/permissions" }) {
            assertEquals(200,
                    given().header("Host", HOST_ALFA).auth().oauth2(alfaToken).get(path).statusCode(),
                    "GET " + path + " com 2 empresas no tenant");
        }
    }
}
