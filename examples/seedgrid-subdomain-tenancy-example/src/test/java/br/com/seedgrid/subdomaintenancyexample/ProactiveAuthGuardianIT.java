package br.com.seedgrid.subdomaintenancyexample;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * GUARDIAO X11 - NAO ALTERAR, ENFRAQUECER NEM DESABILITAR sem decisao explicita do dono.
 *
 * <p><b>O que protege:</b> o subdomain-tenancy funciona com a autenticacao proativa do Quarkus no padrao do
 * security ({@code quarkus.http.auth.proactive=true}); JWT em endpoint com permissao responde 200/403, nunca 500.</p>
 * <p><b>Por que existe:</b> com o padrao, o augmentor de RBAC roda fora do contexto de requisicao e o
 * {@code TenantContext} estourava {@code ContextNotActiveException} (500), obrigando o host a desligar a opcao.</p>
 * <p><b>Dano se quebrar:</b> todo host precisa de {@code proactive=false} para nao devolver 500 em endpoints protegidos.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(ProactiveAuthGuardianIT.Profile.class)
class ProactiveAuthGuardianIT extends ProactiveAuthGuardianBase {
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return overrides(true);
        }
    }
}
