package br.com.seedgrid.filemanagementexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Prova o fluxo REST completo do DocumentResource (backend filesystem, base-path do
 * application.properties de teste): POST base64 -> GET devolve os mesmos bytes -> metadata correto
 * -> DELETE -> GET posterior devolve 404.
 */
@QuarkusTest
class DocumentResourceTest {

    // Sem clearCache(): desde a lib 2026.07.08 o registry cacheia por type+config, entao o storage
    // do base-path deste app de teste nao e afetado pelos @TempDir das outras classes de teste.

    @Test
    void store_download_delete_lifecycle() {
        String content = "conteudo REST de exemplo - documento.txt";
        String base64 = Base64.getEncoder().encodeToString(content.getBytes());

        // 1. POST /documents -> 201 com a StorageKey (campo "key") + metadata
        String key = given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                      "filename": "documento.txt",
                      "prefix": "rest-test",
                      "contentType": "text/plain",
                      "contentBase64": "%s"
                    }
                    """.formatted(base64))
                .when().post("/documents")
                .then()
                .statusCode(201)
                .body("key", equalTo("rest-test/documento.txt"))
                .body("uri", equalTo("filesystem://rest-test/documento.txt"))
                .body("filename", equalTo("documento.txt"))
                .body("sizeBytes", equalTo(content.getBytes().length))
                .extract().jsonPath().getString("key");

        // 2. GET /documents/{key} -> devolve o conteudo original identico
        given()
                .when().get("/documents/" + key)
                .then()
                .statusCode(200)
                .body(equalTo(content));

        // 3. GET /documents/_meta/{key} -> metadata correto
        given()
                .when().get("/documents/_meta/" + key)
                .then()
                .statusCode(200)
                .body("filename", equalTo("documento.txt"))
                .body("sizeBytes", equalTo(content.getBytes().length))
                .body("lastModifiedAt", notNullValue());

        // 4. DELETE /documents/{key} -> 204
        given()
                .when().delete("/documents/" + key)
                .then()
                .statusCode(204);

        // 5. GET apos delete -> 404 (retrieve lanca StorageNotFoundException, mapeada pra 404)
        given()
                .when().get("/documents/" + key)
                .then()
                .statusCode(404);

        // 6. DELETE de novo -> 404
        given()
                .when().delete("/documents/" + key)
                .then()
                .statusCode(404);
    }

    @Test
    void post_invalidBase64_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                    { "filename": "x.txt", "contentBase64": "@@@nao-e-base64@@@" }
                    """)
                .when().post("/documents")
                .then()
                .statusCode(400);
    }
}
