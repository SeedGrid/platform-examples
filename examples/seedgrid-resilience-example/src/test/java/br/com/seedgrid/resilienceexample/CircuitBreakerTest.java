package br.com.seedgrid.resilienceexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Prova o ciclo de vida completo do circuit breaker declarativo do seedgrid-resilience-core:
 * CLOSED -> OPEN -> HALF_OPEN -> CLOSED, contra o CatalogService (configurado com
 * slidingWindowSize=minimumNumberOfCalls=4, failureRateThreshold=50%, openStateDurationMs=3s,
 * permittedCallsInHalfOpen=2, fallbackMethod="catalogFallback").
 *
 * <p>Roda como um unico fluxo sequencial (nao testes independentes) porque o estado do circuito
 * e do contador de chamadas reais e compartilhado (ApplicationScoped) durante toda a vida da
 * aplicacao de teste.</p>
 */
@QuarkusTest
class CircuitBreakerTest {

    private static final String PRODUCT_ID = "42";

    @Test
    void circuitOpens_shortCircuits_thenRecoversThroughHalfOpen() throws InterruptedException {
        setChaos(true);

        // 1-4: com chaos ligado, o corpo real do CatalogService sempre falha. minimumNumberOfCalls
        // = slidingWindowSize = 4, entao apos a 4a falha o circuito calcula failureRate=100% >=
        // threshold(50%) e abre. Como ha fallbackMethod, todas retornam 200 via fallback.
        for (int i = 1; i <= 4; i++) {
            callCatalog_expectFallback();
        }
        assertEquals(4, realCallCount(), "as 4 chamadas devem ter batido no corpo real (falhando)");

        // 5: circuito ja ABERTO - o interceptor nem chama o corpo real, vai direto pro fallback.
        callCatalog_expectFallback();
        assertEquals(4, realCallCount(), "com o circuito aberto, a chamada real NAO deve acontecer");

        // Corrige a causa raiz (como seria numa recuperacao real de um servico downstream).
        setChaos(false);

        // Aguarda o circuito sair de OPEN pra HALF_OPEN (openStateDurationMs=3000ms).
        Thread.sleep(3_200);

        // 6 e 7: HALF_OPEN permite permittedCallsInHalfOpen=2 chamadas de sonda; como chaos esta
        // desligado, ambas tem sucesso real e, na 2a, o circuito fecha (CLOSED).
        callCatalog_expectSuccess();
        assertEquals(5, realCallCount(), "a 1a sonda em HALF_OPEN deve chamar o corpo real");

        callCatalog_expectSuccess();
        assertEquals(6, realCallCount(), "a 2a sonda em HALF_OPEN deve chamar o corpo real e fechar o circuito");

        // 8: circuito CLOSED de novo - chamada normal, corpo real executa.
        callCatalog_expectSuccess();
        assertEquals(7, realCallCount());
    }

    private static void setChaos(boolean enabled) {
        given()
                .when().post("/api/catalog/chaos/" + enabled)
                .then()
                .statusCode(200)
                .body("chaos", equalTo(enabled));
    }

    private static void callCatalog_expectFallback() {
        given()
                .when().get("/api/catalog/" + PRODUCT_ID)
                .then()
                .statusCode(200)
                .body("result", equalTo("produto-indisponivel:" + PRODUCT_ID));
    }

    private static void callCatalog_expectSuccess() {
        given()
                .when().get("/api/catalog/" + PRODUCT_ID)
                .then()
                .statusCode(200)
                .body("result", equalTo("produto-" + PRODUCT_ID));
    }

    private static int realCallCount() {
        return given()
                .when().get("/api/catalog/_stats")
                .then()
                .statusCode(200)
                .extract().jsonPath().getInt("realCalls");
    }
}
