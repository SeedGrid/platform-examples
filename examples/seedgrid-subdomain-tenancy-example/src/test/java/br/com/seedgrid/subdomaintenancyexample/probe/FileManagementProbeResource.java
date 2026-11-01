package br.com.seedgrid.subdomaintenancyexample.probe;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import br.com.seedgrid.filemanagement.domains.StorageKey;
import br.com.seedgrid.filemanagement.domains.StorageOptions;
import br.com.seedgrid.filemanagement.services.FileStorageRegistry;
import br.com.seedgrid.filemanagement.spis.FileStorage;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Usado pelos guardioes FileManagement*GuardianIT (plano 120): grava/apaga um arquivo com o tenant da requisicao ativo e devolve o resultado. */
@Path("/probe/filemanagement")
@Produces(MediaType.TEXT_PLAIN)
@Authenticated
@Blocking
public class FileManagementProbeResource {

    private static final String BASE = System.getProperty("java.io.tmpdir") + "/fm-probe";
    private static volatile StorageKey last;

    @Inject
    FileStorageRegistry registry;


    @POST
    @Path("/store")
    public Response store() {
        try {
            FileStorage storage = registry.get("filesystem", Map.of("base-path", BASE));
            last = storage.store("probe-" + UUID.randomUUID() + ".txt", "ola".getBytes(StandardCharsets.UTF_8),
                    StorageOptions.builder().prefix("probe").contentType("text/plain").build());
            return Response.ok("STORED " + last.toUri()).build();
        } catch (Throwable t) {
            return Response.serverError().entity("FAIL " + t.getClass().getName() + ": " + root(t)).build();
        }
    }

    @POST
    @Path("/delete")
    public Response delete() {
        try {
            FileStorage storage = registry.get("filesystem", Map.of("base-path", BASE));
            storage.delete(last);
            return Response.ok("DELETED " + last.toUri()).build();
        } catch (Throwable t) {
            return Response.serverError().entity("FAIL " + t.getClass().getName() + ": " + root(t)).build();
        }
    }

    private static String root(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return t.getMessage() + " | causa raiz: " + r.getClass().getName() + ": " + r.getMessage();
    }
}
