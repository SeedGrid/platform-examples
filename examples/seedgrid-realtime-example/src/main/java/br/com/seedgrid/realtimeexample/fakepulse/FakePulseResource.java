package br.com.seedgrid.realtimeexample.fakepulse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * SERVIDOR FALSO do SeedGrid Pulse, so' para o exemplo rodar sem Pulse real.
 *
 * <p>Fala o pedaco do contrato que o realtime usa: emite o token de conexao do usuario e aceita a publicacao de
 * mensagens, guardando na memoria o que recebeu (para o {@code GET /fake-pulse/received}). Nada disto existe num produto:
 * la {@code seedgrid.realtime.pulse.base-url} aponta para o Pulse de verdade.</p>
 */
@ApplicationScoped
@Path("/fake-pulse")
@Tag(name = "Fake Pulse", description = "Pulse falso do exemplo (nao faz parte da extension)")
public class FakePulseResource {

    private final List<Map<String, Object>> received = new CopyOnWriteArrayList<>();

    @POST
    @Path("/api/v1/connect-tokens")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response connectToken(@HeaderParam("x-api-key") String projectKey, Map<String, Object> body) {
        if (projectKey == null || projectKey.isBlank()) {
            return Response.status(401).build();
        }
        long expiresAt = Instant.now().getEpochSecond() + 120;
        return Response.status(201).entity(Map.of(
                "token", "pct_" + UUID.randomUUID().toString().replace("-", ""),
                "expiresAt", expiresAt,
                "connectUrl", "wss://pulse.exemplo.invalid/api/v1/connect",
                "subject", String.valueOf(body.get("subject")))).build();
    }

    @POST
    @Path("/api/v1/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response publish(@HeaderParam("x-api-key") String projectKey, Map<String, Object> body) {
        if (projectKey == null || projectKey.isBlank()) {
            return Response.status(401).build();
        }
        received.add(body);
        return Response.status(202).entity(Map.of(
                "messageId", "msg_" + received.size(),
                "status", "accepted",
                "createdAt", String.valueOf(Instant.now().getEpochSecond()))).build();
    }

    /** O que o realtime publicou ate agora (so' o exemplo tem isto, para voce ver o aviso sair). */
    @GET
    @Path("/received")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, Object>> received() {
        return List.copyOf(received);
    }

    /** Esquece o que foi recebido (usado pelos testes). */
    public void clear() {
        received.clear();
    }
}
