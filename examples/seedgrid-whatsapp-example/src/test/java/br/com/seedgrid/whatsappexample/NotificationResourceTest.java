package br.com.seedgrid.whatsappexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Fluxo completo via HTTP: POST /api/notifications envia (ou simula falha de) uma mensagem
 * WhatsApp atraves da extension seedgrid-whatsapp e persiste o resultado; GET /api/notifications
 * lista o historico. O provider externo (Evolution API) e' substituido por {@link StubWhatsAppApi}
 * — nao ha nenhuma chamada de rede real nestes testes.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class NotificationResourceTest {

    @BeforeEach
    void resetStub() {
        StubWhatsAppApi.reset();
    }

    @Test
    void sendTextMessage_success_persistsSentNotificationAndCallsWhatsAppApi() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"phoneNumber":"11999999999","message":"Ola! Seu pedido foi confirmado."}
                """)
        .when()
            .post("/api/notifications")
        .then()
            .statusCode(201)
            .body("status", equalTo("SENT"))
            .body("providerMessageId", equalTo("stub-message-id-123"))
            .body("phoneNumber", equalTo("11999999999"))
            .body("channelId", equalTo("example-instance"))
            .body("errorMessage", equalTo(null));

        assertAll("WhatsAppApi foi chamado com os parametros corretos",
            () -> assertEquals(1, StubWhatsAppApi.calls.size()),
            () -> assertEquals("example-instance", StubWhatsAppApi.calls.get(0).channelId()),
            () -> assertEquals("11999999999", StubWhatsAppApi.calls.get(0).message().number()),
            () -> assertEquals("Ola! Seu pedido foi confirmado.", StubWhatsAppApi.calls.get(0).message().text())
        );
    }

    @Test
    void sendTextMessage_providerFailure_persistsFailedNotificationWithErrorMessage() {
        StubWhatsAppApi.failNext = true;

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"phoneNumber":"11988888888","message":"Mensagem que vai falhar"}
                """)
        .when()
            .post("/api/notifications")
        .then()
            .statusCode(502)
            .body("status", equalTo("FAILED"))
            .body("errorMessage", equalTo("Simulated Evolution API outage"))
            .body("providerMessageId", equalTo(null));

        assertEquals(1, StubWhatsAppApi.calls.size());
    }

    @Test
    void sendTextMessage_blankPhoneNumber_rejectedWithBadRequest() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"phoneNumber":"","message":"x"}
                """)
        .when()
            .post("/api/notifications")
        .then()
            .statusCode(400);

        assertTrue(StubWhatsAppApi.calls.isEmpty(), "validacao deve barrar antes de chamar o WhatsAppApi");
    }

    @Test
    void listAll_returnsHistoryIncludingSuccessAndFailure() {
        given().contentType(ContentType.JSON)
            .body("""
                {"phoneNumber":"11999999999","message":"primeira"}
                """)
            .post("/api/notifications");

        StubWhatsAppApi.failNext = true;
        given().contentType(ContentType.JSON)
            .body("""
                {"phoneNumber":"11988888888","message":"segunda"}
                """)
            .post("/api/notifications");

        given()
        .when()
            .get("/api/notifications")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(2))
            .body("[0].id", notNullValue());

        assertFalse(StubWhatsAppApi.calls.isEmpty());
    }
}
