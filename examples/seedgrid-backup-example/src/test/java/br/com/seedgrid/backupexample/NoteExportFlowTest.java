package br.com.seedgrid.backupexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.seedgrid.security.entities.BaseUser;
import br.com.seedgrid.security.repositories.BaseUserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Demonstra o fluxo COMPLETO do seedgrid-backup usando o dominio deste app (Note) como dado real:
 * usuario loga (JWT do seedgrid-security), cria uma anotacao, pede um export (POST /exports,
 * endpoint pronto da extension), acompanha o job ate DONE (pipeline assincrono REAL,
 * {@code seedgrid.backup.async-enabled=true}) e baixa o ZIP gerado de verdade pelo worker.
 *
 * <p><b>Nota historica</b>: ate a versao 2026.07.02 da extension este teste NAO conseguia exercitar
 * o pipeline assincrono real — 3 bugs (schema "default" em host standalone, deadlock do worker em
 * virtual thread + JTA aninhado, e entidade destacada no saveArtifacts) obrigavam a simular a
 * conclusao anexando um artefato manualmente via repository. Os 3 foram corrigidos na
 * 2026.07.08 (detalhes no README), entao o teste agora valida o ciclo de ponta a ponta.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class NoteExportFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    /** Timeout generoso: o worker roda em background e o ambiente de CI pode ser lento. */
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    @Inject
    BaseUserRepository userRepository;

    @BeforeEach
    void confirmBootstrapAdminEmail() {
        // O FirstAdminBootstrapService (seedgrid-security) cria o primeiro admin com o e-mail
        // NAO confirmado e changePasswordRequired=true; confirmamos direto no banco pra poder
        // logar no teste (fluxo real exigiria clicar no link do e-mail de boas-vindas).
        QuarkusTransaction.requiringNew().run(() -> {
            BaseUser admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
            admin.confirmEmail();
            admin.clearChangePasswordRequired();
        });
    }

    @Test
    void asyncExportRunsToCompletionAndServesGeneratedZip() {
        String token = loginAdmin();

        String noteTitle = "Ideia de negocio " + System.currentTimeMillis();
        createNote(token, noteTitle, "Anotacao pessoal usada como dado real do export.");

        Response created = given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("format", "CSV_ZIP", "scope", "RAW"))
                .when()
                .post("/exports")
                .then()
                .statusCode(201)
                .extract().response();

        String jobId = created.path("jobId");
        assertAll("job recem-criado",
                () -> assertNotNull(jobId, "jobId presente na resposta do POST /exports"),
                () -> assertEquals("QUEUED", created.path("status")),
                // Host standalone resolve o schema fisico real do Postgres ("public"): e o
                // SingleTenantBackupProvider default da extension, sem nenhum override neste app.
                () -> assertEquals("public", created.path("databaseSchema")));

        Response finished = awaitTerminalStatus(token, jobId);

        assertAll("ciclo assincrono completo",
                () -> assertEquals("DONE", finished.path("status"),
                        "job deveria terminar DONE (errorMessage="
                                + finished.path("errorMessage") + ")"),
                () -> assertEquals(Integer.valueOf(100), finished.path("progress")),
                () -> assertEquals(Boolean.TRUE, finished.path("downloadable")),
                () -> {
                    List<String> artifactTypes = finished.path("artifacts.type");
                    assertNotNull(artifactTypes, "job DONE deve ter artefatos");
                    assertTrue(artifactTypes.contains("ZIP"),
                            "job DONE deve ter artefato ZIP, tipos=" + artifactTypes);
                });

        byte[] zip = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/exports/" + jobId + "/download")
                .then()
                .statusCode(200)
                .extract().asByteArray();

        assertAll("download do ZIP gerado pelo worker",
                () -> assertTrue(zip.length > 0, "ZIP baixado nao pode ser vazio"),
                // Assinatura "PK" de arquivo ZIP: bytes reais gerados pelo pipeline, nao mock.
                () -> assertEquals('P', (char) zip[0]),
                () -> assertEquals('K', (char) zip[1]));
    }

    /**
     * Polling simples (mesmo padrao do ExportAsyncFlowIT da propria extension) ate o job sair de
     * QUEUED/RUNNING ou estourar o timeout.
     */
    private Response awaitTerminalStatus(String token, String jobId) {
        Instant deadline = Instant.now().plus(ASYNC_TIMEOUT);
        Response last = null;

        while (Instant.now().isBefore(deadline)) {
            last = given()
                    .header("Authorization", "Bearer " + token)
                    .when()
                    .get("/exports/" + jobId)
                    .then()
                    .statusCode(200)
                    .extract().response();

            String status = last.path("status");
            if (!"QUEUED".equals(status) && !"RUNNING".equals(status)) {
                return last;
            }

            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fail("Polling interrompido");
            }
        }

        return fail("Export nao terminou em " + ASYNC_TIMEOUT + "; ultimo status: "
                + (last == null ? "nenhum" : (String) last.path("status")));
    }

    @Test
    void listsOwnExportJobsForCurrentUser() {
        String token = loginAdmin();
        createNote(token, "Nota para listagem", "conteudo");
        createExportJob(token);

        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("page", 0)
                .queryParam("size", 10)
                .when()
                .get("/exports")
                .then()
                .statusCode(200)
                .body("size()", org.hamcrest.Matchers.greaterThanOrEqualTo(1));
    }

    @Test
    void downloadOfUnknownJobIdReturns404() {
        String token = loginAdmin();

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/exports/00000000-0000-0000-0000-000000000000/download")
                .then()
                .statusCode(404);
    }

    private String loginAdmin() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"login\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .when()
                .post("/public/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"))
                .extract().path("token");
    }

    private void createNote(String bearer, String title, String content) {
        given()
                .header("Authorization", "Bearer " + bearer)
                .contentType(ContentType.JSON)
                .body(Map.of("title", title, "content", content))
                .when()
                .post("/api/notes")
                .then()
                .statusCode(201);
    }

    private String createExportJob(String bearer) {
        return given()
                .header("Authorization", "Bearer " + bearer)
                .contentType(ContentType.JSON)
                .body(Map.of("format", "CSV_ZIP", "scope", "RAW"))
                .when()
                .post("/exports")
                .then()
                .statusCode(201)
                .extract().path("jobId");
    }

}
