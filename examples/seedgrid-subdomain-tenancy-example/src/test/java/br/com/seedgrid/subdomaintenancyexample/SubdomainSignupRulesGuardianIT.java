package br.com.seedgrid.subdomaintenancyexample;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.seedgrid.subdomaintenancy.dtos.TenantSignupRequest;
import br.com.seedgrid.subdomaintenancy.exceptions.ReservedSubdomainException;
import br.com.seedgrid.subdomaintenancy.services.TenantService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * GUARDIAO (plano 121, DEM3) - NAO ALTERAR, ENFRAQUECER nem DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> o cadastro recusa nomes reservados da plataforma ({@code master}, {@code www}, {@code api},
 * {@code admin}...), em qualquer caixa, antes de gravar o tenant. O padrao de formato (sem {@code _}, hifen no meio) e'
 * coberto pelo {@code SubdomainRulesGuardianTest} da extension.</p>
 * <p><b>Dano se quebrar:</b> um cliente cadastra o tenant {@code master} e colide com o banco/contexto da plataforma.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(SubdomainSignupRulesGuardianIT.Profile.class)
class SubdomainSignupRulesGuardianIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("seedgrid.subdomain-tenancy.domain-suffix", "crm.seedgrid.com.br");
        }
    }

    @Inject
    TenantService tenantService;

    private TenantSignupRequest request(String subdomain) {
        return TenantSignupRequest.builder().subdomain(subdomain).cnpj("11222333000181")
                .corporateName("Reservado LTDA").tradeName("Reservado").rootEmail("admin@reservado.test")
                .plainRootPassword("Teste@123456").build();
    }

    @Test
    void nomesReservadosSaoRecusadosNoCadastro() {
        for (String reserved : new String[] { "master", "MASTER", "www", "api", "admin" }) {
            assertThrows(ReservedSubdomainException.class, () -> tenantService.save(request(reserved)), reserved);
        }
    }
}
