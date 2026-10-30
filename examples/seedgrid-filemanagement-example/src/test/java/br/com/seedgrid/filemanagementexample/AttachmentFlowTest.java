package br.com.seedgrid.filemanagementexample;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.seedgrid.filemanagementexample.consent.FakeProviderResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Anexos LIGADOS num host de verdade: o app indexa a lib (quarkus.index-dependency), liga o recurso em
 * application.properties e o runner da lib cria sg_attachments na subida. Os dois modos de autenticacao passam pelo
 * mesmo caminho: grava, le pelo ID do anexo, renova token, revoga.
 */
@QuarkusTest
class AttachmentFlowTest {

    @Inject
    EntityManager em;

    @Inject
    FakeProviderResource provider;

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String body(String owner, String storage, String filename, String content) {
        return "{\"ownerKey\":\"" + owner + "\",\"storage\":\"" + storage + "\",\"filename\":\"" + filename
                + "\",\"contentType\":\"text/plain\",\"contentBase64\":\"" + b64(content) + "\"}";
    }

    private long rowsFor(String owner) {
        return ((Number) em.createNativeQuery("select count(*) from sg_attachments where owner_key = :o")
                .setParameter("o", owner).getSingleResult()).longValue();
    }

    @Test
    void migrationDaLibCriouATabelaNoHostERegistraOHistoricoProprio() {
        Number tabela = (Number) em.createNativeQuery(
                "select count(*) from information_schema.tables where table_name = 'sg_attachments'").getSingleResult();
        Number historico = (Number) em.createNativeQuery("select count(*) from information_schema.tables "
                + "where table_name = 'sg_flyway_schema_history_filemanagement'").getSingleResult();

        assertThat(tabela.intValue()).isEqualTo(1);
        assertThat(historico.intValue()).isEqualTo(1);
    }

    @Test
    void filesystemGravaLePeloIdDoAnexoEApagaArquivoELinha() {
        String owner = "fs-" + UUID.randomUUID();

        String id = given().contentType(ContentType.JSON).body(body(owner, "filesystem", "nota.txt", "ola anexo"))
                .post("/attachments").then().statusCode(201)
                .body("storage", equalTo("filesystem")).body("ownerKey", equalTo(owner)).body("sizeBytes", equalTo(9))
                .extract().path("id");

        assertThat(rowsFor(owner)).isEqualTo(1);
        assertThat(given().get("/attachments/{id}/content", id).then().statusCode(200).extract().asString())
                .isEqualTo("ola anexo");
        given().queryParam("ownerKey", owner).get("/attachments").then().statusCode(200).body("$", hasSize(1));

        given().delete("/attachments/{id}", id).then().statusCode(204);
        assertThat(rowsFor(owner)).isZero();
        given().get("/attachments/{id}/content", id).then().statusCode(404);
    }

    @Test
    void consentGravaNoProvedorDoUsuarioLePeloIdRenovaOTokenERevogaPedindoConsentimento() {
        String owner = "empresa-" + UUID.randomUUID();
        given().post("/consent/{o}", owner).then().statusCode(201);

        String id = given().contentType(ContentType.JSON).body(body(owner, "dropbox", "contrato.txt", "no drive do usuario"))
                .post("/attachments").then().statusCode(201).body("storage", equalTo("dropbox")).extract().path("id");
        int depoisDoStore = provider.tokenCalls();

        // outra operacao: o token do servidor falso vale 30 s (< 60 s de folga), entao a lib renova de novo
        assertThat(given().get("/attachments/{id}/content", id).then().statusCode(200).extract().asString())
                .isEqualTo("no drive do usuario");
        assertThat(provider.tokenCalls()).isGreaterThan(depoisDoStore);
        assertThat(rowsFor(owner)).isEqualTo(1);

        // o usuario revoga: a proxima leitura pede consentimento de novo, sem vazar nada
        given().delete("/consent/{o}", owner).then().statusCode(204);
        String resposta = given().get("/attachments/{id}/content", id).then().statusCode(409)
                .body("error", equalTo("consent_required")).extract().asString();
        assertThat(resposta).doesNotContain("refresh-").doesNotContain("acesso-").doesNotContain("app-secret");
    }

    @Test
    void consentSemTerConcedidoPedeConsentimento() {
        given().contentType(ContentType.JSON)
                .body(body("ninguem-" + UUID.randomUUID(), "dropbox", "x.txt", "x"))
                .post("/attachments").then().statusCode(409).body("error", equalTo("consent_required"));
    }

    @Test
    void donosDiferentesNaoMisturamAnexos() {
        String a = "dono-a-" + UUID.randomUUID();
        String b = "dono-b-" + UUID.randomUUID();
        given().contentType(ContentType.JSON).body(body(a, "filesystem", "a.txt", "a")).post("/attachments")
                .then().statusCode(201);
        given().contentType(ContentType.JSON).body(body(b, "filesystem", "b.txt", "b")).post("/attachments")
                .then().statusCode(201);

        List<String> doA = given().queryParam("ownerKey", a).get("/attachments").then().statusCode(200)
                .extract().jsonPath().getList("filename");

        assertThat(doA).containsExactly("a.txt");
    }
}
