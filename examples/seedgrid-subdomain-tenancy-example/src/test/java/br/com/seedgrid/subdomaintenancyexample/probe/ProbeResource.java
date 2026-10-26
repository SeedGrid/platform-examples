package br.com.seedgrid.subdomaintenancyexample.probe;

import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.common.annotation.Blocking;
import br.com.seedgrid.security.entities.CompanyAccessMode;
import br.com.seedgrid.security.services.CompanyAccessService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Recurso SO DE TESTE: devolve o nome do banco que atendeu a requisicao. Prova, na pratica, em qual
 * banco (tenant) a credencial foi resolvida. Usado pelos testes de isolamento entre tenants.
 */
@Path("/probe")
@Produces(MediaType.TEXT_PLAIN)
@Blocking
public class ProbeResource {

    @Inject
    EntityManager em;

    @Inject
    CompanyAccessService companyAccess;

    /**
     * Muda o estado de acesso da empresa NO TENANT do Host (o que o billing faria no escopo do tenant). So' teste:
     * o servico precisa rodar numa requisicao real para o tenant ser resolvido.
     */
    @POST
    @Path("/public/access-mode/{companyPublicId}/{mode}")
    @jakarta.annotation.security.PermitAll
    public String setAccessMode(@PathParam("companyPublicId") String companyPublicId,
            @PathParam("mode") CompanyAccessMode mode) {
        companyAccess.setMode(companyPublicId, mode, "teste");
        return mode.name();
    }

    /** Exige a permissao PROBE_READ (a role de integracao do teste a tem). */
    @GET
    @Path("/permission")
    @PermissionsAllowed("PROBE_READ")
    @Transactional
    public String withPermission() {
        return currentDatabase();
    }

    /** Escrita (POST) com a mesma permissao: prova o estado de acesso da empresa (READ_ONLY barra, ACTIVE passa). */
    @POST
    @Path("/write")
    @PermissionsAllowed("PROBE_READ")
    @Transactional
    public String write() {
        return currentDatabase();
    }

    /** Permissao que SO o tenant alfa concede a role ADMIN (teste de vazamento do cache de RBAC). */
    @GET
    @Path("/only-alfa")
    @PermissionsAllowed("PROBE_ALFA")
    @Transactional
    public String onlyAlfa() {
        return currentDatabase();
    }

    /** Permissao que SO o tenant beta concede a role ADMIN. */
    @GET
    @Path("/only-beta")
    @PermissionsAllowed("PROBE_BETA")
    @Transactional
    public String onlyBeta() {
        return currentDatabase();
    }

    /** Qualquer credencial valida (JWT ou API key). */
    @GET
    @Path("/me")
    @Authenticated
    @Transactional
    public String authenticated() {
        return currentDatabase();
    }

    /** Sem autenticacao: so mostra qual banco a resolucao de tenant escolheu para o Host. */
    @GET
    @Path("/anon")
    @jakarta.annotation.security.PermitAll
    @Transactional
    public String anonymous() {
        return currentDatabase();
    }

    private String currentDatabase() {
        return (String) em.createNativeQuery("select current_database()").getSingleResult();
    }
}
