package br.com.seedgrid.filemanagementexample.consent;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * SERVIDOR FALSO de um provedor de nuvem (OAuth + API no estilo do Dropbox), so' para o exemplo rodar sem chave real.
 *
 * <p>Faz o papel do provedor: entrega e rotaciona tokens, guarda os arquivos na memoria e recusa (401) token de acesso
 * desconhecido. Nada disto existe num produto de verdade: la as URLs apontam para o provedor.</p>
 */
@ApplicationScoped
@Path("/fake-provider")
@Tag(name = "Fake provider", description = "Servidor OAuth/API falso do exemplo (nao faz parte da lib)")
public class FakeProviderResource {

    private static final Pattern PATH_ARG = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"");

    private final Set<String> validRefresh = ConcurrentHashMap.newKeySet();
    private final Set<String> validAccess = ConcurrentHashMap.newKeySet();
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final AtomicInteger counter = new AtomicInteger();

    @ConfigProperty(name = "app.fake-provider.expires-in", defaultValue = "30")
    int expiresIn;

    // ---- o que o provedor faria na volta do redirecionamento ----

    /** Faz de conta que o usuario autorizou: devolve o refresh token que o provedor entregaria. */
    public String authorize() {
        String refresh = "refresh-" + UUID.randomUUID();
        validRefresh.add(refresh);
        return refresh;
    }

    /** Faz de conta que o usuario revogou o acesso no painel do provedor. */
    public void revoke(String refreshToken) {
        validRefresh.remove(refreshToken);
        validAccess.clear();
    }

    /** Quantas vezes o endpoint de token foi chamado (para o teste provar a renovacao). */
    public int tokenCalls() {
        return tokenCalls.get();
    }

    // ---- OAuth ----

    @POST
    @Path("/oauth/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(@FormParam("grant_type") String grantType, @FormParam("refresh_token") String refreshToken) {
        tokenCalls.incrementAndGet();
        if (!"refresh_token".equals(grantType) || refreshToken == null || !validRefresh.remove(refreshToken)) {
            return Response.status(400).entity("{\"error\":\"invalid_grant\"}").build();
        }
        String newRefresh = "refresh-" + UUID.randomUUID();
        String access = "acesso-" + UUID.randomUUID();
        validRefresh.add(newRefresh);
        validAccess.add(access);
        return Response.ok("{\"access_token\":\"" + access + "\",\"refresh_token\":\"" + newRefresh
                + "\",\"expires_in\":" + expiresIn + "}").build();
    }

    // ---- API de arquivos (subconjunto do Dropbox) ----

    @POST
    @Path("/2/files/upload")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upload(@HeaderParam("Authorization") String auth, @HeaderParam("Dropbox-API-Arg") String arg,
                           byte[] body) {
        if (!authorized(auth)) {
            return unauthorized();
        }
        String id = "id:" + counter.incrementAndGet();
        byte[] content = body == null ? new byte[0] : body;
        files.put(id, content);
        String name = pathOf(arg);
        name = name.substring(name.lastIndexOf('/') + 1);
        return Response.ok("{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"size\":" + content.length + "}").build();
    }

    @POST
    @Path("/2/files/download")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@HeaderParam("Authorization") String auth, @HeaderParam("Dropbox-API-Arg") String arg) {
        if (!authorized(auth)) {
            return unauthorized();
        }
        byte[] content = files.get(pathOf(arg));
        return content == null ? Response.status(409).build() : Response.ok(content).build();
    }

    @POST
    @Path("/2/files/delete_v2")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@HeaderParam("Authorization") String auth, byte[] body) {
        if (!authorized(auth)) {
            return unauthorized();
        }
        Matcher m = PATH_ARG.matcher(new String(body, StandardCharsets.UTF_8));
        if (!m.find() || files.remove(m.group(1)) == null) {
            return Response.status(409).build();
        }
        return Response.ok("{\"metadata\":{}}").build();
    }

    @POST
    @Path("/2/files/get_metadata")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Response metadata(@HeaderParam("Authorization") String auth, byte[] body) {
        if (!authorized(auth)) {
            return unauthorized();
        }
        Matcher m = PATH_ARG.matcher(new String(body, StandardCharsets.UTF_8));
        if (!m.find() || !files.containsKey(m.group(1))) {
            return Response.status(409).build();
        }
        return Response.ok("{\"id\":\"" + m.group(1) + "\",\"name\":\"arquivo\",\"size\":"
                + files.get(m.group(1)).length + "}").build();
    }

    private boolean authorized(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                && validAccess.contains(authorization.substring(7));
    }

    private static Response unauthorized() {
        return Response.status(401).entity("{\"error_summary\":\"expired_access_token/\"}").build();
    }

    private static String pathOf(String arg) {
        Matcher m = PATH_ARG.matcher(arg == null ? "" : arg);
        return m.find() ? m.group(1) : "";
    }
}
