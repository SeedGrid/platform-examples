package br.com.seedgrid.cache.testkit;

import java.time.Duration;

import org.awaitility.Awaitility;

import br.com.seedgrid.cache.core.failover.CacheFailoverStatus;
import br.com.seedgrid.cache.core.failover.FailoverCache;

/**
 * Esperas por condicao (Awaitility, nunca {@code Thread.sleep} fixo) sobre o estado do failover.
 *
 * <p>Estados: {@link FailoverCache.State#NORMAL} e {@link FailoverCache.State#FAILOVER}.
 * O padrao de espera maxima e {@link #DEFAULT_MAX} (60 s); sondagem a cada 100 ms.</p>
 */
public final class FailoverAwait {

    /** Espera maxima padrao. */
    public static final Duration DEFAULT_MAX = Duration.ofSeconds(60);

    private static final Duration POLL = Duration.ofMillis(100);

    private FailoverAwait() {
    }

    /**
     * Espera o {@link CacheFailoverStatus} chegar ao estado, por ate {@link #DEFAULT_MAX}.
     *
     * @param status estado do failover da aplicacao
     * @param expected estado esperado
     * @throws org.awaitility.core.ConditionTimeoutException se nao chegar a tempo
     */
    public static void awaitState(CacheFailoverStatus status, FailoverCache.State expected) {
        awaitState(status, expected, DEFAULT_MAX);
    }

    /**
     * Espera o {@link CacheFailoverStatus} chegar ao estado.
     *
     * @param status estado do failover da aplicacao
     * @param expected estado esperado
     * @param max tempo maximo
     * @throws org.awaitility.core.ConditionTimeoutException se nao chegar a tempo
     */
    public static void awaitState(CacheFailoverStatus status, FailoverCache.State expected, Duration max) {
        Awaitility.await("failover em " + expected).atMost(max).pollInterval(POLL)
                .until(() -> status.state() == expected);
    }

    /**
     * Espera o {@link FailoverCache} chegar ao estado, por ate {@link #DEFAULT_MAX}.
     *
     * @param cache cache com failover
     * @param expected estado esperado
     * @throws org.awaitility.core.ConditionTimeoutException se nao chegar a tempo
     */
    public static void awaitState(FailoverCache cache, FailoverCache.State expected) {
        awaitState(cache, expected, DEFAULT_MAX);
    }

    /**
     * Espera o {@link FailoverCache} chegar ao estado.
     *
     * @param cache cache com failover
     * @param expected estado esperado
     * @param max tempo maximo
     * @throws org.awaitility.core.ConditionTimeoutException se nao chegar a tempo
     */
    public static void awaitState(FailoverCache cache, FailoverCache.State expected, Duration max) {
        Awaitility.await("failover em " + expected).atMost(max).pollInterval(POLL)
                .until(() -> cache.state() == expected);
    }

    /**
     * Atalho: espera {@code NORMAL}.
     *
     * @param cache cache com failover
     */
    public static void awaitNormal(FailoverCache cache) {
        awaitState(cache, FailoverCache.State.NORMAL);
    }

    /**
     * Atalho: espera {@code FAILOVER}.
     *
     * @param cache cache com failover
     */
    public static void awaitFailover(FailoverCache cache) {
        awaitState(cache, FailoverCache.State.FAILOVER);
    }
}
