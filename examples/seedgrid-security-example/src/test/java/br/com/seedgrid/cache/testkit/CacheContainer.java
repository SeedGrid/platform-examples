package br.com.seedgrid.cache.testkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ulimit;

/**
 * Container de um servidor compativel com RESP (Redis, Valkey ou Dragonfly) com <b>porta FIXA no host</b>.
 *
 * <p><b>Por que porta fixa:</b> o mapeamento aleatorio do Testcontainers muda quando o container e
 * recriado. Aqui o container e criado uma unica vez e depois so e {@link #stop() parado} e
 * {@link #start() iniciado de novo}: o endereco ({@link #host()}:{@link #port()}) nunca muda, entao o
 * cliente (Vert.x Redis) reconecta sozinho ao mesmo endereco. Persistencia de dados NAO e garantida
 * apos stop/start (basta reconectar e escrever).</p>
 *
 * <p>Operacoes de ciclo de vida (idempotentes e seguras para uso por varias threads):</p>
 * <ul>
 *   <li>{@link #start()} - a primeira chamada cria e sobe; as seguintes religam o container parado
 *       (ou despausam, se estiver pausado) e so retornam com o servidor respondendo {@code PING};</li>
 *   <li>{@link #stop()} - para o container (conexoes recusadas);</li>
 *   <li>{@link #pause()} / {@link #unpause()} - congela/descongela o container (servidor "travado":
 *       a conexao TCP e aceita mas nada responde), equivalente a {@code docker pause};</li>
 *   <li>{@link #endpoint()} - URL {@code redis://host:porta} para criar clientes;</li>
 *   <li>{@link #close()} - remove o container (fim do teste).</li>
 * </ul>
 *
 * <p>Imagens (as mesmas dos ITs atuais): {@code redis:7-alpine}, {@code valkey/valkey:8-alpine} e
 * {@code docker.dragonflydb.io/dragonflydb/dragonfly:latest}.</p>
 */
public final class CacheContainer implements AutoCloseable {

    /** Tipo de servidor e imagem usada. */
    public enum Kind {
        REDIS("redis:7-alpine"),
        VALKEY("valkey/valkey:8-alpine"),
        DRAGONFLY("docker.dragonflydb.io/dragonflydb/dragonfly:latest");

        private final String image;

        Kind(String image) {
            this.image = image;
        }

        /** Nome da imagem Docker. */
        public String image() {
            return image;
        }
    }

    private static final int INTERNAL_PORT = 6379;

    private final Kind kind;
    private final int hostPort;
    private final GenericContainer<?> container;
    private boolean created;
    private boolean paused;

    private CacheContainer(Kind kind) {
        this.kind = kind;
        this.hostPort = freePort();
        this.container = newContainer(kind, hostPort);
    }

    /** Container Redis ({@code redis:7-alpine}); ainda nao iniciado. */
    public static CacheContainer redis() {
        return new CacheContainer(Kind.REDIS);
    }

    /** Container Valkey ({@code valkey/valkey:8-alpine}); ainda nao iniciado. */
    public static CacheContainer valkey() {
        return new CacheContainer(Kind.VALKEY);
    }

    /** Container Dragonfly; ainda nao iniciado. */
    public static CacheContainer dragonfly() {
        return new CacheContainer(Kind.DRAGONFLY);
    }

    /** Container do {@code kind} informado; ainda nao iniciado. */
    public static CacheContainer of(Kind kind) {
        return new CacheContainer(kind);
    }

