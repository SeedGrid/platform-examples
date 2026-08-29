package br.com.seedgrid.resilienceexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Prova o rate limiting declarativo do seedgrid-resilience-core em dois algoritmos distintos.
 * Cada teste usa seu proprio endpoint/configKey ("greeting" e "checkout"), com estado isolado
 * no cache in-memory - a ordem de execucao entre os dois testes nao importa.
 */
@QuarkusTest
class RateLimitTest {

    @Test
    void fixedWindow_allowsUpToPermits_thenRateLimitsWithHeaders() {
        // permits=3 numa janela de 10s (configKey="greeting") - as 3 primeiras passam.
        for (int i = 1; i <= 3; i++) {
            given()
                    .when().get("/api/greeting")
                    .then()
                    .statusCode(200)
                    .header("X-RateLimit-Limit", equalTo("3"))
                    .body("message", equalTo("hello"));
        }

        // A 4a chamada dentro da mesma janela de 10s estoura o limite -> 429 com os headers
        // X-RateLimit-* + Retry-After do http.RateLimitExceptionMapper. (Ate a primeira build
        // da 2026.07.08 a lib empacotava um segundo ExceptionMapper duplicado sem os headers
        // X-RateLimit-*, tornando o contrato do 429 ambiguo; o duplicado foi removido.)
        given()
                .when().get("/api/greeting")
                .then()
                .statusCode(429)
                .header("X-RateLimit-Limit", equalTo("3"))
                .header("X-RateLimit-Remaining", equalTo("0"))
                .header("Retry-After", notNullValue());
    }

    @Test
    void tokenBucket_allowsBurstUpToCapacity_thenRateLimits() {
        // burstCapacity=5 (configKey="checkout") - o balde comeca cheio, entao as 5 primeiras
        // chamadas passam mesmo com permits=2/min (rajada controlada).
        for (int i = 1; i <= 5; i++) {
            given()
                    .when().get("/api/checkout")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("ok"));
        }

        // Balde vazio: a 6a chamada e negada ate repor pelo menos 1 token.
        given()
                .when().get("/api/checkout")
                .then()
                .statusCode(429)
                .header("Retry-After", notNullValue());
    }
}
