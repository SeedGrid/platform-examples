package br.com.seedgrid.resilienceexample;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Prova que o rate limit nao deixa passar permits a mais sob concorrencia: 50 requisicoes
 * disparadas ao mesmo tempo contra um limite de 20 (janela fixa) e de 10 (token bucket) devem
 * resultar em EXATAMENTE 20 e 10 respostas 200, o resto 429. Endpoints proprios para nao dividir
 * estado com os outros testes.
 */
@QuarkusTest
class RateLimitConcurrencyTest {

    private static final int REQUESTS = 50;

    @Test
    void fixedWindow_under50ParallelCalls_letsExactlyPermitsThrough() throws Exception {
        int[] counts = fireInParallel("/api/burst-window");
        assertEquals(20, counts[0], "200 em janela fixa de 20 permits");
        assertEquals(REQUESTS - 20, counts[1], "o restante deve ser 429");
    }

    @Test
    void tokenBucket_under50ParallelCalls_letsExactlyBurstCapacityThrough() throws Exception {
        int[] counts = fireInParallel("/api/burst-bucket");
        assertEquals(10, counts[0], "200 no balde com rajada de 10");
        assertEquals(REQUESTS - 10, counts[1], "o restante deve ser 429");
    }

    /** Dispara REQUESTS chamadas liberadas pelo mesmo latch e devolve {quantidade de 200, quantidade de 429}. */
    private int[] fireInParallel(String path) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        try {
            CountDownLatch ready = new CountDownLatch(REQUESTS);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                Callable<Integer> call = () -> {
                    ready.countDown();
                    go.await();
                    return given().when().get(path).then().extract().statusCode();
                };
                futures.add(pool.submit(call));
            }
            ready.await();
            go.countDown();
            int ok = 0;
            int limited = 0;
            for (Future<Integer> f : futures) {
                int status = f.get();
                if (status == 200) {
                    ok++;
                } else if (status == 429) {
                    limited++;
                } else {
                    throw new AssertionError("status inesperado: " + status);
                }
            }
            return new int[] {ok, limited};
        } finally {
            pool.shutdownNow();
        }
    }
}