    @SuppressWarnings("resource")
    private static GenericContainer<?> newContainer(Kind kind, int hostPort) {
        // Espera por PING pelo host (nao pelo exec interno de porta do Testcontainers, que falha de forma
        // intermitente no Podman; o Dragonfly tambem nao escreve o log de prontidao no stdout).
        GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse(kind.image()))
                .withExposedPorts(INTERNAL_PORT)
                .waitingFor(new AbstractWaitStrategy() {
                    @Override
                    protected void waitUntilReady() {
                        pingUntilReady(waitStrategyTarget.getHost(), hostPort, startupTimeout);
                    }
                })
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                        .withPortBindings(new PortBinding(Ports.Binding.bindPort(hostPort),
                                new ExposedPort(INTERNAL_PORT))));
        if (kind == Kind.DRAGONFLY) {
            // Dragonfly recomenda memlock ilimitado (mesmo ajuste do SeedGridCacheDragonflyIT)
            c.withCreateContainerCmdModifier(cmd -> {
                var hostConfig = cmd.getHostConfig();
                if (hostConfig != null) {
                    hostConfig.withUlimits(new Ulimit[] {new Ulimit("memlock", -1L, -1L)});
                }
            });
        }
        return c;
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("sem porta livre para o container de cache", e);
        }
    }

    /**
     * Sobe o container. Na primeira chamada cria-o; depois de {@link #stop()} religa o mesmo container
     * (mesmo endereco); depois de {@link #pause()} despausa. Retorna quando o servidor responde PING.
     *
     * @return este container
     */
    public synchronized CacheContainer start() {
        if (!created) {
            container.start();
            created = true;
        } else if (paused) {
            unpause();
        } else if (!container.isRunning()) {
            container.getDockerClient().startContainerCmd(container.getContainerId()).exec();
        }
        awaitReady(Duration.ofSeconds(60));
        return this;
    }

    /** Para o container (o servidor deixa de aceitar conexoes). Pode ser religado com {@link #start()}. */
    public synchronized void stop() {
        requireCreated("stop");
        if (paused) {
            unpause();
        }
        if (container.isRunning()) {
            container.getDockerClient().stopContainerCmd(container.getContainerId()).withTimeout(2).exec();
        }
    }

    /** Congela o container ({@code docker pause}): conexoes ficam penduradas, sem resposta. */
    public synchronized void pause() {
        requireCreated("pause");
        if (!paused) {
            container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
            paused = true;
        }
    }

    /** Descongela o container ({@code docker unpause}). */
    public synchronized void unpause() {
        requireCreated("unpause");
        if (paused) {
            container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
            paused = false;
        }
    }

    /** {@code true} se o container esta em execucao (pausado conta como em execucao). */
    public synchronized boolean isRunning() {
        return created && container.isRunning();
    }

    /** {@code true} se o container esta pausado. */
    public synchronized boolean isPaused() {
        return paused;
    }

    /** Host onde o container escuta. */
    public String host() {
        return container.getHost();
    }

    /** Porta FIXA no host (nao muda entre stop/start). */
    public int port() {
        return hostPort;
    }

    /** URL de conexao {@code redis://host:porta}; estavel durante toda a vida do objeto. */
    public String endpoint() {
        return "redis://" + host() + ":" + hostPort;
    }

    /** Tipo de servidor. */
    public Kind kind() {
        return kind;
    }

    /** Nome curto do tipo ("redis", "valkey", "dragonfly"), util em mensagens e nomes de log. */
    public String name() {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Espera o servidor responder {@code PING} com {@code +PONG} (laco com timeout; sem sleep fixo).
     *
     * @param max tempo maximo de espera
     */
    public void awaitReady(Duration max) {
        pingUntilReady(host(), hostPort, max);
    }

    private static void pingUntilReady(String host, int port, Duration max) {
        Awaitility.await("servidor RESP respondendo PING em " + host + ":" + port)
                .atMost(max)
                .pollInterval(Duration.ofMillis(100))
                .ignoreExceptions()
                .until(() -> ping(host, port));
    }

    private static boolean ping(String host, int port) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 500);
            s.setSoTimeout(500);
            OutputStream out = s.getOutputStream();
            out.write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = s.getInputStream();
            byte[] buf = new byte[7];
            int n = in.read(buf);
            return n >= 5 && new String(buf, 0, n, StandardCharsets.US_ASCII).startsWith("+PONG");
        }
    }

    private void requireCreated(String op) {
        if (!created) {
            throw new IllegalStateException("container ainda nao foi iniciado (" + op + ")");
        }
    }

    /** Remove o container (fim do teste). Seguro chamar mais de uma vez. */
    @Override
    public synchronized void close() {
        if (created) {
            try {
                if (paused) {
                    unpause();
                }
            } catch (RuntimeException ignored) {
                // melhor esforco: o stop abaixo remove o container de qualquer forma
            }
            String id = container.getContainerId();
            com.github.dockerjava.api.DockerClient docker = container.getDockerClient();
            container.stop();
            created = false;
            paused = false;
            awaitRemoved(docker, id);
        }
    }

    /**
     * Espera o runtime (Podman/Docker) terminar de remover o container e a porta fixa ser liberada, para
     * que o proximo container da JVM nao comece em cima de uma remocao ainda em andamento (inspecoes
     * transitorias derrubavam a espera de porta de outros ITs logo depois). Melhor esforco: nunca falha.
     */
    private void awaitRemoved(com.github.dockerjava.api.DockerClient docker, String id) {
        try {
            Awaitility.await("container " + name() + " removido")
                    .atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                    .until(() -> {
                        try {
                            docker.inspectContainerCmd(id).exec();
                            return false;
                        } catch (com.github.dockerjava.api.exception.NotFoundException gone) {
                            return true;
                        }
                    });
            Awaitility.await("porta " + hostPort + " liberada")
                    .atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                    .until(() -> {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(host(), hostPort), 300);
                            return false;
                        } catch (IOException closed) {
                            return true;
                        }
                    });
        } catch (RuntimeException ignored) {
            // melhor esforco
        }
    }
}
