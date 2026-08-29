package br.com.seedgrid.cacheexample;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Prova o cache-aside do CatalogService (backend in-memory DefaultSeedGridCache) via HTTP:
 * 1a leitura = MISS (roda a operacao cara), 2a = HIT (nao roda), evict força MISS de novo.
 *
 * <p>Usa um id unico deste teste e le o contador global /items/_stats como baseline (o cache e
 * o contador sao @ApplicationScoped, vivem por toda a JVM de teste) - por isso as asserts sao
 * sobre o DELTA do contador, nunca sobre valor absoluto.</p>
 */
@QuarkusTest
class CacheHitMissTest {

    private static final String ID = "hitmiss-42";

    private long stats() {
        return given().when().get("/items/_stats")
                .then().statusCode(200)
                .extract().jsonPath().getLong("realExecutions");
    }

    @Test
    void firstCallMisses_secondHits_evictForcesMissAgain() {
        long base = stats();

        // 1a chamada: MISS - a operacao cara roda (contador +1).
        given().when().get("/items/" + ID)
                .then().statusCode(200)
                .body("hit", equalTo(false))
                .body("id", equalTo(ID));
        long afterFirst = stats();
        assert afterFirst == base + 1 : "MISS deveria rodar a operacao real 1x (delta=1)";

        // 2a chamada, mesmo id: HIT - vem do cache, a operacao cara NAO roda (contador nao sobe).
        given().when().get("/items/" + ID)
                .then().statusCode(200)
                .body("hit", equalTo(true));
        long afterHit = stats();
        assert afterHit == base + 1 : "HIT nao pode rodar a operacao real (delta continua 1)";

        // Evict: remove do cache -> proxima leitura volta a ser MISS (contador +1).
        given().when().delete("/items/" + ID)
                .then().statusCode(204);

        given().when().get("/items/" + ID)
                .then().statusCode(200)
                .body("hit", equalTo(false));
        long afterEvict = stats();
        assert afterEvict == base + 2 : "apos evict, a leitura deve ser MISS de novo (delta=2)";
    }

    @Test
    void hitReturnsSameValueAsMiss() {
        String id = "consistency-7";

        String miss = given().when().get("/items/" + id)
                .then().statusCode(200).body("hit", equalTo(false))
                .extract().jsonPath().getString("value");

        String hit = given().when().get("/items/" + id)
                .then().statusCode(200).body("hit", equalTo(true))
                .extract().jsonPath().getString("value");

        assert miss.equals(hit) : "o valor cacheado (HIT) deve ser identico ao computado (MISS)";
    }
}
